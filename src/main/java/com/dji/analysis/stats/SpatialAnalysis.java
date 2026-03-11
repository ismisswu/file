package com.dji.analysis.stats;

import com.dji.analysis.model.DiseaseCluster;

import java.util.ArrayList;
import java.util.List;

/**
 * Spatial analysis utilities:
 * <ul>
 *   <li>Connected-component labelling (BFS, 4-connectivity)</li>
 *   <li>Cluster statistics (area, centroid, compactness, bounding box)</li>
 *   <li>Simplified Moran's I spatial autocorrelation</li>
 *   <li>Spatial heterogeneity via sliding-window standard deviation</li>
 * </ul>
 */
public final class SpatialAnalysis {

    private SpatialAnalysis() {}

    // -----------------------------------------------------------------------
    // Connected-component labelling
    // -----------------------------------------------------------------------

    /**
     * Label connected components in a boolean mask using 4-connectivity BFS.
     *
     * @param mask    2D boolean mask [rows][cols], true = foreground pixel
     * @param minSize minimum pixel count for a component to be included
     * @return list of {@link DiseaseCluster} objects, sorted largest-first
     */
    public static List<DiseaseCluster> labelComponents(boolean[][] mask, int minSize) {
        if (mask == null || mask.length == 0) return new ArrayList<>();
        int rows = mask.length;
        int cols = mask[0].length;
        int[][] labels = new int[rows][cols]; // 0 = unvisited
        int nextLabel = 1;
        List<DiseaseCluster> clusters = new ArrayList<>();

        int[] dRow = {-1, 1, 0, 0};
        int[] dCol = {0, 0, -1, 1};

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (mask[r][c] && labels[r][c] == 0) {
                    // BFS
                    int label = nextLabel++;
                    List<int[]> pixels = new ArrayList<>();
                    int[] queue = new int[rows * cols * 2];
                    int head = 0, tail = 0;
                    queue[tail++] = r;
                    queue[tail++] = c;
                    labels[r][c] = label;

                    while (head < tail) {
                        int cr = queue[head++];
                        int cc = queue[head++];
                        pixels.add(new int[]{cr, cc});

                        for (int d = 0; d < 4; d++) {
                            int nr = cr + dRow[d];
                            int nc = cc + dCol[d];
                            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols
                                    && mask[nr][nc] && labels[nr][nc] == 0) {
                                labels[nr][nc] = label;
                                queue[tail++] = nr;
                                queue[tail++] = nc;
                            }
                        }
                    }

                    if (pixels.size() >= minSize) {
                        clusters.add(buildCluster(label, pixels));
                    }
                }
            }
        }

        clusters.sort((a, b) -> Integer.compare(b.getPixelCount(), a.getPixelCount()));
        return clusters;
    }

    private static DiseaseCluster buildCluster(int label, List<int[]> pixels) {
        DiseaseCluster c = new DiseaseCluster(label);
        c.setPixelCount(pixels.size());

        int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;
        int minC = Integer.MAX_VALUE, maxC = Integer.MIN_VALUE;
        double sumR = 0, sumC = 0;

        for (int[] p : pixels) {
            int r = p[0], col = p[1];
            if (r < minR) minR = r;
            if (r > maxR) maxR = r;
            if (col < minC) minC = col;
            if (col > maxC) maxC = col;
            sumR += r;
            sumC += col;
        }

        c.setMinY(minR); c.setMaxY(maxR);
        c.setMinX(minC); c.setMaxX(maxC);
        c.setCentroidY(sumR / pixels.size());
        c.setCentroidX(sumC / pixels.size());

        // Approximate perimeter = boundary pixels (pixels with at least one non-foreground neighbour)
        // We use bounding-box perimeter approximation: 2*(w+h) for compactness
        int w = maxC - minC + 1;
        int h = maxR - minR + 1;
        double approxPerimeter = 2.0 * (w + h);
        double area = pixels.size();
        double compactness = (approxPerimeter > 0) ? (4 * Math.PI * area) / (approxPerimeter * approxPerimeter) : 0;
        c.setCompactness(Math.min(compactness, 1.0));

        return c;
    }

    // -----------------------------------------------------------------------
    // Spatial heterogeneity
    // -----------------------------------------------------------------------

    /**
     * Compute spatial heterogeneity as the mean standard deviation of
     * pixel values within sliding windows.  A higher value means the image
     * has more local variation (patchy or highly variable field).
     *
     * @param values flat float array in row-major order [rows × cols]
     * @param valid  validity mask (null = all valid)
     * @param rows   image height
     * @param cols   image width
     * @param window half-window radius (e.g., 2 → 5×5 window)
     */
    public static double spatialHeterogeneity(float[] values, boolean[] valid,
                                               int rows, int cols, int window) {
        double totalVar = 0;
        int windowCount = 0;

        for (int r = window; r < rows - window; r++) {
            for (int c = window; c < cols - window; c++) {
                // collect window values
                double sum = 0, sum2 = 0;
                int n = 0;
                for (int dr = -window; dr <= window; dr++) {
                    for (int dc = -window; dc <= window; dc++) {
                        int idx = (r + dr) * cols + (c + dc);
                        if (valid == null || valid[idx]) {
                            sum += values[idx];
                            sum2 += values[idx] * values[idx];
                            n++;
                        }
                    }
                }
                if (n > 1) {
                    double mean = sum / n;
                    double var = sum2 / n - mean * mean;
                    totalVar += Math.sqrt(Math.max(var, 0));
                    windowCount++;
                }
            }
        }

        return windowCount == 0 ? 0 : totalVar / windowCount;
    }

    // -----------------------------------------------------------------------
    // Simplified Moran's I
    // -----------------------------------------------------------------------

    /**
     * Compute a simplified Moran's I statistic using a rook-neighbourhood
     * (4-connectivity) spatial weight matrix.
     * <p>
     * Moran's I ≈ +1 → strong positive spatial autocorrelation (similar values cluster).<br>
     * Moran's I ≈  0 → random spatial pattern.<br>
     * Moran's I ≈ -1 → strong negative autocorrelation (checkerboard pattern).
     * </p>
     * <p>
     * Note: this is an O(n) approximation on a random subsample (max 50 000 pixels)
     * to keep runtime acceptable for large images.
     * </p>
     */
    public static double moransI(float[] values, boolean[] valid, int rows, int cols) {
        // Subsample for performance
        int maxSamples = 50_000;
        int step = Math.max(1, (rows * cols) / maxSamples);

        double globalMean = 0;
        int n = 0;
        for (int i = 0; i < values.length; i += step) {
            if (valid == null || valid[i]) { globalMean += values[i]; n++; }
        }
        if (n < 2) return 0;
        globalMean /= n;

        double numerator = 0, denominator = 0;
        int w = 0; // total number of active spatial weights

        int[] dRow = {-1, 1, 0, 0};
        int[] dCol = {0, 0, -1, 1};

        for (int i = 0; i < values.length; i += step) {
            if (valid != null && !valid[i]) continue;
            int r = i / cols;
            int c = i % cols;
            double zi = values[i] - globalMean;
            denominator += zi * zi;

            for (int d = 0; d < 4; d++) {
                int nr = r + dRow[d];
                int nc = c + dCol[d];
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                int ni = nr * cols + nc;
                if (valid != null && !valid[ni]) continue;
                numerator += zi * (values[ni] - globalMean);
                w++;
            }
        }

        if (denominator == 0 || w == 0) return 0;
        return (n / (double) w) * (numerator / denominator);
    }

    // -----------------------------------------------------------------------
    // Edge / boundary concentration test
    // -----------------------------------------------------------------------

    /**
     * Return the fraction of diseased pixels that lie within {@code edgeBand}
     * pixels of the image border.
     */
    public static double edgeConcentration(boolean[][] mask, int edgeBand) {
        int rows = mask.length;
        int cols = mask[0].length;
        int edgeCount = 0, totalCount = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (mask[r][c]) {
                    totalCount++;
                    if (r < edgeBand || r >= rows - edgeBand || c < edgeBand || c >= cols - edgeBand) {
                        edgeCount++;
                    }
                }
            }
        }
        return totalCount == 0 ? 0 : (double) edgeCount / totalCount;
    }
}
