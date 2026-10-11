package ir.iau.library.dto;

import ir.iau.library.entity.Book;

import java.time.LocalDate;

/** Public catalog projection. Internal shelf locations and staff notes are excluded. */
public record PublicBookDto(
        Long id,
        String title,
        String author,
        String publisher,
        String isbn13,
        String summary,
        String description,
        String subject,
        LocalDate publicationDate,
        Integer pageCount,
        String language,
        String edition,
        Integer copyCount,
        boolean hasCover) {

    public static PublicBookDto from(Book book) {
        return new PublicBookDto(book.getId(), book.getTitle(), book.getAuthor(), book.getPublisher(),
                book.getIsbn13(), book.getSummary(), book.getDescription(), book.getSubject(),
                book.getPublicationDate(), book.getPageCount(), book.getLanguage(), book.getEdition(),
                book.getCopyCount(), book.getBookCoverFile() != null && book.getBookCoverFile().length > 0);
    }
}
