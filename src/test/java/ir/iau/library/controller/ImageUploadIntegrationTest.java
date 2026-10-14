package ir.iau.library.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for defect 1: every image upload larger than 255 bytes returned HTTP 500.
 *
 * <p>{@code books.book_cover_file} and {@code person.profile_picture} are BLOBs on the main
 * tables, but the Envers audit tables declared them as {@code VARBINARY(255)}, so the audit
 * insert failed with "Value too long for column". The blobs are now {@code @NotAudited} and
 * {@code V2__drop_blob_audit_columns.sql} removes the stale columns, so a 4 KiB image must
 * round-trip through create/update.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImageUploadIntegrationTest {

    private static final int IMAGE_BYTES = 4096;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private PersonRepository personRepository;

    private String login() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private static byte[] imageBytes() {
        byte[] bytes = new byte[IMAGE_BYTES];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 251);
        }
        return bytes;
    }

    private static MockMultipartFile jsonPart(String name, String json) {
        return new MockMultipartFile(name, name + ".json",
                MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void bookCoverLargerThanTheOldAuditColumnIsAcceptedAndStoredWhole() throws Exception {
        String token = login();
        String isbn = "0306406152";
        String json = "{\"title\":\"Covered Book\",\"author\":\"Author\",\"isbn10\":\"" + isbn + "\","
                + "\"subject\":\"Subject\",\"active\":true}";

        MvcResult res = mockMvc.perform(multipart("/api/book")
                        .file(jsonPart("book", json))
                        .file(new MockMultipartFile("bookCoverFile", "cover.png", "image/png", imageBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        long id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
        assertThat(bookRepository.findById(id).orElseThrow().getBookCoverFile())
                .hasSize(IMAGE_BYTES);
    }

    @Test
    void personProfilePictureLargerThanTheOldAuditColumnIsAcceptedOnUpdate() throws Exception {
        String token = login();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "picture-" + suffix + "@test.local";
        String nationalId = "pic" + suffix;

        MvcResult created = mockMvc.perform(multipart("/api/person")
                        .file(jsonPart("person", "{\"firstName\":\"Pic\",\"lastName\":\"Ture\","
                                + "\"email\":\"" + email + "\",\"nationalId\":\"" + nationalId + "\"}"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        String updateJson = "{\"firstName\":\"Pic\",\"lastName\":\"Ture\",\"email\":\"" + email + "\","
                + "\"nationalId\":\"" + nationalId + "\",\"active\":true}";

        mockMvc.perform(multipart("/api/person/" + id)
                        .file(jsonPart("person", updateJson))
                        .file(new MockMultipartFile("profilePicture", "avatar.png", "image/png", imageBytes()))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(personRepository.findById(id).orElseThrow().getProfilePicture())
                .hasSize(IMAGE_BYTES);
    }

    @Test
    void bookCoverCanAlsoBeReplacedOnUpdate() throws Exception {
        String token = login();
        String isbn = "0306406152";
        String json = "{\"title\":\"Covered Book 2\",\"author\":\"Author\",\"isbn10\":\"" + isbn + "\","
                + "\"subject\":\"Subject\",\"active\":true}";

        MvcResult created = mockMvc.perform(multipart("/api/book")
                        .file(jsonPart("book", json))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(multipart("/api/book/" + id)
                        .file(jsonPart("book", json))
                        .file(new MockMultipartFile("bookCoverFile", "cover2.png", "image/png", imageBytes()))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(bookRepository.findById(id).orElseThrow().getBookCoverFile())
                .hasSize(IMAGE_BYTES);
    }
}
