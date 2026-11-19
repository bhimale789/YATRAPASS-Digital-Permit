package com.example.touristpermit.qr;

import com.example.touristpermit.common.util.SecureTokens;
import com.example.touristpermit.support.QrCodes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeGeneratorTest {

    final QrCodeGenerator generator = new QrCodeGenerator();

    /** Every generated QR must decode reliably — a QR an officer cannot scan is a failed permit. */
    @Test
    void generatedQrCodesAlwaysDecode() throws Exception {
        List<String> failures = new ArrayList<>();
        // Without the self-check, ~1% of random tokens produced an undecodable image (≈3 per 300).
        for (int i = 0; i < 1000; i++) {
            String url = "http://192.168.1.83:8081/verify/" + SecureTokens.randomUrlSafe(32);
            try {
                assertThat(QrCodes.decode(generator.png(url))).isEqualTo(url);
            } catch (Exception e) {
                failures.add(url);
            }
        }
        assertThat(failures).isEmpty();
    }
}
