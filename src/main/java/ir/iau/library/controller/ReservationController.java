package ir.iau.library.controller;

import ir.iau.library.dto.ReservationDto;
import ir.iau.library.entity.Reservation;
import ir.iau.library.service.EntityMapper;
import ir.iau.library.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final EntityMapper entityMapper;

    @PostMapping
    public ResponseEntity<ReservationDto> createReservation(
            @RequestParam Long personId,
            @RequestParam Long bookId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate) {
        Reservation reservation = reservationService.createReservation(personId, bookId, expiryDate);
        return ResponseEntity.status(HttpStatus.CREATED).body(entityMapper.toDto(reservation));
    }

    @PutMapping("/{id}/fulfill")
    public ResponseEntity<ReservationDto> fulfillReservation(@PathVariable Long id) {
        Reservation updated = reservationService.fulfillReservation(id);
        return ResponseEntity.ok(entityMapper.toDto(updated));
    }

    @GetMapping("/active")
    public List<ReservationDto> listActive() {
        return reservationService.getActiveReservations().stream()
                .map(entityMapper::toDto)
                .toList();
    }
}
