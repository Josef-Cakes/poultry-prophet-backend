package com.poultryprophet.user;

import com.poultryprophet.security.CustomUserDetails;
import com.poultryprophet.user.dto.CreateHandlerRequest;
import com.poultryprophet.user.dto.HandlerResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/handlers")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public List<HandlerResponse> listHandlers(@AuthenticationPrincipal CustomUserDetails principal) {
        return userService.listHandlers(principal.getFarmId());
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<HandlerResponse> createHandler(@Valid @RequestBody CreateHandlerRequest request,
                                                         @AuthenticationPrincipal CustomUserDetails principal) {
        HandlerResponse response = userService.createHandler(request, principal.getFarmId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
