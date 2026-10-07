package ir.iau.library.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * Read-model for {@code Book}.
 *
 * <p>Mirrors every non-blob field of the entity so the existing JSON contract is
 * preserved field-for-field, but deliberately omits {@code bookCoverFile} (a
 * {@code LONGBLOB}). Previously the controller serialized the raw entity, so every
 * list/get response embedded the full cover image as base64.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookDto {
    private Long id;
    private String title;
    private String author;
    private String translator;
    private String publisher;
    private String isbn10;
    private String isbn13;
    private String description;
    private String deweyDecimal;
    private String congressClassification;
    private String subject;
    private String summary;
    private LocalDate publicationDate;
    private Integer pageCount;
    private String language;
    private String edition;
    private Integer copyCount;
    private String librarySection;
    private String shelfCode;
    private String rowNumbers;
    private String columnNumber;
    private String positionNote;
    private Boolean active;
}
