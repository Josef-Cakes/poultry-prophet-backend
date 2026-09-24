package com.poultryprophet.user;

import com.poultryprophet.auth.AuthService;
import com.poultryprophet.auth.dto.AuthResponse;
import com.poultryprophet.security.CustomUserDetails;
import com.poultryprophet.user.dto.ChangePasswordRequest;
import com.poultryprophet.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own account: display name, login email and password. */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final UserService userService;
    private final AuthService authService;

    public AccountController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PutMapping("/profile")
    public AuthResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request,
                                      @AuthenticationPrincipal CustomUserDetails principal) {
        User updated = userService.updateProfile(principal.getId(), request.fullName(), request.email());
        // Email is the JWT subject, so reissue a token to keep the session valid
        // after an email change.
        return authService.toResponse(updated);
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        userService.changePassword(principal.getId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
