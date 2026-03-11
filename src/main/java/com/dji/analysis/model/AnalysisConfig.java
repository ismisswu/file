package com.dji.analysis.model;

/**
 * Configuration for the drone image analysis pipeline.
 * Controls image format interpretation, thresholds, and analysis parameters.
 */
public class AnalysisConfig {

    /** How pixel values are encoded in the image file. */
    public enum ImageFormat {
        /** Detect automatically from image metadata and content. */
        AUTO_DETECT,
        /** Single-channel 8-bit grayscale: pixel 0 → minValue, pixel 255 → maxValue. */
        GRAYSCALE_8BIT,
        /** Single-channel 16-bit grayscale: pixel 0 → minValue, pixel 65535 → maxValue. */
        GRAYSCALE_16BIT,
        /** 32-bit float single-channel TIFF (GeoTIFF from DJI Terra). */
        FLOAT32_TIFF,
        /** RGB colour image using the Jet (rainbow) colourmap. */
        COLOR_JET,
        /** RGB colour image using DJI's built-in NDVI colourmap (red→yellow→green). */
        COLOR_DJI_NDVI,
        /** RGB colour image using a simple red-to-green gradient. */
        COLOR_RED_TO_GREEN,
        /** Binary mask: non-black pixels are diseased. */
        COLOR_BINARY_MASK
    }

    // -----------------------------------------------------------------------
    // Image format configuration
    // -----------------------------------------------------------------------
    private ImageFormat ndviFormat = ImageFormat.AUTO_DETECT;
    private ImageFormat ndreFormat = ImageFormat.AUTO_DETECT;
    private ImageFormat diseaseMaskFormat = ImageFormat.AUTO_DETECT;

    // -----------------------------------------------------------------------
    // Value range for grayscale / float images
    // -----------------------------------------------------------------------
    private float ndviMinValue = -1.0f;
    private float ndviMaxValue = 1.0f;
    private float ndreMinValue = -1.0f;
    private float ndreMaxValue = 1.0f;

    // -----------------------------------------------------------------------
    // Analysis parameters
    // -----------------------------------------------------------------------
    /** Number of bins used when building histograms. */
    private int histogramBins = 25;

    /** Minimum pixel area (px²) for a disease cluster to be reported. */
    private int minClusterSizePx = 30;

    /** NDVI value below which a pixel is considered stressed. */
    private float ndviStressThreshold = 0.35f;

    /** NDRE value below which a pixel is considered stressed. */
    private float ndreStressThreshold = 0.20f;

    /** Fraction of diseased pixels that triggers a HIGH risk level (0–1). */
    private float highRiskDiseaseRatio = 0.15f;

    /** Fraction of diseased pixels that triggers a MEDIUM risk level (0–1). */
    private float mediumRiskDiseaseRatio = 0.05f;

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------
    public ImageFormat getNdviFormat() { return ndviFormat; }
    public void setNdviFormat(ImageFormat ndviFormat) { this.ndviFormat = ndviFormat; }

    public ImageFormat getNdreFormat() { return ndreFormat; }
    public void setNdreFormat(ImageFormat ndreFormat) { this.ndreFormat = ndreFormat; }

    public ImageFormat getDiseaseMaskFormat() { return diseaseMaskFormat; }
    public void setDiseaseMaskFormat(ImageFormat diseaseMaskFormat) { this.diseaseMaskFormat = diseaseMaskFormat; }

    public float getNdviMinValue() { return ndviMinValue; }
    public void setNdviMinValue(float ndviMinValue) { this.ndviMinValue = ndviMinValue; }

    public float getNdviMaxValue() { return ndviMaxValue; }
    public void setNdviMaxValue(float ndviMaxValue) { this.ndviMaxValue = ndviMaxValue; }

    public float getNdreMinValue() { return ndreMinValue; }
    public void setNdreMinValue(float ndreMinValue) { this.ndreMinValue = ndreMinValue; }

    public float getNdreMaxValue() { return ndreMaxValue; }
    public void setNdreMaxValue(float ndreMaxValue) { this.ndreMaxValue = ndreMaxValue; }

    public int getHistogramBins() { return histogramBins; }
    public void setHistogramBins(int histogramBins) { this.histogramBins = histogramBins; }

    public int getMinClusterSizePx() { return minClusterSizePx; }
    public void setMinClusterSizePx(int minClusterSizePx) { this.minClusterSizePx = minClusterSizePx; }

    public float getNdviStressThreshold() { return ndviStressThreshold; }
    public void setNdviStressThreshold(float ndviStressThreshold) { this.ndviStressThreshold = ndviStressThreshold; }

    public float getNdreStressThreshold() { return ndreStressThreshold; }
    public void setNdreStressThreshold(float ndreStressThreshold) { this.ndreStressThreshold = ndreStressThreshold; }

    public float getHighRiskDiseaseRatio() { return highRiskDiseaseRatio; }
    public void setHighRiskDiseaseRatio(float highRiskDiseaseRatio) { this.highRiskDiseaseRatio = highRiskDiseaseRatio; }

    public float getMediumRiskDiseaseRatio() { return mediumRiskDiseaseRatio; }
    public void setMediumRiskDiseaseRatio(float mediumRiskDiseaseRatio) { this.mediumRiskDiseaseRatio = mediumRiskDiseaseRatio; }
}
