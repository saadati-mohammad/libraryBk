package ir.iau.library.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.iau.library.entity.InputDevice;
import ir.iau.library.entity.StaffAccount;
import ir.iau.library.entity.StaffRole;
import ir.iau.library.repository.InputDeviceRepository;
import ir.iau.library.repository.StaffAccountRepository;
import ir.iau.library.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InputFormPolicyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtService jwtService;
    @Autowired StaffAccountRepository staffAccounts;
    @Autowired InputDeviceRepository devices;

    @Test
    void effectivePolicyPrefersPersonalStationOverrideThenAdminDefault() throws Exception {
        String adminToken = jwtService.generateToken("admin", "ADMIN");
        String username = "policy-librarian-" + System.nanoTime();
        createLibrarian(username);
        String staffToken = jwtService.generateToken(username, "LIBRARIAN");

        mvc.perform(put("/api/admin/input-policies/book-registration")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("station", "main-desk",
                                "allowedSources", List.of("MANUAL", "KEYBOARD_WEDGE"),
                                "defaultSource", "KEYBOARD_WEDGE"))))
                .andExpect(status().isOk());

        MvcResult inherited = mvc.perform(get("/api/input-policies/book-registration")
                        .param("station", "main-desk").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(inherited.getResponse().getContentAsString()).path("scope").asText()).isEqualTo("global");
        assertThat(mapper.readTree(inherited.getResponse().getContentAsString()).path("defaultSource").asText())
                .isEqualTo("KEYBOARD_WEDGE");

        mvc.perform(put("/api/input-policies/book-registration")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("station", "main-desk",
                                "allowedSources", List.of("MANUAL", "PHONE_CAMERA"),
                                "defaultSource", "PHONE_CAMERA"))))
                .andExpect(status().isOk());

        MvcResult personal = mvc.perform(get("/api/input-policies/book-registration")
                        .param("station", "main-desk").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(personal.getResponse().getContentAsString()).path("scope").asText()).isEqualTo("user");
        assertThat(mapper.readTree(personal.getResponse().getContentAsString()).path("defaultSource").asText())
                .isEqualTo("PHONE_CAMERA");

        MvcResult otherStation = mvc.perform(get("/api/input-policies/book-registration")
                        .param("station", "other-desk").header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(otherStation.getResponse().getContentAsString()).path("defaultSource").asText())
                .isEqualTo("MANUAL");
    }

    @Test
    void librariansCannotChangeGlobalPoliciesAndCannotSelectAnotherUsersDevice() throws Exception {
        String owner = "policy-owner-" + System.nanoTime();
        String caller = "policy-caller-" + System.nanoTime();
        createLibrarian(owner);
        createLibrarian(caller);
        String callerToken = jwtService.generateToken(caller, "LIBRARIAN");

        mvc.perform(put("/api/admin/input-policies/book-registration")
                        .header("Authorization", "Bearer " + callerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allowedSources\":[\"MANUAL\"],\"defaultSource\":\"MANUAL\"}"))
                .andExpect(status().isForbidden());

        InputDevice device = new InputDevice();
        device.setName("Private scanner");
        device.setTypeKey("KEYBOARD_WEDGE");
        device.setOwnerUsername(owner);
        device.setConfigJson("{}");
        device.setCapabilitiesJson("[]");
        device.setEnabled(true);
        device = devices.saveAndFlush(device);
        String body = mapper.writeValueAsString(Map.of("allowedSources", List.of("DEVICE:" + device.getId()),
                "defaultSource", "DEVICE:" + device.getId()));

        mvc.perform(put("/api/input-policies/book-registration")
                        .header("Authorization", "Bearer " + callerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotCreatePolicyForUnknownStaffAndCanTargetExistingStaffCaseInsensitively() throws Exception {
        String adminToken = jwtService.generateToken("admin", "ADMIN");
        String target = "policy-target-" + System.nanoTime();
        createLibrarian(target);

        mvc.perform(put("/api/admin/input-policies/loan-checkout")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("ownerUsername", "missing-" + target,
                                "allowedSources", List.of("MANUAL"), "defaultSource", "MANUAL"))))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/admin/input-policies/loan-checkout")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("ownerUsername", target.toUpperCase(),
                                "allowedSources", List.of("MANUAL"), "defaultSource", "MANUAL"))))
                .andExpect(status().isOk());

        String targetToken = jwtService.generateToken(target, "LIBRARIAN");
        MvcResult effective = mvc.perform(get("/api/input-policies/loan-checkout")
                        .header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(effective.getResponse().getContentAsString()).path("scope").asText())
                .isEqualTo("user");
    }

    private void createLibrarian(String username) {
        StaffAccount account = new StaffAccount();
        account.setUsername(username);
        account.setDisplayName("Policy test librarian");
        account.setPasswordHash("unused-test-hash");
        account.setRole(StaffRole.LIBRARIAN);
        account.setActive(true);
        staffAccounts.save(account);
    }
}
