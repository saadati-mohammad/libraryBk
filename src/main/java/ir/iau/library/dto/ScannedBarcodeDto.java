package ir.iau.library.dto;

import java.time.Instant;

public record ScannedBarcodeDto(long sequence, String barcode, Instant receivedAt) { }
