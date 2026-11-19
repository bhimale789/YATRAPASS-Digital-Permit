package com.example.touristpermit.qr;

import com.example.touristpermit.common.util.SecureTokens;
import com.example.touristpermit.permit.PermitService;
import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.example.touristpermit.support.QrCodes;
import com.example.touristpermit.support.TestUsers.TestUser;
import com.example.touristpermit.verification.PermitVerificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QrVerificationIT extends AbstractWebIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PermitVerificationRepository verifications;
    @Autowired
    PermitService permitService;

    record Issued(TestUser tourist, long permitId, String token, String admin) {
    }

    /** Issues a permit valid from {@code start} and extracts the token by decoding the tourist's QR image. */
    private Issued issue(LocalDate start, LocalDate end) throws Exception {
        TestUser tourist = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        JsonNode permit = issuePermit(tourist, admin, start, end);
        assertThat(permit.get("qrActive").asBoolean()).isTrue();
        long id = permit.get("id").asLong();
        return new Issued(tourist, id, tokenFromQr(tourist, id), admin);
    }

    private String tokenFromQr(TestUser tourist, long permitId) throws Exception {
        byte[] png = mvc.perform(get("/api/v1/permits/" + permitId + "/qr").header("Authorization", tourist.bearer()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        String url = QrCodes.decode(png);
        assertThat(url).startsWith("http://test.local/verify/");
        String token = url.substring("http://test.local/verify/".length());
        assertThat(token).matches(QrTokenService.TOKEN_PATTERN);
        return token;
    }

    private JsonNode verify(String bearer, String token) throws Exception {
        return body(mvc.perform(get("/api/v1/permits/verify/" + token).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn());
    }

    @Test
    void qrContainsOnlyVerificationUrlAndTokenIsStoredHashedAndEncrypted() throws Exception {
        Issued i = issue(today(), today().plusDays(2));
        String passport = i.tourist().profile().getPassportNumber();

        // The token is random, not derived from the passport or the (sequential) permit id.
        assertThat(i.token()).doesNotContain(passport)
                .isNotEqualTo(String.valueOf(i.permitId()))
                .doesNotMatch("\\d+")
                .hasSize(43);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT token_hash, token_ciphertext, status FROM verification_tokens WHERE permit_id = ?", i.permitId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("token_hash")).isEqualTo(SecureTokens.sha256Hex(i.token()));
        assertThat((String) rows.get(0).get("token_ciphertext")).doesNotContain(i.token());
        assertThat(rows.get(0).get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void validQrVerifiesWithSafeResponse() throws Exception {
        Issued i = issue(today(), today().plusDays(2));

        String raw = mvc.perform(get("/api/v1/permits/verify/" + i.token()).header("Authorization", i.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.touristName").value("Test Tourist"))
                .andExpect(jsonPath("$.nationality").value("Testland"))
                .andExpect(jsonPath("$.permitNumber").exists())
                .andExpect(jsonPath("$.validFrom").value(today().toString()))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(raw).doesNotContain(i.tourist().profile().getPassportNumber())
                .doesNotContain("dateOfBirth").doesNotContain("phone").doesNotContain("address");
    }

    @Test
    void officerCanVerifyButTouristCannot() throws Exception {
        Issued i = issue(today(), today().plusDays(2));
        assertThat(verify(testUsers.officer().bearer(), i.token()).get("valid").asBoolean()).isTrue();

        mvc.perform(get("/api/v1/permits/verify/" + i.token()).header("Authorization", i.tourist().bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/permits/verify/" + i.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownAndMalformedTokensAreInvalid() throws Exception {
        String admin = testUsers.admin().bearer();
        JsonNode unknown = verify(admin, SecureTokens.randomUrlSafe(32));
        assertThat(unknown.get("valid").asBoolean()).isFalse();
        assertThat(unknown.get("reason").asText()).isEqualTo("INVALID_TOKEN");
        assertThat(unknown.has("permitNumber")).isFalse();

        assertThat(verify(admin, "1").get("reason").asText()).isEqualTo("INVALID_TOKEN");
        assertThat(verify(admin, "not-a-token' OR 1=1 --").get("reason").asText()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void expiredPermitIsInvalidEvenBeforeExpiryJobRuns() throws Exception {
        Issued i = issue(today(), today().plusDays(2));
        jdbc.update("UPDATE permits SET valid_from = ?, valid_until = ? WHERE id = ?",
                today().minusDays(5), today().minusDays(1), i.permitId());

        JsonNode res = verify(i.admin(), i.token());
        assertThat(res.get("valid").asBoolean()).isFalse();
        assertThat(res.get("reason").asText()).isEqualTo("PERMIT_EXPIRED");
        assertThat(res.get("status").asText()).isEqualTo("EXPIRED");
        assertThat(res.has("touristName")).isFalse();

        permitService.expireDuePermits();
        assertThat(verify(i.admin(), i.token()).get("reason").asText()).isEqualTo("PERMIT_EXPIRED");
    }

    @Test
    void cancelledPermitIsInvalid() throws Exception {
        Issued i = issue(today(), today().plusDays(2));
        mvc.perform(post("/api/v1/admin/permits/" + i.permitId() + "/cancel").header("Authorization", i.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Fraud\"}"))
                .andExpect(status().isOk());

        JsonNode res = verify(i.admin(), i.token());
        assertThat(res.get("valid").asBoolean()).isFalse();
        assertThat(res.get("reason").asText()).isEqualTo("PERMIT_CANCELLED");

        // Tourist can no longer fetch the QR image for a cancelled permit
        mvc.perform(get("/api/v1/permits/" + i.permitId() + "/qr").header("Authorization", i.tourist().bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void completedPermitIsNotActive() throws Exception {
        Issued i = issue(today(), today().plusDays(2));
        mvc.perform(post("/api/v1/admin/permits/" + i.permitId() + "/complete").header("Authorization", i.admin()))
                .andExpect(status().isOk());
        assertThat(verify(i.admin(), i.token()).get("reason").asText()).isEqualTo("PERMIT_NOT_ACTIVE");
    }

    @Test
    void permitNotYetValid() throws Exception {
        Issued i = issue(today().plusDays(3), today().plusDays(4));
        JsonNode res = verify(i.admin(), i.token());
        assertThat(res.get("valid").asBoolean()).isFalse();
        assertThat(res.get("reason").asText()).isEqualTo("PERMIT_NOT_YET_VALID");
    }

    @Test
    void regeneratedQrRevokesOldToken() throws Exception {
        Issued i = issue(today(), today().plusDays(2));

        mvc.perform(post("/api/v1/admin/permits/" + i.permitId() + "/qr/regenerate").header("Authorization", i.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Phone stolen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrActive").value(true));

        JsonNode old = verify(i.admin(), i.token());
        assertThat(old.get("valid").asBoolean()).isFalse();
        assertThat(old.get("reason").asText()).isEqualTo("TOKEN_REVOKED");
        assertThat(old.has("permitNumber")).isFalse();

        String newToken = tokenFromQr(i.tourist(), i.permitId());
        assertThat(newToken).isNotEqualTo(i.token());
        assertThat(verify(i.admin(), newToken).get("valid").asBoolean()).isTrue();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM verification_tokens WHERE permit_id = ? AND status = 'REVOKED'",
                Long.class, i.permitId())).isEqualTo(1);
    }

    @Test
    void revokeWithoutReplacement() throws Exception {
        Issued i = issue(today(), today().plusDays(2));
        mvc.perform(post("/api/v1/admin/permits/" + i.permitId() + "/qr/revoke").header("Authorization", i.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Investigation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrActive").value(false));

        assertThat(verify(i.admin(), i.token()).get("reason").asText()).isEqualTo("TOKEN_REVOKED");
        mvc.perform(get("/api/v1/permits/" + i.permitId() + "/qr").header("Authorization", i.tourist().bearer()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/permits/" + i.permitId() + "/qr/revoke").header("Authorization", i.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void checkpointAreaMismatch() throws Exception {
        Issued i = issue(today(), today().plusDays(2));
        long otherArea = testAreas.active(5).getId();
        mvc.perform(get("/api/v1/permits/verify/" + i.token()).param("areaId", String.valueOf(otherArea))
                        .header("Authorization", i.admin()))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.reason").value("AREA_MISMATCH"));
    }

    @Test
    void touristCannotFetchAnotherTouristsQr() throws Exception {
        Issued i = issue(today(), today().plusDays(2));
        mvc.perform(get("/api/v1/permits/" + i.permitId() + "/qr").header("Authorization", testUsers.tourist().bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void everyVerificationIsLoggedAndVisibleInHistory() throws Exception {
        Issued i = issue(today(), today().plusDays(2));
        var officer = testUsers.officer();
        long before = verifications.count();

        verify(officer.bearer(), i.token());
        verify(officer.bearer(), SecureTokens.randomUrlSafe(32));
        mvc.perform(get("/api/v1/permits/verify/" + i.token()).header("Authorization", officer.bearer())
                .header("User-Agent", "ScannerTest/1.0"));

        assertThat(verifications.count()).isEqualTo(before + 3);
        assertThat(verifications.countByPermitId(i.permitId())).isEqualTo(2);

        mvc.perform(get("/api/v1/verifications").param("permitId", String.valueOf(i.permitId()))
                        .header("Authorization", i.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].result").value("VALID"))
                .andExpect(jsonPath("$.content[0].verifiedByEmail").value(officer.user().getEmail()))
                .andExpect(jsonPath("$.content[0].userAgent").value("ScannerTest/1.0"))
                .andExpect(jsonPath("$.content[0].latitude").doesNotExist());

        mvc.perform(get("/api/v1/verifications").param("result", "INVALID").header("Authorization", i.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].failureReason").exists());

        // Officers may scan but not browse the full history
        mvc.perform(get("/api/v1/verifications").header("Authorization", officer.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void locationIsNotStoredUnlessEnabled() throws Exception {
        Issued i = issue(today(), today().plusDays(2));
        mvc.perform(get("/api/v1/permits/verify/" + i.token()).param("latitude", "27.7").param("longitude", "85.3")
                        .header("Authorization", i.admin()))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM permit_verifications WHERE permit_id = ? AND latitude IS NOT NULL",
                Long.class, i.permitId())).isZero();
    }
}
