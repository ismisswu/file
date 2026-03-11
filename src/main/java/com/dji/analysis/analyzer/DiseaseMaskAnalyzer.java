package com.dji.analysis.analyzer;

import com.dji.analysis.model.*;
import com.dji.analysis.stats.SpatialAnalysis;
import com.dji.analysis.utils.ImageLoader;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Analyses a disease mask image produced by DJI Terra / AI disease-detection software.
 *
 * <h3>Supported mask formats</h3>
 * <ul>
 *   <li><b>Binary mask</b> – white/bright pixels are diseased, black = healthy.</li>
 *   <li><b>Multi-class colour mask</b> – each colour encodes a different disease category.
 *       Automatically detects up to 8 dominant non-black colours.</li>
 * </ul>
 *
 * <h3>Analysis output</h3>
 * <ul>
 *   <li>Diseased area fraction and class breakdown.</li>
 *   <li>Connected-component cluster analysis: count, size distribution,
 *       compactness, bounding boxes.</li>
 *   <li>Spatial distribution pattern: isolated / clustered / edge-concentrated / patchy.</li>
 *   <li>Severity classification per cluster.</li>
 *   <li>Edge-concentration analysis to detect field-boundary infections.</li>
 * </ul>
 */
public class DiseaseMaskAnalyzer {

    /** RGB threshold below which a pixel is treated as "healthy / background". */
    private static final int BACKGROUND_THRESHOLD = 30;

    /** Number of pixels that defines cluster size categories. */
    private static final int LARGE_CLUSTER_PX  = 500;
    private static final int MEDIUM_CLUSTER_PX = 100;

    private final AnalysisConfig config;

    public DiseaseMaskAnalyzer(AnalysisConfig config) {
        this.config = config;
    }

