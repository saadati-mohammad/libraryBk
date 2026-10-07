package ir.iau.library.repository;

import ir.iau.library.entity.Book;
import ir.iau.library.entity.Person;
import ir.iau.library.entity.Reservation;
import ir.iau.library.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByStatus(ReservationStatus status);
    List<Reservation> findByExpiryDateBeforeAndStatus(LocalDate date, ReservationStatus status);

    /** True if the person already holds an ACTIVE reservation for this book. */
    Optional<Reservation> findFirstByPersonAndBookAndStatus(Person person, Book book, ReservationStatus status);
}
