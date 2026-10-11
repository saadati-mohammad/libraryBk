package ir.iau.library.controller;

import ir.iau.library.dto.CreateStaffAccountRequest;
import ir.iau.library.dto.StaffAccountDto;
import ir.iau.library.dto.UpdateStaffAccountRequest;
import ir.iau.library.service.StaffAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/staff-accounts")
public class StaffAccountController {
    private final StaffAccountService service;

    public StaffAccountController(StaffAccountService service) { this.service = service; }

    @GetMapping
    public List<StaffAccountDto> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffAccountDto create(@Valid @RequestBody CreateStaffAccountRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public StaffAccountDto update(@PathVariable long id, @Valid @RequestBody UpdateStaffAccountRequest request,
                                  Authentication authentication) {
        return service.update(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    public StaffAccountDto deactivate(@PathVariable long id, Authentication authentication) {
        return service.deactivate(id, authentication.getName());
    }

    @PostMapping("/{id}/reactivate")
    public StaffAccountDto reactivate(@PathVariable long id) { return service.reactivate(id); }
}
