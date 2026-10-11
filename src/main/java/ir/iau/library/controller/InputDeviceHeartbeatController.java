package ir.iau.library.controller;

import ir.iau.library.dto.InputDeviceHeartbeatDto;
import ir.iau.library.service.InputDeviceService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public route protected by each device's independent high-entropy bearer credential. */
@RestController
@RequestMapping("/api/device-inputs/{id}/heartbeat")
public class InputDeviceHeartbeatController {
    private final InputDeviceService service;

    public InputDeviceHeartbeatController(InputDeviceService service) { this.service = service; }

    @PostMapping
    public InputDeviceHeartbeatDto heartbeat(@PathVariable long id,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim() : null;
        return service.heartbeat(id, token);
    }
}
