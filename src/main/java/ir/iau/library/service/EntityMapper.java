package ir.iau.library.service;

import ir.iau.library.dto.BookDto;
import ir.iau.library.dto.PersonDto;
import ir.iau.library.dto.ReservationDto;
import ir.iau.library.entity.Book;
import ir.iau.library.entity.Person;
import ir.iau.library.entity.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Converts persistence entities to their read-model DTOs.
 *
 * <p>Keeps the JPA entity (and its blobs/associations) out of the API surface while
 * preserving the field-for-field JSON contract the frontend depends on.
 */
@Component
public class EntityMapper {

    public BookDto toDto(Book book) {
        if (book == null) return null;
        return BookDto.builder()
                .id(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .translator(book.getTranslator())
                .publisher(book.getPublisher())
                .isbn10(book.getIsbn10())
                .isbn13(book.getIsbn13())
                .description(book.getDescription())
                .deweyDecimal(book.getDeweyDecimal())
                .congressClassification(book.getCongressClassification())
                .subject(book.getSubject())
                .summary(book.getSummary())
                .publicationDate(book.getPublicationDate())
                .pageCount(book.getPageCount())
                .language(book.getLanguage())
                .edition(book.getEdition())
                .copyCount(book.getCopyCount())
                .librarySection(book.getLibrarySection())
                .shelfCode(book.getShelfCode())
                .rowNumbers(book.getRowNumbers())
                .columnNumber(book.getColumnNumber())
                .positionNote(book.getPositionNote())
                .active(book.getActive())
                .build();
    }

    public Page<BookDto> toBookDtoPage(Page<Book> page) {
        return page.map(this::toDto);
    }

    public PersonDto toDto(Person person) {
        if (person == null) return null;
        return PersonDto.builder()
                .id(person.getId())
                .firstName(person.getFirstName())
                .lastName(person.getLastName())
                .email(person.getEmail())
                .nationalId(person.getNationalId())
                .phone(person.getPhone())
                .birthDate(person.getBirthDate())
                .membershipDate(person.getMembershipDate())
                .membershipType(person.getMembershipType())
                .address(person.getAddress())
                .notes(person.getNotes())
                .active(person.getActive())
                .build();
    }

    public Page<PersonDto> toPersonDtoPage(Page<Person> page) {
        return page.map(this::toDto);
    }

    public ReservationDto toDto(Reservation reservation) {
        if (reservation == null) return null;
        return ReservationDto.builder()
                .id(reservation.getId())
                .reservationDate(reservation.getReservationDate())
                .expiryDate(reservation.getExpiryDate())
                .status(reservation.getStatus() == null ? null : reservation.getStatus().name())
                .person(toDto(reservation.getPerson()))
                .book(toDto(reservation.getBook()))
                .build();
    }
}
