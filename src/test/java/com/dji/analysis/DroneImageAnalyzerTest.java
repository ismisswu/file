package com.dji.analysis;

import com.dji.analysis.analyzer.DiseaseMaskAnalyzer;
import com.dji.analysis.analyzer.NDREAnalyzer;
import com.dji.analysis.analyzer.NDVIAnalyzer;
import com.dji.analysis.model.*;
import com.dji.analysis.stats.DistributionHistogram;
import com.dji.analysis.stats.SpatialAnalysis;
import com.dji.analysis.stats.StatisticsCalculator;
import com.dji.analysis.utils.ColorScaleDecoder;
import com.dji.analysis.utils.ImageLoader;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the DJI 3M drone image analysis pipeline.
 *
 * <p>Because real drone images are not committed to the repository, tests
 * create synthetic PNG images programmatically and exercise every major
 * analysis component.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DroneImageAnalyzerTest {

    @TempDir
    static Path tempDir;

    // -----------------------------------------------------------------------
    // Test image factories
    // -----------------------------------------------------------------------

    /**
     * Create an 8-bit grayscale PNG where the left half has pixel value
     * {@code leftGray} and the right half has pixel value {@code rightGray}.
     * Gray value 128 → normalised 0.502 → NDVI ≈ 0.502 * 2 − 1 = 0.004.
     */
    private static File createGrayscaleImage(Path dir, String name,
                                              int leftGray, int rightGray,
                                              int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        // left half
        g.setColor(new Color(leftGray, leftGray, leftGray));
        g.fillRect(0, 0, width / 2, height);
        // right half
        g.setColor(new Color(rightGray, rightGray, rightGray));
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        File f = dir.resolve(name).toFile();
        ImageIO.write(img, "png", f);
        return f;
    }

    /**
     * Create a colour disease mask PNG: a white disease patch centred in a
     * black background.
     */
    private static File createDiseaseMask(Path dir, String name,
                                           int width, int height,
                                           int patchX, int patchY,
                                           int patchW, int patchH) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(255, 50, 50)); // disease = reddish
        g.fillRect(patchX, patchY, patchW, patchH);
        g.dispose();
        File f = dir.resolve(name).toFile();
        ImageIO.write(img, "png", f);
        return f;
    }

    // -----------------------------------------------------------------------
    // StatisticsCalculator tests
    // -----------------------------------------------------------------------

    @Test @Order(1)
    void testStatisticsCalculatorBasic() {
        float[] values = {1f, 2f, 3f, 4f, 5f};

        assertEquals(3.0, StatisticsCalculator.mean(values, null), 1e-6, "mean");
        assertEquals(3.0, StatisticsCalculator.median(values, null), 1e-6, "median");
        assertEquals(1.0, StatisticsCalculator.min(values, null), 1e-6, "min");
        assertEquals(5.0, StatisticsCalculator.max(values, null), 1e-6, "max");

        double stdDev = StatisticsCalculator.stdDev(values, null);
        assertTrue(stdDev > 1.4 && stdDev < 1.6, "std dev ≈ √2 ≈ 1.414");
    }

    @Test @Order(2)
    void testStatisticsCalculatorWithMask() {
        float[] values = {10f, 20f, 30f, -999f, -999f};
        boolean[] valid = {true, true, true, false, false};

        assertEquals(20.0, StatisticsCalculator.mean(values, valid), 1e-6, "masked mean");
        assertEquals(20.0, StatisticsCalculator.median(values, valid), 1e-6, "masked median");
        assertEquals(3, StatisticsCalculator.countValid(valid, values.length), "valid count");
    }

    @Test @Order(3)
    void testPercentiles() {
        float[] v = new float[100];
        for (int i = 0; i < 100; i++) v[i] = i + 1; // 1..100

        assertEquals(25.75, StatisticsCalculator.percentile(v, null, 25), 0.01, "P25");
        assertEquals(50.5,  StatisticsCalculator.percentile(v, null, 50), 0.01, "P50");
        assertEquals(75.25, StatisticsCalculator.percentile(v, null, 75), 0.01, "P75");
    }

    @Test @Order(4)
    void testPearsonCorrelation() {
        int n = 100;
        float[] x = new float[n];
        float[] y = new float[n];
        float[] yNeg = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = i;
            y[i] = i;          // perfect positive
            yNeg[i] = n - i;   // perfect negative
        }
        assertEquals(1.0,  StatisticsCalculator.pearsonCorrelation(x, y, null),    0.001);
        assertEquals(-1.0, StatisticsCalculator.pearsonCorrelation(x, yNeg, null), 0.001);
    }

    @Test @Order(5)
    void testHistogramEdgesAndCounts() {
        float[] v = {0.1f, 0.2f, 0.3f, 0.8f, 0.9f};
        int[] counts = StatisticsCalculator.histogram(v, null, 5, 0.0, 1.0);
        double[] edges = StatisticsCalculator.histogramEdges(5, 0.0, 1.0);

        assertEquals(6, edges.length, "edge count = bins + 1");
        assertEquals(5, counts.length, "count array size = bins");

        int total = 0;
        for (int c : counts) total += c;
        assertEquals(5, total, "all values should fall in histogram");
    }

    // -----------------------------------------------------------------------
    // DistributionHistogram tests
    // -----------------------------------------------------------------------

    @Test @Order(6)
    void testDistributionHistogram() {
        double[] edges = {0, 0.5, 1.0};
        int[] counts = {70, 30};
        DistributionHistogram h = new DistributionHistogram(edges, counts);

        assertEquals(0.7, h.frequency(0), 0.001, "bin 0 frequency");
        assertEquals(0.25, h.binCenter(0), 0.001, "bin 0 centre");
        assertEquals(0, h.modeBinIndex(), "mode is bin 0");
        assertFalse(h.isBimodal(), "two-bin histogram cannot be bimodal");
    }

    // -----------------------------------------------------------------------
    // SpatialAnalysis tests
    // -----------------------------------------------------------------------

    @Test @Order(7)
    void testConnectedComponentLabelling() {
        boolean[][] mask = {
            {false, false, false, false, false},
            {false,  true,  true, false, false},
            {false,  true, false, false, false},
            {false, false, false,  true,  true},
            {false, false, false, false, false}
        };
        List<DiseaseCluster> clusters = SpatialAnalysis.labelComponents(mask, 1);
        assertEquals(2, clusters.size(), "should find 2 connected components");
        // Largest component has 3 pixels
        assertEquals(3, clusters.get(0).getPixelCount(), "largest cluster size");
        assertEquals(2, clusters.get(1).getPixelCount(), "second cluster size");
    }

    @Test @Order(8)
    void testMoransIPositive() {
        // Checkerboard-like uniform data → near zero Moran's I
        int rows = 10, cols = 10;
        float[] values = new float[rows * cols];
        for (int i = 0; i < values.length; i++) values[i] = 0.5f;
        double moransI = SpatialAnalysis.moransI(values, null, rows, cols);
        // All identical values → numerator is 0 → Moran's I = 0
        assertEquals(0.0, moransI, 0.001, "uniform field → Moran's I = 0");
    }

    @Test @Order(9)
    void testSpatialHeterogeneity() {
        int rows = 20, cols = 20;
        float[] uniform = new float[rows * cols];
        float[] varied  = new float[rows * cols];
        for (int i = 0; i < rows * cols; i++) {
            uniform[i] = 0.5f;
            varied[i]  = (i % 2 == 0) ? 0.2f : 0.8f; // alternating high/low
        }
        double hUniform = SpatialAnalysis.spatialHeterogeneity(uniform, null, rows, cols, 2);
        double hVaried  = SpatialAnalysis.spatialHeterogeneity(varied,  null, rows, cols, 2);
        assertTrue(hVaried > hUniform, "varied field should have higher heterogeneity");
    }

    // -----------------------------------------------------------------------
    // ColorScaleDecoder tests
    // -----------------------------------------------------------------------

    @Test @Order(10)
    void testGrayscaleDecode() {
        assertEquals(0.0f, ColorScaleDecoder.decodeGrayscale8(0), 1e-5f);
        assertEquals(1.0f, ColorScaleDecoder.decodeGrayscale8(255), 1e-5f);
        assertEquals(0.5f, ColorScaleDecoder.decodeGrayscale8(128), 0.01f);
    }

    @Test @Order(11)
    void testToPhysicalValue() {
        // 0-normalised → -1 for NDVI range [-1, 1]
        assertEquals(-1.0f, ColorScaleDecoder.toPhysicalValue(0.0f, -1f, 1f), 1e-5f);
        assertEquals(1.0f,  ColorScaleDecoder.toPhysicalValue(1.0f, -1f, 1f), 1e-5f);
        assertEquals(0.0f,  ColorScaleDecoder.toPhysicalValue(0.5f, -1f, 1f), 1e-5f);
    }

    @Test @Order(12)
    void testJetColormapRoundTrip() {
        // Encode a value with the jet colourmap, then decode it back
        float original = 0.6f;
        float[] rgb = ColorScaleDecoder.jetColor(original);
        float decoded = ColorScaleDecoder.decodeJet(
            (int)(rgb[0] * 255), (int)(rgb[1] * 255), (int)(rgb[2] * 255));
        assertEquals(original, decoded, 0.02f, "jet encode→decode round-trip");
    }

    // -----------------------------------------------------------------------
    // ImageLoader tests
    // -----------------------------------------------------------------------

    @Test @Order(13)
    void testLoadGrayscaleImage() throws IOException {
        File f = createGrayscaleImage(tempDir, "test_gray.png", 100, 200, 50, 50);
        BufferedImage img = ImageLoader.loadImage(f);
        assertNotNull(img, "image should load");
        assertEquals(50, img.getWidth());
        assertEquals(50, img.getHeight());
        assertTrue(ImageLoader.isGrayscale(img), "should be grayscale");
    }

    @Test @Order(14)
    void testGrayscaleNormalizedExtraction() throws IOException {
        // Left half = 0, right half = 255 → normalised values 0 and 1
        File f = createGrayscaleImage(tempDir, "test_gray2.png", 0, 255, 100, 50);
        BufferedImage img = ImageLoader.loadImage(f);
        float[] norm = ImageLoader.extractGrayscaleNormalized(img);
        assertEquals(100 * 50, norm.length, "pixel count");
        assertEquals(0.0f, norm[0], 1e-5f, "left half should be 0");
        assertEquals(1.0f, norm[norm.length - 1], 1e-5f, "right half should be 1");
    }

    @Test @Order(15)
    void testBinaryMaskReading() throws IOException {
        File f = createDiseaseMask(tempDir, "test_mask.png", 100, 100, 25, 25, 50, 50);
        BufferedImage img = ImageLoader.loadImage(f);
        boolean[][] mask = ImageLoader.readBinaryMask(img, 20);
        // Centre pixel (50,50) should be in the white patch
        assertTrue(mask[50][50], "centre pixel should be diseased");
        // Corner pixel should be black (background)
        assertFalse(mask[0][0], "corner pixel should be healthy background");
    }

    // -----------------------------------------------------------------------
    // NDVIAnalyzer integration test
    // -----------------------------------------------------------------------

    @Test @Order(16)
    void testNDVIAnalyzerIntegration() throws IOException {
        // Create a synthetic NDVI grayscale image
        // Gray value 204 → normalised 0.8 → NDVI = 0.8*2-1 = 0.6 (healthy)
        // Gray value 77  → normalised 0.302 → NDVI = 0.302*2-1 = -0.396 (stressed)
        File f = createGrayscaleImage(tempDir, "ndvi_test.png", 204, 77, 100, 100);

        AnalysisConfig config = new AnalysisConfig();
        NDVIAnalyzer analyzer = new NDVIAnalyzer(config);
        IndexAnalysisResult result = analyzer.analyze(f);

        assertNotNull(result, "result should not be null");
        assertEquals(IndexAnalysisResult.IndexType.NDVI, result.getIndexType());
        // Mean should be between the two region means
        assertTrue(result.getMean() > -1 && result.getMean() < 1, "NDVI mean in valid range");
        assertNotNull(result.getZones(), "zones should be populated");
        assertFalse(result.getZones().isEmpty(), "at least one zone");
        assertNotNull(result.getInterpretationZh(), "Chinese interpretation should be set");
        assertNotNull(result.getInterpretationEn(), "English interpretation should be set");
        // Std dev > 0 because we have two different grey levels
        assertTrue(result.getStdDev() > 0, "std dev should be > 0 for non-uniform image");
    }

    // -----------------------------------------------------------------------
    // NDREAnalyzer integration test
    // -----------------------------------------------------------------------

    @Test @Order(17)
    void testNDREAnalyzerIntegration() throws IOException {
        File f = createGrayscaleImage(tempDir, "ndre_test.png", 180, 90, 80, 80);

        AnalysisConfig config = new AnalysisConfig();
        NDREAnalyzer analyzer = new NDREAnalyzer(config);
        IndexAnalysisResult result = analyzer.analyze(f);

        assertNotNull(result);
        assertEquals(IndexAnalysisResult.IndexType.NDRE, result.getIndexType());
        assertTrue(result.getTotalValidPixels() > 0, "should have valid pixels");
        assertNotNull(result.getHistogramCounts());
        assertEquals(config.getHistogramBins(), result.getHistogramCounts().length);
    }

    // -----------------------------------------------------------------------
    // DiseaseMaskAnalyzer integration test
    // -----------------------------------------------------------------------

    @Test @Order(18)
    void testDiseaseMaskAnalyzerIntegration() throws IOException {
        // Create a mask with a disease patch covering ~25% of the image
        File f = createDiseaseMask(tempDir, "mask_test.png", 200, 200, 50, 50, 100, 100);

        AnalysisConfig config = new AnalysisConfig();
        config.setMinClusterSizePx(10);
        DiseaseMaskAnalyzer analyzer = new DiseaseMaskAnalyzer(config);
        DiseaseMaskResult result = analyzer.analyze(f);

        assertNotNull(result);
        assertEquals(200 * 200, result.getTotalPixels());
        // Diseased patch = 100*100 = 10000 px out of 40000 → 25%
        assertTrue(result.getDiseasedPercent() > 20 && result.getDiseasedPercent() < 30,
                   "diseased% should be ~25%");
        assertTrue(result.getTotalClusters() >= 1, "should detect at least 1 cluster");
        assertNotNull(result.getSpatialPattern());
    }

    // -----------------------------------------------------------------------
    // HealthScore tests
    // -----------------------------------------------------------------------

    @Test @Order(19)
    void testHealthScoreMapping() {
        assertEquals(HealthScore.RiskLevel.LOW,      HealthScore.scoreToRiskLevel(80));
        assertEquals(HealthScore.RiskLevel.MEDIUM,   HealthScore.scoreToRiskLevel(60));
        assertEquals(HealthScore.RiskLevel.HIGH,     HealthScore.scoreToRiskLevel(40));
        assertEquals(HealthScore.RiskLevel.CRITICAL, HealthScore.scoreToRiskLevel(10));
    }

    @Test @Order(20)
    void testHealthScoreClamping() {
        HealthScore score = new HealthScore();
        score.setOverallScore(150);
        assertEquals(100, score.getOverallScore(), "score should be clamped to 100");
        score.setOverallScore(-10);
        assertEquals(0, score.getOverallScore(), "score should be clamped to 0");
    }

    // -----------------------------------------------------------------------
    // AnalysisReport full pipeline test
    // -----------------------------------------------------------------------

    @Test @Order(21)
    void testFullPipelineReport() throws IOException {
        // Create minimal synthetic images
        File ndviFile  = createGrayscaleImage(tempDir, "full_ndvi.png",  200, 100, 120, 80);
        File ndreFile  = createGrayscaleImage(tempDir, "full_ndre.png",  190, 120, 120, 80);
        File maskFile  = createDiseaseMask(tempDir, "full_mask.png", 120, 80, 30, 20, 20, 15);

        AnalysisConfig config = new AnalysisConfig();
        config.setMinClusterSizePx(5);

        AnalysisReport report = DroneImageAnalyzer.analyzeImages(
                ndviFile.getAbsolutePath(),
                ndreFile.getAbsolutePath(),
                maskFile.getAbsolutePath(),
                config);

        assertNotNull(report, "report should not be null");
        assertNotNull(report.getNdviResult());
        assertNotNull(report.getNdreResult());
        assertNotNull(report.getDiseaseMaskResult());
        assertNotNull(report.getHealthScore());
        assertFalse(report.getRecommendationsZh().isEmpty(),
                    "should always have at least one recommendation");
        assertEquals(report.getRecommendationsZh().size(),
                     report.getRecommendationsEn().size(),
                     "bilingual recommendations should be in sync");

        String reportText = report.generateReport();
        assertFalse(reportText.isEmpty(), "report text should not be empty");
        assertTrue(reportText.contains("NDVI"), "report should mention NDVI");
        assertTrue(reportText.contains("NDRE"), "report should mention NDRE");
        assertTrue(reportText.contains("病害"), "report should contain Chinese text");
    }

    // -----------------------------------------------------------------------
    // ZoneInfo tests
    // -----------------------------------------------------------------------

    @Test @Order(22)
    void testZoneInfoContains() {
        ZoneInfo zone = new ZoneInfo("Test", "测试", 0.4f, 0.6f, "🟢");
        assertTrue(zone.contains(0.4f), "lower bound is inclusive");
        assertTrue(zone.contains(0.5f), "middle value");
        assertFalse(zone.contains(0.6f), "upper bound is exclusive");
        assertFalse(zone.contains(0.3f), "below lower bound");
    }

    // -----------------------------------------------------------------------
    // DiseaseCluster tests
    // -----------------------------------------------------------------------

    @Test @Order(23)
    void testDiseaseClusterBoundingBox() {
        DiseaseCluster cluster = new DiseaseCluster(1);
        cluster.setMinX(10); cluster.setMaxX(30);
        cluster.setMinY(5);  cluster.setMaxY(20);
        assertEquals(21, cluster.getBoundingBoxWidth(),  "bounding box width");
        assertEquals(16, cluster.getBoundingBoxHeight(), "bounding box height");
        double ar = cluster.getAspectRatio();
        assertTrue(ar > 1.0, "landscape rectangle → AR > 1");
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test @Order(24)
    void testStatisticsWithAllSameValues() {
        float[] v = {0.5f, 0.5f, 0.5f, 0.5f};
        assertEquals(0.5, StatisticsCalculator.mean(v, null), 1e-6);
        assertEquals(0.0, StatisticsCalculator.stdDev(v, null), 1e-6);
        assertEquals(0.0, StatisticsCalculator.skewness(v, null), 1e-6);
        assertEquals(0.0, StatisticsCalculator.excessKurtosis(v, null), 1e-6);
    }

    @Test @Order(25)
    void testEmptyClusterList() {
        boolean[][] emptyMask = new boolean[10][10]; // all false
        List<DiseaseCluster> clusters = SpatialAnalysis.labelComponents(emptyMask, 1);
        assertTrue(clusters.isEmpty(), "no clusters in empty mask");
    }
}
