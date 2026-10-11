package ir.iau.library.controller;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pagination and sorting contract for the list endpoints.
 *
 * <p>An unbounded {@code size} let one request pull an entire table, and an unknown
 * {@code sort} property made Spring Data throw deep inside the framework (a 500). The size is
 * now clamped to 100 and the sort property is validated against the entity's own fields, so a
 * typo is a 400.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaginationAndSortValidationTest {

    private static final int MAX_PAGE_SIZE = 100;

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

    private JsonNode getJson(String url, String token, String... params) throws Exception {
        var request = get(url).header("Authorization", "Bearer " + token);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        MvcResult res = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    @Test
    void bookListClampsAnOversizedPageSize() throws Exception {
        String token = login();

        JsonNode page = getJson("/api/book", token, "size", "5000", "page", "0");

        assertThat(page.get("size").asInt()).isEqualTo(MAX_PAGE_SIZE);
    }

    @Test
    void personListClampsAnOversizedPageSize() throws Exception {
        String token = login();

        JsonNode page = getJson("/api/person", token, "size", "5000", "page", "0");

        assertThat(page.get("size").asInt()).isEqualTo(MAX_PAGE_SIZE);
    }

    @Test
    void loanListHonoursTheConfiguredMaxPageSize() throws Exception {
        String token = login();

        // This endpoint uses Spring's Pageable resolver, bound by
        // spring.data.web.pageable.max-page-size.
        JsonNode page = getJson("/api/loan", token, "size", "5000", "page", "0");

        assertThat(page.get("size").asInt()).isEqualTo(MAX_PAGE_SIZE);
    }

    @Test
    void unknownSortPropertyIsRejectedWith400() throws Exception {
        String token = login();

        MvcResult res = mockMvc.perform(get("/api/book")
                        .param("sort", "doesNotExist,desc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("message").asText()).isNotBlank();
    }

    @Test
    void associationSortPropertyIsRejectedWith400() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/person")
                        .param("sort", "loans")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankSortPropertyIsRejectedWith400() throws Exception {
        String token = login();

        // A blank *property* with an explicit direction: must not reach Spring Data.
        mockMvc.perform(get("/api/book")
                        .param("sort", ",desc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptySortParameterFallsBackToTheDefaultSort() throws Exception {
        String token = login();

        // Spring applies @RequestParam's defaultValue for an empty value, so this is a
        // valid (default) sort rather than an error.
        JsonNode page = getJson("/api/book", token, "sort", "");

        assertThat(page.get("sort").get("sorted").asBoolean()).isTrue();
    }

    @Test
    void unknownSortDirectionIsRejectedWith400() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/book")
                        .param("sort", "id,sideways")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aKnownSortPropertyIsAccepted() throws Exception {
        String token = login();

        JsonNode page = getJson("/api/book", token, "sort", "title,asc", "size", "5");

        assertThat(page.get("size").asInt()).isEqualTo(5);
        assertThat(page.get("content").isArray()).isTrue();
    }
}
