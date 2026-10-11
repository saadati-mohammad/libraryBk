package ir.iau.library.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.iau.library.entity.Message;
import ir.iau.library.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

    @Autowired
    private MessageRepository messageRepository;

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

        // Message writes are now scoped to the authenticated principal, so the message
        // being updated is seeded for this caller instead of relying on an empty database.
        Message seeded = messageRepository.save(Message.builder()
                .sender("admin")
                .senderFarsiTitle("Admin")
                .recipient("binding-target")
                .recipientFarsiTitle("Target")
                .subject("binding")
                .message("original text")
                .messageStatus("SENT")
                .isActive(true)
                .dataState(1)
                .build());

        MvcResult res = mockMvc.perform(put("/api/messages/" + seeded.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello world\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("success").asBoolean()).isTrue();
        // The stored text is the plain message, never the literal JSON body.
        assertThat(body.get("data").get("message").asText()).isEqualTo("hello world");
    }
}
