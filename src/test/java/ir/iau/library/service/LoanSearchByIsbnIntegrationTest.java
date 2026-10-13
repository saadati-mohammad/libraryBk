package ir.iau.library.service;

import ir.iau.library.dto.BookLoanFilterDto;
import ir.iau.library.dto.CreateLoanRequestDto;
import ir.iau.library.entity.Book;
import ir.iau.library.entity.Person;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LoanSearchByIsbnIntegrationTest {
    @Autowired private BookLoanService loanService;
    @Autowired private BookRepository books;
    @Autowired private PersonRepository people;

    @Test
    void findsLoansByEitherIsbnFormAndEquivalentChecksum() {
        String unique = UUID.randomUUID().toString().replaceAll("[^0-9]", "");
        String body = unique.substring(0, 9);
        int checksum = 0;
        for (int index = 0; index < body.length(); index++) checksum += (10 - index) * (body.charAt(index) - '0');
        int isbn10Check = (11 - checksum % 11) % 11;
        String isbn10 = body + (isbn10Check == 10 ? "X" : isbn10Check);
        String isbn13 = Isbn.toIsbn13(isbn10);
        Person person = people.saveAndFlush(Person.builder()
                .firstName("Test")
                .lastName("ISBN member")
                .email("isbn-" + unique + "@example.test")
                .nationalId(unique.substring(0, 10))
                .active(true)
                .build());
        Book book = books.saveAndFlush(Book.builder()
                .title("ISBN circulation test " + unique)
                .author("Test author")
                .subject("Test")
                .isbn10(isbn10)
                .active(true)
                .copyCount(1)
                .build());
        CreateLoanRequestDto request = new CreateLoanRequestDto();
        request.setPersonId(person.getId());
        request.setBookId(book.getId());
        Long loanId = loanService.createLoan(request).getId();

        BookLoanFilterDto isbn13Search = new BookLoanFilterDto();
        isbn13Search.setBookIsbn(isbn13.substring(0, 3) + "-" + isbn13.substring(3));
        BookLoanFilterDto isbn10Search = new BookLoanFilterDto();
        isbn10Search.setBookIsbn(isbn10.substring(0, 3) + "-" + isbn10.substring(3));

        assertThat(loanService.findAllFiltered(isbn13Search, PageRequest.of(0, 10)).getContent())
                .extracting("id").containsExactly(loanId);
        assertThat(loanService.findAllFiltered(isbn10Search, PageRequest.of(0, 10)).getContent())
                .extracting("id").containsExactly(loanId);
    }
}
