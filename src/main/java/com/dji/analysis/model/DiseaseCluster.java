package com.dji.analysis.model;

/**
 * Describes a spatially-connected group of diseased (or anomalous) pixels.
 * Computed by connected-component labelling on the disease mask or low-index areas.
 */
public class DiseaseCluster {

    private final int id;
    private int pixelCount;
    private int minX, minY, maxX, maxY;  // bounding box
    private double centroidX, centroidY;
    private double compactness;          // 4π·area / perimeter² – 1 = circle, 0 = irregular
    private double meanNdvi;             // mean NDVI over cluster pixels (if available)
    private double meanNdre;             // mean NDRE over cluster pixels (if available)
    private String severityLabel;        // "轻度/Light", "中度/Moderate", "重度/Severe"

    public DiseaseCluster(int id) {
        this.id = id;
    }

    // -----------------------------------------------------------------------
    // Computed helpers
    // -----------------------------------------------------------------------

    /** Width of the bounding box in pixels. */
    public int getBoundingBoxWidth() { return maxX - minX + 1; }

    /** Height of the bounding box in pixels. */
    public int getBoundingBoxHeight() { return maxY - minY + 1; }

    /**
     * Aspect ratio of the bounding box.
     * Values near 1.0 → roughly square; values far from 1.0 → elongated.
     */
    public double getAspectRatio() {
        int w = getBoundingBoxWidth();
        int h = getBoundingBoxHeight();
        return (h == 0) ? 1.0 : (double) w / h;
    }

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------
    public int getId() { return id; }

    public int getPixelCount() { return pixelCount; }
    public void setPixelCount(int pixelCount) { this.pixelCount = pixelCount; }

    public int getMinX() { return minX; }
    public void setMinX(int minX) { this.minX = minX; }

    public int getMinY() { return minY; }
    public void setMinY(int minY) { this.minY = minY; }

    public int getMaxX() { return maxX; }
    public void setMaxX(int maxX) { this.maxX = maxX; }

    public int getMaxY() { return maxY; }
    public void setMaxY(int maxY) { this.maxY = maxY; }

    public double getCentroidX() { return centroidX; }
    public void setCentroidX(double centroidX) { this.centroidX = centroidX; }

    public double getCentroidY() { return centroidY; }
    public void setCentroidY(double centroidY) { this.centroidY = centroidY; }

    public double getCompactness() { return compactness; }
    public void setCompactness(double compactness) { this.compactness = compactness; }

    public double getMeanNdvi() { return meanNdvi; }
    public void setMeanNdvi(double meanNdvi) { this.meanNdvi = meanNdvi; }

    public double getMeanNdre() { return meanNdre; }
    public void setMeanNdre(double meanNdre) { this.meanNdre = meanNdre; }

    public String getSeverityLabel() { return severityLabel; }
    public void setSeverityLabel(String severityLabel) { this.severityLabel = severityLabel; }

    @Override
    public String toString() {
        return String.format("Cluster#%d  pixels=%d  centre=(%.1f,%.1f)  bbox=[%d,%d]-[%d,%d]  compactness=%.3f  severity=%s",
                id, pixelCount, centroidX, centroidY, minX, minY, maxX, maxY, compactness, severityLabel);
    }
}
