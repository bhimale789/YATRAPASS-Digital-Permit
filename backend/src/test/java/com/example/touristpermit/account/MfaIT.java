package com.example.touristpermit.account;

import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.example.touristpermit.support.TestUsers;
import com.example.touristpermit.support.TotpCodes;
import com.example.touristpermit.support.TestUsers.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MfaIT extends AbstractWebIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    static String code(String secret, long stepOffset) {
        return TotpCodes.code(secret, stepOffset);
    }

    /** Enables 2FA for the user; returns {secret, recovery codes}. */
    private JsonNode[] enable(TestUser user) throws Exception {
        JsonNode setup = body(mvc.perform(post("/api/v1/account/mfa/setup").header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/YatraPass:")))
                .andExpect(jsonPath("$.qrPngDataUrl").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
                .andReturn());
        String secret = setup.get("secret").asText();
        JsonNode enabled = body(mvc.perform(post("/api/v1/account/mfa/enable").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("code", code(secret, 0)))))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(enabled.get("recoveryCodes")).hasSize(10);
        return new JsonNode[]{setup.get("secret"), enabled.get("recoveryCodes")};
    }

    private JsonNode login(TestUser user) throws Exception {
        return body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", user.user().getEmail(), "password", TestUsers.PASSWORD))))
                .andExpect(status().isOk()).andReturn());
    }

    private org.springframework.test.web.servlet.ResultActions mfa(String mfaToken, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/login/mfa").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("mfaToken", mfaToken, "code", code))));
    }

    @Test
    void fullTwoFactorLoginFlow() throws Exception {
        TestUser admin = testUsers.admin();
        JsonNode[] enabled = enable(admin);
        String secret = enabled[0].asText();

        // Password alone no longer yields a session
        JsonNode first = login(admin);
        assertThat(first.get("mfaRequired").asBoolean()).isTrue();
        assertThat(first.has("accessToken")).isFalse();
        String mfaToken = first.get("mfaToken").asText();

        // The MFA token cannot be used as an access token
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + mfaToken))
                .andExpect(status().isUnauthorized());

        mfa(mfaToken, "000000").andExpect(status().isUnauthorized());

        // Next step's code (the current one was consumed when enabling) completes sign-in
        String valid = code(secret, 1);
        String access = body(mfa(mfaToken, valid).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty()).andReturn()).get("accessToken").asText();
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + access)).andExpect(status().isOk());

        // Replaying the same code is refused
        mfa(login(admin).get("mfaToken").asText(), valid).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.mfaEnabled").value(true))
                .andExpect(jsonPath("$.recoveryCodesRemaining").value(10));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'LOGIN_MFA_FAILED' AND actor_id = ?",
                Long.class, admin.user().getId())).isGreaterThanOrEqualTo(2);
    }

    @Test
    void recoveryCodeWorksExactlyOnce() throws Exception {
        TestUser officer = testUsers.officer();
        String recovery = enable(officer)[1].get(0).asText();

        mfa(login(officer).get("mfaToken").asText(), recovery.toLowerCase()).andExpect(status().isOk());
        mfa(login(officer).get("mfaToken").asText(), recovery).andExpect(status().isUnauthorized());
    }

    @Test
    void secretIsEncryptedAtRest() throws Exception {
        TestUser admin = testUsers.admin();
        String secret = enable(admin)[0].asText();
        String stored = jdbc.queryForObject("SELECT totp_secret FROM users WHERE id = ?", String.class, admin.user().getId());
        assertThat(stored).isNotBlank().doesNotContain(secret);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mfa_recovery_codes WHERE user_id = ? AND code_hash ~ '^[0-9a-f]{64}$'",
                Long.class, admin.user().getId())).isEqualTo(10);
    }

    @Test
    void wrongCodeDoesNotEnableAndDisableNeedsACode() throws Exception {
        TestUser tourist = testUsers.tourist();
        String secret = body(mvc.perform(post("/api/v1/account/mfa/setup").header("Authorization", tourist.bearer()))
                .andReturn()).get("secret").asText();
        mvc.perform(post("/api/v1/account/mfa/enable").header("Authorization", tourist.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/v1/account/mfa/enable").header("Authorization", tourist.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("code", code(secret, 0)))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/account/mfa/disable").header("Authorization", tourist.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/account/mfa/disable").header("Authorization", tourist.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("code", code(secret, 1)))))
                .andExpect(status().isNoContent());
        assertThat(login(tourist).get("mfaRequired").asBoolean()).isFalse();
    }

    @Test
    void expiredOrForgedMfaTokenIsRejected() throws Exception {
        mfa("not-a-token", "123456").andExpect(status().isUnauthorized());
        // A normal access token is not accepted in place of an MFA token
        mfa(testUsers.admin().token(), "123456").andExpect(status().isUnauthorized());
    }
}
