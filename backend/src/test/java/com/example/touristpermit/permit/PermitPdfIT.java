package com.example.touristpermit.permit;

import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.example.touristpermit.support.QrCodes;
import com.example.touristpermit.support.TestUsers.TestUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PermitPdfIT extends AbstractWebIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void touristPdfContainsPermitDetailsAndTheWorkingQrCode() throws Exception {
        TestUser t = testUsers.tourist();
        JsonNode permit = issuePermit(t, testUsers.admin().bearer(), today().plusDays(1), today().plusDays(3));
        long id = permit.get("id").asLong();

        MvcResult result = mvc.perform(get("/api/v1/permits/" + id + "/pdf").header("Authorization", t.bearer()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Disposition",
                        "inline; filename=\"" + permit.get("permitNumber").asText() + ".pdf\""))
                .andReturn();

        try (PDDocument pdf = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains(permit.get("permitNumber").asText(),
                    permit.get("applicationNumber").asText(),
                    permit.get("area").get("name").asText(),
                    t.profile().getPassportNumber(),
                    "Test Tourist", "Testland", "Trekking with guide",
                    "STATUS: ACTIVE", "Not yet in effect", "3 days");

            List<BufferedImage> images = images(pdf.getPage(0));
            assertThat(images).hasSize(1);
            byte[] qrPng = mvc.perform(get("/api/v1/permits/" + id + "/qr").header("Authorization", t.bearer()))
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(QrCodes.decode(images.get(0))).isEqualTo(QrCodes.decode(qrPng))
                    .startsWith("http://test.local/verify/");
        }
    }

    @Test
    void downloadParameterServesAnAttachment() throws Exception {
        TestUser t = testUsers.tourist();
        long id = issuePermit(t, testUsers.admin().bearer(), today(), today().plusDays(1)).get("id").asLong();

        mvc.perform(get("/api/v1/permits/" + id + "/pdf").param("download", "true").header("Authorization", t.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment;")));
    }

    @Test
    void cancelledPermitPrintsWithoutQrCode() throws Exception {
        TestUser t = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        long id = issuePermit(t, admin, today(), today().plusDays(2)).get("id").asLong();
        mvc.perform(post("/api/v1/admin/permits/" + id + "/cancel").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Route closed\"}"))
                .andExpect(status().isOk());

        try (PDDocument pdf = Loader.loadPDF(pdfBytes("/api/v1/permits/" + id + "/pdf", t.bearer()))) {
            assertThat(new PDFTextStripper().getText(pdf))
                    .contains("STATUS: CANCELLED", "Reason: Route closed", "No QR code: permit is CANCELLED");
            assertThat(images(pdf.getPage(0))).isEmpty();
        }
    }

    @Test
    void revokedQrIsLeftOffAnActivePermit() throws Exception {
        TestUser t = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        long id = issuePermit(t, admin, today(), today().plusDays(2)).get("id").asLong();
        mvc.perform(post("/api/v1/admin/permits/" + id + "/qr/revoke").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Lost phone\"}"))
                .andExpect(status().isOk());

        try (PDDocument pdf = Loader.loadPDF(pdfBytes("/api/v1/permits/" + id + "/pdf", t.bearer()))) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("STATUS: VALID", "QR code not available");
            assertThat(images(pdf.getPage(0))).isEmpty();
        }
    }

    @Test
    void namesOutsideTheStandardFontStillRender() throws Exception {
        TestUser t = testUsers.tourist();
        jdbc.update("UPDATE tourist_profiles SET first_name = ?, last_name = ? WHERE id = ?",
                "Zoë", "Dvořák 李", t.profile().getId());
        long id = issuePermit(t, testUsers.admin().bearer(), today(), today().plusDays(1)).get("id").asLong();

        try (PDDocument pdf = Loader.loadPDF(pdfBytes("/api/v1/permits/" + id + "/pdf", t.bearer()))) {
            // ë and á exist in the font, ř loses its accent, 李 has no fallback
            assertThat(new PDFTextStripper().getText(pdf)).contains("Zoë Dvorák ?");
        }
    }

    @Test
    void permitPdfAccessIsRestricted() throws Exception {
        TestUser owner = testUsers.tourist();
        String admin = testUsers.admin().bearer();
        long id = issuePermit(owner, admin, today(), today().plusDays(1)).get("id").asLong();

        mvc.perform(get("/api/v1/permits/" + id + "/pdf").header("Authorization", testUsers.tourist().bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/permits/" + id + "/pdf").header("Authorization", owner.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/permits/" + id + "/pdf").header("Authorization", testUsers.officer().bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/permits/" + id + "/pdf"))
                .andExpect(status().isUnauthorized());
        assertThat(pdfBytes("/api/v1/admin/permits/" + id + "/pdf", admin)).startsWith("%PDF".getBytes());
    }

    private byte[] pdfBytes(String path, String bearer) throws Exception {
        return mvc.perform(get(path).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private static List<BufferedImage> images(PDPage page) throws Exception {
        List<BufferedImage> images = new ArrayList<>();
        for (COSName name : page.getResources().getXObjectNames()) {
            if (page.getResources().getXObject(name) instanceof PDImageXObject image) {
                images.add(image.getImage());
            }
        }
        return images;
    }
}
