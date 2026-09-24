package com.poultryprophet.incubation;

import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.incubation.dto.CompleteIncubationRequest;
import com.poultryprophet.incubation.dto.CreateIncubationCycleRequest;
import com.poultryprophet.incubation.dto.IncubationCycleResponse;
import com.poultryprophet.incubation.dto.UpdateIncubationCycleRequest;
import com.poultryprophet.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/incubation-cycles")
public class IncubationController {

    private final IncubationService service;

    public IncubationController(IncubationService service) {
        this.service = service;
    }

    @PostMapping
    // Farm membership is validated by the service; both farm roles may record an egg load.
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IncubationCycleResponse> create(
            @Valid @RequestBody CreateIncubationCycleRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(principal.getFarmId(), request));
    }

    @GetMapping
    public List<IncubationCycleResponse> list(@AuthenticationPrincipal CustomUserDetails principal) {
        return service.list(principal.getFarmId());
    }

    @GetMapping("/{id}")
    public IncubationCycleResponse get(@PathVariable Long id,
                                       @AuthenticationPrincipal CustomUserDetails principal) {
        return IncubationCycleResponse.from(service.require(id, principal.getFarmId()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public IncubationCycleResponse update(@PathVariable Long id,
                                          @RequestBody UpdateIncubationCycleRequest request,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        return service.update(id, principal.getFarmId(), request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('MANAGER')")
    public IncubationCycleResponse complete(@PathVariable Long id,
                                            @Valid @RequestBody CompleteIncubationRequest request,
                                            @AuthenticationPrincipal CustomUserDetails principal) {
        return service.complete(id, principal.getFarmId(), principal.getId(), request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('MANAGER')")
    public IncubationCycleResponse cancel(@PathVariable Long id,
                                          @RequestBody(required = false) java.util.Map<String, String> request,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        String reason = request == null ? null : request.get("reason");
        return service.cancel(id, principal.getFarmId(), principal.getId(), reason);
    }

    @PostMapping("/{id}/create-batch")
    @PreAuthorize("hasRole('MANAGER')")
    public BatchResponse createBatch(@PathVariable Long id,
                                     @AuthenticationPrincipal CustomUserDetails principal) {
        return service.createBatch(id, principal.getFarmId());
    }
}
