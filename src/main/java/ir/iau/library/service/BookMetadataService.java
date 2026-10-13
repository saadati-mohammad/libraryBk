package ir.iau.library.service;

import ir.iau.library.dto.BookMetadataDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded, best-effort catalog lookup; metadata is always reviewed by staff before save. */
@Service
public class BookMetadataService {
    private final RestClient openLibrary;
    private final RestClient googleBooks;
    private final String googleBooksApiKey;
    private final boolean googleBooksEnabled;

    @Autowired
    public BookMetadataService(
            @Value("${app.catalog.open-library-base-url:https://openlibrary.org}") String openLibraryUrl,
            @Value("${app.catalog.google-books-base-url:https://www.googleapis.com/books/v1}") String googleBooksUrl,
            @Value("${app.catalog.google-books-api-key:}") String googleBooksApiKey) {
        this(createClient(openLibraryUrl), createClient(googleBooksUrl), googleBooksApiKey,
                googleBooksApiKey != null && !googleBooksApiKey.isBlank());
    }

    /** Test seam for the pre-existing single-provider unit tests. */
    BookMetadataService(RestClient client) {
        this(client, client, "", false);
    }

    BookMetadataService(RestClient openLibrary, RestClient googleBooks, String googleBooksApiKey) {
        this(openLibrary, googleBooks, googleBooksApiKey,
                googleBooksApiKey != null && !googleBooksApiKey.isBlank());
    }

    private BookMetadataService(RestClient openLibrary, RestClient googleBooks,
                                String googleBooksApiKey, boolean googleBooksEnabled) {
        this.openLibrary = openLibrary;
        this.googleBooks = googleBooks;
        this.googleBooksApiKey = googleBooksApiKey == null ? "" : googleBooksApiKey.strip();
        this.googleBooksEnabled = googleBooksEnabled;
    }

    private static RestClient createClient(String baseUrl) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public List<BookMetadataDto> lookup(String rawIsbn) {
        String isbn = Isbn.normalize(rawIsbn);
        if (!Isbn.isValid(isbn)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISBN checksum");
        return searchProviders("isbn:" + isbn, isbn);
    }

    /** Search by Persian or Latin title/author; providers are independent and failures are isolated. */
    public List<BookMetadataDto> search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.strip().replaceAll("\\s+", " ");
        if (query.length() < 2 || query.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search text must contain 2 to 120 characters");
        }
        return searchProviders(query, null);
    }

