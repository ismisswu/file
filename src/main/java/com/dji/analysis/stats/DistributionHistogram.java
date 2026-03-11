package com.dji.analysis.stats;

/**
 * Histogram data container with convenience analysis methods.
 */
public class DistributionHistogram {

    private final double[] binEdges;   // length = bins + 1
    private final int[] counts;        // length = bins
    private final int totalCount;

    public DistributionHistogram(double[] binEdges, int[] counts) {
        this.binEdges = binEdges;
        this.counts = counts;
        int total = 0;
        for (int c : counts) total += c;
        this.totalCount = total;
    }

    /** Number of bins. */
    public int getBinCount() { return counts.length; }

    /** Centre value of bin {@code i}. */
    public double binCenter(int i) { return (binEdges[i] + binEdges[i + 1]) / 2.0; }

    /** Frequency fraction (0–1) of bin {@code i}. */
    public double frequency(int i) { return totalCount == 0 ? 0 : (double) counts[i] / totalCount; }

    /** Index of the mode bin (highest count). */
    public int modeBinIndex() {
        int maxIdx = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[maxIdx]) maxIdx = i;
        }
        return maxIdx;
    }

    /** Centre value of the mode bin. */
    public double modeValue() { return binCenter(modeBinIndex()); }

    /**
     * Detect if the distribution is bimodal: two local maxima with a valley
     * between them whose count is < 80% of the lower peak.
     */
    public boolean isBimodal() {
        if (counts.length < 5) return false;
        int peaks = 0;
        for (int i = 1; i < counts.length - 1; i++) {
            if (counts[i] > counts[i - 1] && counts[i] > counts[i + 1]) peaks++;
        }
        return peaks >= 2;
    }

    /**
     * ASCII-art bar chart (max 60 chars wide) for console output.
     */
    public String toAsciiChart(int width) {
        int maxCount = 0;
        for (int c : counts) if (c > maxCount) maxCount = c;
        if (maxCount == 0) return "(empty histogram)";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < counts.length; i++) {
            int barLen = (int) Math.round((double) counts[i] / maxCount * width);
            sb.append(String.format("  [%6.3f–%6.3f] |%-" + width + "s| %d%n",
                    binEdges[i], binEdges[i + 1],
                    "█".repeat(barLen),
                    counts[i]));
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------
    public double[] getBinEdges() { return binEdges; }
    public int[] getCounts() { return counts; }
    public int getTotalCount() { return totalCount; }
}
