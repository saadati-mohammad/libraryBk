package ir.iau.library.controller;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that an invalid book payload is rejected with a 400 and field-level messages.
 *
 * <p>Book is created through a multipart part ({@code book=@...json}) and the entity is
 * bound directly from the request, so {@code @Valid @RequestPart} is the path exercised
 * here. A title may use either an ISBN-10 or an ISBN-13 identifier.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookValidationTest {

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

    private MockMultipartFile bookPart(String json) {
        return new MockMultipartFile("book", "book.json", "application/json", json.getBytes());
    }

    @Test
    void missingRequiredFieldsReturn400WithFieldMessages() throws Exception {
        String token = login();
        // No title, author, ISBN identifier or subject.
        String json = "{\"publisher\":\"X\"}";

        MvcResult res = mockMvc.perform(multipart("/api/book")
                        .file(bookPart(json))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("message").asText())
                .contains("title").contains("author").contains("isbn").contains("subject");
    }

    @Test
    void negativePageCountReturns400() throws Exception {
        String token = login();
        String json = "{\"title\":\"T\",\"author\":\"A\",\"isbn10\":\"0-306-40615-2\","
                + "\"subject\":\"S\",\"pageCount\":-5}";

        mockMvc.perform(multipart("/api/book")
                        .file(bookPart(json))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validBookIsCreated() throws Exception {
        String token = login();
        String json = "{\"title\":\"Valid Book\",\"author\":\"Author\","
                + "\"isbn10\":\"0-306-40615-2\",\"subject\":\"Subject\","
                + "\"copyCount\":1,\"active\":true}";

        MvcResult res = mockMvc.perform(multipart("/api/book")
                        .file(bookPart(json))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("id").asLong()).isPositive();
        assertThat(body.get("title").asText()).isEqualTo("Valid Book");
        assertThat(body.get("isbn10").asText()).isEqualTo("0306406152");
    }

    @Test
    void isbn13OnlyBookIsCreated() throws Exception {
        String token = login();
        String json = "{\"title\":\"ISBN-13 Book\",\"author\":\"Author\","
                + "\"isbn13\":\"9780306406157\",\"subject\":\"Subject\",\"copyCount\":1}";

        MvcResult res = mockMvc.perform(multipart("/api/book")
                        .file(bookPart(json))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("isbn13").asText()).isEqualTo("9780306406157");
        assertThat(body.has("isbnIdentifierPresent")).isFalse();
    }

    @Test
    void invalidIsbnChecksumIsRejected() throws Exception {
        String token = login();
        String json = "{\"title\":\"Invalid ISBN\",\"author\":\"Author\","
                + "\"isbn13\":\"9780306406158\",\"subject\":\"Subject\"}";

        MvcResult result = mockMvc.perform(multipart("/api/book")
                        .file(bookPart(json))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("message").asText())
                .contains("isbnIdentifierValid");
    }

    @Test
    void persianDigitsAndSeparatorsAreCanonicalized() throws Exception {
        String token = login();
        String json = "{\"title\":\"Persian ISBN\",\"author\":\"Author\","
                + "\"isbn13\":\"۹۷۸-۰۳۰۶۴۰۶۱۵۷\",\"subject\":\"Subject\"}";

        MvcResult result = mockMvc.perform(multipart("/api/book")
                        .file(bookPart(json))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("isbn13").asText())
                .isEqualTo("9780306406157");
    }
}
