package ir.iau.library.controller;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the bounded/validated page requests used by the list endpoints.
 *
 * <p>Some of these inputs cannot be produced over HTTP (Spring substitutes
 * {@code @RequestParam}'s default value for an empty one), so they are pinned here.
 */
class PageableFactoryTest {

    private static final Set<String> SORTABLE = Set.of("id", "title");

    @Test
    void sizeIsClampedToTheMaximum() {
        Pageable pageable = PageableFactory.of(0, 5000, "id,desc", SORTABLE, Sort.Direction.ASC);

        assertThat(pageable.getPageSize()).isEqualTo(PageableFactory.MAX_PAGE_SIZE);
    }

    @Test
    void nonPositiveSizeIsClampedToOne() {
        assertThat(PageableFactory.of(0, 0, "id,desc", SORTABLE, Sort.Direction.ASC).getPageSize())
                .isEqualTo(1);
        assertThat(PageableFactory.of(0, -10, "id,desc", SORTABLE, Sort.Direction.ASC).getPageSize())
                .isEqualTo(1);
    }

    @Test
    void negativePageIsClampedToZero() {
        assertThat(PageableFactory.of(-3, 10, "id,desc", SORTABLE, Sort.Direction.ASC).getPageNumber())
                .isZero();
    }

    @Test
    void directionIsParsedAndApplied() {
        Pageable pageable = PageableFactory.of(0, 10, "title,asc", SORTABLE, Sort.Direction.DESC);

        assertThat(pageable.getSort().getOrderFor("title").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void missingDirectionUsesTheDefault() {
        Sort sort = PageableFactory.parseSort("title", SORTABLE, Sort.Direction.DESC);

        assertThat(sort.getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void blankSortIsRejected() {
        assertThatThrownBy(() -> PageableFactory.parseSort("  ", SORTABLE, Sort.Direction.ASC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageableFactory.parseSort(null, SORTABLE, Sort.Direction.ASC))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankOrUnknownPropertyIsRejected() {
        assertThatThrownBy(() -> PageableFactory.parseSort(",desc", SORTABLE, Sort.Direction.ASC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageableFactory.parseSort("doesNotExist", SORTABLE, Sort.Direction.ASC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageableFactory.parseSort("loans", SORTABLE, Sort.Direction.ASC))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownDirectionIsRejected() {
        assertThatThrownBy(() -> PageableFactory.parseSort("id,sideways", SORTABLE, Sort.Direction.ASC))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
