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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberAccountIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void registrationCreatesAnIsolatedMemberAccountAndRejectsStaffApis() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String email = "member-" + suffix + "@example.test";
        String nationalId = String.format("%010d", Math.abs(System.nanoTime() % 1_000_000_000L));
        String body = "{\"firstName\":\"Member\",\"lastName\":\"Tester\",\"email\":\"" + email
                + "\",\"nationalId\":\"" + nationalId + "\",\"password\":\"long-test-password-123\"}";

        JsonNode registered = mapper.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(registered.path("email").asText()).isEqualTo(email);
        assertThat(registered.has("nationalId")).isFalse();
        assertThat(registered.has("passwordHash")).isFalse();

        String memberToken = login(email, "long-test-password-123");
        JsonNode profile = mapper.readTree(mvc.perform(get("/api/account/me")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(profile.path("email").asText()).isEqualTo(email);
        assertThat(profile.has("nationalId")).isFalse();

        mvc.perform(get("/api/book").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/person").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/account/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("long-test-password-123", "short")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void membersCanOnlyCancelTheirOwnReservationAndSeeTheirOwnLoans() throws Exception {
        String staffToken = login("admin", "admin");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String memberOne = registerAndLogin("member-a-" + suffix + "@example.test",
                String.format("%010d", Math.abs(System.nanoTime() % 1_000_000_000L)));
        String memberTwo = registerAndLogin("member-b-" + suffix + "@example.test",
                String.format("%010d", Math.abs((System.nanoTime() + 9) % 1_000_000_000L)));
        String isbn = "ac-" + suffix;
        MockMultipartFile bookPart = new MockMultipartFile("book", "book.json", MediaType.APPLICATION_JSON_VALUE,
                ("{\"title\":\"Member Reservation " + suffix + "\",\"author\":\"Author\",\"isbn10\":\""
                        + isbn + "\",\"subject\":\"Member Test\",\"copyCount\":1}").getBytes(StandardCharsets.UTF_8));
        MvcResult createdBook = mvc.perform(multipart("/api/book").file(bookPart)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isCreated()).andReturn();
        long bookId = mapper.readTree(createdBook.getResponse().getContentAsString()).path("id").asLong();

        MvcResult createdReservation = mvc.perform(post("/api/account/reservations")
                        .header("Authorization", "Bearer " + memberOne).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + bookId + "}"))
                .andExpect(status().isCreated()).andReturn();
        long reservationId = mapper.readTree(createdReservation.getResponse().getContentAsString()).path("id").asLong();
        mvc.perform(get("/api/account/reservations").header("Authorization", "Bearer " + memberOne))
                .andExpect(status().isOk());
        mvc.perform(get("/api/account/loans").header("Authorization", "Bearer " + memberOne))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/account/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + memberTwo))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/account/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + memberOne))
                .andExpect(status().isNoContent());
    }

    private String registerAndLogin(String email, String nationalId) throws Exception {
        String body = "{\"firstName\":\"Member\",\"lastName\":\"Tester\",\"email\":\"" + email
                + "\",\"nationalId\":\"" + nationalId + "\",\"password\":\"long-test-password-123\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return login(email, "long-test-password-123");
    }

    private String login(String username, String password) throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("username", username, "password", password));
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        JsonNode response = mapper.readTree(result.getResponse().getContentAsString());
        if (username.startsWith("member-")) assertThat(response.path("role").asText()).isEqualTo("MEMBER");
        return response.path("token").asText();
    }
}
