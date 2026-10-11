package ir.iau.library.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The read models deliberately omit the image blobs, so before these endpoints existed a client
 * could not display a cover or member photo that had actually been uploaded — the feature was
 * write-only. These tests pin the download contract: the bytes come back from a dedicated
 * endpoint with a content type sniffed from the payload, and the list exposes a cheap flag.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImageEndpointIntegrationTest {

    private static final int IMAGE_BYTES = 1024;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String login() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    /** PNG magic bytes followed by filler, so the sniffing logic has something real to read. */
    private static byte[] pngBytes() {
        byte[] bytes = new byte[IMAGE_BYTES];
        bytes[0] = (byte) 0x89;
        bytes[1] = 'P';
        bytes[2] = 'N';
        bytes[3] = 'G';
        for (int i = 4; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 97);
        }
        return bytes;
    }

    private static MockMultipartFile jsonPart(String name, String json) {
        return new MockMultipartFile(name, name + ".json",
                MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private long createBookWithCover(String token, byte[] cover) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String json = "{\"title\":\"Covered\",\"author\":\"Author\",\"isbn10\":\"cov-" + suffix
                + "\",\"subject\":\"Subject\"}";
        MvcResult res = mockMvc.perform(multipart("/api/book")
                        .file(jsonPart("book", json))
                        .file(new MockMultipartFile("bookCoverFile", "cover.png", "image/png", cover))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void uploadedCoverIsServedWithASniffedPngContentType() throws Exception {
        String token = login();
        byte[] cover = pngBytes();
        long id = createBookWithCover(token, cover);

        MvcResult res = mockMvc.perform(get("/api/book/" + id + "/cover")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andReturn();

        assertThat(res.getResponse().getContentAsByteArray()).isEqualTo(cover);
    }

    @Test
    void coverEndpointReturnsNotFoundForABookWithoutACover() throws Exception {
        String token = login();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult created = mockMvc.perform(multipart("/api/book")
                        .file(jsonPart("book", "{\"title\":\"No Cover\",\"author\":\"Author\",\"isbn10\":\"nc-"
                                + suffix + "\",\"subject\":\"Subject\"}"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/book/" + id + "/cover")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void coverEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/book/1/cover"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bookListAndGetExposeTheHasCoverFlag() throws Exception {
        String token = login();
        long withCover = createBookWithCover(token, pngBytes());

        MvcResult single = mockMvc.perform(get("/api/book/" + withCover)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCover").value(true))
                .andReturn();
        assertThat(objectMapper.readTree(single.getResponse().getContentAsString()).has("bookCoverFile"))
                .as("the blob itself must never be serialized")
                .isFalse();
    }

    @Test
    void memberPictureIsServedByItsOwnEndpoint() throws Exception {
        String token = login();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String json = "{\"firstName\":\"Photo\",\"lastName\":\"Member\",\"email\":\"photo-" + suffix
                + "@test.local\",\"nationalId\":\"ph" + suffix + "\"}";

        MvcResult created = mockMvc.perform(multipart("/api/person")
                        .file(jsonPart("person", json))
                        .file(new MockMultipartFile("profilePicture", "avatar.png", "image/png", pngBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasPicture").value(true))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        byte[] served = mockMvc.perform(get("/api/person/" + id + "/picture")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(served).isEqualTo(pngBytes());
    }

    @Test
    void nonImagePayloadIsServedAsOctetStreamRatherThanEchoingTheUploadHeader() throws Exception {
        String token = login();
        byte[] notAnImage = new byte[IMAGE_BYTES]; // all zero bytes: no image magic
        long id = createBookWithCover(token, notAnImage);

        MvcResult res = mockMvc.perform(get("/api/book/" + id + "/cover")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(res.getResponse().getContentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }
}
