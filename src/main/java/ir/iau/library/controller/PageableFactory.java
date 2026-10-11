package ir.iau.library.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Builds bounded, validated {@link Pageable} instances from raw request parameters.
 *
 * <p>List endpoints used to pass {@code page}/{@code size} straight to
 * {@link PageRequest#of(int, int)} with no upper bound and to build a {@link Sort} from an
 * arbitrary client string, so a single request could ask the database for the whole table
 * or make Spring Data throw a deep {@code PropertyReferenceException} (surfacing as a 500).
 * Here the size is clamped to {@value #MAX_PAGE_SIZE} and the sort property must be one of
 * the entity's own fields, otherwise a 400 is produced by
 * {@link ir.iau.library.exception.GlobalExceptionHandler}.
 */
final class PageableFactory {

    /** Mirrors {@code spring.data.web.pageable.max-page-size} in application.properties. */
    static final int MAX_PAGE_SIZE = 100;

    private PageableFactory() {
    }

    static Pageable of(int page, int size, String sort, Set<String> sortableProperties,
                       Sort.Direction defaultDirection) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, parseSort(sort, sortableProperties, defaultDirection));
    }

    /**
     * Parses {@code "property,direction"}.
     *
     * @throws IllegalArgumentException for a blank/unknown property (400) or an unknown
     *                                  direction (also 400, via the same handler)
     */
    static Sort parseSort(String sort, Set<String> sortableProperties, Sort.Direction defaultDirection) {
        if (sort == null || sort.isBlank()) {
            throw new IllegalArgumentException("sort parameter must not be blank");
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        if (property.isEmpty() || !sortableProperties.contains(property)) {
            throw new IllegalArgumentException("Unknown sort property: " + property);
        }
        Sort.Direction direction = defaultDirection;
        if (parts.length > 1) {
            direction = Sort.Direction.fromString(parts[1].trim());
        }
        return Sort.by(direction, property);
    }
}
