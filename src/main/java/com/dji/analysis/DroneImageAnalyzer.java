package com.dji.analysis;

import com.dji.analysis.analyzer.DiseaseMaskAnalyzer;
import com.dji.analysis.analyzer.NDREAnalyzer;
import com.dji.analysis.analyzer.NDVIAnalyzer;
import com.dji.analysis.model.AnalysisConfig;
import com.dji.analysis.model.AnalysisReport;
import com.dji.analysis.model.DiseaseMaskResult;
import com.dji.analysis.model.IndexAnalysisResult;
import com.dji.analysis.utils.ColorScaleDecoder;
import com.dji.analysis.utils.ImageLoader;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Main entry point for the DJI Mavic 3 Multispectral (3M) spectral image
 * analysis system.
 *
 * <h3>Usage</h3>
 * <pre>
 * java -jar drone-image-analyzer.jar &lt;ndvi_image&gt; &lt;ndre_image&gt; &lt;disease_mask_image&gt; [output_report.txt]
 * </pre>
 *
 * <h3>Supported image formats</h3>
 * <ul>
 *   <li>PNG / JPEG – 8-bit grayscale or colour (jet / DJI NDVI colour scale)</li>
 *   <li>16-bit grayscale PNG</li>
 *   <li>TIFF (requires TwelveMonkeys ImageIO on the classpath)</li>
 * </ul>
 *
 * <h3>Analysis pipeline</h3>
 * <ol>
 *   <li>Load and decode each image to a flat array of physical index values.</li>
 *   <li>Compute rich statistics: mean, median, std-dev, skewness, kurtosis,
 *       percentiles, histogram.</li>
 *   <li>Classify pixels into agronomic zones.</li>
 *   <li>Detect spatially-contiguous stress/disease clusters (connected-component
 *       labelling) with size, shape, compactness and severity metrics.</li>
 *   <li>Compute spatial-autocorrelation (Moran's I) and spatial heterogeneity.</li>
 *   <li>Cross-analyse NDVI ↔ NDRE (Pearson correlation, early-vs-late stress
 *       staging).</li>
 *   <li>Score overall field health (0–100) and classify risk level.</li>
 *   <li>Generate actionable bilingual (Chinese + English) recommendations.</li>
 * </ol>
 */
public class DroneImageAnalyzer {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: DroneImageAnalyzer <ndvi_image> <ndre_image> <disease_mask_image> [output.txt]");
            System.err.println("       Image formats: PNG, JPEG, TIFF (8-bit or 16-bit grayscale, or colour)");
            System.exit(1);
        }

        String ndviPath        = args[0];
        String ndrePath        = args[1];
        String diseaseMaskPath = args[2];
        String outputPath      = args.length >= 4 ? args[3] : null;

        System.out.println("═".repeat(72));
        System.out.println("  DJI Mavic 3 Multispectral – Intelligent Image Analysis");
        System.out.println("═".repeat(72));
        System.out.println("  NDVI  image : " + ndviPath);
        System.out.println("  NDRE  image : " + ndrePath);
        System.out.println("  Mask  image : " + diseaseMaskPath);
        if (outputPath != null) System.out.println("  Output file : " + outputPath);
        System.out.println();

        try {
            AnalysisReport report = analyzeImages(ndviPath, ndrePath, diseaseMaskPath,
                                                  new AnalysisConfig());
            String reportText = report.generateReport();

            // Print to console
            System.out.println(reportText);

            // Optionally write to file
            if (outputPath != null) {
                try (PrintWriter pw = new PrintWriter(outputPath, "UTF-8")) {
                    pw.print(reportText);
                }
                System.out.println("Report written to: " + outputPath);
            }
        } catch (IOException e) {
            System.err.println("Error reading image file: " + e.getMessage());
            System.exit(2);
        }
    }

    // -----------------------------------------------------------------------
    // Core analysis API
    // -----------------------------------------------------------------------

    /**
     * Analyse three spectral images and return a complete {@link AnalysisReport}.
     *
     * <p>This method is intended for programmatic use (e.g., embedded in a Spring
     * service or called from tests).
     *
     * @param ndviPath        path to the NDVI image
     * @param ndrePath        path to the NDRE image
     * @param diseaseMaskPath path to the disease mask image
     * @param config          analysis configuration (format hints, thresholds, etc.)
     * @return fully-populated {@link AnalysisReport}
     * @throws IOException if any image file cannot be loaded
     */
    public static AnalysisReport analyzeImages(String ndviPath, String ndrePath,
                                                String diseaseMaskPath,
                                                AnalysisConfig config) throws IOException {
        System.out.print("  Loading and analysing NDVI  image... ");
        NDVIAnalyzer ndviAnalyzer = new NDVIAnalyzer(config);
        IndexAnalysisResult ndviResult = ndviAnalyzer.analyze(new File(ndviPath));
        System.out.println("done.");

        System.out.print("  Loading and analysing NDRE  image... ");
        NDREAnalyzer ndreAnalyzer = new NDREAnalyzer(config);
        IndexAnalysisResult ndreResult = ndreAnalyzer.analyze(new File(ndrePath));
        System.out.println("done.");

        System.out.print("  Loading and analysing disease mask... ");
        DiseaseMaskAnalyzer diseaseAnalyzer = new DiseaseMaskAnalyzer(config);
        DiseaseMaskResult diseaseMaskResult = diseaseAnalyzer.analyze(new File(diseaseMaskPath));
        System.out.println("done.");

        // Supply pixel arrays for cross-analysis (correlation, stress staging)
        System.out.print("  Performing cross-index analysis...   ");
        AnalysisReport report = new AnalysisReport();
        report.setNdviResult(ndviResult);
        report.setNdreResult(ndreResult);
        report.setDiseaseMaskResult(diseaseMaskResult);

        // Re-read both images for pixel-level cross-analysis if they share same dimensions
        try {
            float[] ndviPx = loadIndexPixels(ndviPath, config, true);
            float[] ndrePx = loadIndexPixels(ndrePath, config, false);
            if (ndviPx.length == ndrePx.length) {
                report.setNdviPixels(ndviPx);
                report.setNdrePixels(ndrePx);
            }
        } catch (Exception ignored) {
            // Cross-analysis is best-effort; proceed without it
        }

        report.performCrossAnalysis();
        System.out.println("done.\n");

        return report;
    }

    /**
     * Helper: reload an index image and return decoded float pixel values.
     *
     * @param isNdvi true → use NDVI config, false → use NDRE config
     */
    private static float[] loadIndexPixels(String path, AnalysisConfig config, boolean isNdvi)
            throws IOException {
        BufferedImage img = ImageLoader.loadImage(new File(path));
        int w = img.getWidth(), h = img.getHeight();
        float[] values = new float[w * h];

        AnalysisConfig.ImageFormat fmt = isNdvi ? config.getNdviFormat() : config.getNdreFormat();
        if (fmt == AnalysisConfig.ImageFormat.AUTO_DETECT) {
            fmt = ImageLoader.isGrayscale(img) ? AnalysisConfig.ImageFormat.GRAYSCALE_8BIT
                                               : AnalysisConfig.ImageFormat.COLOR_DJI_NDVI;
        }

        float minVal = isNdvi ? config.getNdviMinValue() : config.getNdreMinValue();
        float maxVal = isNdvi ? config.getNdviMaxValue() : config.getNdreMaxValue();

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
}
