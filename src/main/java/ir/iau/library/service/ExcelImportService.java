package ir.iau.library.service;

import ir.iau.library.dto.BookFilterDto;
import ir.iau.library.dto.BookImportResult;
import ir.iau.library.dto.BookImportRowIssue;
import ir.iau.library.dto.PersonFilterDto;
import ir.iau.library.dto.PersonImportResult;
import ir.iau.library.entity.Book;
import ir.iau.library.entity.Person;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ExcelImportService {
    private static final Validator FALLBACK_VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final BookRepository bookRepository;
    private final PersonRepository personRepository;
    private final long maxImportFileBytes;
    private final int maxImportRows;
    private final Validator validator;

    @Autowired
    public ExcelImportService(BookRepository bookRepository, PersonRepository personRepository,
                              Validator validator,
                              @Value("${app.upload.max-file-size:10485760}") long maxImportFileBytes,
                              @Value("${app.import.max-rows:5000}") int maxImportRows) {
        this.bookRepository = bookRepository;
        this.personRepository = personRepository;
        this.validator = validator;
        this.maxImportFileBytes = maxImportFileBytes;
        this.maxImportRows = maxImportRows;
    }

    /** Test and non-Spring construction keeps the documented safe defaults. */
    public ExcelImportService(BookRepository bookRepository, PersonRepository personRepository) {
        this(bookRepository, personRepository, FALLBACK_VALIDATOR, 10L * 1024 * 1024, 5000);
    }

    public ExcelImportService(BookRepository bookRepository, PersonRepository personRepository,
                              long maxImportFileBytes, int maxImportRows) {
        this(bookRepository, personRepository, FALLBACK_VALIDATOR, maxImportFileBytes, maxImportRows);
    }

    // متد اصلی برای خواندن فایل اکسل
    @Transactional // عملیات ذخیره را در یک تراکنش قرار می‌دهد
    public BookImportResult importBooksFromExcel(MultipartFile file) throws IOException {
        return processBookImport(file, true);
    }

    @Transactional
    public BookImportResult previewBooksFromExcel(MultipartFile file) throws IOException {
        return processBookImport(file, false);
    }

    private BookImportResult processBookImport(MultipartFile file, boolean persist) throws IOException {
        validateImportFile(file);
        List<ImportBookRow> importRows = new ArrayList<>();
        List<BookImportRowIssue> invalidRows = new ArrayList<>();
        int totalRows = 0;
        DataFormatter dataFormatter = new DataFormatter(); // برای تبدیل انواع داده به رشته

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("فایل اکسل هیچ برگی ندارد.");
            Sheet sheet = workbook.getSheetAt(0);
            validateRowCount(sheet);
            Iterator<Row> rowIterator = sheet.iterator();

            // از روی هدر رد شو
            if (rowIterator.hasNext()) {
                rowIterator.next();
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();

                // اگر ردیف خالی بود، از آن عبور کن
                if (isRowEmpty(row)) {
                    continue;
                }
                totalRows++;

                BookFilterDto bookDto = new BookFilterDto();
                List<String> rowErrors = new ArrayList<>();

                // خواندن داده‌ها با استفاده از متد کمکی امن
                bookDto.setIsbn10(getCellValueAsString(row.getCell(0), dataFormatter));
                bookDto.setTitle(getCellValueAsString(row.getCell(1), dataFormatter));
                bookDto.setAuthor(getCellValueAsString(row.getCell(2), dataFormatter));
                bookDto.setTranslator(getCellValueAsString(row.getCell(3), dataFormatter));
                bookDto.setDescription(getCellValueAsString(row.getCell(4), dataFormatter));
                bookDto.setPublisher(getCellValueAsString(row.getCell(5), dataFormatter));
                bookDto.setIsbn13(getCellValueAsString(row.getCell(6), dataFormatter));
                bookDto.setDeweyDecimal(getCellValueAsString(row.getCell(7), dataFormatter));
                bookDto.setCongressClassification(getCellValueAsString(row.getCell(8), dataFormatter));
                bookDto.setSubject(getCellValueAsString(row.getCell(9), dataFormatter));
                bookDto.setSummary(getCellValueAsString(row.getCell(10), dataFormatter));

                // مدیریت امن تاریخ، اعداد و مقادیر بولی
                String publicationDateStr = getCellValueAsString(row.getCell(11), dataFormatter);
                if (publicationDateStr != null && !publicationDateStr.isEmpty()) {
                    try {
                        // فرمت‌های مختلف تاریخ را امتحان کنید
                        bookDto.setPublicationDate(LocalDate.parse(publicationDateStr, DateTimeFormatter.ISO_LOCAL_DATE)); // YYYY-MM-DD
                    } catch (Exception e) {
                        rowErrors.add("تاریخ انتشار باید با قالب YYYY-MM-DD وارد شود");
                    }
                }

                String pageCountStr = getCellValueAsString(row.getCell(12), dataFormatter);
                try {
                    bookDto.setPageCount(parseIntegerCell(pageCountStr, "pageCount"));
                } catch (IllegalArgumentException exception) {
                    rowErrors.add(exception.getMessage());
                }

                bookDto.setLanguage(getCellValueAsString(row.getCell(13), dataFormatter));
                bookDto.setEdition(getCellValueAsString(row.getCell(14), dataFormatter));

                String activeStr = getCellValueAsString(row.getCell(15), dataFormatter);
                // A blank/absent cell means ACTIVE. Leaving the flag null produced a book that
                // is invisible to ?active=true listings, uncounted on the dashboard and refused
                // by the loan/reservation guards — i.e. an imported but unusable record.
                bookDto.setActive(isActiveCell(activeStr));
                if (activeStr != null && !activeStr.isBlank() && !isValidActiveCell(activeStr)) {
                    rowErrors.add("وضعیت باید TRUE/FALSE، فعال/غیرفعال یا ۱/۰ باشد");
                }

                String copyCountStr = getCellValueAsString(row.getCell(16), dataFormatter);
                try {
                    bookDto.setCopyCount(parseIntegerCell(copyCountStr, "copyCount"));
                } catch (IllegalArgumentException exception) {
                    rowErrors.add(exception.getMessage());
                }

                bookDto.setLibrarySection(getCellValueAsString(row.getCell(17), dataFormatter));
                bookDto.setShelfCode(getCellValueAsString(row.getCell(18), dataFormatter));
                bookDto.setRowNumbers(getCellValueAsString(row.getCell(19), dataFormatter));
                bookDto.setColumnNumber(getCellValueAsString(row.getCell(20), dataFormatter));
                bookDto.setPositionNote(getCellValueAsString(row.getCell(21), dataFormatter));

                if (!rowErrors.isEmpty()) {
                    invalidRows.add(new BookImportRowIssue(row.getRowNum() + 1,
                            rowErrors.stream().distinct().collect(java.util.stream.Collectors.joining("؛ "))));
                    continue;
                }

                Book book = new Book(bookDto);
                try {
                    BookService.normalizeBookIsbns(book);
                } catch (IllegalArgumentException exception) {
                    invalidRows.add(new BookImportRowIssue(row.getRowNum() + 1, exception.getMessage()));
                    continue;
                }
                Set<ConstraintViolation<Book>> violations = validator.validate(book);
                if (!violations.isEmpty()) {
                    String reason = violations.stream().map(ConstraintViolation::getMessage).distinct()
                            .sorted().collect(java.util.stream.Collectors.joining("؛ "));
                    invalidRows.add(new BookImportRowIssue(row.getRowNum() + 1, reason));
                    continue;
                }
                importRows.add(new ImportBookRow(row.getRowNum() + 1, book));
            }
        }

        // Fetch existing ISBN matches in one batch, then detect duplicates inside the file
        // using the same canonical ISBN-10/ISBN-13 identity (where conversion is possible).
        Set<String> isbn10Values = new LinkedHashSet<>();
        Set<String> isbn13Values = new LinkedHashSet<>();
        for (ImportBookRow row : importRows) {
            isbn10Values.addAll(isbnKeys(row.book()).stream().filter(key -> key.startsWith("I10:"))
                    .map(key -> key.substring(4)).toList());
            isbn13Values.addAll(isbnKeys(row.book()).stream().filter(key -> key.startsWith("I13:"))
                    .map(key -> key.substring(4)).toList());
        }
        Set<String> seenKeys = new HashSet<>();
        for (Set<String> chunk : chunks(isbn10Values, 500)) {
            bookRepository.findByIsbn10In(chunk).forEach(book -> seenKeys.addAll(isbnKeys(book)));
        }
        for (Set<String> chunk : chunks(isbn13Values, 500)) {
            bookRepository.findByIsbn13In(chunk).forEach(book -> seenKeys.addAll(isbnKeys(book)));
        }

        List<Book> booksToSave = new ArrayList<>();
        List<Integer> duplicateRows = new ArrayList<>();
        for (ImportBookRow row : importRows) {
            Set<String> keys = isbnKeys(row.book());
            if (!keys.isEmpty() && keys.stream().anyMatch(seenKeys::contains)) {
                duplicateRows.add(row.spreadsheetRow());
                continue;
            }
            seenKeys.addAll(keys);
            booksToSave.add(row.book());
        }

        // Save non-duplicates in one batch. Existing records are never overwritten.
        if (persist && !booksToSave.isEmpty()) {
            bookRepository.saveAll(booksToSave);
        }

        return new BookImportResult(persist ? booksToSave.size() : 0, booksToSave.size(), totalRows,
                duplicateRows, invalidRows);
    }

    private record ImportBookRow(int spreadsheetRow, Book book) { }

    private List<Set<String>> chunks(Set<String> values, int size) {
        List<Set<String>> result = new ArrayList<>();
        List<String> current = new ArrayList<>(size);
        for (String value : values) {
            current.add(value);
            if (current.size() == size) {
                result.add(Set.copyOf(current));
                current.clear();
            }
        }
        if (!current.isEmpty()) result.add(Set.copyOf(current));
        return result;
    }

    private Set<String> isbnKeys(Book book) {
        Set<String> keys = new LinkedHashSet<>();
        String isbn10 = ir.iau.library.service.Isbn.normalize(book.getIsbn10());
        String isbn13 = ir.iau.library.service.Isbn.normalize(book.getIsbn13());
        if (ir.iau.library.service.Isbn.isValid(isbn10)) {
            keys.add("I10:" + isbn10);
            String equivalent = ir.iau.library.service.Isbn.toIsbn13(isbn10);
            if (!equivalent.isEmpty()) keys.add("I13:" + equivalent);
        }
        if (ir.iau.library.service.Isbn.isValid(isbn13)) {
            keys.add("I13:" + isbn13);
            String equivalent = ir.iau.library.service.Isbn.toIsbn10(isbn13);
            if (!equivalent.isEmpty()) keys.add("I10:" + equivalent);
        }
        return keys;
    }

    /**
     * متد کمکی برای خواندن امن مقدار یک سلول به صورت رشته.
     * این متد از NullPointerException جلوگیری کرده و انواع داده مختلف را مدیریت می‌کند.
     */
    private String getCellValueAsString(Cell cell, DataFormatter dataFormatter) {
        if (cell == null) {
            return null; // یا "" اگر می‌خواهید رشته خالی برگردد
        }
        // از DataFormatter برای تبدیل امن هر نوع داده به رشته استفاده می‌کنیم
        return dataFormatter.formatCellValue(cell).trim();
    }

    /** Empty optional integer cells become null; malformed values become row-level validation errors. */
    private Integer parseIntegerCell(String value, String fieldName) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE
                    || parsed % 1 != 0) {
                throw new NumberFormatException("Value is not a finite in-range integer");
            }
            return (int) parsed;
        } catch (NumberFormatException e) {
            String fieldNameFa = "pageCount".equals(fieldName) ? "تعداد صفحات" : "تعداد نسخه‌ها";
            throw new IllegalArgumentException(fieldNameFa + " باید عدد صحیح باشد");
        }
    }

    /**
     * بررسی می‌کند که آیا یک ردیف کاملاً خالی است یا خیر.
     */
    private boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        if (row.getLastCellNum() <= 0) {
            return true;
        }
        for (int cellNum = row.getFirstCellNum(); cellNum < row.getLastCellNum(); cellNum++) {
            Cell cell = row.getCell(cellNum);
            if (cell != null && cell.getCellType() != CellType.BLANK && !getCellValueAsString(cell, new DataFormatter()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("فایل اکسل خالی است.");
        if (file.getSize() > maxImportFileBytes) {
            throw new IllegalArgumentException("حجم فایل از سقف مجاز واردسازی بیشتر است.");
        }
    }

    private void validateRowCount(Sheet sheet) {
        // Row zero is the header; lastRowNum is already the number of possible data rows.
        if (sheet.getLastRowNum() > maxImportRows) {
            throw new IllegalArgumentException("تعداد ردیف‌های فایل از سقف مجاز واردسازی (" + maxImportRows + ") بیشتر است.");
        }
    }



    @Transactional
    public PersonImportResult importPersonsFromExcel(MultipartFile file) throws IOException {
        return processPersonImport(file, true);
    }

    @Transactional
    public PersonImportResult previewPersonsFromExcel(MultipartFile file) throws IOException {
        return processPersonImport(file, false);
    }

    private PersonImportResult processPersonImport(MultipartFile file, boolean persist) throws IOException {
        validateImportFile(file);
        List<PersonImportRow> rows = new ArrayList<>();
        List<BookImportRowIssue> invalid = new ArrayList<>();
        int totalRows = 0;
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("فایل اکسل هیچ برگی ندارد.");
            Sheet sheet = workbook.getSheetAt(0);
            validateRowCount(sheet);
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || isRowEmpty(row)) continue;
                totalRows++;
                int displayRow = index + 1;
                PersonFilterDto dto = new PersonFilterDto();
                dto.setFirstName(getCellValueAsString(row.getCell(0), formatter));
                dto.setLastName(getCellValueAsString(row.getCell(1), formatter));
                dto.setEmail(getCellValueAsString(row.getCell(2), formatter));
                dto.setPhone(getCellValueAsString(row.getCell(3), formatter));
                dto.setNationalId(getCellValueAsString(row.getCell(4), formatter));
                dto.setMembershipType(getCellValueAsString(row.getCell(5), formatter));
                dto.setAddress(getCellValueAsString(row.getCell(6), formatter));
                String active = getCellValueAsString(row.getCell(7), formatter);
                if (active != null && !active.isBlank() && !isValidActiveCell(active)) {
                    invalid.add(new BookImportRowIssue(displayRow, "وضعیت فعال باید TRUE/FALSE، فعال/غیرفعال یا ۱/۰ باشد."));
                    continue;
                }
                dto.setActive(isActiveCell(active));
                try {
                    dto.setBirthDate(parseOptionalImportDate(getCellValueAsString(row.getCell(8), formatter)));
                    dto.setMembershipDateFrom(parseOptionalImportDate(getCellValueAsString(row.getCell(9), formatter)));
                    dto.setMembershipDateTo(parseOptionalImportDate(getCellValueAsString(row.getCell(10), formatter)));
                } catch (RuntimeException ex) {
                    invalid.add(new BookImportRowIssue(displayRow, "تاریخ باید معتبر و با قالب YYYY-MM-DD باشد."));
                    continue;
                }
                Person entity = new Person(dto);
                Set<String> violations = validator.validate(entity).stream()
                        .map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                if (!violations.isEmpty()) {
                    invalid.add(new BookImportRowIssue(displayRow, String.join("؛ ", violations)));
                    continue;
                }
                rows.add(new PersonImportRow(displayRow, dto, entity));
            }
        }

        Set<String> emails = rows.stream().map(r -> r.dto().getEmail().toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        Set<String> nationalIds = rows.stream().map(r -> r.dto().getNationalId()).collect(java.util.stream.Collectors.toSet());
        Set<String> knownEmails = personRepository.findByEmailInIgnoreCase(emails).stream()
                .map(p -> p.getEmail().toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        Set<String> knownNationalIds = personRepository.findByNationalIdIn(nationalIds).stream()
                .map(Person::getNationalId).collect(java.util.stream.Collectors.toSet());
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenNationalIds = new HashSet<>();
        List<Integer> duplicates = new ArrayList<>();
        List<Person> eligible = new ArrayList<>();
        for (PersonImportRow row : rows) {
            String email = row.dto().getEmail().toLowerCase(java.util.Locale.ROOT);
            String nationalId = row.dto().getNationalId();
            if (knownEmails.contains(email) || knownNationalIds.contains(nationalId)
                    || !seenEmails.add(email) || !seenNationalIds.add(nationalId)) {
                duplicates.add(row.row());
            } else {
                eligible.add(row.person());
            }
        }
        if (persist && !eligible.isEmpty()) personRepository.saveAll(eligible);
        return new PersonImportResult(persist ? eligible.size() : 0, eligible.size(), totalRows, duplicates, invalid);
    }

    private static LocalDate parseOptionalImportDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private record PersonImportRow(int row, PersonFilterDto dto, Person person) { }

    /**
     * Interprets the spreadsheet's "active" cell.
     *
     * <p>A blank or absent cell means <strong>active</strong>: an imported row must never end up
     * as a silently unusable record (invisible in active listings, uncounted on the dashboard,
     * refused by the loan/reservation guards). The explicit values that mean "inactive" are
     * anything other than {@code TRUE}/{@code فعال}/{@code 1}.
     */
    private static boolean isActiveCell(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return true;
        }
        return rawValue.equalsIgnoreCase("TRUE") || rawValue.equals("فعال") || rawValue.equals("1");
    }

    private static boolean isValidActiveCell(String rawValue) {
        return rawValue.equalsIgnoreCase("TRUE") || rawValue.equalsIgnoreCase("FALSE")
                || rawValue.equals("فعال") || rawValue.equals("غیرفعال") || rawValue.equals("1") || rawValue.equals("0");
    }

}
