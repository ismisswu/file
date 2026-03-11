package com.dji.analysis.model;

import java.util.List;

/**
 * Full analysis result for one vegetation-index image (NDVI or NDRE).
 * Contains statistics, zone breakdown, spatial characteristics, and interpretation.
 */
public class IndexAnalysisResult {

    public enum IndexType { NDVI, NDRE }

    private IndexType indexType;

    // -----------------------------------------------------------------------
    // Basic descriptive statistics
    // -----------------------------------------------------------------------
    private double mean;
    private double median;
    private double stdDev;
    private double min;
    private double max;
    private double skewness;
    private double kurtosis;
    private double p5;   // 5th percentile
    private double p25;  // 25th percentile
    private double p75;  // 75th percentile
    private double p95;  // 95th percentile

    /** Total number of valid (non-masked) pixels analysed. */
    private int totalValidPixels;
    /** Number of pixels that were skipped (e.g. no-data border). */
    private int noDataPixels;

    // -----------------------------------------------------------------------
    // Zone breakdown
    // -----------------------------------------------------------------------
    private List<ZoneInfo> zones;

    // -----------------------------------------------------------------------
    // Spatial characteristics
    // -----------------------------------------------------------------------
    /** Standard deviation of local means in a sliding window → spatial heterogeneity. */
    private double spatialHeterogeneity;
    /** Moran's I index for spatial autocorrelation (-1 to +1). */
    private double moransI;
    /** Fraction of total pixels that fall below the stress threshold. */
    private double stressedAreaFraction;
    /** Number of spatially contiguous stressed zones (low-index clusters). */
    private int stressClusterCount;
    /** List of identified low-value (stress) clusters. */
    private List<DiseaseCluster> stressClusters;

    // -----------------------------------------------------------------------
    // Histogram data
    // -----------------------------------------------------------------------
    private double[] histogramBinEdges;
    private int[] histogramCounts;

    // -----------------------------------------------------------------------
    // Agronomic interpretation
    // -----------------------------------------------------------------------
    /** One-line interpretation in Chinese. */
    private String interpretationZh;
    /** One-line interpretation in English. */
    private String interpretationEn;

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------
    public IndexType getIndexType() { return indexType; }
    public void setIndexType(IndexType indexType) { this.indexType = indexType; }

    public double getMean() { return mean; }
    public void setMean(double mean) { this.mean = mean; }

    public double getMedian() { return median; }
    public void setMedian(double median) { this.median = median; }

    public double getStdDev() { return stdDev; }
    public void setStdDev(double stdDev) { this.stdDev = stdDev; }

    public double getMin() { return min; }
    public void setMin(double min) { this.min = min; }

    public double getMax() { return max; }
    public void setMax(double max) { this.max = max; }

    public double getSkewness() { return skewness; }
    public void setSkewness(double skewness) { this.skewness = skewness; }

    public double getKurtosis() { return kurtosis; }
    public void setKurtosis(double kurtosis) { this.kurtosis = kurtosis; }

    public double getP5() { return p5; }
    public void setP5(double p5) { this.p5 = p5; }

    public double getP25() { return p25; }
    public void setP25(double p25) { this.p25 = p25; }

    public double getP75() { return p75; }
    public void setP75(double p75) { this.p75 = p75; }

    public double getP95() { return p95; }
    public void setP95(double p95) { this.p95 = p95; }

    public int getTotalValidPixels() { return totalValidPixels; }
    public void setTotalValidPixels(int totalValidPixels) { this.totalValidPixels = totalValidPixels; }

    public int getNoDataPixels() { return noDataPixels; }
    public void setNoDataPixels(int noDataPixels) { this.noDataPixels = noDataPixels; }

    public List<ZoneInfo> getZones() { return zones; }
    public void setZones(List<ZoneInfo> zones) { this.zones = zones; }

    public double getSpatialHeterogeneity() { return spatialHeterogeneity; }
    public void setSpatialHeterogeneity(double spatialHeterogeneity) { this.spatialHeterogeneity = spatialHeterogeneity; }

    public double getMoransI() { return moransI; }
    public void setMoransI(double moransI) { this.moransI = moransI; }

    public double getStressedAreaFraction() { return stressedAreaFraction; }
    public void setStressedAreaFraction(double stressedAreaFraction) { this.stressedAreaFraction = stressedAreaFraction; }

    public int getStressClusterCount() { return stressClusterCount; }
    public void setStressClusterCount(int stressClusterCount) { this.stressClusterCount = stressClusterCount; }

    public List<DiseaseCluster> getStressClusters() { return stressClusters; }
    public void setStressClusters(List<DiseaseCluster> stressClusters) { this.stressClusters = stressClusters; }

    public double[] getHistogramBinEdges() { return histogramBinEdges; }
    public void setHistogramBinEdges(double[] histogramBinEdges) { this.histogramBinEdges = histogramBinEdges; }

    public int[] getHistogramCounts() { return histogramCounts; }
    public void setHistogramCounts(int[] histogramCounts) { this.histogramCounts = histogramCounts; }

    public String getInterpretationZh() { return interpretationZh; }
    public void setInterpretationZh(String interpretationZh) { this.interpretationZh = interpretationZh; }

    public String getInterpretationEn() { return interpretationEn; }
    public void setInterpretationEn(String interpretationEn) { this.interpretationEn = interpretationEn; }
}
