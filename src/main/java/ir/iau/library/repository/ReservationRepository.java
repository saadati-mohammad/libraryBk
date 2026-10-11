package ir.iau.library.repository;

import ir.iau.library.entity.Book;
import ir.iau.library.entity.Person;
import ir.iau.library.entity.Reservation;
import ir.iau.library.entity.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @EntityGraph(attributePaths = "book")
    List<Reservation> findTop50ByPersonIdOrderByReservationDateDescIdDesc(Long personId);
    List<Reservation> findByStatusOrderByReservationDateAscIdAsc(ReservationStatus status);
    List<Reservation> findByExpiryDateBeforeAndStatus(LocalDate date, ReservationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    /** True if the person already holds an ACTIVE reservation for this book. */
    Optional<Reservation> findFirstByPersonAndBookAndStatus(Person person, Book book, ReservationStatus status);

    /** Number of reservations in a given state (used by the dashboard). */
    long countByStatus(ReservationStatus status);
}
