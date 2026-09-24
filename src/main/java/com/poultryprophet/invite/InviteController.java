package com.poultryprophet.invite;

import com.poultryprophet.auth.AuthService;
import com.poultryprophet.auth.dto.AuthResponse;
import com.poultryprophet.security.CustomUserDetails;
import com.poultryprophet.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/invites")
public class InviteController {

    private final InviteService inviteService;
    private final AuthService authService;

    public InviteController(InviteService inviteService,
                            AuthService authService) {
        this.inviteService = inviteService;
        this.authService = authService;
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<InviteResponse> create(@Valid @RequestBody CreateInviteRequest request,
                                                 @AuthenticationPrincipal CustomUserDetails principal) {
        InviteResponse response = inviteService.createHandlerInvite(request.email(), principal.getFarmId(), request.expiresInDays());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('HANDLER')")
    public ResponseEntity<List<InviteResponse>> pending(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(
                inviteService.pendingInvitesFor(principal.getUsername(), principal.getFarmId()));
    }

    @PostMapping("/{token}/decline")
    @PreAuthorize("hasRole('HANDLER')")
    public ResponseEntity<Void> decline(@PathVariable String token,
                                        @AuthenticationPrincipal CustomUserDetails principal) {
        inviteService.declineInvite(token, principal.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{token}/accept")
    @PreAuthorize("hasRole('HANDLER')")
    public ResponseEntity<AuthResponse> accept(@PathVariable String token,
                                               @AuthenticationPrincipal CustomUserDetails principal) {
        User user = inviteService.acceptInvite(token, principal.getUsername());
        return ResponseEntity.ok(authService.toResponse(user));
    }
}
