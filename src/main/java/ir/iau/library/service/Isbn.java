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
}
