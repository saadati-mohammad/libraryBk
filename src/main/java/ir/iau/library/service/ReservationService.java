package ir.iau.library.service;

import ir.iau.library.entity.Book;
import ir.iau.library.entity.Person;
import ir.iau.library.entity.Reservation;
import ir.iau.library.entity.ReservationStatus;
import ir.iau.library.dto.CreateLoanRequestDto;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import ir.iau.library.repository.ReservationRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.Clock;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final PersonRepository personRepository;
    private final BookRepository bookRepository;
    private final BookLoanService bookLoanService;
    @org.springframework.beans.factory.annotation.Autowired
    private Clock clock = Clock.systemDefaultZone();

    public Reservation createReservation(Long personId, Long bookId, LocalDate expiryDate) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new EntityNotFoundException("Person not found"));
        // Serialize duplicate checks for the same title to close the create/create race.
        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found"));

        if (expiryDate != null && expiryDate.isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("expiryDate cannot be in the past");
        }

        // A soft-deleted (inactive) person or book must not accept new reservations.
        if (!Boolean.TRUE.equals(person.getActive())) {
            throw new IllegalStateException("Person is not active and cannot reserve books.");
        }
        if (!Boolean.TRUE.equals(book.getActive())) {
            throw new IllegalStateException("Book is not active and cannot be reserved.");
        }

        // Guard against duplicate active reservations for the same person/book.
        reservationRepository.findFirstByPersonAndBookAndStatus(person, book, ReservationStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new IllegalStateException("An active reservation already exists for this person and book.");
                });

        Reservation reservation = Reservation.builder()
                .person(person)
                .book(book)
                .reservationDate(LocalDate.now(clock))
                .expiryDate(expiryDate)
                .status(ReservationStatus.ACTIVE)
                .build();

        return reservationRepository.save(reservation);
    }

    public Reservation fulfillReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found"));

        // Only an ACTIVE reservation can be fulfilled; reject double-fulfilment.
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only an active reservation can be fulfilled (current status: "
                    + reservation.getStatus() + ").");
        }

        // A fulfilled reservation is an actual checkout. The loan service applies member,
        // book and copy-capacity rules in this same transaction; failures keep it active.
        CreateLoanRequestDto loanRequest = new CreateLoanRequestDto();
        loanRequest.setPersonId(reservation.getPerson().getId());
        loanRequest.setBookId(reservation.getBook().getId());
        bookLoanService.createLoan(loanRequest);

        reservation.setStatus(ReservationStatus.FULFILLED);
        return reservationRepository.save(reservation);
    }

    public Reservation cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only an active reservation can be cancelled.");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        return reservationRepository.save(reservation);
    }

    /** Member-scoped cancellation; ownership is checked after locking the row. */
    public Reservation cancelReservationForPerson(Long reservationId, Long personId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found"));
        if (!reservation.getPerson().getId().equals(personId)) {
            throw new AccessDeniedException("Reservation belongs to another member");
        }
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only an active reservation can be cancelled.");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        return reservationRepository.save(reservation);
    }

    public List<Reservation> getActiveReservations() {
        return reservationRepository.findByStatusOrderByReservationDateAscIdAsc(ReservationStatus.ACTIVE);
    }

    public List<Reservation> expireOverdueReservations() {
        LocalDate today = LocalDate.now(clock);
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
