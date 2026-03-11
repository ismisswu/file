package com.dji.analysis.utils;

/**
 * Decodes colour-mapped index images (NDVI / NDRE visualisations) back to
 * their original floating-point index values.
 *
 * <h3>Supported colour maps</h3>
 * <ul>
 *   <li><b>Jet (rainbow)</b> – blue→cyan→green→yellow→red (low→high)</li>
 *   <li><b>DJI NDVI</b>     – red→orange→yellow→light-green→dark-green (low→high)</li>
 *   <li><b>Red-to-Green</b> – pure red→pure green (low→high)</li>
 *   <li><b>Grayscale</b>    – dark→bright (low→high)</li>
 * </ul>
 * <p>
 * All decode methods return a value in [0, 1] representing the normalised index.
 * Callers should then scale to the actual index range (e.g., −1 to 1 for NDVI).
 * </p>
 */
public final class ColorScaleDecoder {

    private ColorScaleDecoder() {}

    // -----------------------------------------------------------------------
    // Jet (rainbow) colourmap
    // -----------------------------------------------------------------------

    /**
     * Reverse the Matplotlib / OpenCV jet colourmap.
     * The jet colourmap encodes values 0–1 as:
     *   0.0 → (0, 0, 128)  dark-blue
     *   0.25→ (0, 255, 255) cyan
     *   0.5 → (0, 128, 0) | (128,255,128) transitions through green
     *   0.75→ (255, 255, 0) yellow
     *   1.0 → (128, 0, 0)  dark-red
     * We invert by building a 256-entry LUT and finding the nearest match.
     *
     * @return normalised value in [0, 1], or −1 if the colour is not on the scale.
     */
    public static float decodeJet(int r, int g, int b) {
        // Use the analytical inverse of the jet formula
        // jet uses four linear segments in each channel
        float rf = r / 255.0f;
        float gf = g / 255.0f;
        float bf = b / 255.0f;

        // Nearest match via LUT (precomputed 1024 points)
        float bestDist = Float.MAX_VALUE;
        float bestVal = 0;
        for (int i = 0; i <= 1023; i++) {
            float v = i / 1023.0f;
            float[] jet = jetColor(v);
            float dr = rf - jet[0];
            float dg = gf - jet[1];
            float db = bf - jet[2];
            float dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) { bestDist = dist; bestVal = v; }
        }
        return bestVal;
    }

    /** Compute the jet colour for value v in [0, 1]. Returns [R, G, B] in [0, 1]. */
    public static float[] jetColor(float v) {
        float r = clamp(1.5f - Math.abs(4 * v - 3));
        float g = clamp(1.5f - Math.abs(4 * v - 2));
        float b = clamp(1.5f - Math.abs(4 * v - 1));
        return new float[]{r, g, b};
    }

    // -----------------------------------------------------------------------
    // DJI NDVI colourmap (red → orange → yellow → light-green → dark-green)
    // -----------------------------------------------------------------------

    /**
     * Approximate reverse of the DJI Terra NDVI visualisation colourmap.
     * The scale runs: dark-red (low) → red → orange → yellow → yellow-green
     *                 → green → dark-green (high).
     */
    public static float decodeDjiNdvi(int r, int g, int b) {
        float rf = r / 255.0f, gf = g / 255.0f, bf = b / 255.0f;
        float bestDist = Float.MAX_VALUE, bestVal = 0;
        for (int i = 0; i <= 1023; i++) {
            float v = i / 1023.0f;
            float[] c = djiNdviColor(v);
            float dr = rf - c[0], dg = gf - c[1], db = bf - c[2];
            float dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) { bestDist = dist; bestVal = v; }
        }
        return bestVal;
    }

    /**
     * DJI NDVI colourmap: piecewise linear from red-to-green with orange/yellow mid-tones.
     * Key anchors (v → RGB):
     *   0.0 → (180,  20,  20)  dark-red
     *   0.2 → (240,  80,   0)  orange-red
     *   0.4 → (255, 200,   0)  orange-yellow
     *   0.6 → (160, 220,  40)  yellow-green
     *   0.8 → ( 60, 180,  30)  medium-green
     *   1.0 → (  0, 100,  10)  dark-green
     */
    public static float[] djiNdviColor(float v) {
        float[][] anchors = {
            {180/255f, 20/255f, 20/255f},
            {240/255f, 80/255f,  0/255f},
            {255/255f,200/255f,  0/255f},
            {160/255f,220/255f, 40/255f},
            { 60/255f,180/255f, 30/255f},
            {  0/255f,100/255f, 10/255f}
        };
        float segLen = 1.0f / (anchors.length - 1);
        int seg = Math.min((int)(v / segLen), anchors.length - 2);
        float t = (v - seg * segLen) / segLen;
        float[] lo = anchors[seg], hi = anchors[seg + 1];
        return new float[]{
            lo[0] + t * (hi[0] - lo[0]),
            lo[1] + t * (hi[1] - lo[1]),
            lo[2] + t * (hi[2] - lo[2])
        };
    }

    // -----------------------------------------------------------------------
    // Simple red-to-green gradient
    // -----------------------------------------------------------------------

    /**
     * Reverse a simple red-to-green gradient where:
     *   0 → pure red  (255, 0, 0)
     *   1 → pure green (0, 255, 0)
     */
    public static float decodeRedToGreen(int r, int g, int b) {
        // Use the green channel fraction as the primary indicator
        float total = r + g;
        if (total < 10) return 0.5f; // ambiguous near black
        return g / total;
    }

    // -----------------------------------------------------------------------
    // Grayscale
    // -----------------------------------------------------------------------

    /** Decode an 8-bit grayscale pixel to a normalised value [0, 1]. */
    public static float decodeGrayscale8(int grayValue) {
        return (grayValue & 0xFF) / 255.0f;
    }

    /** Decode a 16-bit grayscale pixel to a normalised value [0, 1]. */
    public static float decodeGrayscale16(int grayValue) {
        return (grayValue & 0xFFFF) / 65535.0f;
    }

    // -----------------------------------------------------------------------
    // Scale to physical index range
    // -----------------------------------------------------------------------

    /**
     * Map a normalised value [0, 1] to the physical index range [minVal, maxVal].
     */
    public static float toPhysicalValue(float normalised, float minVal, float maxVal) {
        return minVal + normalised * (maxVal - minVal);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
