package ir.iau.library.dto;

import ir.iau.library.entity.Reservation;
import java.time.LocalDate;

public record MemberReservationDto(Long id, Long bookId, String title, String author,
                                   LocalDate reservationDate, LocalDate expiryDate, String status) {
    public static MemberReservationDto from(Reservation reservation) {
        return new MemberReservationDto(reservation.getId(), reservation.getBook().getId(),
                reservation.getBook().getTitle(), reservation.getBook().getAuthor(),
                reservation.getReservationDate(), reservation.getExpiryDate(), reservation.getStatus().name());
    }
}
