package com.example.touristpermit.application;

import com.example.touristpermit.area.PermitArea;
import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.example.touristpermit.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApplicationIT extends AbstractWebIntegrationTest {

    private String request(PermitArea area, LocalDate start, LocalDate end) throws Exception {
        return toJson(Map.of("areaId", area.getId(), "purpose", "Trekking with guide",
                "startDate", start.toString(), "endDate", end.toString()));
    }

    private long submit(TestUser t, PermitArea area, LocalDate start, LocalDate end) throws Exception {
        return body(mvc.perform(post("/api/v1/applications").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(request(area, start, end)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
    }

    @Test
    void submitApplicationCreatesPendingApplication() throws Exception {
        TestUser t = testUsers.tourist();
        PermitArea area = testAreas.active(10);

        mvc.perform(post("/api/v1/applications").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(area, today().plusDays(1), today().plusDays(5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.applicationNumber").value(org.hamcrest.Matchers.matchesPattern(
                        "APP-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.requestedDays").value(5));

        mvc.perform(get("/api/v1/applications").header("Authorization", t.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void incompleteProfileCannotApply() throws Exception {
        TestUser t = testUsers.incompleteTourist();
        mvc.perform(post("/api/v1/applications").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(testAreas.active(5), today().plusDays(1), today().plusDays(2))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void businessRulesAreEnforced() throws Exception {
        TestUser t = testUsers.tourist();
        PermitArea area = testAreas.active(3);

        // inactive area
        mvc.perform(post("/api/v1/applications").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(testAreas.inactive(), today().plusDays(1), today().plusDays(2))))
                .andExpect(status().isUnprocessableEntity());
        // end before start
        mvc.perform(post("/api/v1/applications").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(area, today().plusDays(3), today().plusDays(1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("End date cannot be before start date"));
        // exceeds maximum days (4 > 3)
        mvc.perform(post("/api/v1/applications").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(area, today().plusDays(1), today().plusDays(4))))
                .andExpect(status().isUnprocessableEntity());
        // unknown area
        mvc.perform(post("/api/v1/applications").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("areaId", 999999, "purpose", "Trekking",
                                "startDate", today().plusDays(1).toString(), "endDate", today().plusDays(1).toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void overlappingApplicationForSameAreaIsRejected() throws Exception {
        TestUser t = testUsers.tourist();
        PermitArea area = testAreas.active(10);
        submit(t, area, today().plusDays(1), today().plusDays(5));

        mvc.perform(post("/api/v1/applications").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(area, today().plusDays(4), today().plusDays(8))))
                .andExpect(status().isConflict());

        // Non-overlapping dates are fine
        submit(t, area, today().plusDays(6), today().plusDays(8));
    }

    @Test
    void touristCannotSeeAnotherTouristsApplication() throws Exception {
        TestUser owner = testUsers.tourist();
        long id = submit(owner, testAreas.active(5), today().plusDays(1), today().plusDays(2));

        TestUser other = testUsers.tourist();
        mvc.perform(get("/api/v1/applications/" + id).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/applications/" + id + "/cancel").header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelThenInvalidTransitionsAreRejected() throws Exception {
        TestUser t = testUsers.tourist();
        long id = submit(t, testAreas.active(5), today().plusDays(1), today().plusDays(2));

        mvc.perform(post("/api/v1/applications/" + id + "/cancel").header("Authorization", t.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/v1/applications/" + id + "/cancel").header("Authorization", t.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Application cannot transition from CANCELLED to CANCELLED"));

        String admin = testUsers.admin().bearer();
        mvc.perform(post("/api/v1/admin/applications/" + id + "/review").header("Authorization", admin))
                .andExpect(status().isConflict());
    }

    @Test
    void adminReviewAndRejectFlow() throws Exception {
        TestUser t = testUsers.tourist();
        long id = submit(t, testAreas.active(5), today().plusDays(1), today().plusDays(2));
        String admin = testUsers.admin().bearer();

        mvc.perform(get("/api/v1/admin/applications").param("status", "PENDING")
                        .param("q", t.profile().getPassportNumber()).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tourist.passportNumberMasked").exists());

        mvc.perform(post("/api/v1/admin/applications/" + id + "/review").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        mvc.perform(post("/api/v1/admin/applications/" + id + "/reject").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Missing documents\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Missing documents"))
                .andExpect(jsonPath("$.reviewedAt").exists());

        mvc.perform(post("/api/v1/admin/applications/" + id + "/reject").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/applications/" + id).header("Authorization", t.bearer()))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void touristCannotReviewApplications() throws Exception {
        TestUser t = testUsers.tourist();
        long id = submit(t, testAreas.active(5), today().plusDays(1), today().plusDays(2));
        mvc.perform(post("/api/v1/admin/applications/" + id + "/reject").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"self\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void documentUploadDownloadAndAuthorization() throws Exception {
        TestUser t = testUsers.tourist();
        long id = submit(t, testAreas.active(5), today().plusDays(1), today().plusDays(2));

        long docId = body(mvc.perform(multipart("/api/v1/applications/" + id + "/documents")
                        .file(new MockMultipartFile("file", "../../my passport.pdf", "application/pdf", PDF))
                        .param("documentType", "PASSPORT")
                        .header("Authorization", t.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("my passport.pdf"))
                .andExpect(jsonPath("$.storagePath").doesNotExist())
                .andReturn()).get("id").asLong();

        mvc.perform(get("/api/v1/applications/" + id + "/documents/" + docId + "/content")
                        .header("Authorization", t.bearer()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));

        // Another tourist cannot download it, even guessing IDs
        TestUser other = testUsers.tourist();
        mvc.perform(get("/api/v1/applications/" + id + "/documents/" + docId + "/content")
                        .header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());

        // Admin can download and verify
        String admin = testUsers.admin().bearer();
        mvc.perform(get("/api/v1/admin/documents/" + docId + "/content").header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/documents/" + docId + "/verify").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        // Application detail lists the document
        mvc.perform(get("/api/v1/admin/applications/" + id).header("Authorization", admin))
                .andExpect(jsonPath("$.documents.length()").value(1));

        // Owner can delete while PENDING
        mvc.perform(delete("/api/v1/applications/" + id + "/documents/" + docId).header("Authorization", t.bearer()))
                .andExpect(status().isNoContent());
    }

    @Test
    void maliciousUploadIsRejected() throws Exception {
        TestUser t = testUsers.tourist();
        long id = submit(t, testAreas.active(5), today().plusDays(1), today().plusDays(2));

        mvc.perform(multipart("/api/v1/applications/" + id + "/documents")
                        .file(new MockMultipartFile("file", "evil.pdf", "application/pdf", "MZ\u0090\0".getBytes()))
                        .param("documentType", "PASSPORT")
                        .header("Authorization", t.bearer()))
                .andExpect(status().isUnsupportedMediaType());

        mvc.perform(multipart("/api/v1/applications/" + id + "/documents")
                        .file(new MockMultipartFile("file", "script.sh", "text/x-shellscript", "#!/bin/sh".getBytes()))
                        .param("documentType", "OTHER")
                        .header("Authorization", t.bearer()))
                .andExpect(status().isUnsupportedMediaType());
    }
}
