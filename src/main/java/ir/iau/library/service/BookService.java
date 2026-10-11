package ir.iau.library.service;

import ir.iau.library.dto.BookFilterDto;
import ir.iau.library.dto.PublicCategoryDto;
import ir.iau.library.entity.Book;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.specification.BookSpecification;
import ir.iau.library.util.PersianSearchNormalizer;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.List;

@Service
@Transactional
public class BookService {

    @Autowired
    private BookRepository bookRepository;

    public Page<Book> findAllFiltered(BookFilterDto filter, Pageable pageable) { // تغییر نوع فیلتر
        return bookRepository.findAll(BookSpecification.filter(filter), pageable);
    }

    /** Public search over safe discovery fields. Internal shelf/location fields are never filters here. */
    public Page<Book> findPublic(String query, String subject, Pageable pageable) {
        String term = PersianSearchNormalizer.normalize(query);
        String category = PersianSearchNormalizer.normalize(subject);
        Specification<Book> spec = (root, criteria, cb) -> {
            Predicate active = cb.isTrue(root.get("active"));
            Predicate search = cb.conjunction();
            if (!term.isBlank()) {
                String pattern = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                search = cb.or(
                        cb.like(PersianSearchNormalizer.normalize(cb, root.get("title")), pattern, '\\'),
                        cb.like(PersianSearchNormalizer.normalize(cb, root.get("author")), pattern, '\\'),
                        cb.like(PersianSearchNormalizer.normalize(cb, root.get("publisher")), pattern, '\\'),
                        cb.like(PersianSearchNormalizer.normalize(cb, root.get("subject")), pattern, '\\'),
                        cb.like(PersianSearchNormalizer.normalize(cb, root.get("isbn10")), pattern, '\\'),
                        cb.like(PersianSearchNormalizer.normalize(cb, root.get("isbn13")), pattern, '\\'));
            }
            Predicate categoryFilter = category.isBlank() ? cb.conjunction()
                    : cb.equal(PersianSearchNormalizer.normalize(cb, root.get("subject")), category);
            return cb.and(active, search, categoryFilter);
        };
        return bookRepository.findAll(spec, pageable);
    }

    public List<PublicCategoryDto> publicCategories() {
        return bookRepository.countActiveSubjects().stream()
                .map(row -> new PublicCategoryDto(row.getSubject(), row.getTitleCount()))
                .toList();
    }

    public Book createBook(Book book, MultipartFile bookCoverFile) throws IOException {
        // Never trust a client-supplied identity on create: a set id turns save() into
        // a merge that can overwrite an existing row (mass assignment). The id and the
        // back-reference collection are always server-controlled.
        book.setId(null);
        book.setLoans(null);
        // A book created without an explicit flag is an ACTIVE book. A null flag used to be
        // persisted verbatim, which made the record invisible to ?active=true listings,
        // uncounted on the dashboard and impossible to lend or reserve (the business guards
        // treat any non-true flag as inactive). PersonService already forces the same default
        // for members, so this restores symmetry between the two create paths.
        if (book.getActive() == null) {
            book.setActive(true);
        }
        if (bookCoverFile != null && !bookCoverFile.isEmpty()) {
            book.setBookCoverFile(bookCoverFile.getBytes());
        }
        return bookRepository.save(book);
    }

    public Book updateBook(Long id, Book bookDetails, MultipartFile bookCoverFile) throws IOException {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id " + id));

        book.setTitle(bookDetails.getTitle());
        book.setAuthor(bookDetails.getAuthor());
        book.setTranslator(bookDetails.getTranslator());
        book.setPublisher(bookDetails.getPublisher());
        book.setIsbn10(bookDetails.getIsbn10());
        book.setIsbn13(bookDetails.getIsbn13());
        book.setDescription(bookDetails.getDescription()); // اضافه شده
        book.setDeweyDecimal(bookDetails.getDeweyDecimal());
        book.setCongressClassification(bookDetails.getCongressClassification());
        book.setSubject(bookDetails.getSubject());
        book.setSummary(bookDetails.getSummary());
        book.setPublicationDate(bookDetails.getPublicationDate());
        book.setPageCount(bookDetails.getPageCount());
        book.setLanguage(bookDetails.getLanguage());
        book.setEdition(bookDetails.getEdition());
        book.setCopyCount(bookDetails.getCopyCount()); // اضافه شده
        book.setLibrarySection(bookDetails.getLibrarySection()); // اضافه شده
        book.setShelfCode(bookDetails.getShelfCode()); // اضافه شده
        book.setRowNumbers(bookDetails.getRowNumbers()); // اضافه شده
        book.setColumnNumber(bookDetails.getColumnNumber()); // اضافه شده
        book.setPositionNote(bookDetails.getPositionNote()); // اضافه شده
        // Only an explicit flag changes the state: a null means "the client did not send it",
        // and overwriting the row with null would silently make the book unloanable and
        // invisible. `false` still deactivates explicitly.
        if (bookDetails.getActive() != null) {
            book.setActive(bookDetails.getActive());
        }

        if (bookCoverFile != null && !bookCoverFile.isEmpty()) {
            book.setBookCoverFile(bookCoverFile.getBytes());
        } else if (bookDetails.getBookCoverFile() == null) { // اگر bookCoverFile null فرستاده شده بود و فایل جدیدی هم نبود، یعنی حذف جلد
            book.setBookCoverFile(null);
        }
        // اگر bookDetails.getBookCoverFile() مقداری داشت و فایل جدیدی هم آپلود نشده بود، جلد قبلی باقی می‌ماند

        return bookRepository.save(book);
    }

    /**
     * Soft-deletes a book by flipping its {@code active} flag.
     *
     * <p>A hard delete was destructive here: {@link Book#getLoans()} is mapped with
     * {@code cascade = ALL, orphanRemoval = true}, so removing the book also removed its
     * entire loan history (the library audit trail) and could fail with an FK violation
     * when loans referenced it. The {@code active} flag already exists and is honoured by
     * {@link ir.iau.library.specification.BookSpecification}, so deactivation preserves
     * history while hiding the book from active listings.
     */
    public void deleteBookById(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id " + id));
        // Soft delete: keep the row (and therefore the loan/reservation history) and only
        // hide the book from active listings. Deleting the row cascaded into its loans via
        // orphanRemoval and failed outright when a reservation referenced it.
        book.setActive(false);
        bookRepository.save(book);
    }

    public Optional<Book> getBookById(Long id) {
        // Include inactive books so operators can still inspect and edit deactivated records.
        return bookRepository.findById(id);
    }

    /**
     * Reads the stored cover image inside the service transaction, so the blob is initialised
     * while the persistence context is still open (a detached entity must never be asked for
     * its lazy blob from the controller).
     *
     * @return the image bytes, or empty when the book has no cover
     */
    public Optional<byte[]> getCoverImage(Long id) {
        return bookRepository.findById(id)
                .map(Book::getBookCoverFile)
                .filter(bytes -> bytes != null && bytes.length > 0);
    }
}
