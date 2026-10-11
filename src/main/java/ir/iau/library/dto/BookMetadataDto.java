package ir.iau.library.dto;

import java.util.List;

/** Optional catalog metadata returned for staff review before a book is saved. */
public record BookMetadataDto(
        String title,
        String author,
        String publisher,
        String isbn10,
        String isbn13,
        Integer publicationYear,
        Integer pageCount,
        String language,
        String subject,
        List<String> subjects) { }
