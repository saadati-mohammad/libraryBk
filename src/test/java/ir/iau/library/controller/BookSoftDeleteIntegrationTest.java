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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for defect 2/3: deleting a book must be a soft delete that keeps loan and
 * reservation history, hide the book from active listings, and stop new loans/reservations.
 *
 * <p>A hard delete returned 204 while wiping the book's loans (cascade + orphanRemoval) and
 * returned 500 when a reservation referenced the book (FK_RESERVATIONS_BOOK).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookSoftDeleteIntegrationTest {

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

    private MockMultipartFile jsonPart(String name, String json) {
        return new MockMultipartFile(name, name + ".json",
                MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private long createPerson(String token) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        MvcResult res = mockMvc.perform(multipart("/api/person")
                        .file(jsonPart("person", "{\"firstName\":\"Soft\",\"lastName\":\"Delete\","
                                + "\"email\":\"soft-" + suffix + "@test.local\","
                                + "\"nationalId\":\"sd" + suffix + "\",\"active\":true}"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createBook(String token, String title) throws Exception {
        String isbn = "sd-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult res = mockMvc.perform(multipart("/api/book")
                        .file(jsonPart("book", "{\"title\":\"" + title + "\",\"author\":\"Author\","
                                + "\"isbn10\":\"" + isbn + "\",\"subject\":\"Subject\","
                                + "\"copyCount\":1,\"active\":true}"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void deletingABookWithAnActiveLoanKeepsTheLoanHistoryAndHidesTheBook() throws Exception {
        String token = login();
        long personId = createPerson(token);
        long bookId = createBook(token, "Loaned Then Deleted");

        mockMvc.perform(post("/api/loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personId\":" + personId + ",\"bookId\":" + bookId + "}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/book/" + bookId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // The loan survived: history is the library's audit trail.
        MvcResult loans = mockMvc.perform(get("/api/loan").param("size", "100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(containsBookId(objectMapper.readTree(loans.getResponse().getContentAsString()).get("content"), bookId))
                .as("the loan for the soft-deleted book must still be listed")
                .isTrue();

        // The book row still exists, flagged inactive ...
        mockMvc.perform(get("/api/book/" + bookId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // ... and it is excluded from active listings.
        MvcResult active = mockMvc.perform(get("/api/book").param("active", "true").param("size", "100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(containsId(objectMapper.readTree(active.getResponse().getContentAsString()).get("content"), bookId))
                .as("a soft-deleted book must not appear in active listings")
                .isFalse();
    }

    @Test
    void deletingABookWithAReservationSucceedsAndKeepsTheReservation() throws Exception {
        String token = login();
        long personId = createPerson(token);
        long bookId = createBook(token, "Reserved Then Deleted");

        mockMvc.perform(post("/api/reservations")
                        .param("personId", String.valueOf(personId))
                        .param("bookId", String.valueOf(bookId))
                        .param("expiryDate", LocalDate.now().plusDays(7).toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // Used to fail with 500 (FK_RESERVATIONS_BOOK) because the row was hard-deleted.
        mockMvc.perform(delete("/api/book/" + bookId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        MvcResult active = mockMvc.perform(get("/api/reservations/active")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(containsBookIdInReservations(
                objectMapper.readTree(active.getResponse().getContentAsString()), bookId))
                .as("the reservation must survive the book's soft delete")
                .isTrue();
    }

    @Test
    void aSoftDeletedBookCannotBeLoanedOrReserved() throws Exception {
        String token = login();
        long personId = createPerson(token);
        long bookId = createBook(token, "Deleted Before Loan");

        mockMvc.perform(delete("/api/book/" + bookId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personId\":" + personId + ",\"bookId\":" + bookId + "}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/reservations")
                        .param("personId", String.valueOf(personId))
                        .param("bookId", String.valueOf(bookId))
                        .param("expiryDate", LocalDate.now().plusDays(7).toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    private static boolean containsBookId(JsonNode loans, long bookId) {
        for (JsonNode loan : loans) {
            if (loan.path("bookId").asLong() == bookId) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsId(JsonNode rows, long id) {
        for (JsonNode row : rows) {
            if (row.path("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsBookIdInReservations(JsonNode reservations, long bookId) {
        for (JsonNode reservation : reservations) {
            if (reservation.path("book").path("id").asLong() == bookId) {
                return true;
            }
        }
        return false;
    }
}
