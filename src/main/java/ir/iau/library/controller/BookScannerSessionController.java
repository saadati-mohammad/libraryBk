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

/** Staff-authenticated desktop control for short-lived companion scanner sessions. */
@RestController
@RequestMapping("/api/book/scanner-sessions")
public class BookScannerSessionController {
    private final PhoneScannerSessionService sessions;

    public BookScannerSessionController(PhoneScannerSessionService sessions) { this.sessions = sessions; }

    @PostMapping
    public ScannerSessionDto create() { return sessions.create(); }

    @GetMapping("/{id}/scans")
    public List<ScannedBarcodeDto> poll(@PathVariable String id,
                                       @RequestParam(defaultValue = "0") long afterSequence) {
        return sessions.poll(id, Math.max(0, afterSequence));
    }

    @DeleteMapping("/{id}")
    public void close(@PathVariable String id) { sessions.close(id); }
}
