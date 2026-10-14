package ir.iau.library.service;

/** ISBN formatting and check-digit validation shared by catalog metadata workflows. */
public final class Isbn {
    private Isbn() { }

    public static String normalize(String input) {
        if (input == null) return "";
        StringBuilder normalized = new StringBuilder(input.length());
        input.codePoints().forEach(codePoint -> {
            if (Character.isWhitespace(codePoint) || codePoint == '-') return;
            if (codePoint >= 0x06f0 && codePoint <= 0x06f9) {
                normalized.append((char) ('0' + codePoint - 0x06f0));
            } else if (codePoint >= 0x0660 && codePoint <= 0x0669) {
                normalized.append((char) ('0' + codePoint - 0x0660));
            } else {
                normalized.appendCodePoint(codePoint);
            }
        });
        return normalized.toString().toUpperCase(java.util.Locale.ROOT);
    }

    public static boolean isValid(String input) {
        String value = normalize(input);
        if (value.matches("[0-9]{9}[0-9X]")) {
            int sum = 0;
            for (int i = 0; i < 10; i++) sum += (10 - i) * (value.charAt(i) == 'X' ? 10 : value.charAt(i) - '0');
            return sum % 11 == 0;
        }
        if (!value.matches("[0-9]{13}")) return false;
        int sum = 0;
        for (int i = 0; i < 12; i++) sum += (value.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return (10 - sum % 10) % 10 == value.charAt(12) - '0';
    }

    /** Returns the ISBN-10 equivalent of a 978 ISBN-13, or an empty string when unavailable. */
    public static String toIsbn10(String input) {
        String value = normalize(input);
        if (!value.matches("978[0-9]{10}") || !isValid(value)) return "";
        String body = value.substring(3, 12);
        int sum = 0;
        for (int index = 0; index < body.length(); index++) sum += (10 - index) * (body.charAt(index) - '0');
        int check = (11 - sum % 11) % 11;
        return body + (check == 10 ? "X" : check);
    }

    /** Returns the 978 ISBN-13 equivalent of a valid ISBN-10, or an empty string when unavailable. */
    public static String toIsbn13(String input) {
        String value = normalize(input);
        if (!value.matches("[0-9]{9}[0-9X]") || !isValid(value)) return "";
        String body = "978" + value.substring(0, 9);
        int sum = 0;
        for (int index = 0; index < body.length(); index++) sum += (body.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
        return body + (10 - sum % 10) % 10;
    }
}
