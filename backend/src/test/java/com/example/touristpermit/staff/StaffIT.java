package com.example.touristpermit.staff;

import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.example.touristpermit.support.TestUsers;
import com.example.touristpermit.support.TestUsers.TestUser;
import com.example.touristpermit.user.Role;
import com.example.touristpermit.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffIT extends AbstractWebIntegrationTest {

    @Autowired
    UserRepository users;

    private JsonNode create(String admin, String email, Role role) throws Exception {
        return body(mvc.perform(post("/api/v1/admin/staff").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", email, "displayName", "Checkpoint " + role, "role", role))))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode login(String email, String password) throws Exception {
        return body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn());
    }

    @Test
    void createdOfficerMustChangeTemporaryPasswordFirst() throws Exception {
        String admin = testUsers.admin().bearer();
        String email = TestUsers.uniqueEmail("officer");
        JsonNode created = create(admin, email, Role.OFFICER);
        String temp = created.get("temporaryPassword").asText();
        assertThat(temp).hasSize(16);
        assertThat(created.at("/staff/passwordChangeRequired").asBoolean()).isTrue();

        JsonNode session = login(email, temp);
        assertThat(session.get("passwordChangeRequired").asBoolean()).isTrue();
        String bearer = "Bearer " + session.get("accessToken").asText();

        // Everything except the account/password endpoints is blocked until the password is changed
        mvc.perform(get("/api/v1/officer/dashboard").header("Authorization", bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You must change your password before continuing"));
        mvc.perform(get("/api/v1/account").header("Authorization", bearer)).andExpect(status().isOk());

        // Staff need 12+ characters
        mvc.perform(post("/api/v1/account/password").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("currentPassword", temp, "newPassword", "short1pass"))))
                .andExpect(status().isUnprocessableEntity());
        JsonNode changed = body(mvc.perform(post("/api/v1/account/password").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("currentPassword", temp, "newPassword", "Checkpoint-Officer-2026"))))
                .andExpect(status().isOk()).andReturn());
        assertThat(changed.get("passwordChangeRequired").asBoolean()).isFalse();

        mvc.perform(get("/api/v1/officer/dashboard").header("Authorization", "Bearer " + changed.get("accessToken").asText()))
                .andExpect(status().isOk());
        // Old refresh token was revoked by the password change
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("refreshToken", session.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCurrentPasswordIsRejected() throws Exception {
        TestUser tourist = testUsers.tourist();
        mvc.perform(post("/api/v1/account/password").header("Authorization", tourist.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("currentPassword", "wrong-pass-1", "newPassword", "NewPassw0rd"))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/account/password").header("Authorization", tourist.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("currentPassword", TestUsers.PASSWORD, "newPassword", "NewPassw0rd"))))
                .andExpect(status().isOk());
    }

    @Test
    void resetPasswordAndDisableSignTheUserOut() throws Exception {
        String admin = testUsers.admin().bearer();
        TestUser officer = testUsers.officer();
        long id = officer.user().getId();
        String refresh = login(officer.user().getEmail(), TestUsers.PASSWORD).get("refreshToken").asText();

        String temp = body(mvc.perform(post("/api/v1/admin/staff/" + id + "/reset-password").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn()).get("temporaryPassword").asText();
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("refreshToken", refresh)))).andExpect(status().isUnauthorized());
        assertThat(login(officer.user().getEmail(), temp).get("passwordChangeRequired").asBoolean()).isTrue();

        mvc.perform(patch("/api/v1/admin/staff/" + id + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", officer.user().getEmail(), "password", temp))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotLockThemselvesOut() throws Exception {
        TestUser admin = testUsers.admin();
        long self = admin.user().getId();
        mvc.perform(patch("/api/v1/admin/staff/" + self + "/status").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/v1/admin/staff/" + self).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Me\",\"role\":\"OFFICER\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/admin/staff/" + self + "/reset-password").header("Authorization", admin.bearer()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void lastActiveAdminCannotBeDisabledOrDemoted() throws Exception {
        TestUser a = testUsers.admin();
        TestUser b = testUsers.admin();
        // Disable every other admin so that only a and b remain active
        users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN && u.isEnabled()
                        && !u.getId().equals(a.user().getId()) && !u.getId().equals(b.user().getId()))
                .forEach(u -> { u.setEnabled(false); users.save(u); });

        mvc.perform(patch("/api/v1/admin/staff/" + b.user().getId() + "/status").header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        // Now 'a' is the only active admin; a fresh admin token for another (disabled) admin can't act,
        // and 'a' cannot be demoted by anyone.
        TestUser c = testUsers.admin();
        mvc.perform(put("/api/v1/admin/staff/" + a.user().getId()).header("Authorization", c.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"A\",\"role\":\"OFFICER\"}"))
                .andExpect(status().isOk()); // c is also active, so a may be demoted
        mvc.perform(put("/api/v1/admin/staff/" + c.user().getId()).header("Authorization", c.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"C\",\"role\":\"OFFICER\"}"))
                .andExpect(status().isUnprocessableEntity()); // own role
        mvc.perform(patch("/api/v1/admin/staff/" + c.user().getId() + "/status").header("Authorization", c.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isUnprocessableEntity());
        // Restore other admins for later tests
        users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN)
                .forEach(u -> { u.setEnabled(true); users.save(u); });
    }

    @Test
    void staffListExcludesTouristsAndOnlyAdminsMayManage() throws Exception {
        TestUser tourist = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        mvc.perform(get("/api/v1/admin/staff").param("q", tourist.user().getEmail()).header("Authorization", admin))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/v1/admin/staff/" + tourist.user().getId() + "/reset-password").header("Authorization", admin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/staff").header("Authorization", testUsers.officer().bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/staff").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", TestUsers.uniqueEmail("x"), "displayName", "X", "role", "TOURIST"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void adminCanResetAnotherUsersTwoFactor() throws Exception {
        TestUser officer = testUsers.officer();
        String secret = body(mvc.perform(post("/api/v1/account/mfa/setup").header("Authorization", officer.bearer()))
                .andReturn()).get("secret").asText();
        mvc.perform(post("/api/v1/account/mfa/enable").header("Authorization", officer.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("code", com.example.touristpermit.support.TotpCodes.code(secret, 0)))));

        mvc.perform(post("/api/v1/admin/staff/" + officer.user().getId() + "/reset-mfa")
                        .header("Authorization", testUsers.admin().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaEnabled").value(false));
        assertThat(login(officer.user().getEmail(), TestUsers.PASSWORD).get("mfaRequired").asBoolean()).isFalse();
    }
}
