package com.example.touristpermit.security;

import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.example.touristpermit.support.TestUsers.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role-based access matrix and cross-user isolation. */
class SecurityIT extends AbstractWebIntegrationTest {

    static final String[] ADMIN_ENDPOINTS = {
            "/api/v1/admin/dashboard",
            "/api/v1/admin/tourists",
            "/api/v1/admin/applications",
            "/api/v1/admin/permits",
            "/api/v1/admin/permits/expiring",
            "/api/v1/admin/areas",
            "/api/v1/admin/audit-logs",
            "/api/v1/verifications",
    };

    static final String[] TOURIST_ENDPOINTS = {
            "/api/v1/tourists/me",
            "/api/v1/applications",
            "/api/v1/permits",
    };

    TestUser tourist;
    TestUser officer;
    TestUser admin;

    @BeforeEach
    void users() {
        tourist = testUsers.tourist();
        officer = testUsers.officer();
        admin = testUsers.admin();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/admin/dashboard", "/api/v1/admin/tourists", "/api/v1/admin/applications", "/api/v1/admin/permits",
            "/api/v1/admin/areas", "/api/v1/admin/audit-logs", "/api/v1/verifications", "/api/v1/tourists/me",
            "/api/v1/applications", "/api/v1/permits", "/api/v1/permits/verify/abc"})
    void unauthenticatedRequestsAreRejected(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void touristCannotAccessAnyAdminApi() throws Exception {
        for (String path : ADMIN_ENDPOINTS) {
            mvc.perform(get(path).header("Authorization", tourist.bearer())).andExpect(status().isForbidden());
        }
    }

    @Test
    void officerCanOnlyUseTheScanner() throws Exception {
        for (String path : ADMIN_ENDPOINTS) {
            mvc.perform(get(path).header("Authorization", officer.bearer())).andExpect(status().isForbidden());
        }
        for (String path : TOURIST_ENDPOINTS) {
            mvc.perform(get(path).header("Authorization", officer.bearer())).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/permits/verify/abc").header("Authorization", officer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void adminCannotActAsTourist() throws Exception {
        for (String path : TOURIST_ENDPOINTS) {
            mvc.perform(get(path).header("Authorization", admin.bearer())).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/applications").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotEditTouristIdentityData() throws Exception {
        // There is no admin endpoint that changes passport/identity data; the tourist endpoint is TOURIST-only.
        mvc.perform(put("/api/v1/tourists/me").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"passportNumber\":\"HACKED123\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/tourists/" + tourist.profile().getId()).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"passportNumber\":\"HACKED123\"}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void adminAccountStatusEndpointCannotTargetStaffAccounts() throws Exception {
        // Status changes are addressed by tourist-profile id, so staff users are unreachable through it.
        mvc.perform(patch("/api/v1/admin/tourists/999999/status").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void touristDataIsIsolatedBetweenTourists() throws Exception {
        TestUser other = testUsers.tourist();
        long appId = submitApplication(tourist, testAreas.active(5), today().plusDays(1), today().plusDays(2));

        mvc.perform(get("/api/v1/applications").header("Authorization", other.bearer()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/applications/" + appId + "/documents").header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tourists/me").header("Authorization", other.bearer()))
                .andExpect(jsonPath("$.passportNumber").value(other.profile().getPassportNumber()));
    }

    @Test
    void publicEndpointsAreReachable() throws Exception {
        mvc.perform(get("/api/v1/areas")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void nonHealthActuatorEndpointsAreNotExposed() throws Exception {
        mvc.perform(get("/actuator/env").header("Authorization", admin.bearer())).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/beans").header("Authorization", admin.bearer())).andExpect(status().isNotFound());
    }

    @Test
    void errorsNeverLeakStackTraces() throws Exception {
        mvc.perform(get("/api/v1/admin/permits/not-a-number").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.message").value("Malformed or missing request data"));
    }
}
