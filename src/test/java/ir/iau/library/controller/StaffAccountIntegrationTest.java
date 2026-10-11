package ir.iau.library.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffAccountIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void adminCreatesStaffAndRoleOrDeactivationChangesInvalidateExistingToken() throws Exception {
        String admin = login("admin", "admin");
        String suffix = Long.toString(System.nanoTime());
        String username = "librarian-" + suffix;
        String email = username + "@example.test";
        String body = mapper.writeValueAsString(java.util.Map.of(
                "username", username, "displayName", "Library Operator", "email", email,
                "password", "long-staff-password-123", "role", "LIBRARIAN"));

        JsonNode created = mapper.readTree(mvc.perform(post("/api/admin/staff-accounts")
                        .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(created.path("username").asText()).isEqualTo(username);
        assertThat(created.path("active").asBoolean()).isTrue();
        assertThat(created.has("passwordHash")).isFalse();
        long id = created.path("id").asLong();

        String librarian = login(username, "long-staff-password-123");
        mvc.perform(get("/api/book").header("Authorization", "Bearer " + librarian))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/staff-accounts").header("Authorization", "Bearer " + librarian))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/admin/staff-accounts/" + id).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Updated Operator\",\"email\":\"" + email + "\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/staff-accounts").header("Authorization", "Bearer " + librarian))
                .andExpect(status().isOk());

        String changedRoleToken = login(username, "long-staff-password-123");
        mvc.perform(delete("/api/admin/staff-accounts/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/book").header("Authorization", "Bearer " + changedRoleToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("username", username, "password", "long-staff-password-123"))))
                .andExpect(status().isUnauthorized());

        JsonNode inactive = mapper.readTree(mvc.perform(get("/api/admin/staff-accounts")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(inactive.toString()).contains("\"active\":false");
    }

    @Test
    void nonAdminCannotCreateStaffAndBootstrapUsernameIsReserved() throws Exception {
        String admin = login("admin", "admin");
        String suffix = Long.toString(System.nanoTime());
        mvc.perform(post("/api/admin/staff-accounts").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"collision-" + suffix + "\",\"displayName\":\"Operator\",\"email\":\"collision-" + suffix + "@example.test\",\"password\":\"long-staff-password-123\",\"role\":\"LIBRARIAN\"}"))
                .andExpect(status().isCreated());
        String username = "member-" + suffix + "@example.test";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Test\",\"lastName\":\"Member\",\"email\":\"" + username
                                + "\",\"nationalId\":\"" + String.format("%010d", Math.abs(System.nanoTime() % 1_000_000_000L))
                                + "\",\"password\":\"long-member-password-123\"}"))
                .andExpect(status().isCreated());
        String member = login(username, "long-member-password-123");
        mvc.perform(post("/api/admin/staff-accounts").header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"privileged-" + suffix + "\",\"displayName\":\"Member\",\"email\":\"privileged-" + suffix + "@example.test\",\"password\":\"long-staff-password-123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/staff-accounts").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"displayName\":\"Collision\",\"email\":\"admin-collision-" + suffix + "@example.test\",\"password\":\"long-staff-password-123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }
}
