package ir.iau.library.dto;

import java.util.List;

/** Bounded, row-oriented summary for member workbook previews and imports. */
public record PersonImportResult(int imported, int eligible, int totalRows,
                                 List<Integer> duplicateRows, List<BookImportRowIssue> invalidRows) {
    public PersonImportResult {
        duplicateRows = List.copyOf(duplicateRows);
        invalidRows = List.copyOf(invalidRows);
    }
    public int skippedDuplicates() { return duplicateRows.size(); }
    public int skippedInvalid() { return invalidRows.size(); }
}
