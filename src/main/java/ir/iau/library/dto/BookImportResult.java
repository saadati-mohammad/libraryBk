package ir.iau.library.dto;

import java.util.List;

/** Outcome of a catalog import; duplicate rows are reported without overwriting records. */
public record BookImportResult(int imported, int eligible, int totalRows,
                               List<Integer> duplicateRows, List<BookImportRowIssue> invalidRows) {
    public BookImportResult {
        duplicateRows = List.copyOf(duplicateRows);
        invalidRows = List.copyOf(invalidRows);
    }

    public int skippedDuplicates() {
        return duplicateRows.size();
    }

    public int skippedInvalid() { return invalidRows.size(); }
}
