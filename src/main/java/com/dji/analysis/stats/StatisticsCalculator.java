package com.dji.analysis.stats;

import java.util.Arrays;

/**
 * Numerically-stable, allocation-efficient statistics calculator.
 * All methods accept a {@code float[]} array of values and an optional
 * {@code boolean[]} validity mask (null means all values are valid).
 */
public final class StatisticsCalculator {

    private StatisticsCalculator() {}

    // -----------------------------------------------------------------------
    // Central tendency
    // -----------------------------------------------------------------------

    /** Arithmetic mean using Kahan compensated summation for precision. */
    public static double mean(float[] values, boolean[] valid) {
        double sum = 0, comp = 0;
        int count = 0;
        for (int i = 0; i < values.length; i++) {
            if (valid == null || valid[i]) {
                double y = values[i] - comp;
                double t = sum + y;
                comp = (t - sum) - y;
                sum = t;
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    /** Median value (destructively sorts a copy of valid values). */
    public static double median(float[] values, boolean[] valid) {
        float[] copy = extractValid(values, valid);
        if (copy.length == 0) return Double.NaN;
        Arrays.sort(copy);
        int n = copy.length;
        return (n % 2 == 0) ? 0.5 * (copy[n / 2 - 1] + copy[n / 2]) : copy[n / 2];
    }

    // -----------------------------------------------------------------------
    // Spread
    // -----------------------------------------------------------------------

    /** Population standard deviation. */
    public static double stdDev(float[] values, boolean[] valid) {
        return Math.sqrt(variance(values, valid));
    }

    /** Population variance using Welford's online algorithm. */
    public static double variance(float[] values, boolean[] valid) {
        long n = 0;
        double mean = 0, m2 = 0;
        for (int i = 0; i < values.length; i++) {
            if (valid == null || valid[i]) {
                n++;
                double delta = values[i] - mean;
                mean += delta / n;
                m2 += delta * (values[i] - mean);
            }
        }
        return n < 2 ? 0.0 : m2 / n;
    }

    // -----------------------------------------------------------------------
    // Shape of distribution
    // -----------------------------------------------------------------------

    /**
     * Skewness (Fisher's moment coefficient).
     * Positive → right tail; negative → left tail.
     */
    public static double skewness(float[] values, boolean[] valid) {
        double mu = mean(values, valid);
        double sigma = stdDev(values, valid);
        if (sigma == 0) return 0;
        double sum = 0;
        int count = 0;
        for (int i = 0; i < values.length; i++) {
            if (valid == null || valid[i]) {
                sum += Math.pow((values[i] - mu) / sigma, 3);
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    /**
     * Excess kurtosis (normal distribution → 0).
     * Positive (leptokurtic) → heavy tails; negative (platykurtic) → light tails.
     */
    public static double excessKurtosis(float[] values, boolean[] valid) {
        double mu = mean(values, valid);
        double sigma = stdDev(values, valid);
        if (sigma == 0) return 0;
        double sum = 0;
        int count = 0;
        for (int i = 0; i < values.length; i++) {
            if (valid == null || valid[i]) {
                sum += Math.pow((values[i] - mu) / sigma, 4);
                count++;
            }
        }
        return count == 0 ? 0 : (sum / count) - 3.0;
    }

    // -----------------------------------------------------------------------
    // Order statistics / percentiles
    // -----------------------------------------------------------------------

    /**
     * Percentile using linear interpolation (same convention as NumPy's default).
     *
     * @param p percentage in [0, 100]
     */
    public static double percentile(float[] values, boolean[] valid, double p) {
        float[] sorted = extractValid(values, valid);
        if (sorted.length == 0) return Double.NaN;
        Arrays.sort(sorted);
        double idx = (p / 100.0) * (sorted.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) return sorted[lo];
        double frac = idx - lo;
        return sorted[lo] * (1 - frac) + sorted[hi] * frac;
    }

    public static double min(float[] values, boolean[] valid) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            if ((valid == null || valid[i]) && values[i] < min) min = values[i];
        }
        return min == Double.MAX_VALUE ? Double.NaN : min;
    }

    public static double max(float[] values, boolean[] valid) {
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            if ((valid == null || valid[i]) && values[i] > max) max = values[i];
        }
        return max == -Double.MAX_VALUE ? Double.NaN : max;
    }

    // -----------------------------------------------------------------------
    // Correlation
    // -----------------------------------------------------------------------

    /**
     * Pearson correlation coefficient between two arrays of the same length.
     * Both arrays must have the same validity mask (or null = all valid).
     */
    public static double pearsonCorrelation(float[] x, float[] y, boolean[] valid) {
        if (x.length != y.length) throw new IllegalArgumentException("Arrays must have same length");
        double meanX = mean(x, valid);
        double meanY = mean(y, valid);
        double sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < x.length; i++) {
            if (valid == null || valid[i]) {
                double dx = x[i] - meanX;
                double dy = y[i] - meanY;
                sumXY += dx * dy;
                sumX2 += dx * dx;
                sumY2 += dy * dy;
            }
        }
        double denom = Math.sqrt(sumX2 * sumY2);
        return denom == 0 ? 0 : sumXY / denom;
    }

    // -----------------------------------------------------------------------
    // Histogram
    // -----------------------------------------------------------------------

    /**
     * Compute a fixed-bin histogram over valid values.
     *
     * @param bins  number of bins
     * @param lo    lower bound (inclusive)
     * @param hi    upper bound (exclusive)
     * @return array of {@code bins} counts
     */
    public static int[] histogram(float[] values, boolean[] valid, int bins, double lo, double hi) {
        int[] counts = new int[bins];
        double range = hi - lo;
        if (range <= 0) return counts;
        for (int i = 0; i < values.length; i++) {
            if (valid == null || valid[i]) {
                double v = values[i];
                if (v < lo || v > hi) continue;
                int bin = (int) ((v - lo) / range * bins);
                if (bin >= bins) bin = bins - 1;
                counts[bin]++;
            }
        }
        return counts;
    }

    /** Compute the bin-edge array for a histogram (bins+1 values). */
    public static double[] histogramEdges(int bins, double lo, double hi) {
        double[] edges = new double[bins + 1];
        double step = (hi - lo) / bins;
        for (int i = 0; i <= bins; i++) edges[i] = lo + i * step;
        return edges;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    public static int countValid(boolean[] valid, int total) {
        if (valid == null) return total;
        int n = 0;
        for (boolean v : valid) if (v) n++;
        return n;
    }

    private static float[] extractValid(float[] values, boolean[] valid) {
        int count = countValid(valid, values.length);
        float[] result = new float[count];
        int idx = 0;
        for (int i = 0; i < values.length; i++) {
            if (valid == null || valid[i]) result[idx++] = values[i];
        }
        return result;
    }
}
