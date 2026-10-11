package ir.iau.library.service;

import ir.iau.library.dto.RegisterMemberRequestDto;
import ir.iau.library.repository.MemberAccountRepository;
import ir.iau.library.repository.PersonRepository;
import ir.iau.library.repository.StaffAccountRepository;
import ir.iau.library.security.AdminCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MemberRegistrationServiceTest {
    @Test
    void disabledRegistrationFailsClosedWithoutTouchingMemberData() {
        PersonRepository people = mock(PersonRepository.class);
        MemberAccountRepository accounts = mock(MemberAccountRepository.class);
        StaffAccountRepository staffAccounts = mock(StaffAccountRepository.class);
        AdminCredentials adminCredentials = mock(AdminCredentials.class);
        var service = new MemberRegistrationService(people, accounts, staffAccounts, adminCredentials,
                mock(org.springframework.security.crypto.password.PasswordEncoder.class));
        ReflectionTestUtils.setField(service, "registrationEnabled", false);
        var request = new RegisterMemberRequestDto("A", "B", "a@example.test", "0123456789", null,
                "long-test-password-123");

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503 SERVICE_UNAVAILABLE");
        verifyNoInteractions(people, accounts, staffAccounts, adminCredentials);
    }
}
