package ir.iau.library.controller;

import ir.iau.library.dto.BookMetadataDto;
import ir.iau.library.service.BookMetadataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Staff-only ISBN lookup for assisted book entry; it never persists a book automatically. */
@RestController
@RequestMapping("/api/book/metadata")
public class BookMetadataController {
    private final BookMetadataService service;

    public BookMetadataController(BookMetadataService service) { this.service = service; }

    @GetMapping("/isbn/{isbn}")
    public List<BookMetadataDto> byIsbn(@PathVariable String isbn) { return service.lookup(isbn); }
}
