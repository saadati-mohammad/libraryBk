package ir.iau.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScannerSubmissionDto(
        @NotBlank @Size(max = 128) String pairingToken,
        @NotBlank @Size(max = 24) String barcode,
        Long deviceId,
        @Size(max = 128) String deviceToken) { }
