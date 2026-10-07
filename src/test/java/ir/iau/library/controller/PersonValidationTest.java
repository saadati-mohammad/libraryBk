package ir.iau.library.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
 * Verifies that an invalid person payload is rejected with a 400 and field-level messages,
 * rather than surfacing the underlying DB NOT NULL constraint as an opaque 500.
 *
 * <p>Person is created through a multipart part ({@code person=@...json}), so the
 * {@code @Valid @RequestPart} binding path is what is exercised here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PersonValidationTest {

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

    private MockMultipartFile personPart(String json) {
        return new MockMultipartFile("person", "person.json", "application/json", json.getBytes());
    }

    @Test
    void missingRequiredFieldsReturn400WithFieldMessages() throws Exception {
        String token = login();
        // No email and no nationalId — both are NOT NULL / required.
        String json = "{\"firstName\":\"X\",\"lastName\":\"Y\"}";

        MvcResult res = mockMvc.perform(multipart("/api/person")
                        .file(personPart(json))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("message").asText()).contains("email").contains("nationalId");
    }

    @Test
    void invalidEmailFormatReturns400() throws Exception {
        String token = login();
        String json = "{\"firstName\":\"X\",\"lastName\":\"Y\",\"email\":\"not-an-email\",\"nationalId\":\"999000111\"}";

        mockMvc.perform(multipart("/api/person")
                        .file(personPart(json))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validPersonIsCreated() throws Exception {
        String token = login();
        String json = "{\"firstName\":\"Valid\",\"lastName\":\"User\","
                + "\"email\":\"valid-person@test.local\",\"nationalId\":\"888000222\"}";

        MvcResult res = mockMvc.perform(multipart("/api/person")
                        .file(personPart(json))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("id").asLong()).isPositive();
        assertThat(body.get("email").asText()).isEqualTo("valid-person@test.local");
    }
}
