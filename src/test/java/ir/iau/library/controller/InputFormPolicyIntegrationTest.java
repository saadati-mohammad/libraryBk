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
        var systemDefault = mapper.readTree(otherStation.getResponse().getContentAsString());
        assertThat(systemDefault.path("defaultSource").asText()).isEqualTo("MANUAL");
        assertThat(systemDefault.path("allowedSources").toString())
                .contains("\"MANUAL\"", "\"KEYBOARD_WEDGE\"", "\"PHONE_CAMERA\"");
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

        MvcResult adminRead = mvc.perform(get("/api/admin/input-policies/loan-checkout")
                        .param("ownerUsername", target.toUpperCase())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(adminRead.getResponse().getContentAsString()).path("scope").asText())
                .isEqualTo("user");

        String targetToken = jwtService.generateToken(target, "LIBRARIAN");
        MvcResult effective = mvc.perform(get("/api/input-policies/loan-checkout")
                        .header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk()).andReturn();
        assertThat(mapper.readTree(effective.getResponse().getContentAsString()).path("scope").asText())
                .isEqualTo("user");
    }

    @Test
    void effectivePolicyReturnsOnlySelectedEnabledDeviceProfilesToTheirOwner() throws Exception {
        String owner = "profile-owner-" + System.nanoTime();
        String other = "profile-other-" + System.nanoTime();
        createLibrarian(owner);
        createLibrarian(other);
        InputDevice device = new InputDevice();
        device.setName("Owned wedge scanner");
        device.setTypeKey("KEYBOARD_WEDGE");
        device.setOwnerUsername(owner);
        device.setStation("main-desk");
        device.setConfigJson("{\"prefix\":\"[\",\"suffix\":\"]\",\"terminator\":\"TAB\",\"minimumLength\":10}");
        device.setTokenHash("must-never-be-returned");
        device = devices.saveAndFlush(device);

        String ownerToken = jwtService.generateToken(owner, "LIBRARIAN");
        mvc.perform(put("/api/input-policies/book-registration")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("station", "main-desk",
                                "allowedSources", List.of("MANUAL", "DEVICE:" + device.getId()),
                                "defaultSource", "DEVICE:" + device.getId()))))
                .andExpect(status().isOk());

        var ownPolicy = mapper.readTree(mvc.perform(get("/api/input-policies/book-registration")
                        .param("station", "main-desk").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(ownPolicy.path("deviceProfiles")).hasSize(1);
        assertThat(ownPolicy.path("deviceProfiles").get(0).path("deviceId").asLong()).isEqualTo(device.getId());
        assertThat(ownPolicy.path("deviceProfiles").get(0).path("config").path("terminator").asText()).isEqualTo("TAB");
        assertThat(ownPolicy.toString()).doesNotContain("must-never-be-returned", "tokenHash");

        String otherToken = jwtService.generateToken(other, "LIBRARIAN");
        var otherPolicy = mapper.readTree(mvc.perform(get("/api/input-policies/book-registration")
                        .param("station", "main-desk").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(otherPolicy.path("deviceProfiles")).isEmpty();
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
