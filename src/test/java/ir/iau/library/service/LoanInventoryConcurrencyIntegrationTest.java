package ir.iau.library.service;

import ir.iau.library.dto.CreateLoanRequestDto;
import ir.iau.library.entity.Book;
import ir.iau.library.entity.Person;
import ir.iau.library.repository.BookLoanRepository;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises H2 row locking and copy capacity through independent service transactions. */
@SpringBootTest
@ActiveProfiles("test")
class LoanInventoryConcurrencyIntegrationTest {

    @Autowired private BookLoanService loanService;
    @Autowired private PersonRepository personRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private BookLoanRepository loanRepository;

    @Test
    void concurrentCheckoutsCannotExceedAggregateCopyCount() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        Person first = savePerson("first", unique);
        Person second = savePerson("second", unique);
        Book book = bookRepository.saveAndFlush(Book.builder()
                .title("Concurrency test " + unique)
                .author("Test author")
                .isbn10("0306406152")
                .subject("Test")
                .copyCount(1)
                .active(true)
                .build());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Boolean> firstCheckout = checkout(pool, ready, start, first, book);
            CompletableFuture<Boolean> secondCheckout = checkout(pool, ready, start, second, book);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            CompletableFuture.allOf(firstCheckout, secondCheckout).get(10, TimeUnit.SECONDS);

            long accepted = (firstCheckout.join() ? 1 : 0) + (secondCheckout.join() ? 1 : 0);
            assertThat(accepted).isEqualTo(1);
            assertThat(loanRepository.countActiveLoansByBook(book)).isEqualTo(1);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private Person savePerson(String suffix, String unique) {
        return personRepository.saveAndFlush(Person.builder()
                .firstName("Test")
                .lastName("Member " + suffix)
                .email(suffix + "-" + unique + "@example.test")
                .nationalId(unique.substring(0, 10) + suffix.substring(0, 1))
                .active(true)
                .build());
    }

    private CompletableFuture<Boolean> checkout(
            ExecutorService pool, CountDownLatch ready, CountDownLatch start, Person person, Book book) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Checkout barrier timed out");
                }
                CreateLoanRequestDto request = new CreateLoanRequestDto();
                request.setPersonId(person.getId());
                request.setBookId(book.getId());
                loanService.createLoan(request);
                return true;
            } catch (IllegalStateException capacityReached) {
                if (capacityReached.getMessage() != null
                        && capacityReached.getMessage().contains("No copies")) {
                    return false;
                }
                throw capacityReached;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }, pool);
    }
}
