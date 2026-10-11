package ir.iau.library.service;

import ir.iau.library.entity.Book;
import ir.iau.library.entity.Person;
import ir.iau.library.entity.Reservation;
import ir.iau.library.entity.ReservationStatus;
import ir.iau.library.dto.BookLoanDto;
import ir.iau.library.repository.BookRepository;
import ir.iau.library.repository.PersonRepository;
import ir.iau.library.repository.ReservationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Business rules for reservations. Deactivation (soft delete) must be honoured on both
 * sides: a deactivated member may not reserve, and a soft-deleted book may not be
 * reserved — otherwise the delete flags would only hide rows from the UI.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookLoanService bookLoanService;

    @InjectMocks
    private ReservationService service;

    private Person activePerson;
    private Book activeBook;

    @BeforeEach
    void setUp() {
        activePerson = Person.builder()
                .id(1L)
                .firstName("Ada")
                .lastName("Lovelace")
                .email("ada@example.com")
                .nationalId("1234567890")
                .active(true)
                .build();
        activeBook = Book.builder()
                .id(2L)
                .title("Notes on the Analytical Engine")
                .active(true)
                .build();
    }

    @Test
    void createReservation_savesActiveReservation() {
        LocalDate expiry = LocalDate.now().plusDays(7);
        when(personRepository.findById(1L)).thenReturn(Optional.of(activePerson));
        when(bookRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(activeBook));
        when(reservationRepository.findFirstByPersonAndBookAndStatus(
                activePerson, activeBook, ReservationStatus.ACTIVE)).thenReturn(Optional.empty());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        Reservation saved = service.createReservation(1L, 2L, expiry);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(captor.getValue().getReservationDate()).isEqualTo(LocalDate.now());
        assertThat(captor.getValue().getExpiryDate()).isEqualTo(expiry);
        assertThat(saved.getPerson()).isEqualTo(activePerson);
        assertThat(saved.getBook()).isEqualTo(activeBook);
    }

    @Test
    void createReservation_rejectsDeactivatedPerson() {
        Person inactive = Person.builder()
                .id(1L)
                .email("ada@example.com")
                .nationalId("1234567890")
                .active(false)
                .build();

        when(personRepository.findById(1L)).thenReturn(Optional.of(inactive));
        when(bookRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(activeBook));

        assertThatThrownBy(() -> service.createReservation(1L, 2L, LocalDate.now().plusDays(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Person is not active");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_rejectsSoftDeletedBook() {
        Book deleted = Book.builder().id(2L).title("Deleted").active(false).build();

        when(personRepository.findById(1L)).thenReturn(Optional.of(activePerson));
        when(bookRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.createReservation(1L, 2L, LocalDate.now().plusDays(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Book is not active");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_rejectsPastExpiryDate() {
        when(personRepository.findById(1L)).thenReturn(Optional.of(activePerson));
        when(bookRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(activeBook));

        assertThatThrownBy(() -> service.createReservation(1L, 2L, LocalDate.now().minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_rejectsDuplicateActiveReservation() {
        when(personRepository.findById(1L)).thenReturn(Optional.of(activePerson));
        when(bookRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(activeBook));
        when(reservationRepository.findFirstByPersonAndBookAndStatus(
                activePerson, activeBook, ReservationStatus.ACTIVE))
                .thenReturn(Optional.of(Reservation.builder().id(5L).build()));

        assertThatThrownBy(() -> service.createReservation(1L, 2L, LocalDate.now().plusDays(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_throwsWhenPersonMissing() {
        when(personRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createReservation(99L, 2L, LocalDate.now().plusDays(1)))
                .isInstanceOf(EntityNotFoundException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void fulfillReservation_rejectsNonActiveReservation() {
        Reservation fulfilled = Reservation.builder().id(5L).status(ReservationStatus.FULFILLED).build();
        when(reservationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(fulfilled));

        assertThatThrownBy(() -> service.fulfillReservation(5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only an active reservation");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void fulfillReservationCreatesLoanAndMarksReservationFulfilled() {
        Reservation active = Reservation.builder()
                .id(5L).status(ReservationStatus.ACTIVE)
                .person(activePerson).book(activeBook).build();
        when(reservationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(active));
        when(bookLoanService.createLoan(any())).thenReturn(BookLoanDto.builder().id(9L).build());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        Reservation fulfilled = service.fulfillReservation(5L);

        assertThat(fulfilled.getStatus()).isEqualTo(ReservationStatus.FULFILLED);
        verify(bookLoanService).createLoan(any());
        verify(reservationRepository).save(active);
    }

    @Test
    void cancelReservationMarksOnlyActiveReservationsCancelled() {
        Reservation active = Reservation.builder().id(5L).status(ReservationStatus.ACTIVE).build();
        when(reservationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(active));
        when(reservationRepository.save(active)).thenReturn(active);

        Reservation cancelled = service.cancelReservation(5L);

        assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(reservationRepository).save(active);
    }

    @Test
    void cancelReservationRejectsNonActiveReservation() {
        Reservation fulfilled = Reservation.builder().id(5L).status(ReservationStatus.FULFILLED).build();
        when(reservationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(fulfilled));

        assertThatThrownBy(() -> service.cancelReservation(5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only an active reservation");
        verify(reservationRepository, never()).save(any());
    }
}
