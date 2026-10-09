package com.poultryprophet.input;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.incubation.IncubationCycle;
import com.poultryprophet.incubation.IncubationService;
import com.poultryprophet.input.dto.CreateFarmInputRequest;
import com.poultryprophet.input.dto.FarmInputLogResponse;
import com.poultryprophet.inventory.InventoryService;
import com.poultryprophet.inventory.InventoryStatus;
import com.poultryprophet.inventory.dto.InventoryUseResult;
import com.poultryprophet.user.Role;
import com.poultryprophet.vaccination.VaccinationInputLinker;
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
    private final VaccinationInputLinker vaccinationInputLinker;

    public FarmInputService(FarmInputLogRepository repository, BatchService batchService,
                            IncubationService incubationService) {
        this(repository, batchService, incubationService, null, null);
    }

    public FarmInputService(FarmInputLogRepository repository, BatchService batchService,
                            IncubationService incubationService, InventoryService inventoryService) {
        this(repository, batchService, incubationService, inventoryService, null);
    }

    @Autowired
    public FarmInputService(FarmInputLogRepository repository, BatchService batchService,
                            IncubationService incubationService, InventoryService inventoryService,
                            VaccinationInputLinker vaccinationInputLinker) {
        this.repository = repository;
        this.batchService = batchService;
        this.incubationService = incubationService;
        this.inventoryService = inventoryService;
        this.vaccinationInputLinker = vaccinationInputLinker;
    }

    @Transactional
    public FarmInputLogResponse create(Long farmId, Long userId, Role role, CreateFarmInputRequest request) {
        if (farmId == null) throw new BadRequestException("Join a farm before recording inputs");

        UUID operationId = request.operationId() == null ? UUID.randomUUID() : request.operationId();
        FarmInputLog existing = repository.findByOperationId(operationId).orElse(null);
        if (existing != null) {
            if (!farmId.equals(existing.getFarmId())
                    || !java.util.Objects.equals(request.batchId(), existing.getBatchId())
                    || !java.util.Objects.equals(request.incubationCycleId(), existing.getIncubationCycleId())
                    || request.productType() != existing.getProductType()
                    || !java.util.Objects.equals(request.brandName().trim(), existing.getBrandName())
                    || !java.util.Objects.equals(trimToNull(request.productName()), existing.getProductName())
                    || !java.util.Objects.equals(request.quantity(), existing.getQuantity())
                    || !java.util.Objects.equals(trimToNull(request.unit()), existing.getUnit())
                    || !java.util.Objects.equals(trimToNull(request.route()), existing.getRoute())
                    || !java.util.Objects.equals(trimToNull(request.purpose()), existing.getPurpose())
                    || !java.util.Objects.equals(trimToNull(request.notes()), existing.getNotes())
                    || (request.recordedAt() != null && !java.util.Objects.equals(request.recordedAt(), existing.getRecordedAt()))
                    || !java.util.Objects.equals(request.farmProductId(), existing.getFarmProductId())
                    || !java.util.Objects.equals(request.affectedBirdCount(), existing.getAffectedBirdCount())) {
                throw new ConflictException("operationId has already been used for a different input record");
            }
            if (vaccinationInputLinker != null) vaccinationInputLinker.linkIfScheduled(existing);
            return FarmInputLogResponse.from(existing);
        }
        if (request.batchId() == null && request.incubationCycleId() == null) {
            throw new BadRequestException("Link the input to a batch or incubation cycle");
        }
        if (request.batchId() != null) batchService.requireWritableBatch(request.batchId(), farmId);
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
        if (vaccinationInputLinker != null) vaccinationInputLinker.linkIfScheduled(saved);
        if (inventoryService != null && request.farmProductId() != null) {
            if (request.quantity() == null) {
                throw new BadRequestException("Enter the units used for a catalog product so stock and batch cost can be updated");
            }
            InventoryUseResult result = inventoryService.applyUsage(farmId, userId, request.farmProductId(),
                    request.quantity(), saved.getRecordedAt(), saved.getBatchId(), saved.getId(), operationId);
            saved.setInventoryMovementId(result.movementId());
            saved.setInventoryStatus(InventoryStatus.valueOf(result.status()));
            saved.setUnitCostSnapshot(result.unitCost());
            saved.setCalculatedCost(result.batchCost());
            saved.setCostStatus(result.costStatus());
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
