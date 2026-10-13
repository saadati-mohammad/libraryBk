package ir.iau.library.dto;

import java.util.List;

/** Outcome of a catalog import; duplicate rows are reported without overwriting records. */
public record BookImportResult(int imported, List<Integer> duplicateRows) {
    public BookImportResult {
        duplicateRows = List.copyOf(duplicateRows);
    }

    public int skippedDuplicates() {
        return duplicateRows.size();
    }
}
