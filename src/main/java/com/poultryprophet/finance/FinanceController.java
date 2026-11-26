package com.poultryprophet.finance;

import com.poultryprophet.finance.dto.*;
import com.poultryprophet.common.PageResponse;
import com.poultryprophet.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poultryprophet.sync.CommandReceiptService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/financial-transactions")
@PreAuthorize("hasRole('MANAGER')")
public class FinanceController {
    private final FinanceService service;
    private final CommandReceiptService receipts;
    private final ObjectMapper mapper;
    public FinanceController(FinanceService service, CommandReceiptService receipts, ObjectMapper mapper) {
        this.service = service; this.receipts = receipts; this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<FinancialTransactionResponse> create(@Valid @RequestBody CreateFinancialTransactionRequest req,
                                                                @RequestHeader(value = "Idempotency-Key", required = false) UUID operationId,
                                                                @AuthenticationPrincipal CustomUserDetails principal) {
        FinancialTransactionResponse saved;
        if (operationId == null) {
            // Older clients retain their existing behavior until they adopt keyed submissions.
            saved = service.create(principal.getFarmId(), principal.getId(), req);
        } else {
            var identity = new CommandReceiptService.CommandIdentity(operationId, 1, principal.getFarmId(), principal.getId(),
                    "FINANCE_CREATE", "FARM", principal.getFarmId());
            saved = receipts.executeOnce(identity, mapper.valueToTree(req), FinancialTransactionResponse.class,
                    () -> service.create(principal.getFarmId(), principal.getId(), req)).outcome();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
    @PostMapping("/{id}/corrections")
    public FinancialTransactionResponse correctExpense(@PathVariable Long id, @Valid @RequestBody CorrectExpenseRequest request,
                                                       @AuthenticationPrincipal CustomUserDetails principal) {
        var identity = new CommandReceiptService.CommandIdentity(request.operationId(), 1, principal.getFarmId(), principal.getId(),
                "FINANCE_CORRECTION", "FINANCIAL_TRANSACTION", id);
        return receipts.executeOnce(identity, mapper.valueToTree(request), FinancialTransactionResponse.class,
                () -> service.correctExpense(id, principal.getFarmId(), principal.getId(), request)).outcome();
    }

    @GetMapping
    public List<FinancialTransactionResponse> list(@RequestParam(required = false) LocalDate start,
                                                   @RequestParam(required = false) LocalDate end,
                                                   @AuthenticationPrincipal CustomUserDetails principal) {
        return service.list(principal.getFarmId(), start, end);
    }
    @GetMapping("/page")
    public PageResponse<FinancialTransactionResponse> page(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) LocalDate start,
                                                           @RequestParam(required = false) LocalDate end,
                                                           @RequestParam(required = false) Long batchId,
                                                           @RequestParam(required = false) Boolean farmWide,
                                                           @RequestParam(required = false) FinanceTransactionType type,
                                                           @RequestParam(required = false) String category,
                                                           @RequestParam(required = false) String sourceType,
                                                           @RequestParam(required = false) FinanceTransactionStatus status,
                                                           @AuthenticationPrincipal CustomUserDetails principal) {
        return service.page(principal.getFarmId(), start, end, batchId, farmWide, type, category, sourceType, status, page, size);
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
    @GetMapping("/analytics")
    public FinanceAnalyticsResponse analytics(@RequestParam(required = false) Long batchId,
                                              @RequestParam(required = false) LocalDate start,
                                              @RequestParam(required = false) LocalDate end,
                                              @RequestParam(required = false) FinanceTransactionType type,
                                              @RequestParam(required = false) String category,
                                              @RequestParam(required = false) String sourceType,
                                              @RequestParam(required = false) FinanceTransactionStatus status,
                                              @RequestParam(defaultValue = "false") boolean farmWide,
                                              @AuthenticationPrincipal CustomUserDetails principal) {
        return service.analytics(principal.getFarmId(), batchId, start, end, type, farmWide, category, sourceType, status);
    }

}
