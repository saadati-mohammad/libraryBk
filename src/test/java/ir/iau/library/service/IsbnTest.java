package ir.iau.library.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IsbnTest {
    @Test
    void normalizesSeparatorsAndCase() {
        assertThat(Isbn.normalize(" 0-306-40615-x ")).isEqualTo("030640615X");
    }

    @Test
    void validatesIsbn10AndIsbn13Checksums() {
        assertThat(Isbn.isValid("0-306-40615-2")).isTrue();
        assertThat(Isbn.isValid("080442957X")).isTrue();
        assertThat(Isbn.isValid("9780306406157")).isTrue();
        assertThat(Isbn.isValid("9780306406158")).isFalse();
        assertThat(Isbn.isValid("1234567890123")).isFalse();
    }

    @Test
    void normalizesPersianAndArabicIndicDigitsBeforeValidation() {
        assertThat(Isbn.normalize("۹۷۸-۰۳۰۶۴۰۶۱۵۷")).isEqualTo("9780306406157");
        assertThat(Isbn.normalize("٩٧٨ ٠٣٠٦٤٠٦١٥٧")).isEqualTo("9780306406157");
        assertThat(Isbn.isValid("۹۷۸-۰۳۰۶۴۰۶۱۵۷")).isTrue();
    }
}
