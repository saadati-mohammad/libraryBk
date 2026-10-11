package ir.iau.library.controller;

import ir.iau.library.dto.LoginRequestDto;
import ir.iau.library.dto.LoginResponseDto;
import ir.iau.library.dto.MemberProfileDto;
import ir.iau.library.dto.RegisterMemberRequestDto;
import ir.iau.library.service.MemberRegistrationService;
import ir.iau.library.service.PasswordResetService;
import ir.iau.library.dto.ForgotPasswordRequest;
import ir.iau.library.dto.ResetPasswordRequest;
import ir.iau.library.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoint. The only public member of the {@code /api} surface; every other
 * controller requires a valid bearer token (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final MemberRegistrationService memberRegistrationService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                          MemberRegistrationService memberRegistrationService,
                          PasswordResetService passwordResetService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.memberRegistrationService = memberRegistrationService;
        this.passwordResetService = passwordResetService;
    }

    @org.springframework.web.bind.annotation.PostMapping("/register")
    public ResponseEntity<MemberProfileDto> register(@Valid @RequestBody RegisterMemberRequestDto request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(memberRegistrationService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException ex) {
            // Do not distinguish unknown-user from wrong-password.
            throw new BadCredentialsException("نام کاربری یا رمز عبور نادرست است");
        }

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse("ADMIN");

        String token = jwtService.generateToken(authentication.getName(), role);
        return ResponseEntity.ok(new LoginResponseDto(
                token, authentication.getName(), role, jwtService.getExpirationSeconds()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ir.iau.library.dto.ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.accepted().body(ir.iau.library.dto.ApiResponse.success(
                null, passwordResetService.request(request.email())));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ir.iau.library.dto.ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request.token(), request.password());
        return ResponseEntity.ok(ir.iau.library.dto.ApiResponse.success(null,
                "رمز عبور با موفقیت تغییر کرد. اکنون با رمز جدید وارد شوید."));
    }
}
