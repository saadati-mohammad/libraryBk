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

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks down the client-error contract added to {@code GlobalExceptionHandler}: malformed
 * input must produce 4xx with the {@code ApiResponse} envelope instead of a 500, and a
 * database conflict must not leak raw persistence text.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerContractTest {

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

    private static MockMultipartFile jsonPart(String name, String json) {
        return new MockMultipartFile(name, name + ".json",
                MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void malformedJsonBodyReturns400WithEnvelope() throws Exception {
        String token = login();

        MvcResult res = mockMvc.perform(post("/api/loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personId\": 1,")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEnvelope(res);
    }

    @Test
    void malformedPublicLoginBodyReturns400WithEnvelope() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEnvelope(res);
    }

    @Test
    void missingRequiredRequestParameterReturns400() throws Exception {
        String token = login();

        MvcResult res = mockMvc.perform(post("/api/reservations")
                        // expiryDate is required and deliberately omitted
                        .param("personId", "1")
                        .param("bookId", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode body = assertEnvelope(res);
        assertThat(body.get("message").asText()).contains("expiryDate");
    }

    @Test
    void wrongTypedPathVariableReturns400() throws Exception {
        String token = login();

        MvcResult res = mockMvc.perform(get("/api/book/not-a-number")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode body = assertEnvelope(res);
        assertThat(body.get("message").asText()).contains("id");
    }

    @Test
    void unsupportedHttpMethodReturns405() throws Exception {
        String token = login();

        MvcResult res = mockMvc.perform(patch("/api/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andReturn();

        assertEnvelope(res);
    }

    @Test
    void duplicateUniqueFieldReturns409WithoutLeakingPersistenceDetail() throws Exception {
        String token = login();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "dupe-" + suffix + "@test.local";
        String body = "{\"firstName\":\"Dup\",\"lastName\":\"Licate\",\"email\":\"" + email + "\","
                + "\"nationalId\":\"dupe" + suffix + "\"}";

        mockMvc.perform(multipart("/api/person")
                        .file(jsonPart("person", body))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // Same email again: the DB unique constraint must surface as 409, not 500.
        MvcResult res = mockMvc.perform(multipart("/api/person")
                        .file(jsonPart("person", body))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode envelope = assertEnvelope(res);
        String message = envelope.get("message").asText();
        // Never echo the raw persistence/JDBC text.
        assertThat(message)
                .doesNotContainIgnoringCase("insert")
                .doesNotContainIgnoringCase("constraint")
                .doesNotContainIgnoringCase("SQL")
                .doesNotContainIgnoringCase("Unique index");
    }

    private JsonNode assertEnvelope(MvcResult res) throws Exception {
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("message").asText()).isNotBlank();
        return body;
    }
}
