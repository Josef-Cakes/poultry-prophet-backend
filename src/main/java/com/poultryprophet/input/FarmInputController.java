package com.poultryprophet.input;

import com.poultryprophet.input.dto.CreateFarmInputRequest;
import com.poultryprophet.input.dto.FarmInputLogResponse;
import com.poultryprophet.common.PageResponse;
import com.poultryprophet.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inputs")
public class FarmInputController {

    private final FarmInputService service;

    public FarmInputController(FarmInputService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<FarmInputLogResponse> create(@Valid @RequestBody CreateFarmInputRequest request,
                                                       @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(
                principal.getFarmId(), principal.getId(), principal.getUser().getRole(), request));
    }

    @GetMapping
    public List<FarmInputLogResponse> list(@RequestParam(required = false) Long batchId,
                                           @RequestParam(required = false) Long incubationCycleId,
                                           @AuthenticationPrincipal CustomUserDetails principal) {
        return service.list(principal.getFarmId(), batchId, incubationCycleId);
    }

    @GetMapping("/page")
    public PageResponse<FarmInputLogResponse> page(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) Long batchId,
                                                  @RequestParam(required = false) Long incubationCycleId,
                                                  @RequestParam(required = false) Boolean farmWide,
                                                  @RequestParam(required = false) InputProductType productType,
                                                  @AuthenticationPrincipal CustomUserDetails principal) {
        return service.page(principal.getFarmId(), batchId, incubationCycleId, farmWide, productType, page, size);
    }
}
