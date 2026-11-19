package com.example.touristpermit.area;

import com.example.touristpermit.support.AbstractWebIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AreaPhotoIT extends AbstractWebIntegrationTest {

    @Autowired
    PermitAreaRepository areas;

    @Autowired
    com.example.touristpermit.config.AppProperties properties;

    static byte[] png(int rgb) throws Exception {
        BufferedImage img = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, rgb);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private MockMultipartHttpServletRequestBuilder upload(long areaId, MockMultipartFile file, String bearer) {
        MockMultipartHttpServletRequestBuilder b = multipart(HttpMethod.PUT, "/api/v1/admin/areas/" + areaId + "/photo");
        b.file(file);
        b.header("Authorization", bearer);
        return b;
    }

    private Path stored(long areaId) {
        String rel = areas.findById(areaId).orElseThrow().getPhotoPath();
        return rel == null ? null : Path.of(properties.storage().path()).toAbsolutePath().normalize().resolve(rel);
    }

    @Test
    void adminUploadsReplacesAndRemovesPhoto() throws Exception {
        String admin = testUsers.admin().bearer();
        long id = testAreas.active(5).getId();
        byte[] first = png(0xff0000);

        JsonNode uploaded = body(mvc.perform(upload(id, new MockMultipartFile("file", "view.png", "image/png", first), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/areas/" + id + "/photo?v=")))
                .andReturn());
        Path firstFile = stored(id);
        assertThat(firstFile).exists();

        // Public (unauthenticated) photo endpoint serves the image with caching headers
        mvc.perform(get(uploaded.get("photoUrl").asText()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(first))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age")));

        // Photo URL appears in the public area listing
        mvc.perform(get("/api/v1/areas/" + id)).andExpect(jsonPath("$.photoUrl").exists());

        // Replacing writes a new file and deletes the old one after commit
        byte[] second = png(0x00ff00);
        mvc.perform(upload(id, new MockMultipartFile("file", "new.png", "image/png", second), admin))
                .andExpect(status().isOk());
        assertThat(firstFile).doesNotExist();
        assertThat(stored(id)).exists().isNotEqualTo(firstFile);
        mvc.perform(get("/api/v1/areas/" + id + "/photo")).andExpect(content().bytes(second));

        // Removing clears it; cards fall back to generated art
        Path secondFile = stored(id);
        mvc.perform(delete("/api/v1/admin/areas/" + id + "/photo").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").doesNotExist());
        assertThat(secondFile).doesNotExist();
        mvc.perform(get("/api/v1/areas/" + id + "/photo")).andExpect(status().isNotFound());
    }

    @Test
    void webpIsAccepted() throws Exception {
        long id = testAreas.active(5).getId();
        byte[] webp = {'R', 'I', 'F', 'F', 30, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ', 0, 0, 0, 0};
        mvc.perform(upload(id, new MockMultipartFile("file", "view.webp", "image/webp", webp), testUsers.admin().bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/areas/" + id + "/photo")).andExpect(content().contentType("image/webp"));
    }

    @Test
    void nonImagesAndDisguisedFilesAreRejected() throws Exception {
        String admin = testUsers.admin().bearer();
        long id = testAreas.active(5).getId();

        mvc.perform(upload(id, new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF), admin))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(upload(id, new MockMultipartFile("file", "evil.png", "image/png", "<svg onload=alert(1)>".getBytes()), admin))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(upload(id, new MockMultipartFile("file", "pic.svg", "image/svg+xml", "<svg/>".getBytes()), admin))
                .andExpect(status().isUnsupportedMediaType());
        assertThat(areas.findById(id).orElseThrow().hasPhoto()).isFalse();
    }

    @Test
    void onlyAdminsCanManagePhotos() throws Exception {
        long id = testAreas.active(5).getId();
        MockMultipartFile file = new MockMultipartFile("file", "view.png", "image/png", png(0));
        mvc.perform(upload(id, file, testUsers.tourist().bearer())).andExpect(status().isForbidden());
        mvc.perform(upload(id, file, testUsers.officer().bearer())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/areas/" + id + "/photo")).andExpect(status().isUnauthorized());
    }
}
