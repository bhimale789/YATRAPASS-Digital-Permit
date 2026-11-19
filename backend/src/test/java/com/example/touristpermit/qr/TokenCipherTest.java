package com.example.touristpermit.qr;

import com.example.touristpermit.common.util.SecureTokens;
import com.example.touristpermit.config.AppProperties;
import com.example.touristpermit.support.TestProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCipherTest {

    final TokenCipher cipher = new TokenCipher(TestProperties.defaults());

    @Test
    void roundTripsAndUsesFreshIv() {
        String token = SecureTokens.randomUrlSafe(32);
        String c1 = cipher.encrypt(token, 7);
        String c2 = cipher.encrypt(token, 7);

        assertThat(c1).isNotEqualTo(c2).doesNotContain(token);
        assertThat(c1.length()).isLessThanOrEqualTo(255);
        assertThat(cipher.decrypt(c1, 7)).isEqualTo(token);
    }

    @Test
    void ciphertextIsBoundToPermit() {
        String c = cipher.encrypt("secret-token", 7);
        assertThatThrownBy(() -> cipher.decrypt(c, 8)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void detectsTampering() {
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt("secret-token", 7));
        raw[raw.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> cipher.decrypt(tampered, 7)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsWrongKeyLength() {
        AppProperties base = TestProperties.defaults();
        AppProperties shortKey = new AppProperties(base.baseUrl(), base.security(), base.storage(),
                new AppProperties.Qr(Base64.getEncoder().encodeToString(new byte[16])), base.verification(),
                base.permits(), null, base.routing());
        assertThatThrownBy(() -> new TokenCipher(shortKey)).isInstanceOf(IllegalStateException.class);
    }
}