    public DiseaseMaskResult analyze(File imageFile) throws IOException {
        BufferedImage img = ImageLoader.loadImage(imageFile);
        int w = img.getWidth(), h = img.getHeight();
        int[] argb = ImageLoader.extractARGB(img);

        DiseaseMaskResult result = new DiseaseMaskResult();
        result.setTotalPixels(w * h);

        // ---- Classify each pixel ----
        boolean[][] diseasedMask = new boolean[h][w];
        Map<Integer, Integer> colorCounts = new LinkedHashMap<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = argb[y * w + x];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                boolean isBackground = (r < BACKGROUND_THRESHOLD
                                     && g < BACKGROUND_THRESHOLD
                                     && b < BACKGROUND_THRESHOLD);

                if (!isBackground) {
                    diseasedMask[y][x] = true;
                    // Quantise colour to 64-level buckets for class detection
                    int qColor = quantiseColor(r, g, b);
                    colorCounts.merge(qColor, 1, Integer::sum);
                }
            }
        }

        int diseasedPx = countTrue(diseasedMask);
        int healthyPx  = w * h - diseasedPx;
        result.setDiseasedPixels(diseasedPx);
        result.setHealthyPixels(healthyPx);
        result.setDiseasedFraction((double) diseasedPx / (w * h));

        // ---- Class breakdown ----
        Map<String, Integer> classCounts = buildClassMap(colorCounts, diseasedPx);
        result.setClassPixelCounts(classCounts);
        Map<String, Double> classPct = new LinkedHashMap<>();
        classCounts.forEach((k, v) -> classPct.put(k, 100.0 * v / (w * h)));
        result.setClassPercentages(classPct);

        // ---- Cluster analysis ----
        List<DiseaseCluster> clusters = SpatialAnalysis.labelComponents(
                diseasedMask, config.getMinClusterSizePx());
        result.setClusters(clusters);
        result.setTotalClusters(clusters.size());

        int large = 0, medium = 0, small = 0;
        double maxSize = 0;
        double[] sizes = new double[clusters.size()];
        for (int i = 0; i < clusters.size(); i++) {
            int sz = clusters.get(i).getPixelCount();
            sizes[i] = sz;
            if (sz >= LARGE_CLUSTER_PX) large++;
            else if (sz >= MEDIUM_CLUSTER_PX) medium++;
            else small++;
            if (sz > maxSize) maxSize = sz;

            // Assign severity based on compactness and size
            clusters.get(i).setSeverityLabel(classifySeverity(clusters.get(i)));
        }
        result.setLargeClusters(large);
        result.setMediumClusters(medium);
        result.setSmallClusters(small);
        result.setMaxClusterSizePx(maxSize);

        if (!clusters.isEmpty()) {
            double meanSz = Arrays.stream(sizes).average().orElse(0);
            result.setMeanClusterSizePx(meanSz);
            double variance = Arrays.stream(sizes).map(s -> (s - meanSz) * (s - meanSz)).average().orElse(0);
            double stdDev = Math.sqrt(variance);
            result.setClusterDispersionIndex(meanSz == 0 ? 0 : stdDev / meanSz);
        }

        // ---- Severity breakdown ----
        int lightSev = 0, modSev = 0, sevSev = 0;
        for (DiseaseCluster c : clusters) {
            String sev = c.getSeverityLabel();
            if (sev != null && sev.contains("Light")) lightSev++;
            else if (sev != null && sev.contains("Moderate")) modSev++;
            else if (sev != null && sev.contains("Severe")) sevSev++;
        }
        result.setLightSeverityClusters(lightSev);
        result.setModerateSeverityClusters(modSev);
        result.setSevereSeverityClusters(sevSev);

        // ---- Spatial pattern classification ----
        result.setSpatialPattern(classifySpatialPattern(result, diseasedMask, h, w));

        // ---- Interpretation ----
        result.setInterpretationZh(interpretZh(result));
        result.setInterpretationEn(interpretEn(result));

        return result;
    }

    // -----------------------------------------------------------------------
    // Colour quantisation & class naming
    // -----------------------------------------------------------------------

    /** Reduce each channel to 4 levels (0, 85, 170, 255) for colour clustering. */
    private static int quantiseColor(int r, int g, int b) {
        int qr = (r / 64) * 64;
        int qg = (g / 64) * 64;
        int qb = (b / 64) * 64;
        return (qr << 16) | (qg << 8) | qb;
    }

    private static Map<String, Integer> buildClassMap(Map<Integer, Integer> colorCounts, int totalDiseased) {
        if (colorCounts.isEmpty()) {
            return Map.of("Diseased / 病害区域", totalDiseased);
        }

        // If there's essentially one colour family → treat as binary mask
        if (colorCounts.size() <= 3) {
            return Map.of("Diseased / 病害区域", totalDiseased);
        }

        // Multi-class: name classes by dominant hue
        Map<String, Integer> result = new LinkedHashMap<>();
        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(colorCounts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int classIdx = 1;
        for (Map.Entry<Integer, Integer> entry : sorted) {
            if (classIdx > 8) break;
            int qc = entry.getKey();
            int r = (qc >> 16) & 0xFF;
            int g = (qc >> 8) & 0xFF;
            int b = qc & 0xFF;
            String hueName = hueName(r, g, b);
            result.put("Class " + classIdx + " (" + hueName + ")", entry.getValue());
            classIdx++;
        }
        return result;
    }

    private static String hueName(int r, int g, int b) {
        if (r > g && r > b) return "Red/红";
        if (g > r && g > b) return "Green/绿";
        if (b > r && b > g) return "Blue/蓝";
        if (r > 200 && g > 200 && b < 100) return "Yellow/黄";
        if (r > 200 && g > 100 && b < 100) return "Orange/橙";
        if (r > 150 && g < 100 && b > 150) return "Magenta/品红";
        return "Mixed/混合";
    }

    // -----------------------------------------------------------------------
    // Severity classification
    // -----------------------------------------------------------------------

    private String classifySeverity(DiseaseCluster cluster) {
        int sz = cluster.getPixelCount();
        double compact = cluster.getCompactness();
        if (sz >= LARGE_CLUSTER_PX && compact > 0.5) return "重度/Severe";
        // Medium: either meets the size threshold, or is half-sized but compact enough
        if (sz >= MEDIUM_CLUSTER_PX || (sz >= MEDIUM_CLUSTER_PX / 2 && compact > 0.4)) return "中度/Moderate";
        return "轻度/Light";
    }

    // -----------------------------------------------------------------------
    // Spatial pattern classification
    // -----------------------------------------------------------------------

    private DiseaseMaskResult.SpatialPattern classifySpatialPattern(
            DiseaseMaskResult result, boolean[][] mask, int rows, int cols) {

        int nClusters = result.getTotalClusters();
        double diseasedPct = result.getDiseasedPercent();
        double dispersion = result.getClusterDispersionIndex();

        // Edge concentration
        int edgeBand = Math.min(rows, cols) / 10;
        double edgeConc = SpatialAnalysis.edgeConcentration(mask, edgeBand);

        if (edgeConc > 0.55) return DiseaseMaskResult.SpatialPattern.EDGE_CONCENTRATED;
        if (nClusters <= 3 && diseasedPct < 5) return DiseaseMaskResult.SpatialPattern.ISOLATED;
        if (dispersion < 0.5 && nClusters >= 5) return DiseaseMaskResult.SpatialPattern.CLUSTERED;
        if (diseasedPct > 20 && dispersion > 1.0) return DiseaseMaskResult.SpatialPattern.UNIFORMLY_DISPERSED;
        return DiseaseMaskResult.SpatialPattern.PATCHY;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static int countTrue(boolean[][] mask) {
        int count = 0;
        for (boolean[] row : mask) for (boolean v : row) if (v) count++;
        return count;
    }

    // -----------------------------------------------------------------------
    // Interpretation
    // -----------------------------------------------------------------------

    private String interpretZh(DiseaseMaskResult r) {
        return String.format(
            "病害掩膜分析：患病面积占比 %.2f%%（共%d个聚集区）。" +
            "大型病斑%d个，中型%d个，小型%d个。" +
            "空间分布模式：%s。重度病斑%d个，中度%d个，轻度%d个。",
            r.getDiseasedPercent(), r.getTotalClusters(),
            r.getLargeClusters(), r.getMediumClusters(), r.getSmallClusters(),
            r.getSpatialPattern() == null ? "未知" : r.getSpatialPattern().getLabel(),
            r.getSevereSeverityClusters(), r.getModerateSeverityClusters(), r.getLightSeverityClusters()
        );
    }

    private String interpretEn(DiseaseMaskResult r) {
        return String.format(
            "Disease mask: %.2f%% of area affected (%d cluster(s)). " +
            "Large: %d, Medium: %d, Small: %d. " +
            "Spatial pattern: %s. Severe: %d, Moderate: %d, Light: %d cluster(s).",
            r.getDiseasedPercent(), r.getTotalClusters(),
            r.getLargeClusters(), r.getMediumClusters(), r.getSmallClusters(),
            r.getSpatialPattern() == null ? "unknown" : r.getSpatialPattern().getLabel(),
            r.getSevereSeverityClusters(), r.getModerateSeverityClusters(), r.getLightSeverityClusters()
        );
    }
}
