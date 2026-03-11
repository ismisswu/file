package com.dji.analysis.model;

import com.dji.analysis.stats.StatisticsCalculator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated analysis report combining NDVI, NDRE and disease-mask results.
 *
 * <h3>Cross-analysis capabilities</h3>
 * <ul>
 *   <li>NDVI–NDRE Pearson correlation (requires pixel arrays to be supplied).</li>
 *   <li>Agreement between disease mask and low-index areas.</li>
 *   <li>Composite health score (0–100) with risk-level classification.</li>
 *   <li>Actionable recommendations in Chinese and English.</li>
 * </ul>
 */
public class AnalysisReport {

    private IndexAnalysisResult ndviResult;
    private IndexAnalysisResult ndreResult;
    private DiseaseMaskResult diseaseMaskResult;
    private HealthScore healthScore;

    // Cross-analysis metrics
    private double ndviNdreCorrelation = Double.NaN;
    private double earlyStressFraction = 0;  // NDRE low but NDVI not yet low
    private double advancedStressFraction = 0; // both NDVI and NDRE low

    // Pixel arrays for cross-analysis (optional, set before calling performCrossAnalysis)
    private float[] ndviPixels;
    private float[] ndrePixels;
    private boolean[] validMask;

    // Generated lists
    private List<String> recommendationsZh = new ArrayList<>();
    private List<String> recommendationsEn = new ArrayList<>();

    private final String analysisTimestamp =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    // -----------------------------------------------------------------------
    // Cross-analysis
    // -----------------------------------------------------------------------

    /**
     * Compute cross-index metrics.  Call after setting all three results
     * (and optionally pixel arrays for correlation).
     */
    public void performCrossAnalysis() {
        computeHealthScore();
        if (ndviPixels != null && ndrePixels != null
                && ndviPixels.length == ndrePixels.length) {
            ndviNdreCorrelation = StatisticsCalculator.pearsonCorrelation(
                    ndviPixels, ndrePixels, validMask);
            computeStressStaging();
        }
        generateRecommendations();
    }

    private void computeHealthScore() {
        HealthScore score = new HealthScore();

        // NDVI component (0–40 points): based on mean NDVI relative to target
        double ndviMean = ndviResult != null ? ndviResult.getMean() : 0.5;
        double ndviScore = Math.max(0, Math.min(40, (ndviMean / 0.7) * 40));

        // NDRE component (0–30 points): based on mean NDRE
        double ndreMean = ndreResult != null ? ndreResult.getMean() : 0.35;
        double ndreScore = Math.max(0, Math.min(30, (ndreMean / 0.5) * 30));

        // Disease penalty (0–30 points deducted)
        double diseasedPct = diseaseMaskResult != null ? diseaseMaskResult.getDiseasedPercent() : 0;
        double diseasePenalty = Math.min(30, diseasedPct * 2.0);

        // Stress cluster penalty
        int stressClusters = (ndviResult != null ? ndviResult.getStressClusterCount() : 0)
                           + (ndreResult != null ? ndreResult.getStressClusterCount() : 0);
        double clusterPenalty = Math.min(10, stressClusters * 0.5);

        double overall = ndviScore + ndreScore - diseasePenalty - clusterPenalty;
        score.setOverallScore(overall);
        score.setNdviScore(ndviScore);
        score.setNdreScore(ndreScore);
        score.setDiseasePenalty(diseasePenalty + clusterPenalty);
        score.setRiskLevel(HealthScore.scoreToRiskLevel(overall));

        score.setSummaryZh(buildSummaryZh(score, ndviMean, ndreMean, diseasedPct));
        score.setSummaryEn(buildSummaryEn(score, ndviMean, ndreMean, diseasedPct));

        this.healthScore = score;
    }

    private void computeStressStaging() {
        if (ndviPixels == null || ndrePixels == null) return;

        double ndviThresh = 0.35, ndreThresh = 0.20;
        int earlyStress = 0, advancedStress = 0, valid = 0;
        for (int i = 0; i < ndviPixels.length; i++) {
            if (validMask != null && !validMask[i]) continue;
            valid++;
            boolean lowNdvi = ndviPixels[i] < ndviThresh;
            boolean lowNdre = ndrePixels[i] < ndreThresh;
            if (lowNdre && !lowNdvi) earlyStress++;
            if (lowNdre && lowNdvi) advancedStress++;
        }
        if (valid > 0) {
            earlyStressFraction = (double) earlyStress / valid;
            advancedStressFraction = (double) advancedStress / valid;
        }
    }

