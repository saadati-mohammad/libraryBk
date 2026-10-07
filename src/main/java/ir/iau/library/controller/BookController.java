package ir.iau.library.controller;

import ir.iau.library.dto.BookDto;
import ir.iau.library.dto.BookFilterDto;
import ir.iau.library.entity.Book;
import ir.iau.library.service.BookService;
import ir.iau.library.service.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/book")
public class BookController {

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

        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 ? Sort.Direction.fromString(sortParts[1]) : Sort.Direction.ASC;
        Sort sortOrder = Sort.by(direction, sortParts[0]);
        Pageable pageable = PageRequest.of(page, size, sortOrder);
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

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<BookDto> createBook(
            @RequestPart("book") Book book,
            @RequestPart(value = "bookCoverFile", required = false) MultipartFile bookCoverFile
    ) throws IOException {
        Book created = bookService.createBook(book, bookCoverFile);
        return ResponseEntity.status(HttpStatus.CREATED).body(entityMapper.toDto(created));
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<BookDto> updateBook(
            @PathVariable Long id,
            @RequestPart("book") Book book,
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
