package ir.iau.library.controller;

import ir.iau.library.dto.BookFilterDto;
import ir.iau.library.dto.PersonFilterDto;
import ir.iau.library.service.ExcelImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/excel-import")
@Slf4j
public class ExcelImportController {

    private final ExcelImportService excelImportService;

    @Autowired
    public ExcelImportController(ExcelImportService excelImportService) {
        this.excelImportService = excelImportService;
    }

    @PostMapping("/books")
    public ResponseEntity<Map<String, Object>> importBooks(@RequestPart("file") MultipartFile file) {
        try {
            List<BookFilterDto> books = excelImportService.importBooksFromExcel(file);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "imported", books.size(),
                    "message", "Books successfully imported."));
        } catch (Exception e) {
            // Full detail stays in the server log; the client gets a generic message.
            log.error("Error importing books from Excel: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of(
                            "success", false,
                            "message", "Error importing books. Check the file format and try again."));
        }
    }

    @PostMapping("/persons")
    public ResponseEntity<Map<String, Object>> importPersons(@RequestPart("file") MultipartFile file) {
        try {
            List<PersonFilterDto> persons = excelImportService.importPersonsFromExcel(file);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "imported", persons.size(),
                    "message", "Persons successfully imported."));
        } catch (Exception e) {
            log.error("Error importing persons from Excel: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of(
                            "success", false,
                            "message", "Error importing persons. Check the file format and try again."));
        }
    }
}
