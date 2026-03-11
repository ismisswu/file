package com.dji.analysis.model;

import java.util.List;
import java.util.Map;

/**
 * Full analysis result for the disease mask image.
 * Combines pixel-level statistics with spatial cluster analysis.
 */
public class DiseaseMaskResult {

    // -----------------------------------------------------------------------
    // Overall statistics
    // -----------------------------------------------------------------------
    private int totalPixels;
    private int healthyPixels;
    private int diseasedPixels;
    private double diseasedFraction;   // 0–1
    private double diseasedPercent;    // 0–100

    // -----------------------------------------------------------------------
    // Class breakdown (for multi-class masks)
    // -----------------------------------------------------------------------
    /** Map of class label → pixel count (e.g., {"Healthy"→12000, "Leaf blight"→340}). */
    private Map<String, Integer> classPixelCounts;
    /** Map of class label → percentage of total pixels. */
    private Map<String, Double> classPercentages;

    // -----------------------------------------------------------------------
    // Cluster / spatial analysis
    // -----------------------------------------------------------------------
    private int totalClusters;
    private int largeClusters;    // clusters > 500 px
    private int mediumClusters;   // clusters 100–500 px
    private int smallClusters;    // clusters < 100 px
    private double meanClusterSizePx;
    private double maxClusterSizePx;
    private double clusterDispersionIndex;  // ratio of std-dev to mean cluster size
    private List<DiseaseCluster> clusters;

    // -----------------------------------------------------------------------
    // Spatial distribution pattern
    // -----------------------------------------------------------------------
    public enum SpatialPattern {
        ISOLATED("零星分布 / Isolated spots"),
        CLUSTERED("聚集分布 / Clustered"),
        EDGE_CONCENTRATED("边缘聚集 / Edge-concentrated"),
        UNIFORMLY_DISPERSED("均匀分散 / Uniformly dispersed"),
        PATCHY("斑块分布 / Patchy");

        private final String label;
        SpatialPattern(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private SpatialPattern spatialPattern;

    // -----------------------------------------------------------------------
    // Severity breakdown
    // -----------------------------------------------------------------------
    private int lightSeverityClusters;
    private int moderateSeverityClusters;
    private int severeSeverityClusters;

    // -----------------------------------------------------------------------
    // Cross-index agreement (filled in during cross-analysis)
    // -----------------------------------------------------------------------
    /** Fraction of diseased pixels that also show low NDVI. */
    private double diseaseNdviAgreementFraction;
    /** Fraction of diseased pixels that also show low NDRE. */
    private double diseaseNdreAgreementFraction;
    /** Fraction of low-NDVI pixels NOT in disease mask (potential undetected disease). */
    private double undetectedStressFraction;

    // -----------------------------------------------------------------------
    // Interpretation
    // -----------------------------------------------------------------------
    private String interpretationZh;
    private String interpretationEn;

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------
    public int getTotalPixels() { return totalPixels; }
    public void setTotalPixels(int totalPixels) { this.totalPixels = totalPixels; }

    public int getHealthyPixels() { return healthyPixels; }
    public void setHealthyPixels(int healthyPixels) { this.healthyPixels = healthyPixels; }

    public int getDiseasedPixels() { return diseasedPixels; }
    public void setDiseasedPixels(int diseasedPixels) { this.diseasedPixels = diseasedPixels; }

    public double getDiseasedFraction() { return diseasedFraction; }
    public void setDiseasedFraction(double diseasedFraction) {
        this.diseasedFraction = diseasedFraction;
        this.diseasedPercent = diseasedFraction * 100.0;
    }

    public double getDiseasedPercent() { return diseasedPercent; }

    public Map<String, Integer> getClassPixelCounts() { return classPixelCounts; }
    public void setClassPixelCounts(Map<String, Integer> classPixelCounts) { this.classPixelCounts = classPixelCounts; }

    public Map<String, Double> getClassPercentages() { return classPercentages; }
    public void setClassPercentages(Map<String, Double> classPercentages) { this.classPercentages = classPercentages; }

    public int getTotalClusters() { return totalClusters; }
    public void setTotalClusters(int totalClusters) { this.totalClusters = totalClusters; }

    public int getLargeClusters() { return largeClusters; }
    public void setLargeClusters(int largeClusters) { this.largeClusters = largeClusters; }

    public int getMediumClusters() { return mediumClusters; }
    public void setMediumClusters(int mediumClusters) { this.mediumClusters = mediumClusters; }

    public int getSmallClusters() { return smallClusters; }
    public void setSmallClusters(int smallClusters) { this.smallClusters = smallClusters; }

    public double getMeanClusterSizePx() { return meanClusterSizePx; }
    public void setMeanClusterSizePx(double meanClusterSizePx) { this.meanClusterSizePx = meanClusterSizePx; }

    public double getMaxClusterSizePx() { return maxClusterSizePx; }
    public void setMaxClusterSizePx(double maxClusterSizePx) { this.maxClusterSizePx = maxClusterSizePx; }

    public double getClusterDispersionIndex() { return clusterDispersionIndex; }
    public void setClusterDispersionIndex(double clusterDispersionIndex) { this.clusterDispersionIndex = clusterDispersionIndex; }

    public List<DiseaseCluster> getClusters() { return clusters; }
    public void setClusters(List<DiseaseCluster> clusters) { this.clusters = clusters; }

    public SpatialPattern getSpatialPattern() { return spatialPattern; }
    public void setSpatialPattern(SpatialPattern spatialPattern) { this.spatialPattern = spatialPattern; }

    public int getLightSeverityClusters() { return lightSeverityClusters; }
    public void setLightSeverityClusters(int lightSeverityClusters) { this.lightSeverityClusters = lightSeverityClusters; }

    public int getModerateSeverityClusters() { return moderateSeverityClusters; }
    public void setModerateSeverityClusters(int moderateSeverityClusters) { this.moderateSeverityClusters = moderateSeverityClusters; }

    public int getSevereSeverityClusters() { return severeSeverityClusters; }
    public void setSevereSeverityClusters(int severeSeverityClusters) { this.severeSeverityClusters = severeSeverityClusters; }

    public double getDiseaseNdviAgreementFraction() { return diseaseNdviAgreementFraction; }
    public void setDiseaseNdviAgreementFraction(double diseaseNdviAgreementFraction) { this.diseaseNdviAgreementFraction = diseaseNdviAgreementFraction; }

    public double getDiseaseNdreAgreementFraction() { return diseaseNdreAgreementFraction; }
    public void setDiseaseNdreAgreementFraction(double diseaseNdreAgreementFraction) { this.diseaseNdreAgreementFraction = diseaseNdreAgreementFraction; }

    public double getUndetectedStressFraction() { return undetectedStressFraction; }
    public void setUndetectedStressFraction(double undetectedStressFraction) { this.undetectedStressFraction = undetectedStressFraction; }

    public String getInterpretationZh() { return interpretationZh; }
    public void setInterpretationZh(String interpretationZh) { this.interpretationZh = interpretationZh; }

    public String getInterpretationEn() { return interpretationEn; }
    public void setInterpretationEn(String interpretationEn) { this.interpretationEn = interpretationEn; }
}