    private void generateRecommendations() {
        recommendationsZh.clear();
        recommendationsEn.clear();

        HealthScore score = healthScore;
        if (score == null) return;

        double diseasedPct = diseaseMaskResult != null ? diseaseMaskResult.getDiseasedPercent() : 0;
        double ndviMean = ndviResult != null ? ndviResult.getMean() : 1;
        double ndreMean = ndreResult != null ? ndreResult.getMean() : 1;
        int largeClusters = diseaseMaskResult != null ? diseaseMaskResult.getLargeClusters() : 0;

        // --- Disease spread risk ---
        if (diseasedPct > 15) {
            addRec("🚨 病害严重（>15%面积受影响）：立即安排整田防治，建议使用无人机喷洒农药，重点针对大型病斑区域。",
                   "🚨 Severe disease (>15% area): Initiate field-wide treatment immediately. " +
                   "Recommend UAV precision spraying, prioritising large clusters.");
        } else if (diseasedPct > 5) {
            addRec("⚠️ 中度病害（5–15%面积）：对病斑区域进行精准施药，同时加强监测防止扩散。",
                   "⚠️ Moderate disease (5–15% area): Apply precision treatment to diseased zones. " +
                   "Increase monitoring frequency to prevent spread.");
        } else if (diseasedPct > 0.5) {
            addRec("ℹ️ 轻度病害（<5%面积）：标记并定点处理病斑，下周复查卫星/无人机影像。",
                   "ℹ️ Mild disease (<5% area): Mark and spot-treat diseased patches. " +
                   "Repeat UAV survey in 7 days.");
        }

        // --- NDVI-based recommendations ---
        if (ndviMean < 0.30) {
            addRec("🟡 全田NDVI偏低（均值<0.30）：检查灌溉、施肥情况，可能存在大面积营养缺乏或干旱胁迫。",
                   "🟡 Very low field-average NDVI (<0.30): Inspect irrigation and fertilisation. " +
                   "Possible widespread nutrient deficiency or drought stress.");
        } else if (ndviResult != null && ndviResult.getStressedAreaFraction() > 0.20) {
            addRec("🟡 超过20%区域NDVI低于胁迫阈值：建议土壤采样分析养分状况，重点检查低值聚集区。",
                   "🟡 >20% of area below NDVI stress threshold: Conduct soil sampling in low-value " +
                   "clusters. Check for nutrient deficiencies.");
        }

        // --- NDRE / early stress ---
        if (!Double.isNaN(ndviNdreCorrelation) && ndviNdreCorrelation < 0.5) {
            addRec("🔬 NDVI与NDRE相关性较低（r=" + String.format("%.2f", ndviNdreCorrelation) +
                   "）：部分区域存在早期胁迫（NDRE降低但NDVI尚未响应），建议提前干预。",
                   "🔬 Low NDVI–NDRE correlation (r=" + String.format("%.2f", ndviNdreCorrelation) +
                   "): Early-stage stress detected (NDRE declining before NDVI). " +
                   "Preventive intervention recommended.");
        }
        if (earlyStressFraction > 0.10) {
            addRec(String.format("🔬 约%.1f%%的像素表现为早期胁迫（NDRE低但NDVI正常）：考虑补充叶面肥或调整施肥方案。",
                                 earlyStressFraction * 100),
                   String.format("🔬 ~%.1f%% of pixels show early stress (low NDRE, normal NDVI): " +
                                 "Consider foliar fertilisation or adjust nutrient programme.",
                                 earlyStressFraction * 100));
        }
        if (ndreMean < 0.20) {
            addRec("🌿 NDRE均值偏低，叶绿素含量不足：建议增施氮肥，关注微量元素（Mg, Fe）状况。",
                   "🌿 Low mean NDRE – insufficient chlorophyll: Increase nitrogen application. " +
                   "Check micronutrient status (Mg, Fe).");
        }

        // --- Spatial pattern recommendations ---
        if (diseaseMaskResult != null) {
            if (diseaseMaskResult.getSpatialPattern() == DiseaseMaskResult.SpatialPattern.EDGE_CONCENTRATED) {
                addRec("📍 病害集中于田块边缘：排查边缘土壤、水源或外来传播源（如相邻田块感染）。",
                       "📍 Disease concentrated at field edges: Investigate edge soil conditions, " +
                       "water sources or external spread from neighbouring fields.");
            } else if (diseaseMaskResult.getSpatialPattern() == DiseaseMaskResult.SpatialPattern.CLUSTERED
                    && largeClusters >= 3) {
                addRec("📍 病害呈聚集分布，多个大型病斑：建议优先处理最大病斑以防止中心扩散。",
                       "📍 Clustered disease with multiple large foci: Prioritise treatment of the " +
                       "largest clusters to prevent epicentre-driven spread.");
            }
        }

        // --- Monitoring ---
        if (score.getRiskLevel() == HealthScore.RiskLevel.HIGH
                || score.getRiskLevel() == HealthScore.RiskLevel.CRITICAL) {
            addRec("📅 风险等级高：建议3–5天内再次飞行复查，动态追踪病害发展趋势。",
                   "📅 High risk level: Re-fly in 3–5 days to monitor disease progression dynamics.");
        } else {
            addRec("📅 建议每7–14天定期进行无人机巡检，持续监测作物健康状况。",
                   "📅 Recommend routine UAV survey every 7–14 days for ongoing crop health monitoring.");
        }
    }

