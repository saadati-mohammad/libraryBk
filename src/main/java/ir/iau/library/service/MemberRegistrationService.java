package ir.iau.library.service;

import ir.iau.library.dto.MemberProfileDto;
import ir.iau.library.dto.RegisterMemberRequestDto;
import ir.iau.library.entity.MemberAccount;
import ir.iau.library.entity.Person;
import ir.iau.library.repository.MemberAccountRepository;
import ir.iau.library.repository.PersonRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MemberRegistrationService {
    private final PersonRepository people;
    private final MemberAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    @Value("${app.registration.enabled:true}")
    private boolean registrationEnabled;

    @Transactional
    public MemberProfileDto register(RegisterMemberRequestDto request) {
        if (!registrationEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Member registration is not available");
        }
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        String nationalId = request.nationalId().strip();
        if (people.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("این ایمیل قبلاً ثبت شده است.");
        }
        if (people.existsByNationalId(nationalId)) {
            throw new IllegalStateException("این کد ملی قبلاً ثبت شده است.");
        }
        Person person = Person.builder()
                .firstName(request.firstName().strip())
                .lastName(request.lastName().strip())
                .email(email)
                .nationalId(nationalId)
                .phone(request.phone() == null ? null : request.phone().strip())
                .membershipDate(LocalDate.now())
                .membershipType("STANDARD")
                .active(true)
                .build();
        person = people.saveAndFlush(person);

        MemberAccount account = new MemberAccount();
        account.setUsername(email);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setPerson(person);
        accounts.save(account);
        return MemberProfileDto.from(person);
    }
}
