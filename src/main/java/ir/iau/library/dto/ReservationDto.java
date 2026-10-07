package ir.iau.library.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * Read-model for {@code Reservation}.
 *
 * <p>Replaces the raw-entity response. Keeps the same JSON shape the frontend already
 * relies on ({@code person}/{@code book} objects plus the two dates and status) but
 * exposes only the DTOs of the associated entities rather than their full graphs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationDto {
    private Long id;
    private LocalDate reservationDate;
    private LocalDate expiryDate;
    private String status;
    private PersonDto person;
    private BookDto book;
}
