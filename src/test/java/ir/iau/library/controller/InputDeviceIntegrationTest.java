package ir.iau.library.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.iau.library.entity.InputDevice;
import ir.iau.library.entity.StaffAccount;
import ir.iau.library.entity.StaffRole;
import ir.iau.library.repository.InputDeviceRepository;
import ir.iau.library.repository.StaffAccountRepository;
import ir.iau.library.security.JwtService;
import ir.iau.library.service.PhoneScannerSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InputDeviceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired InputDeviceRepository devices;
    @Autowired StaffAccountRepository staffAccounts;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PhoneScannerSessionService scannerSessions;

    @Test
    void adminCanDiscoverSchemaProvisionTokenRotateDisableAndRevokeDevice() throws Exception {
        String admin = login("admin", "admin");
        JsonNode types = mapper.readTree(mvc.perform(get("/api/admin/input-devices/types")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(types.toString()).contains("KEYBOARD_WEDGE", "PHONE_CAMERA", "MANUAL", "terminator");

        String request = mapper.writeValueAsString(Map.of(
                "name", "Front desk scanner", "typeKey", "KEYBOARD_WEDGE", "ownerUsername", "admin",
                "station", "front-desk", "config", Map.of("prefix", "[", "suffix", "]", "terminator", "ENTER")));
        JsonNode created = mapper.readTree(mvc.perform(post("/api/admin/input-devices")
                        .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id = created.path("device").path("id").asLong();
        String originalToken = created.path("token").asText();
        assertThat(originalToken).isNotBlank();
        assertThat(created.path("device").has("tokenHash")).isFalse();

        InputDevice persisted = devices.findById(id).orElseThrow();
        assertThat(persisted.getTokenHash()).isEqualTo(sha256(originalToken));
        assertThat(persisted.getConfigJson()).contains("terminator");
        mvc.perform(post("/api/device-inputs/" + id + "/heartbeat").header("Authorization", "Bearer " + originalToken))
                .andExpect(status().isOk());

        JsonNode rotated = mapper.readTree(mvc.perform(post("/api/admin/input-devices/" + id + "/rotate-token")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String newToken = rotated.path("token").asText();
        assertThat(newToken).isNotEqualTo(originalToken);
        mvc.perform(post("/api/device-inputs/" + id + "/heartbeat").header("Authorization", "Bearer " + originalToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/input-devices/" + id + "/disable").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(post("/api/device-inputs/" + id + "/heartbeat").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/input-devices/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        JsonNode audit = mapper.readTree(mvc.perform(get("/api/admin/input-devices/" + id + "/audit")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(audit.toString()).contains("CREATED", "TOKEN_ROTATED", "DISABLED", "REVOKED");
        assertThat(audit.toString()).doesNotContain(originalToken, newToken);
        assertThat(mvc.perform(get("/api/admin/input-devices").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).contains("\"revoked\":true");
    }

    @Test
    void invalidTypesAndConfigurationsAreRejectedAndAdminRoutesAreProtected() throws Exception {
        String admin = login("admin", "admin");
        String badType = mapper.writeValueAsString(Map.of("name", "Unknown", "typeKey", "RFID_TODO", "config", Map.of()));
        mvc.perform(post("/api/admin/input-devices").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(badType))
                .andExpect(status().isBadRequest());
        String badConfig = mapper.writeValueAsString(Map.of("name", "Scanner", "typeKey", "KEYBOARD_WEDGE",
                "config", Map.of("terminator", "EXECUTE_CODE")));
        mvc.perform(post("/api/admin/input-devices").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(badConfig))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/input-devices")).andExpect(status().isUnauthorized());

        String username = "device-librarian-" + System.nanoTime();
        StaffAccount librarian = new StaffAccount();
        librarian.setUsername(username);
        librarian.setDisplayName("Input Device Test Librarian");
        librarian.setPasswordHash(passwordEncoder.encode("long-test-password-123"));
        librarian.setRole(StaffRole.LIBRARIAN);
        staffAccounts.save(librarian);
        String librarianToken = jwtService.generateToken(username, "LIBRARIAN");
        mvc.perform(get("/api/admin/input-devices").header("Authorization", "Bearer " + librarianToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void registeredPhoneCanScanAndRevocationImmediatelyBlocksItsCredential() throws Exception {
        String admin = login("admin", "admin");
        String request = mapper.writeValueAsString(Map.of("name", "Desk phone", "typeKey", "PHONE_CAMERA",
                "ownerUsername", "admin", "station", "desk", "config", Map.of("cameraFacing", "environment")));
        JsonNode provisioned = mapper.readTree(mvc.perform(post("/api/admin/input-devices")
                        .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long deviceId = provisioned.path("device").path("id").asLong();
        String deviceToken = provisioned.path("token").asText();
        var session = scannerSessions.create();
        String scan = mapper.writeValueAsString(Map.of("pairingToken", session.pairingToken(),
                "barcode", "9780306406157", "deviceId", deviceId, "deviceToken", deviceToken));

        mvc.perform(post("/api/phone-scanner/sessions/" + session.sessionId() + "/scans")
                        .contentType(MediaType.APPLICATION_JSON).content(scan))
                .andExpect(status().isOk());
        assertThat(devices.findById(deviceId).orElseThrow().getLastSeenAt()).isNotNull();

        mvc.perform(delete("/api/admin/input-devices/" + deviceId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(post("/api/phone-scanner/sessions/" + session.sessionId() + "/scans")
                        .contentType(MediaType.APPLICATION_JSON).content(scan))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private String sha256(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
