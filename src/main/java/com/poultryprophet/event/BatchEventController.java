package com.poultryprophet.event;

import com.poultryprophet.event.dto.BatchEventResponse;
import com.poultryprophet.event.dto.BatchEventReportResponse;
import com.poultryprophet.event.dto.CreateBatchEventRequest;
import com.poultryprophet.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/batches/{batchId}/events")
public class BatchEventController {

    private final BatchEventService eventService;

    public BatchEventController(BatchEventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<BatchEventResponse> create(
            @PathVariable Long batchId,
            @Valid @RequestBody CreateBatchEventRequest req,
            @AuthenticationPrincipal CustomUserDetails principal) {
        BatchEventResponse response = eventService.create(
                batchId, principal.getFarmId(), principal.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<BatchEventResponse> recent(
            @PathVariable Long batchId,
            @RequestParam(defaultValue = "30") int limit,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return eventService.recent(batchId, principal.getFarmId(), limit);
    }

    @GetMapping("/page")
    public com.poultryprophet.common.PageResponse<BatchEventResponse> page(
            @PathVariable Long batchId, @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end, @RequestParam(required = false) EventType type,
            @RequestParam(required = false) Long handlerId, @RequestParam(required = false) String concernLevel,
            @RequestParam(defaultValue = "false") boolean populationOnly,
            @RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal CustomUserDetails principal) {
        return eventService.page(batchId, principal.getFarmId(), start, end, type, handlerId, concernLevel,
                populationOnly, search, page, size);
    }

    @GetMapping("/report")
    public BatchEventReportResponse report(
            @PathVariable Long batchId, @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end, @RequestParam(required = false) EventType type,
            @RequestParam(required = false) Long handlerId, @RequestParam(required = false) String concernLevel,
            @RequestParam(defaultValue = "false") boolean populationOnly,
            @RequestParam(required = false) String search, @AuthenticationPrincipal CustomUserDetails principal) {
        return eventService.report(batchId, principal.getFarmId(), start, end, type, handlerId, concernLevel,
                populationOnly, search);
    }
}
