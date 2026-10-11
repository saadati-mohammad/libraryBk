package ir.iau.library.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersianSearchNormalizerTest {
    @Test
    void canonicalizesPersianArabicLettersDigitsAndInvisibleDirectionMarks() {
        assertThat(PersianSearchNormalizer.normalize("\u0643\u0650\u062a\u0627\u0628\u200c\u062e\u0627\u0646\u0647 \u064a ۱۲٣ ١٢٣\u200f"))
                .isEqualTo("\u06a9\u0650\u062a\u0627\u0628\u062e\u0627\u0646\u0647 \u06cc 123 123");
    }
}
