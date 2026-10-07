package ir.iau.library.service;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import ir.iau.library.dto.BookFilterDto;
import ir.iau.library.dto.PersonFilterDto;
import ir.iau.library.entity.Book;
import ir.iau.library.entity.Person;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import ir.iau.library.specification.DuplicateFieldException;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class ExcelImportService {

    private final BookRepository bookRepository;
    private final PersonRepository personRepository;

    @Autowired
    public ExcelImportService(BookRepository bookRepository, PersonRepository personRepository) {
        this.bookRepository = bookRepository;
        this.personRepository = personRepository;
    }

    // متد اصلی برای خواندن فایل اکسل
    @Transactional // عملیات ذخیره را در یک تراکنش قرار می‌دهد
    public List<BookFilterDto> importBooksFromExcel(MultipartFile file) throws IOException {
        List<BookFilterDto> bookDtos = new ArrayList<>();
        List<Book> booksToSave = new ArrayList<>();
        DataFormatter dataFormatter = new DataFormatter(); // برای تبدیل انواع داده به رشته

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
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

                BookFilterDto bookDto = new BookFilterDto();

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
                        // اینجا می‌توانید لاگ بزنید یا فرمت دیگری را امتحان کنید
                        System.err.println("Could not parse date: " + publicationDateStr + " at row " + row.getRowNum());
                    }
                }

                String pageCountStr = getCellValueAsString(row.getCell(12), dataFormatter);
                Integer pageCount = parseIntegerCell(pageCountStr, row.getRowNum(), "pageCount");
                if (pageCount != null) {
                    bookDto.setPageCount(pageCount);
                }

                bookDto.setLanguage(getCellValueAsString(row.getCell(13), dataFormatter));
                bookDto.setEdition(getCellValueAsString(row.getCell(14), dataFormatter));

                String activeStr = getCellValueAsString(row.getCell(15), dataFormatter);
                if (activeStr != null) {
                    bookDto.setActive(activeStr.equalsIgnoreCase("TRUE") || activeStr.equalsIgnoreCase("فعال") || activeStr.equals("1"));
                }

                String copyCountStr = getCellValueAsString(row.getCell(16), dataFormatter);
                Integer copyCount = parseIntegerCell(copyCountStr, row.getRowNum(), "copyCount");
                if (copyCount != null) {
                    bookDto.setCopyCount(copyCount);
                }

                bookDto.setLibrarySection(getCellValueAsString(row.getCell(17), dataFormatter));
                bookDto.setShelfCode(getCellValueAsString(row.getCell(18), dataFormatter));
                bookDto.setRowNumbers(getCellValueAsString(row.getCell(19), dataFormatter));
                bookDto.setColumnNumber(getCellValueAsString(row.getCell(20), dataFormatter));
                bookDto.setPositionNote(getCellValueAsString(row.getCell(21), dataFormatter));

                bookDtos.add(bookDto);
                booksToSave.add(new Book(bookDto));
            }
        }

        // ذخیره تمام کتاب‌ها به صورت یکجا برای بهینگی
        if (!booksToSave.isEmpty()) {
            bookRepository.saveAll(booksToSave);
        }

        return bookDtos;
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

    /**
     * تبدیل امن مقدار یک سلول به عدد صحیح.
     * سلول خالی/غیرعددی مقدار null برمی‌گرداند (بدون پرتاب استثنا) تا یک سلول بدفرمت
     * کل ایمپورت را متوقف نکند. مقدار اعشاری (مثل ۳۰۰.۰ از اکسل) به نزدیک‌ترین عدد صحیح گرد می‌شود.
     */
    private Integer parseIntegerCell(String value, int rowNum, String fieldName) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            System.err.println("Could not parse " + fieldName + ": '" + value + "' at row " + rowNum);
            return null;
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



    @Transactional // عملیات ذخیره را در یک تراکنش قرار می‌دهد
    public List<PersonFilterDto> importPersonsFromExcel(MultipartFile file) throws IOException, InvalidFormatException {
        List<PersonFilterDto> personDtos = new ArrayList<>();
        DataFormatter dataFormatter = new DataFormatter(); // برای تبدیل انواع داده به رشته

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if (row.getRowNum() == 0) continue;  // Skip header row

                // Read data from each cell and convert to DTO
                PersonFilterDto personDto = new PersonFilterDto();
                personDto.setFirstName(getCellValueAsString(row.getCell(0), dataFormatter));
                personDto.setLastName(getCellValueAsString(row.getCell(1), dataFormatter));
                personDto.setEmail(getCellValueAsString(row.getCell(2), dataFormatter));
                personDto.setPhone(getCellValueAsString(row.getCell(3), dataFormatter));
                personDto.setNationalId(getCellValueAsString(row.getCell(4), dataFormatter));
                personDto.setMembershipType(getCellValueAsString(row.getCell(5), dataFormatter));
                personDto.setAddress(getCellValueAsString(row.getCell(6), dataFormatter));
                String activeStr = getCellValueAsString(row.getCell(7), dataFormatter);

                // Check for duplicate fields
                if (personRepository.existsByEmail(personDto.getEmail())) {
                    throw new DuplicateFieldException("Email", personDto.getEmail());
                }

                if (personRepository.existsByNationalId(personDto.getNationalId())) {
                    throw new DuplicateFieldException("National ID", personDto.getNationalId());
                }
                if (activeStr != null) {
                    personDto.setActive(activeStr.equalsIgnoreCase("TRUE") || activeStr.equalsIgnoreCase("فعال") || activeStr.equals("1"));
                }
                // Handle birthDate and membershipDate fields
                String birthDateStr = getCellValueAsString(row.getCell(8), dataFormatter);
                if (birthDateStr != null && !birthDateStr.isEmpty()) {
                    try {
                        personDto.setBirthDate(LocalDate.parse(birthDateStr, DateTimeFormatter.ISO_LOCAL_DATE)); // YYYY-MM-DD
                    } catch (Exception e) {
                        // Handle invalid date format, log it if necessary
                        System.err.println("Could not parse birth date: " + birthDateStr + " at row " + row.getRowNum());
                    }
                }

                String membershipDateFromStr = getCellValueAsString(row.getCell(9), dataFormatter);
                if (membershipDateFromStr != null && !membershipDateFromStr.isEmpty()) {
                    try {
                        personDto.setMembershipDateFrom(LocalDate.parse(membershipDateFromStr, DateTimeFormatter.ISO_LOCAL_DATE)); // YYYY-MM-DD
                    } catch (Exception e) {
                        // Handle invalid date format, log it if necessary
                        System.err.println("Could not parse membership date from: " + membershipDateFromStr + " at row " + row.getRowNum());
                    }
                }

                String membershipDateToStr = getCellValueAsString(row.getCell(10), dataFormatter);
                if (membershipDateToStr != null && !membershipDateToStr.isEmpty()) {
                    try {
                        personDto.setMembershipDateTo(LocalDate.parse(membershipDateToStr, DateTimeFormatter.ISO_LOCAL_DATE)); // YYYY-MM-DD
                    } catch (Exception e) {
                        // Handle invalid date format, log it if necessary
                        System.err.println("Could not parse membership date to: " + membershipDateToStr + " at row " + row.getRowNum());
                    }
                }

                // Add DTO to the list
                personDtos.add(personDto);

                // Save to DB (if necessary)
                Person person = new Person(personDto);
                personRepository.save(person);
            }
        }

        return personDtos;
    }

}
