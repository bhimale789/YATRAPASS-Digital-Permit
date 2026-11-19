package com.example.touristpermit.auth;

import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.example.touristpermit.support.TestUsers;
import com.example.touristpermit.user.Role;
import com.example.touristpermit.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIT extends AbstractWebIntegrationTest {

    @Autowired
    UserRepository users;

    private Map<String, String> registration(String email) {
        return Map.of("email", email, "password", "Str0ngPassw0rd", "firstName", "John", "lastName", "Smith");
    }

    private JsonNode register(String email) throws Exception {
        return body(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(registration(email))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    @Test
    void registrationCreatesTouristAndReturnsTokens() throws Exception {
        String email = TestUsers.uniqueEmail("Reg");
        JsonNode res = register(email);

        assertThat(res.get("accessToken").asText()).isNotBlank();
        assertThat(res.get("refreshToken").asText()).isNotBlank();
        assertThat(res.at("/user/role").asText()).isEqualTo("TOURIST");
        assertThat(res.at("/user/email").asText()).isEqualTo(email.toLowerCase());
        assertThat(users.findByEmail(email.toLowerCase())).get()
                .satisfies(u -> {
                    assertThat(u.getRole()).isEqualTo(Role.TOURIST);
                    assertThat(u.getPasswordHash()).startsWith("$2").doesNotContain("Str0ngPassw0rd");
                });
    }

    @Test
    void roleSuppliedByClientIsIgnored() throws Exception {
        String email = TestUsers.uniqueEmail("sneaky");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Str0ngPassw0rd","firstName":"A","lastName":"B","role":"ADMIN"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("TOURIST"));
    }

    @Test
    void duplicateEmailIsRejectedWith409() throws Exception {
        String email = TestUsers.uniqueEmail("dup");
        register(email);

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(registration(email.toUpperCase()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
    }

    @Test
    void invalidRegistrationReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", "not-an-email", "password", "short",
                                "firstName", "", "lastName", "X"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        org.hamcrest.Matchers.hasItems("email", "password", "firstName")))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("lastName"))));
    }

    @Test
    void loginSucceedsWithCorrectPassword() throws Exception {
        String email = TestUsers.uniqueEmail("login");
        register(email);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", email, "password", "Str0ngPassw0rd"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void loginFailsWithInvalidPassword() throws Exception {
        String email = TestUsers.uniqueEmail("badpw");
        register(email);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", email, "password", "WrongPassw0rd"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void loginForUnknownEmailGivesSameError() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", "nobody@example.test", "password", "WrongPassw0rd"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        String email = TestUsers.uniqueEmail("disabled");
        register(email);
        var user = users.findByEmail(email.toLowerCase()).orElseThrow();
        user.setEnabled(false);
        users.save(user);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("email", email, "password", "Str0ngPassw0rd"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokenAndDetectsReuse() throws Exception {
        JsonNode reg = register(TestUsers.uniqueEmail("refresh"));
        String firstRefresh = reg.get("refreshToken").asText();

        JsonNode rotated = body(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("refreshToken", firstRefresh))))
                .andExpect(status().isOk()).andReturn());
        String secondRefresh = rotated.get("refreshToken").asText();
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // Reusing the old token fails and revokes the whole session family.
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("refreshToken", firstRefresh))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("refreshToken", secondRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String refresh = register(TestUsers.uniqueEmail("logout")).get("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("refreshToken", refresh))))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/v1/tourists/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = testUsers.admin().token();
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");

        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void touristCannotAccessAdminApi() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", testUsers.tourist().bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/v1/tourists/me"))
                .andExpect(result -> {
                    assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
                    assertThat(result.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
                    assertThat(result.getResponse().getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
                });
    }
}
