package com.dji.analysis.utils;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Loads images from disk and extracts flat pixel arrays in a uniform format.
 * <p>
 * Supports:
 * <ul>
 *   <li>8-bit grayscale PNG / JPEG</li>
 *   <li>16-bit grayscale PNG / TIFF</li>
 *   <li>8-bit RGB/RGBA PNG / JPEG</li>
 *   <li>32-bit float TIFF (via TwelveMonkeys plug-in registered in the JVM)</li>
 * </ul>
 * </p>
 */
public final class ImageLoader {

    private ImageLoader() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Load an image from the filesystem. */
    public static BufferedImage loadImage(File file) throws IOException {
        if (!file.exists()) {
            throw new IOException("Image file not found: " + file.getAbsolutePath());
        }
        BufferedImage img = ImageIO.read(file);
        if (img == null) {
            throw new IOException("Unsupported image format or corrupt file: " + file.getName());
        }
        return img;
    }

    /**
     * Extract a flat array of normalised float values [0, 1] from a grayscale image.
     * For 8-bit: divides by 255. For 16-bit: divides by 65535.
     *
     * @return row-major float array of length width × height
     */
    public static float[] extractGrayscaleNormalized(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        float[] result = new float[w * h];

        if (img.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < data.length; i++) {
                result[i] = (data[i] & 0xFF) / 255.0f;
            }
        } else if (img.getType() == BufferedImage.TYPE_USHORT_GRAY) {
            short[] data = ((DataBufferUShort) img.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < data.length; i++) {
                result[i] = (data[i] & 0xFFFF) / 65535.0f;
            }
        } else {
            // Fallback: convert to grayscale using luminance weights
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    result[y * w + x] = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
                }
            }
        }
        return result;
    }

    /**
     * Extract packed ARGB integers for a colour image.
     *
     * @return row-major int array of length width × height (ARGB packed)
     */
    public static int[] extractARGB(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] result = new int[w * h];
        img.getRGB(0, 0, w, h, result, 0, w);
        return result;
    }

    /**
     * Build a boolean validity mask: a pixel is invalid (no-data) when it is
     * fully transparent (alpha == 0) in ARGB images, or exactly 0 / 255 in
     * grayscale images typically used as padding.
     *
     * <p>For standard analysis images, all pixels are treated as valid (null returned).
     */
    public static boolean[] buildValidMask(BufferedImage img, boolean excludeBlackBorder) {
        if (!excludeBlackBorder) return null; // null = all valid

        int w = img.getWidth(), h = img.getHeight();
        boolean[] mask = new boolean[w * h];
        boolean hasAlpha = img.getColorModel().hasAlpha();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                int rgb = img.getRGB(x, y);
                if (hasAlpha) {
                    int alpha = (rgb >> 24) & 0xFF;
                    mask[idx] = (alpha > 0);
                } else {
                    // Treat pure black as no-data border
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    mask[idx] = (r > 0 || g > 0 || b > 0);
                }
            }
        }
        return mask;
    }

    /** Return true when the image appears to be a single-channel (grayscale) image. */
    public static boolean isGrayscale(BufferedImage img) {
        return img.getType() == BufferedImage.TYPE_BYTE_GRAY
                || img.getType() == BufferedImage.TYPE_USHORT_GRAY;
    }

    /** Convenience: read pixels as 2D boolean mask from a binary mask image. */
    public static boolean[][] readBinaryMask(BufferedImage img, int threshold) {
        int w = img.getWidth(), h = img.getHeight();
        boolean[][] mask = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int brightness = (r + g + b) / 3;
                mask[y][x] = brightness > threshold;
            }
        }
        return mask;
    }

    /** Read 2D boolean mask using a specific colour channel (0=R, 1=G, 2=B) above threshold. */
    public static boolean[][] readChannelMask(BufferedImage img, int channel, int threshold) {
        int w = img.getWidth(), h = img.getHeight();
        boolean[][] mask = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int value;
                if (channel == 0) value = (rgb >> 16) & 0xFF;
                else if (channel == 1) value = (rgb >> 8) & 0xFF;
                else value = rgb & 0xFF;
                mask[y][x] = value > threshold;
            }
        }
        return mask;
    }
}
