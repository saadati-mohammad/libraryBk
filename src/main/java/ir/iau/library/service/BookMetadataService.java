package ir.iau.library.service;

import ir.iau.library.dto.BookMetadataDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.time.Duration;

/** Bounded, best-effort Open Library lookup; metadata is always reviewed by staff before save. */
@Service
public class BookMetadataService {
    private final RestClient client;

    @Autowired
    public BookMetadataService(@Value("${app.catalog.metadata-base-url:https://openlibrary.org}") String baseUrl) {
        this(createClient(baseUrl));
    }

    BookMetadataService(RestClient client) { this.client = client; }

    private static RestClient createClient(String baseUrl) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public List<BookMetadataDto> lookup(String rawIsbn) {
        String isbn = Isbn.normalize(rawIsbn);
        if (!Isbn.isValid(isbn)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISBN checksum");
        String uri = UriComponentsBuilder.fromPath("/search.json")
                .queryParam("isbn", isbn)
                .queryParam("fields", "title,author_name,publisher,isbn,first_publish_year,number_of_pages_median,language,subject")
                .queryParam("limit", "5")
                .build().encode().toUriString();
        try {
            Map<?, ?> response = client.get().uri(uri).retrieve().body(Map.class);
            if (response == null || !(response.get("docs") instanceof List<?> docs) || docs.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No matching title was found");
            }
            List<BookMetadataDto> candidates = docs.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .map(BookMetadataService::toMetadata).filter(metadata -> metadata.title() != null).limit(5).toList();
            if (candidates.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No matching title was found");
            return candidates;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catalog metadata provider is unavailable", ex);
        }
    }

    private static BookMetadataDto toMetadata(Map<?, ?> doc) {
        List<String> isbns = strings(doc.get("isbn"));
        String isbn10 = isbns.stream().filter(value -> Isbn.normalize(value).length() == 10).findFirst().orElse(null);
        String isbn13 = isbns.stream().filter(value -> Isbn.normalize(value).length() == 13).findFirst().orElse(null);
        List<String> subjects = strings(doc.get("subject")).stream().limit(10).toList();
        return new BookMetadataDto(string(doc.get("title")), join(doc.get("author_name")),
                first(doc.get("publisher")), isbn10, isbn13, integer(doc.get("first_publish_year")),
                integer(doc.get("number_of_pages_median")), first(doc.get("language")), first(doc.get("subject")), subjects);
    }

    private static String string(Object value) { return value instanceof String s && !s.isBlank() ? s : null; }
    private static Integer integer(Object value) { return value instanceof Number number ? number.intValue() : null; }
    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast).filter(s -> !s.isBlank()).toList();
    }
    private static String first(Object value) { return strings(value).stream().findFirst().orElse(null); }
    private static String join(Object value) { return String.join(", ", strings(value)); }
}
