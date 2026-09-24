package com.poultryprophet.finance;

import com.poultryprophet.finance.dto.*;
import com.poultryprophet.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/financial-transactions")
@PreAuthorize("hasRole('MANAGER')")
public class FinanceController {
    private final FinanceService service;
    public FinanceController(FinanceService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<FinancialTransactionResponse> create(@Valid @RequestBody CreateFinancialTransactionRequest req,
                                                                @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal.getFarmId(), principal.getId(), req));
    }
    @GetMapping
    public List<FinancialTransactionResponse> list(@RequestParam(required = false) LocalDate start,
                                                   @RequestParam(required = false) LocalDate end,
                                                   @AuthenticationPrincipal CustomUserDetails principal) {
        return service.list(principal.getFarmId(), start, end);
    }
    @PostMapping("/{id}/void")
    public FinancialTransactionResponse voidTransaction(@PathVariable Long id,
                                                        @RequestBody(required = false) VoidFinancialTransactionRequest req,
                                                        @AuthenticationPrincipal CustomUserDetails principal) {
        return service.voidTransaction(id, principal.getFarmId(), req == null ? null : req.reason());
    }
    @GetMapping("/summary")
    public FinanceSummaryResponse summary(@RequestParam(required = false) LocalDate start,
                                          @RequestParam(required = false) LocalDate end,
                                          @AuthenticationPrincipal CustomUserDetails principal) {
        return service.summary(principal.getFarmId(), start, end);
    }
}
