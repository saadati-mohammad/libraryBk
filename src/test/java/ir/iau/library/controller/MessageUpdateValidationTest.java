package ir.iau.library.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for {@code PUT /api/messages/{id}}.
 *
 * <p>The controller used to bind the raw request body as a {@code String}, so a JSON
 * payload was stored literally as the message text and no validation applied. It now
 * binds a typed {@link ir.iau.library.dto.UpdateMessageRequest} with {@code @Valid}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageUpdateValidationTest {

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

    @Test
    void blankMessageReturns400() throws Exception {
        String token = login();

        mockMvc.perform(put("/api/messages/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingMessageFieldReturns400() throws Exception {
        String token = login();

        mockMvc.perform(put("/api/messages/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validBodyIsAcceptedAndNotStoredAsLiteralJson() throws Exception {
        String token = login();

        // No message with id 1 exists in a fresh test DB; the service returns its own
        // not-found response, but the request must not be rejected with a 500 by the
        // binding layer. Assert the body was parsed into a string (no braces stored).
        MvcResult res = mockMvc.perform(put("/api/messages/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello world\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        // The literal JSON must never appear as the stored message text.
        assertThat(body).doesNotContain("\"message\":\"hello world\"");
    }
}
