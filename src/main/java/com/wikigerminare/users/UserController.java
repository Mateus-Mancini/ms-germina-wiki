package com.wikigerminare.users;

import com.wikigerminare.users.dto.OwnUserProfileResponse;
import com.wikigerminare.users.dto.PublicUserProfileResponse;
import com.wikigerminare.users.dto.UpdateUserProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<OwnUserProfileResponse> getOwnProfile() {
        return ResponseEntity.ok(userService.getOwnProfile());
    }

    @PatchMapping("/me")
    public ResponseEntity<OwnUserProfileResponse> updateOwnProfile(
            @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        return ResponseEntity.ok(userService.updateOwnProfile(request));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<PublicUserProfileResponse> getPublicProfile(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(userService.getPublicProfile(userId));
    }
}
