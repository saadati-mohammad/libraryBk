package ir.iau.library.controller;

import ir.iau.library.dto.ScannedBarcodeDto;
import ir.iau.library.dto.ScannerSubmissionDto;
import ir.iau.library.service.PhoneScannerSessionService;
import ir.iau.library.service.InputDeviceService;
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
    private final InputDeviceService devices;

    public PhoneScannerController(PhoneScannerSessionService sessions, InputDeviceService devices) {
        this.sessions = sessions;
        this.devices = devices;
    }

    @PostMapping("/{id}/scans")
    public ScannedBarcodeDto submit(@PathVariable String id, @Valid @RequestBody ScannerSubmissionDto request) {
        if ((request.deviceId() == null) != (request.deviceToken() == null)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Device id and token must be supplied together");
        }
        if (request.deviceId() != null) devices.authenticatePhoneScanner(request.deviceId(), request.deviceToken());
        return sessions.submit(id, request.pairingToken(), request.barcode(), request.deviceId());
    }
}
