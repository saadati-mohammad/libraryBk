package ir.iau.library.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ir.iau.library.dto.BookFilterDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Audited
@Table(name="books")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Book {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The book form marks these required; mirroring the contract here means an invalid
    // payload fails fast with a 400 (via GlobalExceptionHandler) instead of being persisted
    // with a blank title/author. The entity is bound directly from the request, so the
    // constraints live where the binding happens.
    @NotBlank(message = "عنوان کتاب الزامی است")
    @Size(max = 255, message = "عنوان کتاب نمی‌تواند بیش از ۲۵۵ کاراکتر باشد")
    private String title;

    @NotBlank(message = "نام نویسنده الزامی است")
    @Size(max = 255, message = "نام نویسنده نمی‌تواند بیش از ۲۵۵ کاراکتر باشد")
    private String author;

    private String translator;
    private String publisher;

    @NotBlank(message = "شابک ۱۰ الزامی است")
    private String isbn10;

    private String isbn13;
    @Column(length = 2000)
    private String description;
    private String deweyDecimal;
    private String congressClassification;
    @NotBlank(message = "موضوع الزامی است")
    private String subject;

    @Column(length = 2000)
    private String summary;
    private LocalDate publicationDate;

    @Min(value = 1, message = "تعداد صفحات باید حداقل ۱ باشد")
    private Integer pageCount;

    private String language;

    // @Lob byte[] already maps to a large-object column. The previous
    // columnDefinition="LONGBLOB" conflicted with H2 (which reports the column as
    // BLOB) and broke schema validation once ddl-auto=validate was enabled.
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "book_cover_file")
    private byte[] bookCoverFile;

    private String edition;

    @Min(value = 0, message = "تعداد نسخه‌ها نمی‌تواند منفی باشد")
    private Integer copyCount;

    private String librarySection; // بخش کتابخانه (مثلاً: علوم انسانی، مهندسی)
    private String shelfCode;      // کد یا شماره قفسه (مثلاً: A3, B5)
    private String rowNumbers;      // ردیف (مثلاً: ردیف 2)
    private String columnNumber;   // ستون (مثلاً: ستون 4)
    @Column(length = 2000)
    private String positionNote;   // توضیح اضافی درباره مکان یا شرایط خاص نگهداری
    private Boolean active; // soft delete flag

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<BookLoan> loans = new ArrayList<>();

    public Book(BookFilterDto bookFilterDto) {
        this.isbn10 = bookFilterDto.getIsbn10();
        this.title = bookFilterDto.getTitle();
        this.author = bookFilterDto.getAuthor();
        this.translator = bookFilterDto.getTranslator();
        this.description = bookFilterDto.getDescription();
        this.publisher = bookFilterDto.getPublisher();
        this.isbn13 = bookFilterDto.getIsbn13();
        this.deweyDecimal = bookFilterDto.getDeweyDecimal();
        this.congressClassification = bookFilterDto.getCongressClassification();
        this.subject = bookFilterDto.getSubject();
        this.summary = bookFilterDto.getSummary();
        this.publicationDate = bookFilterDto.getPublicationDate(); // Date یا String، بستگی به نوع فیلد دارد
        this.pageCount = bookFilterDto.getPageCount();
        this.language = bookFilterDto.getLanguage();
        this.edition = bookFilterDto.getEdition();
        this.active = bookFilterDto.getActive(); // یا getActive() اگر Boolean باشد
        this.copyCount = bookFilterDto.getCopyCount();
        this.librarySection = bookFilterDto.getLibrarySection();
        this.shelfCode = bookFilterDto.getShelfCode();
        this.rowNumbers = bookFilterDto.getRowNumbers();
        this.columnNumber = bookFilterDto.getColumnNumber();
        this.positionNote = bookFilterDto.getPositionNote();
    }
}
