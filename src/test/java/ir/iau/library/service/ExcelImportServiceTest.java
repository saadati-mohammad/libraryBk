package ir.iau.library.service;

import ir.iau.library.entity.Book;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;

/**
 * Regression coverage for the Excel book import. The original code called
 * {@code Double.parseDouble} directly on cell values, so a single non-numeric cell
 * (e.g. "N/A") threw NumberFormatException and aborted the whole import. These tests
 * pin the safe-parsing behaviour.
 */
@ExtendWith(MockitoExtension.class)
class ExcelImportServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private PersonRepository personRepository;

    private ExcelImportService service;

    @BeforeEach
    void setUp() {
        service = new ExcelImportService(bookRepository, personRepository);
        lenient().when(bookRepository.findByIsbn10In(anyCollection())).thenReturn(List.of());
        lenient().when(bookRepository.findByIsbn13In(anyCollection())).thenReturn(List.of());
    }

    private MockMultipartFile xlsxWithRow(String pageCount, String copyCount) throws Exception {
        return xlsxWithRowAndActive(pageCount, copyCount, null);
    }

    /** @param active {@code null} leaves cell 15 empty (the optional "active" column). */
    private MockMultipartFile xlsxWithRowAndActive(String pageCount, String copyCount, String active)
            throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("books");
            Row header = sheet.createRow(0);
            for (int i = 0; i <= 21; i++) {
                header.createCell(i).setCellValue("col" + i);
            }
            Row row = sheet.createRow(1);
            row.createCell(1).setCellValue("عنوان تست");   // title
            row.createCell(2).setCellValue("نویسنده تست"); // author
            row.createCell(9).setCellValue("موضوع تست");   // subject
            row.createCell(12).setCellValue(pageCount);    // pageCount
            if (active != null) {
                row.createCell(15).setCellValue(active);   // active (optional)
            }
            row.createCell(16).setCellValue(copyCount);    // copyCount
            wb.write(out);
            return new MockMultipartFile(
                    "file", "books.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    @Test
    void importBooks_doesNotAbortOnNonNumericPageCount() throws Exception {
        MockMultipartFile file = xlsxWithRow("N/A", "not-a-number");

        var result = service.importBooksFromExcel(file);

        // The row must still be imported; the bad numeric cells are skipped, not fatal.
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skippedDuplicates()).isZero();
    }

    @Test
    void importBooks_parsesNumericCells() throws Exception {
        MockMultipartFile file = xlsxWithRow("300", "2");

        var result = service.importBooksFromExcel(file);

        assertThat(result.imported()).isEqualTo(1);
    }

    /**
     * The "active" column is optional in the spreadsheet. A blank cell used to leave the flag
     * null, which produced an imported-but-unusable book (invisible in active listings, refused
     * by the loan/reservation guards). A missing value must mean active.
     */
    @Test
    void importBooks_treatsABlankActiveCellAsActive() throws Exception {
        MockMultipartFile file = xlsxWithRow("300", "2");

        service.importBooksFromExcel(file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Book>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .extracting(Book::getActive)
                .isEqualTo(true);
    }

    @Test
    void importBooks_honoursAnExplicitInactiveCell() throws Exception {
        MockMultipartFile file = xlsxWithRowAndActive("300", "2", "FALSE");

        var result = service.importBooksFromExcel(file);

        assertThat(result.imported()).isEqualTo(1);
    }

    @Test
    void importBooks_skipsDuplicatesWithinWorkbookAndReportsTheirRows() throws Exception {
        var result = service.importBooksFromExcel(xlsxWithIsbns("0-306-40615-2", "0306406152"));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.duplicateRows()).containsExactly(3);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Book>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void importBooks_matchesExistingIsbn10AgainstEquivalentIsbn13() throws Exception {
        Book existing = new Book();
        existing.setIsbn10("0306406152");
        when(bookRepository.findByIsbn10In(anyCollection())).thenReturn(List.of(existing));

        var result = service.importBooksFromExcel(xlsxWithIsbns("9780306406157"));

        assertThat(result.imported()).isZero();
        assertThat(result.duplicateRows()).containsExactly(2);
        org.mockito.Mockito.verify(bookRepository, org.mockito.Mockito.never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    private MockMultipartFile xlsxWithIsbns(String... isbns) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("books");
            Row header = sheet.createRow(0);
            for (int i = 0; i <= 21; i++) header.createCell(i).setCellValue("col" + i);
            for (int index = 0; index < isbns.length; index++) {
                Row row = sheet.createRow(index + 1);
                String isbn = isbns[index];
                String normalizedIsbn = Isbn.normalize(isbn);
                row.createCell(normalizedIsbn.length() == 13 ? 6 : 0).setCellValue(isbn);
                row.createCell(1).setCellValue("عنوان " + index);
                row.createCell(2).setCellValue("نویسنده");
                row.createCell(9).setCellValue("موضوع");
            }
            wb.write(out);
            return new MockMultipartFile("file", "books.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}
