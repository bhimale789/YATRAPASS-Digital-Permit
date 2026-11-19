package com.example.touristpermit.application;

import com.example.touristpermit.permit.PermitStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "PENDING, UNDER_REVIEW, true",
            "PENDING, REJECTED, true",
            "PENDING, CANCELLED, true",
            "PENDING, APPROVED, false",
            "UNDER_REVIEW, APPROVED, true",
            "UNDER_REVIEW, REJECTED, true",
            "UNDER_REVIEW, CANCELLED, true",
            "UNDER_REVIEW, PENDING, false",
            "APPROVED, CANCELLED, false",
            "APPROVED, REJECTED, false",
            "REJECTED, APPROVED, false",
            "REJECTED, PENDING, false",
            "CANCELLED, PENDING, false",
    })
    void applicationTransitions(ApplicationStatus from, ApplicationStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "ACTIVE, EXPIRED, true",
            "ACTIVE, CANCELLED, true",
            "ACTIVE, COMPLETED, true",
            "ACTIVE, ACTIVE, false",
            "EXPIRED, ACTIVE, false",
            "CANCELLED, ACTIVE, false",
            "COMPLETED, CANCELLED, false",
            "EXPIRED, CANCELLED, false",
    })
    void permitTransitions(PermitStatus from, PermitStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
