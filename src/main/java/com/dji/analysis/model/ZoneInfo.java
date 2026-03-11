package com.dji.analysis.model;

/**
 * Represents a single vegetation or health zone in an index image.
 * Each zone covers a contiguous range of index values and carries
 * statistics about its spatial extent and agronomic interpretation.
 */
public class ZoneInfo {

    private final String name;
    private final String nameZh;         // Chinese name
    private final float lowerBound;      // inclusive
    private final float upperBound;      // exclusive (use Float.MAX_VALUE for last zone)
    private final String colorIndicator; // ANSI/display colour hint

    private int pixelCount;
    private double coveragePercent;      // % of total valid pixels
    private double meanValue;
    private double stdDev;

    public ZoneInfo(String name, String nameZh, float lowerBound, float upperBound, String colorIndicator) {
        this.name = name;
        this.nameZh = nameZh;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.colorIndicator = colorIndicator;
    }

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------
    public String getName() { return name; }
    public String getNameZh() { return nameZh; }
    public float getLowerBound() { return lowerBound; }
    public float getUpperBound() { return upperBound; }
    public String getColorIndicator() { return colorIndicator; }

    public int getPixelCount() { return pixelCount; }
    public void setPixelCount(int pixelCount) { this.pixelCount = pixelCount; }

    public double getCoveragePercent() { return coveragePercent; }
    public void setCoveragePercent(double coveragePercent) { this.coveragePercent = coveragePercent; }

    public double getMeanValue() { return meanValue; }
    public void setMeanValue(double meanValue) { this.meanValue = meanValue; }

    public double getStdDev() { return stdDev; }
    public void setStdDev(double stdDev) { this.stdDev = stdDev; }

    /** Returns true if a value falls within this zone. */
    public boolean contains(float value) {
        return value >= lowerBound && (upperBound == Float.MAX_VALUE || value < upperBound);
    }

    @Override
    public String toString() {
        return String.format("[%s / %s] (%.2f – %s): %.1f%% coverage",
                name, nameZh,
                lowerBound,
                upperBound == Float.MAX_VALUE ? "∞" : String.format("%.2f", upperBound),
                coveragePercent);
    }
}
