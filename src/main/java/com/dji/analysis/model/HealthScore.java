package com.dji.analysis.model;

/**
 * Aggregated health score for the analysed field or image region.
 * Score is 0–100 (100 = perfectly healthy).
 */
public class HealthScore {

    public enum RiskLevel {
        LOW("低风险 / Low Risk"),
        MEDIUM("中等风险 / Medium Risk"),
        HIGH("高风险 / High Risk"),
        CRITICAL("严重风险 / Critical Risk");

        private final String label;
        RiskLevel(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private double overallScore;          // 0–100
    private double ndviScore;             // component score
    private double ndreScore;             // component score
    private double diseasePenalty;        // points deducted for disease
    private RiskLevel riskLevel;
    private String summaryZh;             // Chinese summary sentence
    private String summaryEn;             // English summary sentence

    // -----------------------------------------------------------------------
    // Factory helpers
    // -----------------------------------------------------------------------

    /** Derive risk level from overall score. */
    public static RiskLevel scoreToRiskLevel(double score) {
        if (score >= 75) return RiskLevel.LOW;
        if (score >= 50) return RiskLevel.MEDIUM;
        if (score >= 25) return RiskLevel.HIGH;
        return RiskLevel.CRITICAL;
    }

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------
    public double getOverallScore() { return overallScore; }
    public void setOverallScore(double overallScore) {
        this.overallScore = Math.max(0, Math.min(100, overallScore));
    }

    public double getNdviScore() { return ndviScore; }
    public void setNdviScore(double ndviScore) { this.ndviScore = ndviScore; }

    public double getNdreScore() { return ndreScore; }
    public void setNdreScore(double ndreScore) { this.ndreScore = ndreScore; }

    public double getDiseasePenalty() { return diseasePenalty; }
    public void setDiseasePenalty(double diseasePenalty) { this.diseasePenalty = diseasePenalty; }

    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public String getSummaryZh() { return summaryZh; }
    public void setSummaryZh(String summaryZh) { this.summaryZh = summaryZh; }

    public String getSummaryEn() { return summaryEn; }
    public void setSummaryEn(String summaryEn) { this.summaryEn = summaryEn; }
}
