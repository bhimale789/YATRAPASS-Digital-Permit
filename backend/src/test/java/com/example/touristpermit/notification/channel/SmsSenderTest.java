package com.example.touristpermit.notification.channel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsSenderTest {

    @Test
    void masksAllButLastThreeDigits() {
        assertThat(SmsSender.mask("+977 9841234567")).isEqualTo("****567");
        assertThat(SmsSender.mask(null)).isEqualTo("***");
    }

    @Test
    void sparrowAcceptsOnlyNepaliMobileNumbers() {
        assertThat(SparrowSmsSender.toLocalNumber("9841234567")).isEqualTo("9841234567");
        assertThat(SparrowSmsSender.toLocalNumber("+977-984-1234567")).isEqualTo("9841234567");
        assertThatThrownBy(() -> SparrowSmsSender.toLocalNumber("+1 202 555 0100"))
                .isInstanceOf(RecipientNotSupportedException.class);
        assertThatThrownBy(() -> SparrowSmsSender.toLocalNumber("014123456"))
                .isInstanceOf(RecipientNotSupportedException.class);
    }
}
