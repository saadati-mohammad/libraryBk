package ir.iau.library;

import ir.iau.library.dto.ConversationCriteria;
import ir.iau.library.dto.SearchCriteria;
import ir.iau.library.entity.Book;
import ir.iau.library.entity.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Lombok {@code @Builder.Default} values. Without the annotation the generated
 * builders ignored the field initializers and produced {@code null} collections and
 * {@code null}/{@code 0} flags, which is exactly the kind of value that only shows up as an
 * NPE deep in a request.
 */
class BuilderDefaultsTest {

    @Test
    void bookBuilderProducesAnEmptyLoanList() {
        assertThat(Book.builder().build().getLoans()).isNotNull().isEmpty();
    }

    @Test
    void personBuilderDefaultsToActiveWithEmptyLoanList() {
        Person person = Person.builder().build();

        assertThat(person.getActive()).isTrue();
        assertThat(person.getLoans()).isNotNull().isEmpty();
    }

    @Test
    void entitiesConstructedWithoutABuilderStillCarryTheirDefaults() {
        // Jackson binds request parts through the no-args constructor, not the builder.
        assertThat(new Person().getActive()).isTrue();
        assertThat(new Person().getLoans()).isNotNull().isEmpty();
        assertThat(new Book().getLoans()).isNotNull().isEmpty();
    }

    @Test
    void criteriaBuildersDefaultPaging() {
        ConversationCriteria conversation = ConversationCriteria.builder().build();
        assertThat(conversation.getPage()).isZero();
        assertThat(conversation.getSize()).isEqualTo(15);

        SearchCriteria search = SearchCriteria.builder().build();
        assertThat(search.getPage()).isZero();
        assertThat(search.getSize()).isEqualTo(15);
    }
}
