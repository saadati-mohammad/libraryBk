package ir.iau.library.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BookMetadataMultiProviderTest {
    private MockRestServiceServer openLibraryServer;
    private MockRestServiceServer googleBooksServer;
    private BookMetadataService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder openLibrary = RestClient.builder().baseUrl("https://openlibrary.test");
        RestClient.Builder googleBooks = RestClient.builder().baseUrl("https://googlebooks.test");
        openLibraryServer = MockRestServiceServer.bindTo(openLibrary).build();
        googleBooksServer = MockRestServiceServer.bindTo(googleBooks).build();
        service = new BookMetadataService(openLibrary.build(), googleBooks.build(), "");
    }

    @AfterEach
    void verifyRequests() {
        openLibraryServer.verify();
        googleBooksServer.verify();
    }

    @Test
    void searchesPersianTextAcrossBothProvidersAndCombinesResults() {
        openLibraryServer.expect(requestTo(org.hamcrest.Matchers.containsString("q=%D8%B4%D8%A7%D8%B2%D8%AF%D9%87")))
                .andRespond(withSuccess("""
                        {"docs":[{"title":"شازده کوچولو","author_name":["آنتوان دو سنت اگزوپری"],
                        "publisher":["ناشر ایرانی"],"first_publish_year":1402,"language":["per"]}]}
                        """, MediaType.APPLICATION_JSON));
        googleBooksServer.expect(requestTo(org.hamcrest.Matchers.containsString("q=%D8%B4%D8%A7%D8%B2%D8%AF%D9%87")))
                .andRespond(withSuccess("""
                        {"items":[{"volumeInfo":{"title":"کتاب ایرانی","authors":["نویسنده"],
                        "publisher":"انتشارات","publishedDate":"2021-05","pageCount":120,
                        "language":"fa","categories":["داستان"],"industryIdentifiers":[
                        {"type":"ISBN_13","identifier":"9780306406157"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        var results = service.search("شازده  کوچولو");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(item -> item.title()).contains("شازده کوچولو", "کتاب ایرانی");
        assertThat(results).filteredOn(item -> "کتاب ایرانی".equals(item.title())).singleElement()
                .satisfies(item -> {
                    assertThat(item.language()).isEqualTo("fa");
                    assertThat(item.publicationYear()).isEqualTo(2021);
                    assertThat(item.isbn13()).isEqualTo("9780306406157");
                });
    }

    @Test
    void rejectsTooShortSearchBeforeCallingProviders() {
        assertThatThrownBy(() -> service.search("x"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }
}
