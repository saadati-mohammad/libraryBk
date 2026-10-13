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
    void publicSearchNormalizesArabicLettersZeroWidthCharactersAndPersianDigits() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        Book saved = books.saveAndFlush(Book.builder()
                .title("کتاب‌خانه‌ی كودک ۱۲۳ " + suffix)
                .author("نویسنده")
                .isbn10("0306406152")
                .subject("کتابخانه")
                .active(true)
                .copyCount(1)
                .build());

        JsonNode result = mapper.readTree(mockMvc.perform(get("/api/public/books")
                        .param("q", "\u06a9\u062a\u0627\u0628\u062e\u0627\u0646\u0647\u200c\u064a \u0643\u0648\u062f\u06a9 ١٢٣ " + suffix)
                        .param("subject", "کتابخانه"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(result.path("totalElements").asLong()).isEqualTo(1);
        assertThat(result.path("content").get(0).path("id").asLong()).isEqualTo(saved.getId());
    }

    @Test
    void publicSearchAndDetailsExposeOnlyActivePublicFieldsWithoutAuthentication() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        Book active = books.saveAndFlush(Book.builder()
                .title("Public Discovery " + suffix).author("A. Writer").isbn10("0306406152")
                .subject("Public Category").active(true).copyCount(2)
                .shelfCode("STAFF-SHELF").positionNote("PRIVATE LOCATION").build());
        Book inactive = books.saveAndFlush(Book.builder()
                .title("Hidden Discovery " + suffix).author("A. Writer").isbn10("0306406152")
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

    @Test
    void relevanceSortRanksExactTitlesBeforeAuthorMatches() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String term = "Relevance " + suffix;
        Book authorMatch = books.saveAndFlush(Book.builder().title("Other title " + suffix)
                .author(term).isbn13("9780306406157").subject("Relevance test")
                .active(true).copyCount(1).build());
        Book prefixMatch = books.saveAndFlush(Book.builder().title(term + " extended")
                .author("Writer").isbn13("9781861972712").subject("Relevance test")
                .active(true).copyCount(1).build());
        Book exactMatch = books.saveAndFlush(Book.builder().title(term)
                .author("Writer").isbn13("9780140328721").subject("Relevance test")
                .active(true).copyCount(1).build());

        JsonNode result = mapper.readTree(mockMvc.perform(get("/api/public/books")
                        .param("q", term).param("sort", "relevance"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(result.path("content").get(0).path("id").asLong()).isEqualTo(exactMatch.getId());
        assertThat(result.path("content").get(1).path("id").asLong()).isEqualTo(prefixMatch.getId());
        assertThat(result.path("content").get(2).path("id").asLong()).isEqualTo(authorMatch.getId());
    }
}
