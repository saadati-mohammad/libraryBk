package ir.iau.library.service;

import ir.iau.library.entity.Book;
import ir.iau.library.entity.Person;
import ir.iau.library.entity.Reservation;
import ir.iau.library.entity.ReservationStatus;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import ir.iau.library.repository.ReservationRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final PersonRepository personRepository;
    private final BookRepository bookRepository;

    public Reservation createReservation(Long personId, Long bookId, LocalDate expiryDate) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new EntityNotFoundException("Person not found"));
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found"));

        if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("expiryDate cannot be in the past");
        }

        // Guard against duplicate active reservations for the same person/book.
        reservationRepository.findFirstByPersonAndBookAndStatus(person, book, ReservationStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new IllegalStateException("An active reservation already exists for this person and book.");
                });

        Reservation reservation = Reservation.builder()
                .person(person)
                .book(book)
                .reservationDate(LocalDate.now())
                .expiryDate(expiryDate)
                .status(ReservationStatus.ACTIVE)
                .build();

        return reservationRepository.save(reservation);
    }

    public Reservation fulfillReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found"));

        // Only an ACTIVE reservation can be fulfilled; reject double-fulfilment.
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only an active reservation can be fulfilled (current status: "
                    + reservation.getStatus() + ").");
        }

        reservation.setStatus(ReservationStatus.FULFILLED);
        return reservationRepository.save(reservation);
    }

    public List<Reservation> getActiveReservations() {
        return reservationRepository.findByStatus(ReservationStatus.ACTIVE);
    }

    public List<Reservation> expireOverdueReservations() {
        LocalDate today = LocalDate.now();
        List<Reservation> toExpire = reservationRepository.findByExpiryDateBeforeAndStatus(today, ReservationStatus.ACTIVE);
        toExpire.forEach(r -> r.setStatus(ReservationStatus.EXPIRED));
        return reservationRepository.saveAll(toExpire);
    }

    /**
     *  هر روز ساعت ۲ نیمه‌شب وضعیت رزروهای منقضی را به‌روزرسانی می‌کند
     */
    @Scheduled(cron = "${app.reservation.expire-cron:0 0 2 * * *}")
    public void scheduledExpire() {
        try {
            expireOverdueReservations();
        } catch (Exception e) {
            // Never let a scheduled failure propagate and stop future executions.
            log.error("Scheduled reservation expiry failed: {}", e.getMessage(), e);
        }
    }
}