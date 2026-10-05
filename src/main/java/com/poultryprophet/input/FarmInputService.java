package com.poultryprophet.input;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.incubation.IncubationCycle;
import com.poultryprophet.incubation.IncubationService;
import com.poultryprophet.input.dto.CreateFarmInputRequest;
import com.poultryprophet.input.dto.FarmInputLogResponse;
import com.poultryprophet.inventory.InventoryService;
import com.poultryprophet.inventory.InventoryStatus;
import com.poultryprophet.inventory.dto.InventoryUseResult;
import com.poultryprophet.user.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FarmInputService {

    private final FarmInputLogRepository repository;
    private final BatchService batchService;
    private final IncubationService incubationService;
    private final InventoryService inventoryService;

    public FarmInputService(FarmInputLogRepository repository, BatchService batchService,
                            IncubationService incubationService) {
        this(repository, batchService, incubationService, null);
    }

    @Autowired
    public FarmInputService(FarmInputLogRepository repository, BatchService batchService,
                            IncubationService incubationService, InventoryService inventoryService) {
        this.repository = repository;
        this.batchService = batchService;
        this.incubationService = incubationService;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public FarmInputLogResponse create(Long farmId, Long userId, Role role, CreateFarmInputRequest request) {
        if (farmId == null) throw new BadRequestException("Join a farm before recording inputs");

        UUID operationId = request.operationId() == null ? UUID.randomUUID() : request.operationId();
        FarmInputLog existing = repository.findByOperationId(operationId).orElse(null);
        if (existing != null) {
            if (!farmId.equals(existing.getFarmId())
                    || !java.util.Objects.equals(request.batchId(), existing.getBatchId())
                    || request.productType() != existing.getProductType()
                    || !request.brandName().trim().equals(existing.getBrandName())) {
                throw new BadRequestException("operationId has already been used for a different input record");
            }
            return FarmInputLogResponse.from(existing);
        }
        if (request.batchId() == null && request.incubationCycleId() == null) {
            throw new BadRequestException("Link the input to a batch or incubation cycle");
        }
        if (request.batchId() != null) batchService.requireBatch(request.batchId(), farmId);
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
        log.setOperationId(operationId);
        log.setFarmProductId(request.farmProductId());
        log.setAffectedBirdCount(request.affectedBirdCount());
        log.setInventoryStatus(request.farmProductId() == null
                ? InventoryStatus.UNTRACKED : InventoryStatus.PENDING_STOCK_REVIEW);
        FarmInputLog saved = repository.save(log);
        if (inventoryService != null && request.farmProductId() != null && request.quantity() != null) {
            InventoryUseResult result = inventoryService.applyUsage(farmId, userId, request.farmProductId(),
                    java.math.BigDecimal.valueOf(request.quantity()), saved.getRecordedAt(), saved.getBatchId(), saved.getId());
            saved.setInventoryMovementId(result.movementId());
            saved.setInventoryStatus(InventoryStatus.valueOf(result.status()));
            saved = repository.save(saved);
        }
        return FarmInputLogResponse.from(saved);
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
