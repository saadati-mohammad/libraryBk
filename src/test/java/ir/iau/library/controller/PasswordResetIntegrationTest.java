package ir.iau.library.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.iau.library.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.password-reset.enabled=true",
        "app.password-reset.public-url=http://localhost:4200",
        "spring.mail.from=no-reply@example.test"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordResetTokenRepository resetTokens;
    @MockitoBean JavaMailSender mailSender;

    @Test
    void memberCanResetWithOneTimeEmailTokenAndOnlyTokenHashIsPersisted() throws Exception {
        String email = "reset-" + System.nanoTime() + "@example.test";
        String nationalId = String.format("%010d", Math.abs(System.nanoTime() % 1_000_000_000L));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("firstName", "Reset", "lastName", "Test",
                                "email", email, "nationalId", nationalId, "password", "old-member-password-123"))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted());

        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String body = captor.getValue().getText();
        Matcher matcher = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)").matcher(body);
        assertThat(matcher.find()).isTrue();
        String rawToken = matcher.group(1);
        var persistedTokens = resetTokens.findAllByUsernameIgnoreCase(email);
        assertThat(persistedTokens).hasSize(1);
        assertThat(persistedTokens.getFirst().getTokenHash()).isNotEqualTo(rawToken);

        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", rawToken, "password", "new-member-password-456"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", email, "password", "old-member-password-123"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", email, "password", "new-member-password-456"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", rawToken, "password", "another-password-789"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownEmailGetsTheSameGenericResponseWithoutSendingMail() throws Exception {
        reset(mailSender);
        MvcResult result = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "unknown-" + System.nanoTime() + "@example.test"))))
                .andExpect(status().isAccepted()).andReturn();
        JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("message").asText()).contains("اگر حسابی");
        verifyNoInteractions(mailSender);
    }

    @Test
    void staffCanResetTheirPasswordThroughTheSameEmailFlow() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String username = "reset-staff-" + suffix;
        String email = username + "@example.test";
        String adminToken = login("admin", "admin");
        mvc.perform(post("/api/admin/staff-accounts").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "displayName", "Reset Staff",
                                "email", email, "password", "old-staff-password-123", "role", "LIBRARIAN"))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted());
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        Matcher matcher = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)")
                .matcher(captor.getValue().getText());
        assertThat(matcher.find()).isTrue();

        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", matcher.group(1), "password", "new-staff-password-456"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", "new-staff-password-456"))))
                .andExpect(status().isOk());
    }

    @Test
    void expiredResetLinkCannotChangeThePassword() throws Exception {
        String email = "expired-reset-" + System.nanoTime() + "@example.test";
        String nationalId = String.format("%010d", Math.abs(System.nanoTime() % 1_000_000_000L));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("firstName", "Expired", "lastName", "Test",
                                "email", email, "nationalId", nationalId, "password", "old-member-password-123"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isAccepted());
        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        Matcher matcher = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)")
                .matcher(captor.getValue().getText());
        assertThat(matcher.find()).isTrue();
        var token = resetTokens.findAllByUsernameIgnoreCase(email).getFirst();
        token.setExpiresAt(java.time.Instant.now().minusSeconds(1));
        resetTokens.save(token);

        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", matcher.group(1), "password", "new-member-password-456"))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", email, "password", "old-member-password-123"))))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }
}
