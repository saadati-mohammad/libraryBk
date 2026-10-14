package ir.iau.library.controller;

import ir.iau.library.dto.PublicBookDto;
import ir.iau.library.dto.PublicCategoryDto;
import ir.iau.library.service.BookService;
import ir.iau.library.service.ImageContentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** Read-only, deliberately minimal public projection of the active library catalog. */
@RestController
@RequestMapping("/api/public")
public class PublicCatalogController {
    private static final Set<String> SORTABLE = Set.of("id", "title", "author", "publicationDate");
    private final BookService bookService;

    public PublicCatalogController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/books")
    public Page<PublicBookDto> books(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String subject,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {
        Pageable pageable = "relevance".equalsIgnoreCase(sort)
                ? PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 24))
                : PageableFactory.of(page, Math.min(size, 24), sort, SORTABLE, Sort.Direction.DESC);
        return bookService.findPublic(q, subject, pageable).map(PublicBookDto::from);
    }

    @GetMapping("/books/{id}")
    public ResponseEntity<PublicBookDto> book(@PathVariable Long id) {
        return bookService.getBookById(id)
                .filter(book -> Boolean.TRUE.equals(book.getActive()))
                .map(PublicBookDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/books/{id}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable Long id) {
        return bookService.getBookById(id)
                .filter(book -> Boolean.TRUE.equals(book.getActive()))
                .flatMap(book -> bookService.getCoverImage(id))
                .map(bytes -> ResponseEntity.ok()
                        .contentType(ImageContentType.detect(bytes))
                        .cacheControl(CacheControl.noCache())
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/categories")
    public java.util.List<PublicCategoryDto> categories() {
        return bookService.publicCategories();
    }
}
