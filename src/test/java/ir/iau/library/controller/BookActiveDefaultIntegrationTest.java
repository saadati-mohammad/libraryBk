package ir.iau.library.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.iau.library.entity.Book;
import ir.iau.library.repository.BookRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for the "silently unusable record" defect and for the multipart error contract.
 *
 * <p>A create request that omits {@code active} used to persist {@code null}. The row was then
 * invisible to {@code ?active=true} listings, uncounted on the dashboard and rejected by the
 * loan/reservation guards with a 409 — an imported or hand-made book nobody could borrow.
 *
 * <p>A missing required multipart part (for example an Excel import with no file, or a book
 * create with no {@code book} part) used to surface as a 500 because no handler mapped
 * {@code MissingServletRequestPartException}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookActiveDefaultIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookRepository bookRepository;

    private String login() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private static MockMultipartFile jsonPart(String name, String json) {
        return new MockMultipartFile(name, name + ".json",
                MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private long createBook(String token, String json) throws Exception {
        MvcResult res = mockMvc.perform(multipart("/api/book")
                        .file(jsonPart("book", json))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createPerson(String token) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String json = "{\"firstName\":\"Active\",\"lastName\":\"Default\",\"email\":\"active-" + suffix
                + "@test.local\",\"nationalId\":\"ad" + suffix + "\"}";
        MvcResult res = mockMvc.perform(multipart("/api/person")
                        .file(jsonPart("person", json))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void bookCreatedWithoutTheActiveFlagIsActiveListedAndLendable() throws Exception {
        String token = login();
        String isbn = "0306406152";

        // 1. create with NO "active" field at all
        MvcResult created = mockMvc.perform(multipart("/api/book")
                        .file(jsonPart("book", "{\"title\":\"Active By Default\",\"author\":\"Author\","
                                + "\"isbn10\":\"" + isbn + "\",\"subject\":\"Subject\"}"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        long bookId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // 2. it is part of the active listing
        MvcResult listing = mockMvc.perform(get("/api/book").param("active", "true").param("size", "100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(listing.getResponse().getContentAsString()).get("content");
        boolean present = false;
        for (JsonNode node : content) {
            if (node.get("id").asLong() == bookId) {
                present = true;
                break;
            }
        }
        assertThat(present).as("active book must be listed under ?active=true").isTrue();

        // 3. and it can actually be lent (the guard used to answer 409 for a null flag)
        long personId = createPerson(token);
        mockMvc.perform(post("/api/loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personId\":" + personId + ",\"bookId\":" + bookId + "}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ON_LOAN"));
    }

    @Test
    void updatingABookWithoutTheActiveFlagKeepsItActive() throws Exception {
        String token = login();
        String isbn = "0306406152";
        long bookId = createBook(token, "{\"title\":\"Keep Active\",\"author\":\"Author\","
                + "\"isbn10\":\"" + isbn + "\",\"subject\":\"Subject\",\"active\":true}");

        mockMvc.perform(multipart("/api/book/" + bookId)
                        .file(jsonPart("book", "{\"title\":\"Keep Active (edited)\",\"author\":\"Author\","
                                + "\"isbn10\":\"" + isbn + "\",\"subject\":\"Subject\"}"))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void bookCreateWithoutTheBookPartIsABadRequestNotAServerError() throws Exception {
        String token = login();

        mockMvc.perform(multipart("/api/book")
                        .file(new MockMultipartFile("bookCoverFile", "cover.png", "image/png", new byte[16]))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void excelImportWithoutAFileIsABadRequestNotAServerError() throws Exception {
        String token = login();

        mockMvc.perform(multipart("/api/excel-import/books")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void excelImportSkipsExistingAndInFileIsbnDuplicatesAndReportsRows() throws Exception {
        String token = login();
        long existingId = createBook(token, "{\"title\":\"Keep Original\",\"author\":\"Author\","
                + "\"isbn10\":\"0306406152\",\"subject\":\"Subject\"}");
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("books");
            Row header = sheet.createRow(0);
            for (int index = 0; index <= 21; index++) header.createCell(index).setCellValue("col" + index);
            Row existingDuplicate = sheet.createRow(1);
            existingDuplicate.createCell(1).setCellValue("Must Not Replace Existing");
            existingDuplicate.createCell(2).setCellValue("Author");
            existingDuplicate.createCell(9).setCellValue("Subject");
            existingDuplicate.createCell(6).setCellValue("9780306406157");
            Row newBook = sheet.createRow(2);
            newBook.createCell(1).setCellValue("New imported book");
            newBook.createCell(2).setCellValue("Author");
            newBook.createCell(9).setCellValue("Subject");
            newBook.createCell(6).setCellValue("9781861972712");
            Row fileDuplicate = sheet.createRow(3);
            fileDuplicate.createCell(1).setCellValue("Duplicate row in workbook");
            fileDuplicate.createCell(2).setCellValue("Author");
            fileDuplicate.createCell(9).setCellValue("Subject");
            fileDuplicate.createCell(0).setCellValue("0-306-40615-2");
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "books.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);
        mockMvc.perform(multipart("/api/excel-import/books/preview").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview").value(true))
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.eligible").value(1))
                .andExpect(jsonPath("$.skippedDuplicates").value(2));
        assertThat(bookRepository.findByIsbn13In(List.of("9781861972712"))).isEmpty();

        mockMvc.perform(multipart("/api/excel-import/books").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.skippedDuplicates").value(2))
                .andExpect(jsonPath("$.skippedInvalid").value(0))
                .andExpect(jsonPath("$.duplicateRows[0]").value(2))
                .andExpect(jsonPath("$.duplicateRows[1]").value(4));

        Book original = bookRepository.findById(existingId).orElseThrow();
        assertThat(original.getTitle()).isEqualTo("Keep Original");
        assertThat(bookRepository.findByIsbn13In(List.of("9781861972712"))).singleElement()
                .extracting(Book::getTitle).isEqualTo("New imported book");
    }
}
