package ir.iau.library.controller;

import ir.iau.library.dto.ScannedBarcodeDto;
import ir.iau.library.dto.ScannerSubmissionDto;
import ir.iau.library.service.PhoneScannerSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Capability-authenticated endpoint; the random pairing secret grants access to one session only. */
@RestController
@RequestMapping("/api/phone-scanner/sessions")
public class PhoneScannerController {
    private final PhoneScannerSessionService sessions;

    public PhoneScannerController(PhoneScannerSessionService sessions) { this.sessions = sessions; }

    @PostMapping("/{id}/scans")
    public ScannedBarcodeDto submit(@PathVariable String id, @Valid @RequestBody ScannerSubmissionDto request) {
        return sessions.submit(id, request.pairingToken(), request.barcode());
    }
}
