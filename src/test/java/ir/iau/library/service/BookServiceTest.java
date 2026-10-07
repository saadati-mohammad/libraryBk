package ir.iau.library.service;

import ir.iau.library.entity.Book;
import ir.iau.library.repository.BookRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookService bookService;

    @Test
    void deleteBookById_softDeletesInsteadOfRemovingRow() {
        Book book = new Book();
        book.setId(1L);
        book.setTitle("کتاب آزمون");
        book.setActive(true);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        bookService.deleteBookById(1L);

        // The row must NOT be hard-deleted — that would cascade-delete loan history.
        verify(bookRepository, never()).deleteById(any());

        ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).save(captor.capture());
        assertThat(captor.getValue().getActive()).isFalse();
    }

    @Test
    void deleteBookById_throwsWhenBookMissing() {
        when(bookRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.deleteBookById(404L))
                .isInstanceOf(EntityNotFoundException.class);

        verify(bookRepository, never()).save(any());
        verify(bookRepository, never()).deleteById(any());
    }
}
