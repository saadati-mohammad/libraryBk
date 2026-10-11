package ir.iau.library.controller;

import ir.iau.library.dto.BookDto;
import ir.iau.library.dto.BookFilterDto;
import ir.iau.library.entity.Book;
import ir.iau.library.service.BookService;
import ir.iau.library.service.EntityMapper;
import ir.iau.library.service.ImageContentType;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@RestController
@RequestMapping("/api/book")
public class BookController {

    /**
     * Sortable properties: only the book's own persistent fields. Anything else (a blank
     * value, a typo, an association such as {@code loans}) is rejected with a 400 instead of
     * reaching Spring Data and failing with an opaque 500.
     */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of(
            "id", "title", "author", "translator", "publisher", "isbn10", "isbn13",
            "description", "deweyDecimal", "congressClassification", "subject", "summary",
            "publicationDate", "pageCount", "language", "edition", "copyCount",
            "librarySection", "shelfCode", "rowNumbers", "columnNumber", "positionNote",
            "active");

    @Autowired
    private BookService bookService;

    @Autowired
    private EntityMapper entityMapper;

    @GetMapping
    public Page<BookDto> listBooks(
            BookFilterDto filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {

        Pageable pageable = PageableFactory.of(page, size, sort, SORTABLE_PROPERTIES, Sort.Direction.ASC);
        // Map to DTOs so the LONGBLOB cover image is never serialized into list responses.
        return entityMapper.toBookDtoPage(bookService.findAllFiltered(filter, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookDto> getBook(@PathVariable Long id) {
        return bookService.getBookById(id)
                .map(entityMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Serves the stored cover image.
     *
     * <p>The read model intentionally omits the blob, so without this endpoint a client could
     * never display a cover that was actually uploaded: the bytes must be fetched separately.
     * The media type is sniffed from the payload rather than trusted from the upload header.
     *
     * @return 200 with the image bytes and the detected {@code Content-Type}, or 404 when the
     * book does not exist or has no cover
     */
    @GetMapping("/{id}/cover")
    public ResponseEntity<byte[]> getBookCover(@PathVariable Long id) {
        return bookService.getCoverImage(id)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(ImageContentType.detect(bytes))
                        .cacheControl(CacheControl.noCache())
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<BookDto> createBook(
            @Valid @RequestPart("book") Book book,
            @RequestPart(value = "bookCoverFile", required = false) MultipartFile bookCoverFile
    ) throws IOException {
        Book created = bookService.createBook(book, bookCoverFile);
        return ResponseEntity.status(HttpStatus.CREATED).body(entityMapper.toDto(created));
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<BookDto> updateBook(
            @PathVariable Long id,
            @Valid @RequestPart("book") Book book,
            @RequestPart(value = "bookCoverFile", required = false) MultipartFile bookCoverFile
    ) throws IOException {
        Book updated = bookService.updateBook(id, book, bookCoverFile);
        return ResponseEntity.ok(entityMapper.toDto(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        bookService.deleteBookById(id);
        return ResponseEntity.noContent().build();
    }
}
