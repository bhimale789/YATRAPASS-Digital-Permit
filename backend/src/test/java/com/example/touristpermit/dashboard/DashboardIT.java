package com.example.touristpermit.dashboard;

import com.example.touristpermit.common.util.SecureTokens;
import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardIT extends AbstractWebIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    com.example.touristpermit.area.PermitAreaRepository areaRepository;

    private JsonNode dashboard(String admin) throws Exception {
        return body(mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn());
    }

    @Test
    void dashboardReflectsActivity() throws Exception {
        String admin = testUsers.admin().bearer();
        JsonNode before = dashboard(admin).get("stats");

        var tourist = testUsers.tourist();
        issuePermit(tourist, admin, today(), today().plusDays(2));
        submitApplication(tourist, testAreas.active(5), today().plusDays(1), today().plusDays(2));
        mvc.perform(get("/api/v1/permits/verify/" + SecureTokens.randomUrlSafe(32)).header("Authorization", admin));

        JsonNode d = dashboard(admin);
        JsonNode after = d.get("stats");
        assertThat(after.get("totalTourists").asLong()).isEqualTo(before.get("totalTourists").asLong() + 1);
        assertThat(after.get("activePermits").asLong()).isEqualTo(before.get("activePermits").asLong() + 1);
        assertThat(after.get("pendingApplications").asLong()).isEqualTo(before.get("pendingApplications").asLong() + 1);
        assertThat(after.get("invalidScansToday").asLong()).isEqualTo(before.get("invalidScansToday").asLong() + 1);
        assertThat(after.get("verificationsToday").asLong()).isEqualTo(before.get("verificationsToday").asLong() + 1);

        assertThat(d.get("recentApplications").size()).isBetween(1, 10);
        assertThat(d.get("recentVerifications").get(0).get("failureReason").asText()).isEqualTo("INVALID_TOKEN");
        assertThat(d.get("expiringPermits").size()).isGreaterThanOrEqualTo(1);
        assertThat(d.get("permitsByArea").size()).isGreaterThanOrEqualTo(1);
        assertThat(d.toString()).doesNotContain(tourist.profile().getPassportNumber());
        assertThat(d.get("fees").get("collectedTotal").isNumber()).isTrue();
        assertThat(d.get("fees").get("onlinePaymentsEnabled").asBoolean()).isFalse();
    }

    @Test
    void feeStatsTrackCounterPayments() throws Exception {
        String admin = testUsers.admin().bearer();
        JsonNode before = dashboard(admin).get("fees");
        var area = testAreas.active(5);
        area.setFee(new java.math.BigDecimal("750"));
        areaRepository.save(area);
        long appId = submitApplication(testUsers.tourist(), area, today().plusDays(1), today().plusDays(2));

        assertThat(dashboard(admin).get("fees").get("unpaidOpenApplications").asLong())
                .isEqualTo(before.get("unpaidOpenApplications").asLong() + 1);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/applications/" + appId + "/payments/offline").header("Authorization", admin)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"reference\":\"R-9\"}"))
                .andExpect(status().isCreated());

        JsonNode after = dashboard(admin).get("fees");
        assertThat(after.get("unpaidOpenApplications").asLong()).isEqualTo(before.get("unpaidOpenApplications").asLong());
        assertThat(after.get("collectedTotal").decimalValue())
                .isEqualByComparingTo(before.get("collectedTotal").decimalValue().add(new java.math.BigDecimal("750")));
        assertThat(after.get("collectedThisMonth").decimalValue())
                .isEqualByComparingTo(before.get("collectedThisMonth").decimalValue().add(new java.math.BigDecimal("750")));
    }

    @Test
    void auditLogIsQueryable() throws Exception {
        String admin = testUsers.admin().bearer();
        issuePermit(testUsers.tourist(), admin, today(), today().plusDays(1));

        JsonNode page = body(mvc.perform(get("/api/v1/admin/audit-logs").param("action", "QR_GENERATED")
                        .header("Authorization", admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(page.get("content").get(0).get("action").asText()).isEqualTo("QR_GENERATED");
    }

    @Test
    void officerCannotSeeDashboard() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", testUsers.officer().bearer()))
                .andExpect(status().isForbidden());
    }
}
