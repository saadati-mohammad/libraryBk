package ir.iau.library.dto;

/** A spreadsheet row that cannot be imported until its fields are corrected. */
public record BookImportRowIssue(int row, String reason) { }
