package com.example.touristpermit.road;

import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoadConditionIT extends AbstractWebIntegrationTest {

    static Map<String, Object> point(String title, String status, Long areaId, boolean active) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        m.put("description", "Expect delays");
        m.put("status", status);
        m.put("geometryType", "POINT");
        m.put("coordinates", List.of(Map.of("lat", 28.5967, "lng", 83.8203)));
        m.put("areaId", areaId);
        m.put("active", active);
        return m;
    }

    static Map<String, Object> line(String title, String status, Long areaId) {
        Map<String, Object> m = point(title, status, areaId, true);
        m.put("geometryType", "LINE");
        m.put("coordinates", List.of(Map.of("lat", 28.20, "lng", 83.98), Map.of("lat", 28.35, "lng", 83.90),
                Map.of("lat", 28.60, "lng", 83.82)));
        return m;
    }

    private JsonNode create(String admin, Map<String, Object> body) throws Exception {
        return body(mvc.perform(post("/api/v1/admin/road-conditions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isCreated()).andReturn());
    }

    @Test
    void adminCreatesPointAndLineVisibleToAllRoles() throws Exception {
        String admin = testUsers.admin().bearer();
        long areaId = testAreas.active(10).getId();
        JsonNode pt = create(admin, point("Landslide at km 42", "CLOSED", areaId, true));
        JsonNode ln = create(admin, line("Beni–Jomsom road", "CAUTION", null));

        assertThat(pt.get("coordinates")).hasSize(1);
        assertThat(ln.get("coordinates")).hasSize(3);
        assertThat(ln.at("/coordinates/2/lat").asDouble()).isEqualTo(28.60);
        assertThat(pt.at("/area/id").asLong()).isEqualTo(areaId);
        assertThat(pt.get("updatedBy").asText()).contains("@");

        for (String bearer : List.of(testUsers.tourist().bearer(), testUsers.officer().bearer(), admin)) {
            String json = mvc.perform(get("/api/v1/road-conditions").header("Authorization", bearer))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(json).contains("Landslide at km 42").contains("Beni–Jomsom road");
            assertThat(json).doesNotContain("updatedBy\":\"admin"); // staff identity hidden publicly
        }
    }

    @Test
    void areaFilterIncludesGeneralConditionsAndHidesInactive() throws Exception {
        String admin = testUsers.admin().bearer();
        long areaA = testAreas.active(10).getId();
        long areaB = testAreas.active(10).getId();
        create(admin, point("A-only", "CLOSED", areaA, true));
        create(admin, point("B-only", "OPEN", areaB, true));
        create(admin, point("General", "CAUTION", null, true));
        create(admin, point("Resolved", "OPEN", areaA, false));

        String tourist = testUsers.tourist().bearer();
        String withGeneral = mvc.perform(get("/api/v1/road-conditions").param("areaId", String.valueOf(areaA))
                .header("Authorization", tourist)).andReturn().getResponse().getContentAsString();
        assertThat(withGeneral).contains("A-only").contains("General").doesNotContain("B-only").doesNotContain("Resolved");

        String areaOnly = mvc.perform(get("/api/v1/road-conditions").param("areaId", String.valueOf(areaA))
                        .param("includeGeneral", "false").header("Authorization", tourist))
                .andReturn().getResponse().getContentAsString();
        assertThat(areaOnly).contains("A-only").doesNotContain("General");

        // Admin search still sees inactive ones
        mvc.perform(get("/api/v1/admin/road-conditions").param("active", "false").param("areaId", String.valueOf(areaA))
                        .header("Authorization", admin))
                .andExpect(jsonPath("$.content[0].title").value("Resolved"));
    }

    @Test
    void updateAndDelete() throws Exception {
        String admin = testUsers.admin().bearer();
        long id = create(admin, point("Snow on pass", "CAUTION", null, true)).get("id").asLong();

        Map<String, Object> changed = line("Snow on pass", "CLOSED", null);
        mvc.perform(put("/api/v1/admin/road-conditions/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(changed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.geometryType").value("LINE"));

        mvc.perform(delete("/api/v1/admin/road-conditions/" + id).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/road-conditions/" + id).header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidGeometryIsRejected() throws Exception {
        String admin = testUsers.admin().bearer();
        Map<String, Object> pointWithTwo = point("x", "OPEN", null, true);
        pointWithTwo.put("coordinates", List.of(Map.of("lat", 1, "lng", 1), Map.of("lat", 2, "lng", 2)));
        mvc.perform(post("/api/v1/admin/road-conditions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(pointWithTwo)))
                .andExpect(status().isUnprocessableEntity());

        Map<String, Object> lineWithOne = line("x", "OPEN", null);
        lineWithOne.put("coordinates", List.of(Map.of("lat", 1, "lng", 1)));
        mvc.perform(post("/api/v1/admin/road-conditions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(lineWithOne)))
                .andExpect(status().isUnprocessableEntity());

        Map<String, Object> outOfRange = point("x", "OPEN", null, true);
        outOfRange.put("coordinates", List.of(Map.of("lat", 123, "lng", 83)));
        mvc.perform(post("/api/v1/admin/road-conditions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(outOfRange)))
                .andExpect(status().isBadRequest());

        Map<String, Object> badStatus = point("x", "MAYBE", null, true);
        mvc.perform(post("/api/v1/admin/road-conditions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(badStatus)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminsCanChangeConditions() throws Exception {
        for (String bearer : List.of(testUsers.tourist().bearer(), testUsers.officer().bearer())) {
            mvc.perform(post("/api/v1/admin/road-conditions").header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(point("x", "OPEN", null, true))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/road-conditions")).andExpect(status().isUnauthorized());
    }
}