    private List<BookMetadataDto> searchProviders(String query, String requestedIsbn) {
        List<BookMetadataDto> results = new ArrayList<>();
        int failedProviders = 0;
        int attemptedProviders = 0;

        attemptedProviders++;
        try {
            results.addAll(searchOpenLibrary(query));
        } catch (RestClientException ex) {
            failedProviders++;
        }

        if (googleBooksEnabled) {
            attemptedProviders++;
            try {
                results.addAll(searchGoogleBooks(query));
            } catch (RestClientException ex) {
                failedProviders++;
            }
        }

        List<BookMetadataDto> candidates = deduplicate(results, requestedIsbn);
        if (candidates.isEmpty() && attemptedProviders > 0 && failedProviders == attemptedProviders) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "All catalog metadata providers are unavailable");
        }
        if (candidates.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No matching title was found");
        return candidates;
    }

    private List<BookMetadataDto> searchOpenLibrary(String query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/search.json");
        if (query.startsWith("isbn:") && Isbn.isValid(query.substring("isbn:".length()))) {
            builder.queryParam("isbn", query.substring("isbn:".length()));
        } else {
            builder.queryParam("q", query);
        }
        String uri = builder
                .queryParam("fields", "title,author_name,publisher,isbn,first_publish_year,number_of_pages_median,language,subject")
                .queryParam("limit", "10")
                .build().encode().toUriString();
        Map<?, ?> response = openLibrary.get().uri(URI.create(uri)).retrieve().body(Map.class);
        return mapList(response == null ? null : response.get("docs"), BookMetadataService::fromOpenLibrary);
    }

    private List<BookMetadataDto> searchGoogleBooks(String query) {
        String uri = UriComponentsBuilder.fromPath("/volumes")
                .queryParam("q", query)
                .queryParam("maxResults", "10")
                .queryParamIfPresent("key", this.googleBooksApiKey.isBlank()
                        ? java.util.Optional.empty() : java.util.Optional.of(this.googleBooksApiKey))
                .build().encode().toUriString();
        Map<?, ?> response = googleBooks.get().uri(URI.create(uri)).retrieve().body(Map.class);
        return mapList(response == null ? null : response.get("items"), BookMetadataService::fromGoogleBooks);
    }

    private static List<BookMetadataDto> mapList(Object raw, java.util.function.Function<Map<?, ?>, BookMetadataDto> mapper) {
        if (!(raw instanceof List<?> rows)) return List.of();
        return rows.stream().filter(Map.class::isInstance).map(row -> (Map<?, ?>) row)
                .map(mapper).filter(item -> item.title() != null).toList();
    }

    private static BookMetadataDto fromOpenLibrary(Map<?, ?> doc) {
        List<String> isbns = strings(doc.get("isbn"));
        return metadata(string(doc.get("title")), join(doc.get("author_name")), first(doc.get("publisher")),
                isbns, integer(doc.get("first_publish_year")), integer(doc.get("number_of_pages_median")),
                first(doc.get("language")), strings(doc.get("subject")));
    }

    private static BookMetadataDto fromGoogleBooks(Map<?, ?> item) {
        Map<?, ?> volume = item.get("volumeInfo") instanceof Map<?, ?> value ? value : Map.of();
        List<String> identifiers = new ArrayList<>();
        if (volume.get("industryIdentifiers") instanceof List<?> rows) {
            for (Object row : rows) {
                if (row instanceof Map<?, ?> identifier) {
                    String value = string(identifier.get("identifier"));
                    if (value != null) identifiers.add(value);
                }
            }
        }
        List<String> subjects = strings(volume.get("categories"));
        return metadata(string(volume.get("title")), join(volume.get("authors")), string(volume.get("publisher")),
                identifiers, year(string(volume.get("publishedDate"))), integer(volume.get("pageCount")),
                string(volume.get("language")), subjects);
    }

    private static BookMetadataDto metadata(String title, String author, String publisher, List<String> identifiers,
                                            Integer publicationYear, Integer pageCount, String language,
                                            List<String> subjects) {
        String isbn10 = identifiers.stream().map(Isbn::normalize).filter(value -> value.length() == 10).findFirst().orElse(null);
        String isbn13 = identifiers.stream().map(Isbn::normalize).filter(value -> value.length() == 13).findFirst().orElse(null);
        List<String> safeSubjects = subjects.stream().limit(10).toList();
        return new BookMetadataDto(title, author, publisher, isbn10, isbn13, publicationYear, pageCount,
                language, safeSubjects.stream().findFirst().orElse(null), safeSubjects);
    }

    private static List<BookMetadataDto> deduplicate(List<BookMetadataDto> results, String requestedIsbn) {
        Map<String, BookMetadataDto> unique = new LinkedHashMap<>();
        for (BookMetadataDto item : results) {
            if (requestedIsbn != null && !requestedIsbn.equals(Isbn.normalize(item.isbn10()))
                    && !requestedIsbn.equals(Isbn.normalize(item.isbn13()))) continue;
            String key = item.isbn13() != null ? Isbn.normalize(item.isbn13())
                    : item.isbn10() != null ? Isbn.normalize(item.isbn10())
                    : (item.title() + "|" + item.author()).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
            unique.merge(key, item, BookMetadataService::merge);
        }
        return unique.values().stream().limit(8).toList();
    }

    private static BookMetadataDto merge(BookMetadataDto first, BookMetadataDto second) {
        return new BookMetadataDto(first.title(), choose(first.author(), second.author()),
                choose(first.publisher(), second.publisher()), choose(first.isbn10(), second.isbn10()),
                choose(first.isbn13(), second.isbn13()), choose(first.publicationYear(), second.publicationYear()),
                choose(first.pageCount(), second.pageCount()), choose(first.language(), second.language()),
                choose(first.subject(), second.subject()), first.subjects().isEmpty() ? second.subjects() : first.subjects());
    }

    private static String choose(String first, String second) { return first == null || first.isBlank() ? second : first; }
    private static Integer choose(Integer first, Integer second) { return first == null ? second : first; }
    private static String string(Object value) { return value instanceof String text && !text.isBlank() ? text.strip() : null; }
    private static Integer integer(Object value) { return value instanceof Number number ? number.intValue() : null; }
    private static Integer year(String value) {
        if (value == null || value.length() < 4) return null;
        try { return Integer.parseInt(value.substring(0, 4)); } catch (NumberFormatException ignored) { return null; }
    }
    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast).filter(text -> !text.isBlank()).toList();
    }
    private static String first(Object value) { return strings(value).stream().findFirst().orElse(null); }
    private static String join(Object value) { return String.join(", ", strings(value)); }
}
