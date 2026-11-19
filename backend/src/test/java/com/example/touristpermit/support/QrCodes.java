package com.example.touristpermit.support;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/** Decodes QR PNGs in tests, exactly as a scanner would. */
public final class QrCodes {

    private QrCodes() {
    }

    public static String decode(byte[] png) throws Exception {
        return decode(ImageIO.read(new ByteArrayInputStream(png)));
    }

    public static String decode(BufferedImage image) throws Exception {
        return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(image)))).getText();
    }
}
