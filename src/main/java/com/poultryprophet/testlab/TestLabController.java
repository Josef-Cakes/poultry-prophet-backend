package com.poultryprophet.testlab;

import com.poultryprophet.security.CustomUserDetails;
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

@RestController
@RequestMapping("/api/test-lab")
@PreAuthorize("hasRole('MANAGER')")
public class TestLabController {

    private final TestLabSafetyService safety;
    private final TestLabService service;

    public TestLabController(TestLabSafetyService safety, TestLabService service) {
        this.safety = safety;
        this.service = service;
    }

    @GetMapping("/status")
    public TestLabStatusResponse status() {
        return safety.status();
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateTestBatchResponse> generate(
            @Valid @RequestBody GenerateTestBatchRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.generate(request, principal.getFarmId(), principal.getId()));
    }
}
