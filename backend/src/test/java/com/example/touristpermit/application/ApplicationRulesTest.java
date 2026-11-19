package com.example.touristpermit.application;

import com.example.touristpermit.area.PermitArea;
import com.example.touristpermit.common.exception.BusinessRuleException;
import com.example.touristpermit.common.exception.ConflictException;
import com.example.touristpermit.permit.PermitRepository;
import com.example.touristpermit.support.TestProperties;
import com.example.touristpermit.tourist.TouristProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/** Unit tests for application business rules with mocked repositories. */
@ExtendWith(MockitoExtension.class)
class ApplicationRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    @Mock
    PermitApplicationRepository applications;
    @Mock
    PermitRepository permits;

    ApplicationService service;
    TouristProfile tourist;
    PermitArea area;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T06:00:00Z"), ZoneOffset.UTC);
        service = new ApplicationService(applications, null, null, null, permits, null, null, null, null, null, clock,
                TestProperties.defaults());
        tourist = new TouristProfile(null, "A", "B");
        ReflectionTestUtils.setField(tourist, "id", 1L);
        area = new PermitArea("Area", "AREA", 5);
        ReflectionTestUtils.setField(area, "id", 2L);
    }

    @Test
    void acceptsValidRequest() {
        when(applications.existsOverlapping(anyLong(), anyLong(), any(), any(), any())).thenReturn(false);
        when(permits.existsActiveOverlapping(anyLong(), anyLong(), any(), any())).thenReturn(false);
        assertThatCode(() -> service.validateRequest(tourist, area, TODAY, TODAY.plusDays(4))).doesNotThrowAnyException();
    }

    @Test
    void rejectsInactiveArea() {
        area.setActive(false);
        assertThatThrownBy(() -> service.validateRequest(tourist, area, TODAY, TODAY.plusDays(1)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not currently accepting");
    }

    @Test
    void rejectsAreaThatDoesNotRequirePermit() {
        area.setPermitRequired(false);
        assertThatThrownBy(() -> service.validateRequest(tourist, area, TODAY, TODAY.plusDays(1)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> service.validateRequest(tourist, area, TODAY.plusDays(3), TODAY.plusDays(1)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("End date cannot be before start date");
    }

    @Test
    void rejectsStartInPast() {
        assertThatThrownBy(() -> service.validateRequest(tourist, area, TODAY.minusDays(1), TODAY))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsDurationOverAreaMaximum() {
        // 6 inclusive days > max 5
        assertThatThrownBy(() -> service.validateRequest(tourist, area, TODAY, TODAY.plusDays(5)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("maximum of 5 days");
    }

    @Test
    void rejectsOverlappingOpenApplication() {
        when(applications.existsOverlapping(anyLong(), anyLong(), any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.validateRequest(tourist, area, TODAY, TODAY.plusDays(1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsOverlappingActivePermit() {
        when(applications.existsOverlapping(anyLong(), anyLong(), any(), any(), any())).thenReturn(false);
        when(permits.existsActiveOverlapping(anyLong(), anyLong(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.validateRequest(tourist, area, TODAY, TODAY.plusDays(1)))
                .isInstanceOf(ConflictException.class);
    }
}
