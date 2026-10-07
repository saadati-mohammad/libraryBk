package ir.iau.library.service;

import ir.iau.library.dto.BookFilterDto;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    private MockMultipartFile xlsxWithRow(String pageCount, String copyCount) throws Exception {
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

        List<BookFilterDto> result = service.importBooksFromExcel(file);

        // The row must still be imported; the bad numeric cells are skipped, not fatal.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("عنوان تست");
        assertThat(result.get(0).getPageCount()).isNull();
        assertThat(result.get(0).getCopyCount()).isNull();
    }

    @Test
    void importBooks_parsesNumericCells() throws Exception {
        MockMultipartFile file = xlsxWithRow("300", "2");

        List<BookFilterDto> result = service.importBooksFromExcel(file);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPageCount()).isEqualTo(300);
        assertThat(result.get(0).getCopyCount()).isEqualTo(2);
    }
}
