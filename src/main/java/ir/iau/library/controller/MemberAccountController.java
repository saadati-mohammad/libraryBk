package ir.iau.library.controller;

import ir.iau.library.dto.CreateMemberReservationDto;
import ir.iau.library.dto.MemberLoanDto;
import ir.iau.library.dto.MemberProfileDto;
import ir.iau.library.dto.MemberReservationDto;
import ir.iau.library.dto.UpdateMemberProfileDto;
import ir.iau.library.service.MemberAccountService;
import ir.iau.library.security.AccessControl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class MemberAccountController {
    private final MemberAccountService accountService;

    @GetMapping("/me")
    public MemberProfileDto profile(Principal principal) {
        return accountService.profile(AccessControl.requireUsername(principal));
    }

    @PatchMapping("/profile")
    public MemberProfileDto updateProfile(Principal principal,
                                          @Valid @RequestBody UpdateMemberProfileDto update) {
        return accountService.updateProfile(AccessControl.requireUsername(principal), update);
    }

    @GetMapping("/loans")
    public Page<MemberLoanDto> loans(Principal principal,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size) {
        return accountService.loans(AccessControl.requireUsername(principal), page, size);
    }

    @GetMapping("/reservations")
    public List<MemberReservationDto> reservations(Principal principal) {
        return accountService.reservations(AccessControl.requireUsername(principal));
    }

    @PostMapping("/reservations")
    public ResponseEntity<MemberReservationDto> reserve(Principal principal,
            @Valid @RequestBody CreateMemberReservationDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.reserve(AccessControl.requireUsername(principal), request.bookId()));
    }

    @DeleteMapping("/reservations/{id}")
    public ResponseEntity<Void> cancelReservation(Principal principal, @PathVariable Long id) {
        accountService.cancelReservation(AccessControl.requireUsername(principal), id);
        return ResponseEntity.noContent().build();
    }
}
