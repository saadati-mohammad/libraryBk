package ir.iau.library.controller;

import ir.iau.library.dto.CreateInputDeviceRequest;
import ir.iau.library.dto.InputDeviceAuditEventDto;
import ir.iau.library.dto.InputDeviceDto;
import ir.iau.library.dto.InputSourceTypeDto;
import ir.iau.library.dto.ProvisionedInputDeviceDto;
import ir.iau.library.dto.UpdateInputDeviceRequest;
import ir.iau.library.service.InputDeviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/** Administrative API for the pluggable input-source/device registry. */
@RestController
@RequestMapping("/api/admin/input-devices")
public class InputDeviceAdminController {
    private final InputDeviceService service;

    public InputDeviceAdminController(InputDeviceService service) { this.service = service; }

    @GetMapping("/types")
    public List<InputSourceTypeDto> listTypes() { return service.listTypes(); }

    @GetMapping
    public List<InputDeviceDto> list() { return service.list(); }

    @GetMapping("/{id}")
    public InputDeviceDto get(@PathVariable long id) { return service.get(id); }

    @GetMapping("/{id}/audit")
    public List<InputDeviceAuditEventDto> audit(@PathVariable long id) { return service.audit(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProvisionedInputDeviceDto create(@Valid @RequestBody CreateInputDeviceRequest request,
                                             Authentication authentication, HttpServletRequest httpRequest) {
        return service.create(request, authentication.getName(), httpRequest.getRemoteAddr());
    }

    @PutMapping("/{id}")
    public InputDeviceDto update(@PathVariable long id, @Valid @RequestBody UpdateInputDeviceRequest request,
                                 Authentication authentication, HttpServletRequest httpRequest) {
        return service.update(id, request, authentication.getName(), httpRequest.getRemoteAddr());
    }

    @PostMapping("/{id}/enable")
    public InputDeviceDto enable(@PathVariable long id, Authentication authentication, HttpServletRequest request) {
        return service.setEnabled(id, true, authentication.getName(), request.getRemoteAddr());
    }

    @PostMapping("/{id}/disable")
    public InputDeviceDto disable(@PathVariable long id, Authentication authentication, HttpServletRequest request) {
        return service.setEnabled(id, false, authentication.getName(), request.getRemoteAddr());
    }

    @PostMapping("/{id}/rotate-token")
    public ProvisionedInputDeviceDto rotateToken(@PathVariable long id, Authentication authentication,
                                                  HttpServletRequest request) {
        return service.rotateToken(id, authentication.getName(), request.getRemoteAddr());
    }

    @DeleteMapping("/{id}")
    public InputDeviceDto revoke(@PathVariable long id, Authentication authentication, HttpServletRequest request) {
        return service.revoke(id, authentication.getName(), request.getRemoteAddr());
    }
}
