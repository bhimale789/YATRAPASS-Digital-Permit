package com.example.touristpermit.support;

import com.example.touristpermit.area.PermitArea;
import com.example.touristpermit.config.ClockConfig;
import com.example.touristpermit.support.TestUsers.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integration test base with MockMvc and end-to-end flow helpers. */
@AutoConfigureMockMvc
@Import({TestUsers.class, TestAreas.class})
public abstract class AbstractWebIntegrationTest extends AbstractIntegrationTest {

    protected static final byte[] PDF = "%PDF-1.4\n% integration test\n".getBytes();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected TestUsers testUsers;

    @Autowired
    protected TestAreas testAreas;

    protected static LocalDate today() {
        return LocalDate.now(ClockConfig.PERMIT_ZONE);
    }

    protected String toJson(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected long submitApplication(TestUser t, PermitArea area, LocalDate start, LocalDate end) throws Exception {
        return body(mvc.perform(post("/api/v1/applications").header("Authorization", t.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("areaId", area.getId(), "purpose", "Trekking with guide",
                                "startDate", start.toString(), "endDate", end.toString()))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
    }

    protected void uploadPassport(TestUser t, long applicationId) throws Exception {
        mvc.perform(multipart("/api/v1/applications/" + applicationId + "/documents")
                        .file(new MockMultipartFile("file", "passport.pdf", "application/pdf", PDF))
                        .param("documentType", "PASSPORT")
                        .header("Authorization", t.bearer()))
                .andExpect(status().isCreated());
    }

    /** Approves the application and returns the issued permit JSON. */
    protected JsonNode approve(String adminBearer, long applicationId) throws Exception {
        return body(mvc.perform(post("/api/v1/admin/applications/" + applicationId + "/approve")
                        .header("Authorization", adminBearer))
                .andExpect(status().isCreated())
                .andReturn());
    }

    /** Full flow: tourist applies with a passport document, admin approves. Returns the permit JSON. */
    protected JsonNode issuePermit(TestUser tourist, String adminBearer, LocalDate start, LocalDate end)
            throws Exception {
        long applicationId = submitApplication(tourist, testAreas.active(30), start, end);
        uploadPassport(tourist, applicationId);
        return approve(adminBearer, applicationId);
    }
}
