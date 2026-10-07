package ir.iau.library.service;

import ir.iau.library.entity.Book;
import ir.iau.library.entity.Person;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards against mass assignment on the create endpoints: the request bodies are
 * bound directly to the JPA entities, so a client-supplied id must never be honoured
 * (a set id would turn save() into a merge that overwrites an existing row).
 */
@ExtendWith(MockitoExtension.class)
class CreateMassAssignmentTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private BookService bookService;

    @InjectMocks
    private PersonService personService;

    @Test
    void createBook_ignoresClientSuppliedId() throws Exception {
        Book incoming = Book.builder().id(999L).title("Injected").build();
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        bookService.createBook(incoming, null);

        ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
    }

    @Test
    void createPerson_ignoresClientSuppliedIdAndForcesActive() throws Exception {
        Person incoming = Person.builder()
                .id(999L)
                .email("x@example.com")
                .nationalId("1234567890")
                .active(false)
                .build();
        when(personRepository.save(any(Person.class))).thenAnswer(inv -> inv.getArgument(0));

        personService.createPerson(incoming, null);

        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        verify(personRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getActive()).isTrue();
    }
}
