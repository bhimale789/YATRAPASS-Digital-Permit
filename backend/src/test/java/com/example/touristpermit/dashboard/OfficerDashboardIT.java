package com.example.touristpermit.dashboard;

import com.example.touristpermit.common.util.SecureTokens;
import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.example.touristpermit.support.QrCodes;
import com.example.touristpermit.support.TestUsers.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OfficerDashboardIT extends AbstractWebIntegrationTest {

    private JsonNode dashboard(TestUser officer) throws Exception {
        return body(mvc.perform(get("/api/v1/officer/dashboard").header("Authorization", officer.bearer()))
                .andExpect(status().isOk()).andReturn());
    }

    private void scan(TestUser officer, String token) throws Exception {
        mvc.perform(get("/api/v1/permits/verify/" + token).header("Authorization", officer.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void showsOnlyTheOfficersOwnScans() throws Exception {
        TestUser me = testUsers.officer();
        TestUser colleague = testUsers.officer();

        scan(me, SecureTokens.randomUrlSafe(32));
        scan(me, SecureTokens.randomUrlSafe(32));
        scan(colleague, SecureTokens.randomUrlSafe(32));

        JsonNode d = dashboard(me);
        assertThat(d.at("/stats/scansToday").asLong()).isEqualTo(2);
        assertThat(d.at("/stats/invalidToday").asLong()).isEqualTo(2);
        assertThat(d.at("/stats/validToday").asLong()).isZero();
        assertThat(d.at("/stats/totalScans").asLong()).isEqualTo(2);
        assertThat(d.get("recentScans").size()).isEqualTo(2);
        assertThat(d.get("recentScans").get(0).get("failureReason").asText()).isEqualTo("INVALID_TOKEN");

        assertThat(dashboard(colleague).at("/stats/scansToday").asLong()).isEqualTo(1);
    }

    @Test
    void validScanIsCountedWithoutExposingTouristData() throws Exception {
        TestUser officer = testUsers.officer();
        TestUser tourist = testUsers.tourist();
        long before = dashboard(officer).at("/stats/permitsValidToday").asLong();

        long permitId = issuePermit(tourist, testUsers.admin().bearer(), today(), today().plusDays(1)).get("id").asLong();
        byte[] png = mvc.perform(get("/api/v1/permits/" + permitId + "/qr").header("Authorization", tourist.bearer()))
                .andReturn().getResponse().getContentAsByteArray();
        String url = QrCodes.decode(png);
        scan(officer, url.substring(url.lastIndexOf('/') + 1));

        JsonNode d = dashboard(officer);
        assertThat(d.at("/stats/validToday").asLong()).isEqualTo(1);
        assertThat(d.at("/stats/permitsValidToday").asLong()).isEqualTo(before + 1);
        assertThat(d.get("recentScans").get(0).get("result").asText()).isEqualTo("VALID");
        assertThat(d.toString())
                .doesNotContain(tourist.profile().getPassportNumber())
                .doesNotContain("Test Tourist");
    }

    @Test
    void onlyOfficersCanUseIt() throws Exception {
        mvc.perform(get("/api/v1/officer/dashboard").header("Authorization", testUsers.tourist().bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/officer/dashboard").header("Authorization", testUsers.admin().bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/officer/dashboard")).andExpect(status().isUnauthorized());
    }
}
