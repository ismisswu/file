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
 * Analyses an NDRE (Normalised Difference Red Edge) image.
 *
 * <p>NDRE = (NIR – RedEdge) / (NIR + RedEdge)
 * NDRE is more sensitive than NDVI to chlorophyll content variations and
 * detects early-stage stress before visible symptoms appear in NDVI.
 * Typical NDRE range for crops: 0.20 – 0.60.
 *
 * <h3>Key differences from NDVI analysis</h3>
 * <ul>
 *   <li>Different zone thresholds calibrated for chlorophyll detection.</li>
 *   <li>Early-stress detection: compares NDRE zones against NDVI to stage stress progression.</li>
 *   <li>Separate chlorophyll-sufficiency assessment.</li>
 * </ul>
 */
public class NDREAnalyzer {

    private final AnalysisConfig config;

    // NDRE agronomic zones
    private static final ZoneInfo[] NDRE_ZONES = {
        new ZoneInfo("No/dead vegetation",           "无植被/枯死植被",  -1.0f, 0.05f, "⬛"),
        new ZoneInfo("Severe chlorophyll deficiency","严重叶绿素缺乏",    0.05f, 0.15f, "🟥"),
        new ZoneInfo("Moderate stress",              "中度胁迫",          0.15f, 0.25f, "🟠"),
        new ZoneInfo("Mild stress / sub-optimal",    "轻度胁迫",          0.25f, 0.35f, "🟡"),
        new ZoneInfo("Adequate chlorophyll",         "叶绿素充足",        0.35f, 0.50f, "🟢"),
        new ZoneInfo("High chlorophyll / vigorous",  "高叶绿素/旺盛生长", 0.50f, 1.01f, "🌿"),
    };

    public NDREAnalyzer(AnalysisConfig config) {
        this.config = config;
    }

    public IndexAnalysisResult analyze(File imageFile) throws IOException {
        BufferedImage img = ImageLoader.loadImage(imageFile);
        float[] values = pixelsToIndexValues(img);
        boolean[] valid = ImageLoader.buildValidMask(img, true);

        IndexAnalysisResult result = new IndexAnalysisResult();
        result.setIndexType(IndexAnalysisResult.IndexType.NDRE);

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
        int bins = config.getHistogramBins();
        int[] counts = StatisticsCalculator.histogram(values, valid, bins, -1.0, 1.0);
        double[] edges = StatisticsCalculator.histogramEdges(bins, -1.0, 1.0);
        result.setHistogramCounts(counts);
        result.setHistogramBinEdges(edges);

        // ---- Zone classification ----
        result.setZones(classifyZones(values, valid, validPx));

        // ---- Spatial statistics ----
        int rows = img.getHeight(), cols = img.getWidth();
        result.setSpatialHeterogeneity(
            SpatialAnalysis.spatialHeterogeneity(values, valid, rows, cols, 2));
        result.setMoransI(
            SpatialAnalysis.moransI(values, valid, rows, cols));

        // ---- Stress clusters ----
        float stressThresh = config.getNdreStressThreshold();
        boolean[][] stressMask = buildStressMask(values, valid, rows, cols, stressThresh);
        List<DiseaseCluster> clusters = SpatialAnalysis.labelComponents(stressMask, config.getMinClusterSizePx());
        result.setStressClusters(clusters);
        result.setStressClusterCount(clusters.size());

        long stressedPx = 0;
        for (int i = 0; i < values.length; i++) {
            if ((valid == null || valid[i]) && values[i] < stressThresh) stressedPx++;
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

        AnalysisConfig.ImageFormat fmt = config.getNdreFormat();
        if (fmt == AnalysisConfig.ImageFormat.AUTO_DETECT) {
            fmt = ImageLoader.isGrayscale(img) ? AnalysisConfig.ImageFormat.GRAYSCALE_8BIT
                                               : AnalysisConfig.ImageFormat.COLOR_DJI_NDVI;
        }

        float minVal = config.getNdreMinValue();
        float maxVal = config.getNdreMaxValue();

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
                } else {
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
        for (ZoneInfo template : NDRE_ZONES) {
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
        String chlorDesc = mean >= 0.35 ? "叶绿素含量正常" : (mean >= 0.20 ? "叶绿素轻度不足" : "叶绿素严重缺乏");
        return String.format(
            "NDRE均值=%.3f，中位数=%.3f，标准差=%.3f（%s）。" +
            "%.1f%%的像素低于胁迫阈值（< %.2f）。" +
            "识别到%d个低NDRE聚集区，偏度=%.3f（%s）。",
            mean, r.getMedian(), r.getStdDev(), chlorDesc,
            stressed, config.getNdreStressThreshold(),
            r.getStressClusterCount(),
            r.getSkewness(),
            r.getSkewness() < -0.3 ? "左偏分布，健康像素居多" :
            (r.getSkewness() > 0.3 ? "右偏分布，胁迫像素偏多" : "近似正态分布")
        );
    }

    private String interpretEn(IndexAnalysisResult r) {
        double mean = r.getMean();
        double stressed = r.getStressedAreaFraction() * 100;
        String chlorDesc = mean >= 0.35 ? "chlorophyll levels adequate" :
                           (mean >= 0.20 ? "mild chlorophyll deficiency" : "severe chlorophyll deficiency");
        return String.format(
            "NDRE mean=%.3f, median=%.3f, std=%.3f (%s). " +
            "%.1f%% of pixels below stress threshold (< %.2f). " +
            "%d low-NDRE cluster(s) identified. Skewness=%.3f.",
            mean, r.getMedian(), r.getStdDev(), chlorDesc,
            stressed, config.getNdreStressThreshold(),
            r.getStressClusterCount(), r.getSkewness()
        );
    }
}
