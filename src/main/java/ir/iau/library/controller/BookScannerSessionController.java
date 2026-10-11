package ir.iau.library.controller;

import ir.iau.library.dto.ScannedBarcodeDto;
import ir.iau.library.dto.ScannerSessionDto;
import ir.iau.library.service.PhoneScannerSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.security.Principal;

/** Staff-authenticated desktop control for short-lived companion scanner sessions. */
@RestController
@RequestMapping("/api/book/scanner-sessions")
public class BookScannerSessionController {
    private final PhoneScannerSessionService sessions;

    public BookScannerSessionController(PhoneScannerSessionService sessions) { this.sessions = sessions; }

    @PostMapping
    public ScannerSessionDto create(Principal principal) {
        return sessions.create(ir.iau.library.security.AccessControl.requireUsername(principal));
    }

    @GetMapping("/{id}/scans")
    public List<ScannedBarcodeDto> poll(Principal principal, @PathVariable String id,
                                       @RequestParam(defaultValue = "0") long afterSequence) {
        return sessions.poll(id, Math.max(0, afterSequence),
                ir.iau.library.security.AccessControl.requireUsername(principal));
    }

    @DeleteMapping("/{id}")
    public void close(Principal principal, @PathVariable String id) {
        sessions.close(id, ir.iau.library.security.AccessControl.requireUsername(principal));
    }
}
