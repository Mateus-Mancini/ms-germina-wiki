package com.wikigerminare.auth;

import com.wikigerminare.auth.dto.RegisterRequest;
import com.wikigerminare.users.dto.OwnUserProfileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<OwnUserProfileResponse> register(@RequestBody RegisterRequest request) {
        OwnUserProfileResponse response = registrationService.register(request);
        return ResponseEntity.created(URI.create("/api/users/" + response.id())).body(response);
    }
}
