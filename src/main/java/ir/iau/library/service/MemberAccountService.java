package ir.iau.library.service;

import ir.iau.library.dto.MemberLoanDto;
import ir.iau.library.dto.MemberProfileDto;
import ir.iau.library.dto.MemberReservationDto;
import ir.iau.library.dto.UpdateMemberProfileDto;
import ir.iau.library.entity.MemberAccount;
import ir.iau.library.entity.Person;
import ir.iau.library.repository.BookLoanRepository;
import ir.iau.library.repository.MemberAccountRepository;
import ir.iau.library.repository.ReservationRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberAccountService {
    private final MemberAccountRepository accounts;
    private final BookLoanRepository loans;
    private final ReservationRepository reservations;
    private final ReservationService reservationService;

    public MemberProfileDto profile(String username) { return MemberProfileDto.from(member(username).getPerson()); }

    public MemberProfileDto updateProfile(String username, UpdateMemberProfileDto update) {
        Person person = member(username).getPerson();
        person.setPhone(update.phone() == null ? null : update.phone().strip());
        return MemberProfileDto.from(person);
    }

    public Page<MemberLoanDto> loans(String username, int page, int size) {
        Person person = member(username).getPerson();
        return loans.findByPersonIdOrderByLoanDateDescIdDesc(person.getId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)))
                .map(MemberLoanDto::from);
    }

    public List<MemberReservationDto> reservations(String username) {
        Person person = member(username).getPerson();
        return reservations.findTop50ByPersonIdOrderByReservationDateDescIdDesc(person.getId()).stream()
                .map(MemberReservationDto::from).toList();
    }

    public MemberReservationDto reserve(String username, Long bookId) {
        Person person = member(username).getPerson();
        return MemberReservationDto.from(reservationService.createReservation(person.getId(), bookId,
                java.time.LocalDate.now().plusDays(14)));
    }

    public void cancelReservation(String username, Long reservationId) {
        Person person = member(username).getPerson();
        reservationService.cancelReservationForPerson(reservationId, person.getId());
    }

    private MemberAccount member(String username) {
        MemberAccount account = accounts.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new EntityNotFoundException("Member account not found"));
        if (!Boolean.TRUE.equals(account.getPerson().getActive())) {
            throw new AccessDeniedException("Member account is inactive");
        }
        return account;
    }
}
