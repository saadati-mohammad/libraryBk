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

class BookMetadataServiceTest {
    private MockRestServiceServer server;
    private BookMetadataService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://metadata.test");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new BookMetadataService(builder.build());
    }

    @AfterEach
    void verifyRequests() { server.verify(); }

    @Test
    void rejectsInvalidChecksumWithoutCallingProvider() {
        assertThatThrownBy(() -> service.lookup("9780306406158"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void mapsRealProviderFieldsButLeavesMissingDataEmpty() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("isbn=9780306406157")))
                .andRespond(withSuccess("""
                        {"docs":[{"title":"Example","author_name":["A. Author","B. Author"],
                        "publisher":["Press"],"isbn":["9780306406157"],"first_publish_year":2020,
                        "number_of_pages_median":240,"language":["eng"],"subject":["Libraries"]}]}
                        """, MediaType.APPLICATION_JSON));

        var result = service.lookup("978-0-306-40615-7").getFirst();

        assertThat(result.title()).isEqualTo("Example");
        assertThat(result.author()).isEqualTo("A. Author, B. Author");
        assertThat(result.publisher()).isEqualTo("Press");
        assertThat(result.isbn13()).isEqualTo("9780306406157");
        assertThat(result.publicationYear()).isEqualTo(2020);
        assertThat(result.pageCount()).isEqualTo(240);
        assertThat(result.language()).isEqualTo("eng");
        assertThat(result.subjects()).containsExactly("Libraries");
        assertThat(result.isbn10()).isNull();
    }

    @Test
    void reportsNoMatchWithoutInventingFields() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("isbn=9780306406157")))
                .andRespond(withSuccess("{\"numFound\":0,\"docs\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.lookup("9780306406157"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }
}
