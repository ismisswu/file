package com.dji.analysis.analyzer;

import com.dji.analysis.model.*;
import com.dji.analysis.stats.*;
import com.dji.analysis.utils.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyses a single spectral-index image (NDVI or NDRE).
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>Load image and convert to flat float array of physical index values.</li>
 *   <li>Compute full descriptive statistics (mean, median, std-dev, skewness,
 *       kurtosis, percentiles).</li>
 *   <li>Build value-distribution histogram.</li>
 *   <li>Classify pixels into agronomic zones.</li>
 *   <li>Identify spatially contiguous stress clusters.</li>
 *   <li>Compute spatial statistics: heterogeneity and Moran's I.</li>
 *   <li>Generate agronomic interpretation text.</li>
 * </ol>
 */
public class NDVIAnalyzer {

    private final AnalysisConfig config;

    // NDVI agronomic zones (lower bound inclusive, upper bound exclusive)
    private static final ZoneInfo[] NDVI_ZONES = {
        new ZoneInfo("No vegetation / Water / Built-up", "无植被/水体/建筑", -1.0f, 0.10f, "⬛"),
        new ZoneInfo("Bare soil / Sparse cover",         "裸土/极稀疏植被",  0.10f, 0.25f, "🟤"),
        new ZoneInfo("Sparse vegetation (stressed)",     "稀疏植被(胁迫)",   0.25f, 0.40f, "🟡"),
        new ZoneInfo("Moderate vegetation",              "中等植被",          0.40f, 0.55f, "🟢"),
        new ZoneInfo("Healthy / Dense vegetation",       "健康/茂密植被",     0.55f, 0.70f, "🌿"),
        new ZoneInfo("Very dense / Peak-season crop",    "极茂盛/峰期作物",   0.70f, 1.01f, "🌲"),
    };

    public NDVIAnalyzer(AnalysisConfig config) {
        this.config = config;
    }

    public IndexAnalysisResult analyze(File imageFile) throws IOException {
        BufferedImage img = ImageLoader.loadImage(imageFile);
        float[] values = pixelsToIndexValues(img);
        boolean[] valid = ImageLoader.buildValidMask(img, true);

        IndexAnalysisResult result = new IndexAnalysisResult();
        result.setIndexType(IndexAnalysisResult.IndexType.NDVI);

        int totalPx = img.getWidth() * img.getHeight();
        int validPx = StatisticsCalculator.countValid(valid, totalPx);
        result.setTotalValidPixels(validPx);
        result.setNoDataPixels(totalPx - validPx);

        // ---- Basic statistics ----
        result.setMean(StatisticsCalculator.mean(values, valid));
        result.setMedian(StatisticsCalculator.median(values, valid));
        result.setStdDev(StatisticsCalculator.stdDev(values, valid));
        result.setMin(StatisticsCalculator.min(values, valid));
        result.setMax(StatisticsCalculator.max(values, valid));
        result.setSkewness(StatisticsCalculator.skewness(values, valid));
        result.setKurtosis(StatisticsCalculator.excessKurtosis(values, valid));
        result.setP5(StatisticsCalculator.percentile(values, valid, 5));
        result.setP25(StatisticsCalculator.percentile(values, valid, 25));
        result.setP75(StatisticsCalculator.percentile(values, valid, 75));
        result.setP95(StatisticsCalculator.percentile(values, valid, 95));

        // ---- Histogram ----
        double lo = -1.0, hi = 1.0;
        int bins = config.getHistogramBins();
        int[] counts = StatisticsCalculator.histogram(values, valid, bins, lo, hi);
        double[] edges = StatisticsCalculator.histogramEdges(bins, lo, hi);
        result.setHistogramCounts(counts);
        result.setHistogramBinEdges(edges);

        // ---- Zone classification ----
        List<ZoneInfo> zones = classifyZones(values, valid, validPx);
        result.setZones(zones);

        // ---- Spatial statistics ----
        int rows = img.getHeight(), cols = img.getWidth();
        result.setSpatialHeterogeneity(
            SpatialAnalysis.spatialHeterogeneity(values, valid, rows, cols, 2));
        result.setMoransI(
            SpatialAnalysis.moransI(values, valid, rows, cols));

        // ---- Stress clusters ----
        float stressThresh = config.getNdviStressThreshold();
        boolean[][] stressMask = buildStressMask(values, valid, rows, cols, stressThresh);
        List<DiseaseCluster> clusters = SpatialAnalysis.labelComponents(stressMask, config.getMinClusterSizePx());
        result.setStressClusters(clusters);
        result.setStressClusterCount(clusters.size());

        long stressedPx = 0;
        if (valid == null) {
            for (float v : values) if (v < stressThresh) stressedPx++;
        } else {
            for (int i = 0; i < values.length; i++) if ((valid[i]) && values[i] < stressThresh) stressedPx++;
        }
        result.setStressedAreaFraction(validPx == 0 ? 0 : (double) stressedPx / validPx);

        // ---- Interpretation ----
        result.setInterpretationZh(interpretZh(result));
        result.setInterpretationEn(interpretEn(result));

        return result;
    }

    // -----------------------------------------------------------------------
    // Pixel decoding
    // -----------------------------------------------------------------------

