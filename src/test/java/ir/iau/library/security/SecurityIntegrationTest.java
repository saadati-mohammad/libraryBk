package ir.iau.library.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.method.HandlerMethod;
import java.util.Map;
import java.util.Set;
import ir.iau.library.security.JwtService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the default-deny JWT security contract end to end through the real filter chain:
 * unauthenticated API access is rejected, valid credentials yield a working bearer token,
 * and tampered tokens are rejected.
 *
 * <p>Credentials come from {@code application-test.properties}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void unauthenticatedApiRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/book").param("page", "0").param("size", "1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/book/metadata/isbn/9780306406157"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyNonPublicRestHandlerRejectsAnonymousRequests() throws Exception {
        Set<String> tested = new java.util.LinkedHashSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo mapping = entry.getKey();
            Set<String> paths = mapping.getPatternValues();
            if (paths.isEmpty()) continue;
            Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
            Set<RequestMethod> candidates = methods.isEmpty() ? Set.of(RequestMethod.GET) : methods;
            for (String pattern : paths) {
                if (!pattern.startsWith("/api/") || isPublicOrCapabilityPath(pattern)) continue;
                String path = pattern.replaceAll("\\{[^}/]+}", "1");
                for (RequestMethod method : candidates) {
                    if (method == RequestMethod.OPTIONS) continue;
                    String key = method.name() + " " + path;
                    if (!tested.add(key)) continue;
                    var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .request(org.springframework.http.HttpMethod.valueOf(method.name()), path)
                            .contentType(MediaType.APPLICATION_JSON).content("{}");
                    int actualStatus = mockMvc.perform(request).andReturn().getResponse().getStatus();
                    assertThat(actualStatus).as("Anonymous request to %s", key).isEqualTo(401);
                }
            }
        }
        assertThat(tested).as("secure REST mappings exercised").hasSizeGreaterThan(20);
    }

    private static boolean isPublicOrCapabilityPath(String path) {
        return path.equals("/api/auth/login") || path.equals("/api/auth/register")
                || path.equals("/api/auth/forgot-password") || path.equals("/api/auth/reset-password")
                || path.startsWith("/api/public/") || path.startsWith("/api/phone-scanner/")
                // Device heartbeat is bearer-token public at the JWT layer; its device
                // credential is validated by InputDeviceController before it can mutate state.
                || path.startsWith("/api/device-inputs/") && path.endsWith("/heartbeat");
    }

    @Test
    void badCredentialsReturnUnauthorizedNotServerError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validCredentialsYieldTokenThatAuthorizesApiAccess() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        String token = body.get("token").asText();
        assertThat(token).isNotBlank();
        assertThat(body.get("role").asText()).isEqualTo("ADMIN");

        mockMvc.perform(get("/api/book")
                        .param("page", "0").param("size", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("token").asText();

        mockMvc.perform(get("/api/book")
                        .param("page", "0").param("size", "1")
                        .header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedLoginBodyIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberCannotReadGlobalFileStorageStatistics() throws Exception {
        String email = "security-member-" + Math.abs(System.nanoTime()) + "@example.test";
        String nationalId = String.format("%010d", Math.abs(System.nanoTime() % 1_000_000_000L));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Security\",\"lastName\":\"Member\",\"email\":\""
                                + email + "\",\"nationalId\":\"" + nationalId
                                + "\",\"password\":\"long-test-password-123\"}"))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"long-test-password-123\"}"))
                .andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).path("token").asText();
        mockMvc.perform(get("/api/v1/files/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
