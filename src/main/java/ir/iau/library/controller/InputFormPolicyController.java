package ir.iau.library.controller;

import ir.iau.library.dto.InputFormPolicyDto;
import ir.iau.library.dto.SaveInputFormPolicyRequest;
import ir.iau.library.service.InputFormPolicyService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/input-policies")
public class InputFormPolicyController {
    private final InputFormPolicyService service;

    public InputFormPolicyController(InputFormPolicyService service) { this.service = service; }

    @GetMapping("/{formKey}")
    public InputFormPolicyDto get(@PathVariable String formKey, @RequestParam(required = false) String station,
                                  Authentication authentication) {
        return service.effective(formKey, station, authentication.getName());
    }

    @PutMapping("/{formKey}")
    public InputFormPolicyDto save(@PathVariable String formKey, @Valid @RequestBody SaveInputFormPolicyRequest request,
                                   Authentication authentication) {
        return service.savePersonal(formKey, request, authentication.getName());
    }
}
