package com.example.touristpermit.tourist;

import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.example.touristpermit.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TouristAndAreaIT extends AbstractWebIntegrationTest {

    private Map<String, Object> profile(String passport) {
        Map<String, Object> m = new HashMap<>();
        m.put("firstName", "John");
        m.put("lastName", "Smith");
        m.put("passportNumber", passport);
        m.put("nationality", "USA");
        m.put("dateOfBirth", "1990-05-14");
        m.put("gender", "MALE");
        m.put("phone", "+1 555 0100");
        m.put("address", "1 Example St");
        m.put("emergencyContact", "Jane Smith +1 555 0101");
        return m;
    }

    private static String passport() {
        return "X" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    @Test
    void touristCompletesProfile() throws Exception {
        TestUser t = testUsers.incompleteTourist();
        mvc.perform(get("/api/v1/tourists/me").header("Authorization", t.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false));

        String pp = passport();
        mvc.perform(put("/api/v1/tourists/me").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(profile(pp.toLowerCase()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.passportNumber").value(pp));
    }

    @Test
    void duplicatePassportIsConflict() throws Exception {
        TestUser first = testUsers.tourist();
        TestUser second = testUsers.incompleteTourist();
        mvc.perform(put("/api/v1/tourists/me").header("Authorization", second.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(profile(first.profile().getPassportNumber()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", not(org.hamcrest.Matchers.containsString(
                        first.profile().getPassportNumber()))));
    }

    @Test
    void invalidProfileIsRejected() throws Exception {
        TestUser t = testUsers.incompleteTourist();
        Map<String, Object> bad = profile("bad passport!");
        bad.put("dateOfBirth", "2999-01-01");
        mvc.perform(put("/api/v1/tourists/me").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("passportNumber")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("dateOfBirth")));
    }

    @Test
    void adminCannotEditTouristProfileViaTouristEndpoint() throws Exception {
        mvc.perform(put("/api/v1/tourists/me").header("Authorization", testUsers.admin().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(profile(passport()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTouristListMasksPassportButDetailShowsIt() throws Exception {
        TestUser t = testUsers.tourist();
        String pp = t.profile().getPassportNumber();
        String admin = testUsers.admin().bearer();

        mvc.perform(get("/api/v1/admin/tourists").param("q", pp).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].passportNumberMasked", startsWith("****")))
                .andExpect(jsonPath("$.content[0].passportNumber").doesNotExist());

        mvc.perform(get("/api/v1/admin/tourists/" + t.profile().getId()).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passportNumber").value(pp));
    }

    @Test
    void searchWithLikeWildcardsIsTreatedLiterally() throws Exception {
        testUsers.tourist();
        mvc.perform(get("/api/v1/admin/tourists").param("q", "%").header("Authorization", testUsers.admin().bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void disablingTouristBlocksTheirToken() throws Exception {
        TestUser t = testUsers.tourist();
        mvc.perform(patch("/api/v1/admin/tourists/" + t.profile().getId() + "/status")
                        .header("Authorization", testUsers.admin().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(get("/api/v1/tourists/me").header("Authorization", t.bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void areaAdministration() throws Exception {
        String admin = testUsers.admin().bearer();
        String code = "it-" + UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> req = Map.of("name", "Sample Area", "code", code, "permitRequired", true,
                "maximumDays", 7, "fee", "0.00", "active", true);

        String body = mvc.perform(post("/api/v1/admin/areas").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code.toUpperCase()))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        mvc.perform(post("/api/v1/admin/areas").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(req)))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/areas/" + id)).andExpect(status().isOk());

        mvc.perform(patch("/api/v1/admin/areas/" + id + "/status").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/v1/areas/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void touristCannotManageAreas() throws Exception {
        mvc.perform(post("/api/v1/admin/areas").header("Authorization", testUsers.tourist().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidSortPropertyIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/admin/areas").param("sort", "nonexistent")
                        .header("Authorization", testUsers.admin().bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sortingBySensitiveJoinedFieldIsRefused() throws Exception {
        mvc.perform(get("/api/v1/admin/tourists").param("sort", "user.passwordHash")
                        .header("Authorization", testUsers.admin().bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/tourists").param("sort", "passportNumber")
                        .header("Authorization", testUsers.admin().bearer()))
                .andExpect(status().isBadRequest());
    }
}