    private void addRec(String zh, String en) {
        recommendationsZh.add(zh);
        recommendationsEn.add(en);
    }

    // -----------------------------------------------------------------------
    // Summary builders
    // -----------------------------------------------------------------------

    private String buildSummaryZh(HealthScore score, double ndviMean, double ndreMean, double diseasedPct) {
        return String.format("综合健康评分：%.1f/100（%s）。NDVI均值=%.3f，NDRE均值=%.3f，病害占比=%.2f%%。",
                score.getOverallScore(), score.getRiskLevel().getLabel(),
                ndviMean, ndreMean, diseasedPct);
    }

    private String buildSummaryEn(HealthScore score, double ndviMean, double ndreMean, double diseasedPct) {
        return String.format("Composite health score: %.1f/100 (%s). NDVI mean=%.3f, NDRE mean=%.3f, diseased=%.2f%%.",
                score.getOverallScore(), score.getRiskLevel().getLabel(),
                ndviMean, ndreMean, diseasedPct);
    }

    // -----------------------------------------------------------------------
    // Report generation
    // -----------------------------------------------------------------------

    /**
     * Generate a comprehensive, human-readable text report.
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        String sep = "═".repeat(72);
        String thin = "─".repeat(72);

        sb.append("\n").append(sep).append("\n");
        sb.append("  大疆3M无人机光谱图像智能分析报告\n");
        sb.append("  DJI Mavic 3 Multispectral – Intelligent Spectral Analysis Report\n");
        sb.append("  生成时间 / Generated: ").append(analysisTimestamp).append("\n");
        sb.append(sep).append("\n\n");

        // ---- Health Score ----
        if (healthScore != null) {
            sb.append("【综合健康评分 / Composite Health Score】\n");
            sb.append(thin).append("\n");
            sb.append(String.format("  总分 Score : %.1f / 100\n", healthScore.getOverallScore()));
            sb.append(String.format("  风险等级   : %s\n", healthScore.getRiskLevel().getLabel()));
            sb.append(String.format("  NDVI分项   : %.1f pts\n", healthScore.getNdviScore()));
            sb.append(String.format("  NDRE分项   : %.1f pts\n", healthScore.getNdreScore()));
            sb.append(String.format("  病害扣分   : -%.1f pts\n", healthScore.getDiseasePenalty()));
            sb.append("\n  📊 ").append(healthScore.getSummaryZh()).append("\n");
            sb.append("  📊 ").append(healthScore.getSummaryEn()).append("\n\n");
        }

        // ---- NDVI ----
        if (ndviResult != null) {
            sb.append("【NDVI 分析 / NDVI Analysis】\n");
            sb.append(thin).append("\n");
            appendIndexStats(sb, ndviResult);
        }

        // ---- NDRE ----
        if (ndreResult != null) {
            sb.append("【NDRE 分析 / NDRE Analysis】\n");
            sb.append(thin).append("\n");
            appendIndexStats(sb, ndreResult);
        }

        // ---- Cross analysis ----
        if (!Double.isNaN(ndviNdreCorrelation)) {
            sb.append("【NDVI–NDRE 相关性分析 / Cross-Index Correlation】\n");
            sb.append(thin).append("\n");
            sb.append(String.format("  Pearson r = %.4f  →  %s\n",
                    ndviNdreCorrelation, correlationLabel(ndviNdreCorrelation)));
            sb.append(String.format("  早期胁迫像素 Early stress pixels : %.1f%%\n",
                    earlyStressFraction * 100));
            sb.append(String.format("  晚期胁迫像素 Advanced stress pixels : %.1f%%\n\n",
                    advancedStressFraction * 100));
        }

        // ---- Disease mask ----
        if (diseaseMaskResult != null) {
            sb.append("【病害掩膜分析 / Disease Mask Analysis】\n");
            sb.append(thin).append("\n");
            appendDiseaseStats(sb, diseaseMaskResult);
        }

        // ---- Recommendations ----
        sb.append("【智能建议 / Intelligent Recommendations】\n");
        sb.append(thin).append("\n");
        if (recommendationsZh.isEmpty()) {
            sb.append("  无特殊建议，作物状态良好。\n");
            sb.append("  No specific recommendations – crop appears healthy.\n");
        } else {
            for (int i = 0; i < recommendationsZh.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(recommendationsZh.get(i)).append("\n");
                sb.append("     ").append(recommendationsEn.get(i)).append("\n\n");
            }
        }

        sb.append(sep).append("\n");
        sb.append("  报告结束 / End of Report\n");
        sb.append(sep).append("\n");

        return sb.toString();
    }

    private void appendIndexStats(StringBuilder sb, IndexAnalysisResult r) {
        sb.append(String.format("  像素总数 Total pixels  : %,d (有效/valid: %,d)\n",
                r.getTotalValidPixels() + r.getNoDataPixels(), r.getTotalValidPixels()));
        sb.append(String.format("  均值 Mean              : %.4f\n", r.getMean()));
        sb.append(String.format("  中位数 Median          : %.4f\n", r.getMedian()));
        sb.append(String.format("  标准差 Std Dev         : %.4f\n", r.getStdDev()));
        sb.append(String.format("  范围 Range             : [%.4f, %.4f]\n", r.getMin(), r.getMax()));
        sb.append(String.format("  偏度 Skewness          : %.4f  峰度 Kurtosis: %.4f\n",
                r.getSkewness(), r.getKurtosis()));
        sb.append(String.format("  百分位 Percentiles     : P5=%.3f  P25=%.3f  P75=%.3f  P95=%.3f\n",
                r.getP5(), r.getP25(), r.getP75(), r.getP95()));
        sb.append(String.format("  IQR (P75–P25)          : %.4f\n", r.getP75() - r.getP25()));
        sb.append(String.format("  空间异质性 Heterogeneity: %.4f  Moran's I: %.4f\n",
                r.getSpatialHeterogeneity(), r.getMoransI()));
        sb.append(String.format("  胁迫面积 Stressed area : %.2f%%  胁迫聚集区: %d 个\n\n",
                r.getStressedAreaFraction() * 100, r.getStressClusterCount()));

        // Zone table
        sb.append("  植被分区 / Vegetation Zones:\n");
        sb.append(String.format("  %-45s  %8s  %10s\n", "分区 Zone", "像素 px", "占比 %"));
        sb.append("  " + "─".repeat(68)).append("\n");
        if (r.getZones() != null) {
            for (ZoneInfo z : r.getZones()) {
                sb.append(String.format("  %s %-40s  %,8d  %9.2f%%\n",
                        z.getColorIndicator(),
                        z.getName() + " / " + z.getNameZh(),
                        z.getPixelCount(),
                        z.getCoveragePercent()));
            }
        }
        sb.append("\n");

        // Histogram
        if (r.getHistogramBinEdges() != null && r.getHistogramCounts() != null) {
            sb.append("  数值分布直方图 / Value Distribution Histogram:\n");
            int maxCount = 0;
            for (int c : r.getHistogramCounts()) if (c > maxCount) maxCount = c;
            if (maxCount > 0) {
                int barWidth = 40;
                for (int i = 0; i < r.getHistogramCounts().length; i++) {
                    int barLen = (int) Math.round((double) r.getHistogramCounts()[i] / maxCount * barWidth);
                    sb.append(String.format("  [%6.3f–%6.3f] |%-" + barWidth + "s| %,d%n",
                            r.getHistogramBinEdges()[i], r.getHistogramBinEdges()[i + 1],
                            "█".repeat(barLen),
                            r.getHistogramCounts()[i]));
                }
            }
            sb.append("\n");
        }

        // Top stress clusters
        if (r.getStressClusters() != null && !r.getStressClusters().isEmpty()) {
            sb.append("  主要胁迫聚集区 / Top Stress Clusters (largest 5):\n");
            int shown = Math.min(5, r.getStressClusters().size());
            for (int i = 0; i < shown; i++) {
                DiseaseCluster c = r.getStressClusters().get(i);
                sb.append(String.format("    #%d  %,d px  中心(%.0f,%.0f)  紧凑度=%.3f\n",
                        i + 1, c.getPixelCount(), c.getCentroidX(), c.getCentroidY(),
                        c.getCompactness()));
            }
            sb.append("\n");
        }

        sb.append("  解读 / Interpretation:\n");
        sb.append("  🇨🇳 ").append(r.getInterpretationZh()).append("\n");
        sb.append("  🇬🇧 ").append(r.getInterpretationEn()).append("\n\n");
    }

    private void appendDiseaseStats(StringBuilder sb, DiseaseMaskResult r) {
        sb.append(String.format("  总像素 Total pixels    : %,d\n", r.getTotalPixels()));
        sb.append(String.format("  健康像素 Healthy       : %,d (%.2f%%)\n",
                r.getHealthyPixels(), 100.0 - r.getDiseasedPercent()));
        sb.append(String.format("  病害像素 Diseased      : %,d (%.2f%%)\n",
                r.getDiseasedPixels(), r.getDiseasedPercent()));
        sb.append(String.format("  聚集区总数 Clusters    : %d  (大/Large: %d  中/Mid: %d  小/Small: %d)\n",
                r.getTotalClusters(), r.getLargeClusters(), r.getMediumClusters(), r.getSmallClusters()));
        sb.append(String.format("  最大病斑 Max cluster   : %.0f px  均值: %.1f px\n",
                r.getMaxClusterSizePx(), r.getMeanClusterSizePx()));
        sb.append(String.format("  离散指数 Dispersion Idx: %.3f\n", r.getClusterDispersionIndex()));
        sb.append(String.format("  空间模式 Spatial pattern: %s\n",
                r.getSpatialPattern() == null ? "N/A" : r.getSpatialPattern().getLabel()));

        // Class breakdown
        if (r.getClassPixelCounts() != null && !r.getClassPixelCounts().isEmpty()) {
            sb.append("\n  类别分布 / Class Breakdown:\n");
            r.getClassPixelCounts().forEach((cls, count) ->
                sb.append(String.format("    %-35s  %,8d px  (%.2f%%)\n",
                        cls, count, 100.0 * count / r.getTotalPixels()))
            );
        }

        // Top clusters
        if (r.getClusters() != null && !r.getClusters().isEmpty()) {
            sb.append("\n  主要病斑 / Top Disease Clusters (largest 5):\n");
            int shown = Math.min(5, r.getClusters().size());
            for (int i = 0; i < shown; i++) {
                DiseaseCluster c = r.getClusters().get(i);
                sb.append(String.format(
                    "    #%d  %,d px  中心(%.0f,%.0f)  BB[%d,%d→%d,%d]  紧凑度=%.3f  严重度=%s\n",
                    i + 1, c.getPixelCount(), c.getCentroidX(), c.getCentroidY(),
                    c.getMinX(), c.getMinY(), c.getMaxX(), c.getMaxY(),
                    c.getCompactness(), c.getSeverityLabel()));
            }
        }

        sb.append("\n  解读 / Interpretation:\n");
        sb.append("  🇨🇳 ").append(r.getInterpretationZh()).append("\n");
        sb.append("  🇬🇧 ").append(r.getInterpretationEn()).append("\n\n");
    }

    private String correlationLabel(double r) {
        if (r >= 0.85) return "极强正相关 / Very strong positive correlation";
        if (r >= 0.65) return "强正相关 / Strong positive correlation";
        if (r >= 0.40) return "中等正相关 / Moderate positive correlation";
        if (r >= 0.20) return "弱正相关 / Weak positive correlation";
        if (r >= -0.20) return "几乎无相关 / Near-zero correlation";
        return "负相关 / Negative correlation";
    }

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------
    public IndexAnalysisResult getNdviResult() { return ndviResult; }
    public void setNdviResult(IndexAnalysisResult ndviResult) { this.ndviResult = ndviResult; }

    public IndexAnalysisResult getNdreResult() { return ndreResult; }
    public void setNdreResult(IndexAnalysisResult ndreResult) { this.ndreResult = ndreResult; }

    public DiseaseMaskResult getDiseaseMaskResult() { return diseaseMaskResult; }
    public void setDiseaseMaskResult(DiseaseMaskResult diseaseMaskResult) { this.diseaseMaskResult = diseaseMaskResult; }

    public HealthScore getHealthScore() { return healthScore; }

    public double getNdviNdreCorrelation() { return ndviNdreCorrelation; }
    public double getEarlyStressFraction() { return earlyStressFraction; }
    public double getAdvancedStressFraction() { return advancedStressFraction; }

    public List<String> getRecommendationsZh() { return recommendationsZh; }
    public List<String> getRecommendationsEn() { return recommendationsEn; }

    public void setNdviPixels(float[] ndviPixels) { this.ndviPixels = ndviPixels; }
    public void setNdrePixels(float[] ndrePixels) { this.ndrePixels = ndrePixels; }
    public void setValidMask(boolean[] validMask) { this.validMask = validMask; }
}
