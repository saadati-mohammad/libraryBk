package ir.iau.library.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.iau.library.entity.Book;
import ir.iau.library.entity.BookLoan;
import ir.iau.library.entity.LoanStatus;
import ir.iau.library.entity.Person;
import ir.iau.library.entity.Reservation;
import ir.iau.library.entity.ReservationStatus;
import ir.iau.library.repository.BookLoanRepository;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import ir.iau.library.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for {@code GET /api/dashboard/summary}.
 *
 * <p>The database is shared by every integration test in this JVM, so the assertions are
 * written as deltas against a baseline read from the endpoint itself: that proves the
 * endpoint reflects newly persisted state (rather than returning a constant) while staying
 * independent of test ordering.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardSummaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private BookLoanRepository bookLoanRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private String login() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode summary(String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private static long monthCount(JsonNode summary, String month) {
        for (JsonNode entry : summary.get("monthlyLoans")) {
            if (month.equals(entry.get("month").asText())) {
                return entry.get("count").asLong();
            }
        }
        return 0L;
    }

    @Test
    void summaryReturnsTheExpectedCountsAndShapeForASeededDataset() throws Exception {
        String token = login();
        JsonNode before = summary(token);
        YearMonth currentMonth = YearMonth.now();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Person activePerson = personRepository.save(Person.builder()
                .firstName("Dash").lastName("Board")
                .email("dash-" + suffix + "@test.local")
                .nationalId("dash" + suffix)
                .membershipDate(LocalDate.now())
                .active(true)
                .build());
        // Deactivated member: must not be counted.
        personRepository.save(Person.builder()
                .firstName("Gone").lastName("Member")
                .email("gone-" + suffix + "@test.local")
                .nationalId("gone" + suffix)
                .active(false)
                .build());

        Book activeBook = bookRepository.save(Book.builder()
                .title("Dashboard Book A").author("Author").isbn10("dash-a-" + suffix)
                .subject("Subject").copyCount(1).active(true).build());
        Book secondBook = bookRepository.save(Book.builder()
                .title("Dashboard Book B").author("Author").isbn10("dash-b-" + suffix)
                .subject("Subject").copyCount(1).active(true).build());
        // Soft-deleted book: must not be counted.
        bookRepository.save(Book.builder()
                .title("Dashboard Book Deleted").author("Author").isbn10("dash-c-" + suffix)
                .subject("Subject").copyCount(1).active(false).build());

        BookLoan onLoan = bookLoanRepository.save(BookLoan.builder()
                .person(activePerson).book(activeBook)
                .loanDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(14))
                .status(LoanStatus.ON_LOAN)
                .build());
        bookLoanRepository.save(BookLoan.builder()
                .person(activePerson).book(secondBook)
                .loanDate(LocalDate.now())
                .dueDate(LocalDate.now().minusDays(3))
                .status(LoanStatus.OVERDUE)
                .build());
        reservationRepository.save(Reservation.builder()
                .person(activePerson).book(activeBook)
                .reservationDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(7))
                .status(ReservationStatus.ACTIVE)
                .build());

        JsonNode after = summary(token);

        // --- scalar counts (delta against the pre-seed baseline) ---
        assertThat(after.get("totalBooks").asLong())
                .isEqualTo(before.get("totalBooks").asLong() + 2);
        assertThat(after.get("totalPersons").asLong())
                .isEqualTo(before.get("totalPersons").asLong() + 1);
        assertThat(after.get("activeLoans").asLong())
                .isEqualTo(before.get("activeLoans").asLong() + 2);
        assertThat(after.get("overdueLoans").asLong())
                .isEqualTo(before.get("overdueLoans").asLong() + 1);
        assertThat(after.get("activeReservations").asLong())
                .isEqualTo(before.get("activeReservations").asLong() + 1);

        // --- shape ---
        for (String field : java.util.List.of("totalBooks", "totalPersons", "activeLoans",
                "overdueLoans", "activeReservations", "recentLoans", "monthlyLoans")) {
            assertThat(after.has(field)).as("summary must expose '%s'", field).isTrue();
        }

        JsonNode recent = after.get("recentLoans");
        assertThat(recent.isArray()).isTrue();
        assertThat(recent.size()).isBetween(1, 5);
        boolean newestPresent = false;
        for (JsonNode loan : recent) {
            if (loan.get("id").asLong() == onLoan.getId()) {
                newestPresent = true;
            }
        }
        assertThat(newestPresent).as("the newly created loan must be among the latest").isTrue();
        assertThat(recent.get(0).has("bookTitle")).isTrue();
        assertThat(recent.get(0).has("bookId")).isTrue();
        assertThat(recent.get(0).has("status")).isTrue();
        assertThat(recent.get(0).has("personId")).isTrue();

        JsonNode monthly = after.get("monthlyLoans");
        assertThat(monthly.size()).isEqualTo(6);
        assertThat(monthly.get(5).get("month").asText()).isEqualTo(currentMonth.toString());
        assertThat(monthly.get(0).get("month").asText())
                .isEqualTo(currentMonth.minusMonths(5).toString());
        assertThat(monthly.get(5).get("count").asLong())
                .isEqualTo(monthCount(before, currentMonth.toString()) + 2);
    }

    @Test
    void summaryIsRefusedWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }
}
