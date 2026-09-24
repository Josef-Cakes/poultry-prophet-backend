package com.poultryprophet.input;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.incubation.IncubationCycle;
import com.poultryprophet.incubation.IncubationService;
import com.poultryprophet.batch.BatchHandlerAssignmentRepository;
import com.poultryprophet.input.dto.CreateFarmInputRequest;
import com.poultryprophet.input.dto.FarmInputLogResponse;
import com.poultryprophet.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class FarmInputService {

    private final FarmInputLogRepository repository;
    private final BatchService batchService;
    private final IncubationService incubationService;
    private final BatchHandlerAssignmentRepository assignmentRepository;

    public FarmInputService(FarmInputLogRepository repository, BatchService batchService,
                            IncubationService incubationService,
                            BatchHandlerAssignmentRepository assignmentRepository) {
        this.repository = repository;
        this.batchService = batchService;
        this.incubationService = incubationService;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional
    public FarmInputLogResponse create(Long farmId, Long userId, Role role, CreateFarmInputRequest request) {
        if (farmId == null) throw new BadRequestException("Join a farm before recording inputs");
        if (request.batchId() == null && request.incubationCycleId() == null) {
            throw new BadRequestException("Link the input to a batch or incubation cycle");
        }
        if (request.batchId() != null) batchService.requireBatch(request.batchId(), farmId);
        if (role == Role.HANDLER && request.batchId() != null
                && !assignmentRepository.existsByBatchIdAndUserId(request.batchId(), userId)) {
            throw new BadRequestException("You are not assigned to this batch");
        }
        if (request.incubationCycleId() != null) incubationService.require(request.incubationCycleId(), farmId);
        if (request.productType() == InputProductType.MEDICINE || request.productType() == InputProductType.VACCINE) {
            if (request.purpose() == null || request.purpose().isBlank()) {
                throw new BadRequestException("Purpose is required for medicine and vaccine records");
            }
        }
        FarmInputLog log = new FarmInputLog();
        log.setFarmId(farmId);
        log.setBatchId(request.batchId());
        log.setIncubationCycleId(request.incubationCycleId());
        log.setRecordedAt(request.recordedAt() == null ? Instant.now() : request.recordedAt());
        log.setProductType(request.productType());
        log.setBrandName(request.brandName().trim());
        log.setProductName(trimToNull(request.productName()));
        log.setQuantity(request.quantity());
        log.setUnit(trimToNull(request.unit()));
        log.setRoute(trimToNull(request.route()));
        log.setPurpose(trimToNull(request.purpose()));
        log.setNotes(trimToNull(request.notes()));
        log.setRecordedBy(userId);
        return FarmInputLogResponse.from(repository.save(log));
    }

    @Transactional(readOnly = true)
    public List<FarmInputLogResponse> list(Long farmId, Long batchId, Long cycleId) {
        if (farmId == null) throw new BadRequestException("Join a farm before viewing inputs");
        List<FarmInputLog> logs;
        if (batchId != null) {
            batchService.requireBatch(batchId, farmId);
            logs = repository.findByFarmIdAndBatchIdOrderByRecordedAtDesc(farmId, batchId);
        } else if (cycleId != null) {
            incubationService.require(cycleId, farmId);
            logs = repository.findByFarmIdAndIncubationCycleIdOrderByRecordedAtDesc(farmId, cycleId);
        } else {
            logs = repository.findByFarmIdOrderByRecordedAtDesc(farmId);
        }
        return logs.stream().map(FarmInputLogResponse::from).toList();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
