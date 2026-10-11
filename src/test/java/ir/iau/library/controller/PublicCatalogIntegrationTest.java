package ir.iau.library.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.iau.library.entity.Book;
import ir.iau.library.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicCatalogIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired BookRepository books;

    @Test
    void publicSearchAndDetailsExposeOnlyActivePublicFieldsWithoutAuthentication() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        Book active = books.saveAndFlush(Book.builder()
                .title("Public Discovery " + suffix).author("A. Writer").isbn10("pub" + suffix)
                .subject("Public Category").active(true).copyCount(2)
                .shelfCode("STAFF-SHELF").positionNote("PRIVATE LOCATION").build());
        Book inactive = books.saveAndFlush(Book.builder()
                .title("Hidden Discovery " + suffix).author("A. Writer").isbn10("hid" + suffix)
                .subject("Hidden Category").active(false).copyCount(1).build());

        JsonNode result = mapper.readTree(mockMvc.perform(get("/api/public/books")
                        .param("q", "public discovery " + suffix)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.path("totalElements").asLong()).isEqualTo(1);
        JsonNode row = result.path("content").get(0);
        assertThat(row.path("id").asLong()).isEqualTo(active.getId());
        assertThat(row.path("shelfCode").isMissingNode()).isTrue();
        assertThat(row.path("positionNote").isMissingNode()).isTrue();

        mockMvc.perform(get("/api/public/books/{id}", inactive.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/books/{id}", active.getId()))
                .andExpect(status().isOk());
        JsonNode categories = mapper.readTree(mockMvc.perform(get("/api/public/categories"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(categories.toString()).contains("Public Category");
    }
}
