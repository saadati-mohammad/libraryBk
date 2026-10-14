package ir.iau.library.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;

import java.util.Locale;

/** Shared canonical form for Persian/Arabic-script catalog search terms and database fields. */
public final class PersianSearchNormalizer {
    private static final String ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩";
    private PersianSearchNormalizer() { }

    public static String normalize(String value) {
        if (value == null) return "";
        String result = value.strip().toLowerCase(Locale.ROOT)
                .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
                .replace("\u200c", "").replace("\u200f", "").replace("\u200e", "");
        StringBuilder latinDigits = new StringBuilder(result.length());
        for (int i = 0; i < result.length(); i++) {
            char ch = result.charAt(i);
            int digit = ARABIC_DIGITS.indexOf(ch);
            if (digit >= 0) ch = (char) ('0' + digit);
            else if (ch >= '۰' && ch <= '۹') ch = (char) ('0' + ch - '۰');
            latinDigits.append(ch);
        }
        return latinDigits.toString();
    }

    /** Applies the same normalization in SQL using portable REPLACE/LOWER expressions. */
    public static Expression<String> normalize(CriteriaBuilder cb, Expression<String> expression) {
        Expression<String> normalized = expression;
        normalized = replace(cb, normalized, "ي", "ی");
        normalized = replace(cb, normalized, "ى", "ی");
        normalized = replace(cb, normalized, "ك", "ک");
        normalized = replace(cb, normalized, "\u200c", "");
        normalized = replace(cb, normalized, "\u200f", "");
        normalized = replace(cb, normalized, "\u200e", "");
        for (int i = 0; i < 10; i++) {
            normalized = replace(cb, normalized, String.valueOf((char) ('۰' + i)), String.valueOf((char) ('0' + i)));
            normalized = replace(cb, normalized, String.valueOf(ARABIC_DIGITS.charAt(i)), String.valueOf((char) ('0' + i)));
        }
        return cb.lower(normalized);
    }

    private static Expression<String> replace(CriteriaBuilder cb, Expression<String> value, String from, String to) {
        return cb.function("replace", String.class, value, cb.literal(from), cb.literal(to));
    }
}
