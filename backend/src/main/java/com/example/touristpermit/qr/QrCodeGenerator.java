package com.example.touristpermit.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Renders QR code PNGs with ZXing and verifies every image before returning it.
 * <p>
 * For roughly 1% of contents, the mask pattern ZXing picks produces a symbol that is valid
 * but that standard finder-pattern detectors (including the browser scanner) fail to locate.
 * Each image is therefore decoded back — as rendered and after camera-like downscaling — and
 * re-rendered with another mask pattern if needed. The encoded content never changes.
 */
@Component
public class QrCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(QrCodeGenerator.class);

    private static final int SIZE = 360;
    /** The QR specification's recommended quiet zone, in modules. */
    private static final int QUIET_ZONE = 4;
    private static final int MASK_PATTERNS = 8;

    public byte[] png(String content) {
        BufferedImage fallback = null;
        // null = let ZXing choose the mask; then try each of the 8 masks explicitly.
        for (int mask = -1; mask < MASK_PATTERNS; mask++) {
            BufferedImage image = render(content, mask < 0 ? null : mask);
            if (fallback == null) {
                fallback = image;
            }
            if (decodesReliably(image, content)) {
                return toPng(image);
            }
        }
        log.warn("No QR mask pattern passed the decode self-check; returning default rendering");
        return toPng(fallback);
    }

    private static BufferedImage render(String content, Integer maskPattern) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, QUIET_ZONE);
        if (maskPattern != null) {
            hints.put(EncodeHintType.QR_MASK_PATTERN, maskPattern);
        }
        try {
            return MatrixToImageWriter.toBufferedImage(
                    new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE, hints));
        } catch (WriterException e) {
            throw new IllegalStateException("QR encoding failed", e);
        }
    }

    /** Decodes the image as rendered and after downscaling, as a phone camera frame would be. */
    private static boolean decodesReliably(BufferedImage image, String expected) {
        return expected.equals(decode(image)) && expected.equals(decode(downscale(image, 0.7)));
    }

    private static String decode(BufferedImage image) {
        try {
            return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                    new BufferedImageLuminanceSource(image)))).getText();
        } catch (ReaderException e) {
            return null;
        }
    }

    private static BufferedImage downscale(BufferedImage src, double factor) {
        int size = (int) Math.round(src.getWidth() * factor);
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    private static byte[] toPng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
