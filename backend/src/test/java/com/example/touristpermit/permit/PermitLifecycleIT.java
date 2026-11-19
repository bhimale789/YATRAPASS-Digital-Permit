package com.example.touristpermit.permit;

import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditLogRepository;
import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.example.touristpermit.support.TestUsers.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PermitLifecycleIT extends AbstractWebIntegrationTest {

    @Autowired
    PermitService permitService;
    @Autowired
    AuditLogRepository auditLogs;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void approvalCreatesActivePermitAndUpdatesApplication() throws Exception {
        TestUser t = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        long appId = submitApplication(t, testAreas.active(10), today().plusDays(2), today().plusDays(6));
        uploadPassport(t, appId);

        JsonNode permit = approve(admin, appId);

        assertThat(permit.get("permitNumber").asText()).matches("PRM-\\d{4}-\\d{6}");
        assertThat(permit.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(permit.get("validFrom").asText()).isEqualTo(today().plusDays(2).toString());
        assertThat(permit.get("validUntil").asText()).isEqualTo(today().plusDays(6).toString());
        assertThat(permit.get("issueDate").asText()).isEqualTo(today().toString());

        mvc.perform(get("/api/v1/applications/" + appId).header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.permit.permitNumber").value(permit.get("permitNumber").asText()));

        mvc.perform(get("/api/v1/permits").param("status", "ACTIVE").header("Authorization", t.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tourist").doesNotExist());

        assertThat(auditLogs.findByActionAndEntityId(AuditAction.PERMIT_APPROVED, permit.get("id").asText()))
                .hasSize(1);
    }

    @Test
    void approvalRequiresPassportDocument() throws Exception {
        TestUser t = testUsers.tourist();
        long appId = submitApplication(t, testAreas.active(10), today().plusDays(1), today().plusDays(2));

        mvc.perform(post("/api/v1/admin/applications/" + appId + "/approve")
                        .header("Authorization", testUsers.admin().bearer()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("A passport document must be uploaded before approval"));

        // Failed approval must not leave the application half-transitioned
        mvc.perform(get("/api/v1/applications/" + appId).header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void onlyApprovableApplicationsGeneratePermits() throws Exception {
        TestUser t = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        long appId = submitApplication(t, testAreas.active(10), today().plusDays(1), today().plusDays(2));
        uploadPassport(t, appId);
        mvc.perform(post("/api/v1/admin/applications/" + appId + "/reject").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No\"}"))
                .andExpect(status().isOk());

        // REJECTED -> APPROVED is not allowed
        mvc.perform(post("/api/v1/admin/applications/" + appId + "/approve").header("Authorization", admin))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/permits").header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void doubleApprovalIsRejected() throws Exception {
        TestUser t = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        long appId = submitApplication(t, testAreas.active(10), today().plusDays(1), today().plusDays(2));
        uploadPassport(t, appId);
        approve(admin, appId);

        mvc.perform(post("/api/v1/admin/applications/" + appId + "/approve").header("Authorization", admin))
                .andExpect(status().isConflict());
    }

    @Test
    void touristCannotApprove() throws Exception {
        TestUser t = testUsers.tourist();
        long appId = submitApplication(t, testAreas.active(10), today().plusDays(1), today().plusDays(2));
        uploadPassport(t, appId);
        mvc.perform(post("/api/v1/admin/applications/" + appId + "/approve").header("Authorization", t.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancellationAndInvalidTransitions() throws Exception {
        TestUser t = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        long permitId = issuePermit(t, admin, today().plusDays(1), today().plusDays(3)).get("id").asLong();

        mvc.perform(post("/api/v1/admin/permits/" + permitId + "/cancel").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Issued in error\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Issued in error"));

        // CANCELLED is terminal
        mvc.perform(post("/api/v1/admin/permits/" + permitId + "/complete").header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Permit cannot transition from CANCELLED to COMPLETED"));
        mvc.perform(post("/api/v1/admin/permits/" + permitId + "/cancel").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\"}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/admin/permits").param("status", "CANCELLED").param("q", t.profile().getPassportNumber())
                        .header("Authorization", admin))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void completion() throws Exception {
        TestUser t = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        long permitId = issuePermit(t, admin, today().plusDays(1), today().plusDays(3)).get("id").asLong();

        mvc.perform(post("/api/v1/admin/permits/" + permitId + "/complete").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void expiryJobExpiresPermitsPastValidity() throws Exception {
        TestUser t = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        long permitId = issuePermit(t, admin, today().plusDays(1), today().plusDays(3)).get("id").asLong();
        long futurePermitId = issuePermit(t, admin, today().plusDays(1), today().plusDays(3)).get("id").asLong();

        jdbc.update("UPDATE permits SET valid_from = ?, valid_until = ? WHERE id = ?",
                today().minusDays(5), today().minusDays(1), permitId);

        assertThat(permitService.expireDuePermits()).isGreaterThanOrEqualTo(1);

        mvc.perform(get("/api/v1/permits/" + permitId).header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(get("/api/v1/permits/" + futurePermitId).header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/permits").param("status", "EXPIRED").header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.totalElements").value(1));

        // EXPIRED -> CANCELLED is not allowed
        mvc.perform(post("/api/v1/admin/permits/" + permitId + "/cancel").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"late\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void touristCannotViewAnotherTouristsPermit() throws Exception {
        TestUser owner = testUsers.tourist();
        long permitId = issuePermit(owner, testUsers.admin().bearer(), today().plusDays(1), today().plusDays(2))
                .get("id").asLong();

        mvc.perform(get("/api/v1/permits/" + permitId).header("Authorization", testUsers.tourist().bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void activePermitBlocksOverlappingApplication() throws Exception {
        TestUser t = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        var area = testAreas.active(10);
        long appId = submitApplication(t, area, today().plusDays(1), today().plusDays(4));
        uploadPassport(t, appId);
        approve(admin, appId);

        mvc.perform(post("/api/v1/applications").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(java.util.Map.of("areaId", area.getId(), "purpose", "Second trip",
                                "startDate", today().plusDays(3).toString(), "endDate", today().plusDays(5).toString()))))
                .andExpect(status().isConflict());
    }

    @Test
    void expiringPermitsListing() throws Exception {
        TestUser t = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        issuePermit(t, admin, today().plusDays(1), today().plusDays(2));

        mvc.perform(get("/api/v1/admin/permits/expiring").param("days", "7").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].validUntil").exists());
        mvc.perform(get("/api/v1/admin/permits/expiring").param("days", "500").header("Authorization", admin))
                .andExpect(status().isBadRequest());
    }
}