    private float[] pixelsToIndexValues(BufferedImage img) {
        int totalPx = img.getWidth() * img.getHeight();
        float[] values = new float[totalPx];

        AnalysisConfig.ImageFormat fmt = config.getNdviFormat();
        if (fmt == AnalysisConfig.ImageFormat.AUTO_DETECT) {
            fmt = ImageLoader.isGrayscale(img) ? AnalysisConfig.ImageFormat.GRAYSCALE_8BIT
                                               : AnalysisConfig.ImageFormat.COLOR_DJI_NDVI;
        }

        float minVal = config.getNdviMinValue();
        float maxVal = config.getNdviMaxValue();

        if (fmt == AnalysisConfig.ImageFormat.GRAYSCALE_8BIT
                || fmt == AnalysisConfig.ImageFormat.GRAYSCALE_16BIT) {
            float[] norm = ImageLoader.extractGrayscaleNormalized(img);
            for (int i = 0; i < norm.length; i++) {
                values[i] = ColorScaleDecoder.toPhysicalValue(norm[i], minVal, maxVal);
            }
        } else {
            int[] argb = ImageLoader.extractARGB(img);
            for (int i = 0; i < argb.length; i++) {
                int r = (argb[i] >> 16) & 0xFF;
                int g = (argb[i] >> 8) & 0xFF;
                int b = argb[i] & 0xFF;
                float norm;
                if (fmt == AnalysisConfig.ImageFormat.COLOR_JET) {
                    norm = ColorScaleDecoder.decodeJet(r, g, b);
                } else if (fmt == AnalysisConfig.ImageFormat.COLOR_RED_TO_GREEN) {
                    norm = ColorScaleDecoder.decodeRedToGreen(r, g, b);
                } else { // COLOR_DJI_NDVI default
                    norm = ColorScaleDecoder.decodeDjiNdvi(r, g, b);
                }
                values[i] = ColorScaleDecoder.toPhysicalValue(norm, minVal, maxVal);
            }
        }
        return values;
    }

    // -----------------------------------------------------------------------
    // Zone classification
    // -----------------------------------------------------------------------

    private List<ZoneInfo> classifyZones(float[] values, boolean[] valid, int validPx) {
        List<ZoneInfo> zones = new ArrayList<>();
        for (ZoneInfo template : NDVI_ZONES) {
            ZoneInfo zone = new ZoneInfo(template.getName(), template.getNameZh(),
                                         template.getLowerBound(), template.getUpperBound(),
                                         template.getColorIndicator());
            int count = 0;
            double sum = 0, sum2 = 0;
            for (int i = 0; i < values.length; i++) {
                if ((valid == null || valid[i]) && zone.contains(values[i])) {
                    count++;
                    sum += values[i];
                    sum2 += values[i] * (double) values[i];
                }
            }
            zone.setPixelCount(count);
            zone.setCoveragePercent(validPx == 0 ? 0 : 100.0 * count / validPx);
            if (count > 0) {
                double mean = sum / count;
                zone.setMeanValue(mean);
                zone.setStdDev(Math.sqrt(Math.max(0, sum2 / count - mean * mean)));
            }
            zones.add(zone);
        }
        return zones;
    }

    // -----------------------------------------------------------------------
    // Stress mask
    // -----------------------------------------------------------------------

    private static boolean[][] buildStressMask(float[] values, boolean[] valid,
                                                int rows, int cols, float threshold) {
        boolean[][] mask = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                mask[r][c] = (valid == null || valid[i]) && values[i] < threshold;
            }
        }
        return mask;
    }

    // -----------------------------------------------------------------------
    // Interpretation
    // -----------------------------------------------------------------------

    private String interpretZh(IndexAnalysisResult r) {
        double mean = r.getMean();
        double stressed = r.getStressedAreaFraction() * 100;
        String moranDesc = r.getMoransI() > 0.3 ? "空间聚集" : (r.getMoransI() < -0.1 ? "随机分散" : "弱空间相关");
        return String.format(
            "NDVI均值=%.3f，中位数=%.3f，标准差=%.3f（%s）。" +
            "%.1f%%的像素处于胁迫阈值以下（< %.2f）。" +
            "识别到%d个胁迫聚集区，Moran's I=%.3f（%s）。",
            mean, r.getMedian(), r.getStdDev(),
            mean >= 0.55 ? "植被整体健康" : (mean >= 0.40 ? "植被中等" : "植被整体偏低"),
            stressed, config.getNdviStressThreshold(),
            r.getStressClusterCount(), r.getMoransI(), moranDesc
        );
    }

    private String interpretEn(IndexAnalysisResult r) {
        double mean = r.getMean();
        double stressed = r.getStressedAreaFraction() * 100;
        String moranDesc = r.getMoransI() > 0.3 ? "spatially clustered" :
                           (r.getMoransI() < -0.1 ? "randomly dispersed" : "weakly correlated");
        String healthLabel = mean >= 0.55 ? "generally healthy" :
                             (mean >= 0.40 ? "moderate" : "overall low – significant stress");
        return String.format(
            "NDVI mean=%.3f, median=%.3f, std=%.3f (vegetation is %s). " +
            "%.1f%% of pixels fall below the stress threshold (< %.2f). " +
            "%d stress cluster(s) detected. Moran's I=%.3f (%s).",
            mean, r.getMedian(), r.getStdDev(), healthLabel,
            stressed, config.getNdviStressThreshold(),
            r.getStressClusterCount(), r.getMoransI(), moranDesc
        );
    }
}
