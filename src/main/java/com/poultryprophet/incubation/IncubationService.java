package com.poultryprophet.incubation;

import com.poultryprophet.batch.dto.BatchResponse;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.batch.dto.CreateBatchRequest;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.incubation.dto.CompleteIncubationRequest;
import com.poultryprophet.incubation.dto.CreateIncubationCycleRequest;
import com.poultryprophet.incubation.dto.IncubationCycleResponse;
import com.poultryprophet.incubation.dto.UpdateIncubationCycleRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class IncubationService {

    private final IncubationCycleRepository repository;
    private final BatchService batchService;

    public IncubationService(IncubationCycleRepository repository, BatchService batchService) {
        this.repository = repository;
        this.batchService = batchService;
    }

    @Transactional
    public IncubationCycleResponse create(Long farmId, CreateIncubationCycleRequest request) {
        requireFarm(farmId);
        if (repository.existsByFarmIdAndCycleNameIgnoreCase(farmId, request.cycleName().trim())) {
            throw new BadRequestException("An incubation cycle with that name already exists");
        }
        if (request.loadedDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Egg load date cannot be in the future");
        }
        if (request.expectedHatchDate() != null && request.expectedHatchDate().isBefore(request.loadedDate())) {
            throw new BadRequestException("Expected hatch date cannot be before the load date");
        }
        IncubationCycle cycle = new IncubationCycle();
        cycle.setFarmId(farmId);
        cycle.setCycleName(request.cycleName().trim());
        cycle.setIncubatorCode(request.incubatorCode().trim());
        cycle.setEggSource(trimToNull(request.eggSource()));
        cycle.setBloodline(trimToNull(request.bloodline()));
        cycle.setLoadedDate(request.loadedDate());
        cycle.setEggsLoaded(request.eggsLoaded());
        cycle.setExpectedHatchDate(request.expectedHatchDate() != null
                ? request.expectedHatchDate() : request.loadedDate().plusDays(21));
        cycle.setNotes(trimToNull(request.notes()));
        return IncubationCycleResponse.from(repository.save(cycle));
    }

    @Transactional(readOnly = true)
    public List<IncubationCycleResponse> list(Long farmId) {
        requireFarm(farmId);
        return repository.findByFarmIdOrderByLoadedDateDescCreatedAtDesc(farmId).stream()
                .map(IncubationCycleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public IncubationCycle require(Long id, Long farmId) {
        return repository.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new NotFoundException("Incubation cycle " + id + " not found"));
    }

    @Transactional
    public IncubationCycleResponse update(Long id, Long farmId, UpdateIncubationCycleRequest request) {
        IncubationCycle cycle = require(id, farmId);
        if (cycle.getStatus() == IncubationStatus.COMPLETED || cycle.getStatus() == IncubationStatus.CANCELLED) {
            throw new BadRequestException("Completed or cancelled cycles cannot be edited");
        }
        if (request.incubatorCode() != null && !request.incubatorCode().isBlank()) {
            cycle.setIncubatorCode(request.incubatorCode().trim());
        }
        if (request.eggSource() != null) cycle.setEggSource(trimToNull(request.eggSource()));
        if (request.bloodline() != null) cycle.setBloodline(trimToNull(request.bloodline()));
        if (request.expectedHatchDate() != null) {
            if (request.expectedHatchDate().isBefore(cycle.getLoadedDate())) {
                throw new BadRequestException("Expected hatch date cannot be before the load date");
            }
            cycle.setExpectedHatchDate(request.expectedHatchDate());
        }
        if (request.notes() != null) cycle.setNotes(trimToNull(request.notes()));
        if (request.status() != null) {
            try {
                IncubationStatus status = IncubationStatus.valueOf(request.status().trim().toUpperCase());
                if (status == IncubationStatus.COMPLETED || status == IncubationStatus.CANCELLED) {
                    throw new BadRequestException("Use the completion or cancellation action for this status");
                }
                cycle.setStatus(status);
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Unknown incubation status");
            }
        }
        return IncubationCycleResponse.from(repository.save(cycle));
    }

    @Transactional
    public IncubationCycleResponse complete(Long id, Long farmId, Long userId,
                                            CompleteIncubationRequest request) {
        IncubationCycle cycle = require(id, farmId);
        if (cycle.getStatus() == IncubationStatus.COMPLETED || cycle.getStatus() == IncubationStatus.CANCELLED) {
            throw new BadRequestException("This incubation cycle is already closed");
        }
        if (request.actualHatchDate().isBefore(cycle.getLoadedDate())) {
            throw new BadRequestException("Actual hatch date cannot be before the load date");
        }
        int total = request.hatchedCount() + request.unhatchedCount() + request.removedDamagedCount();
        if (total != cycle.getEggsLoaded()) {
            throw new BadRequestException("Hatched, unhatched, and removed/damaged counts must equal eggs loaded");
        }
        cycle.setActualHatchDate(request.actualHatchDate());
        cycle.setHatchedCount(request.hatchedCount());
        cycle.setUnhatchedCount(request.unhatchedCount());
        cycle.setRemovedDamagedCount(request.removedDamagedCount());
        cycle.setNotes(trimToNull(request.notes()) != null ? trimToNull(request.notes()) : cycle.getNotes());
        cycle.setStatus(IncubationStatus.COMPLETED);
        cycle.setClosedBy(userId);
        cycle.setClosedAt(java.time.Instant.now());
        return IncubationCycleResponse.from(repository.save(cycle));
    }

    @Transactional
    public IncubationCycleResponse cancel(Long id, Long farmId, Long userId, String reason) {
        IncubationCycle cycle = require(id, farmId);
        if (cycle.getStatus() == IncubationStatus.COMPLETED) {
            throw new BadRequestException("Completed cycles cannot be cancelled");
        }
        cycle.setStatus(IncubationStatus.CANCELLED);
        cycle.setClosedBy(userId);
        cycle.setClosedAt(java.time.Instant.now());
        if (reason != null && !reason.isBlank()) cycle.setNotes(reason.trim());
        return IncubationCycleResponse.from(repository.save(cycle));
    }

    @Transactional
    public BatchResponse createBatch(Long id, Long farmId) {
        IncubationCycle cycle = require(id, farmId);
        if (cycle.getStatus() != IncubationStatus.COMPLETED || cycle.getHatchedCount() == null
                || cycle.getActualHatchDate() == null) {
            throw new BadRequestException("Complete the incubation cycle before creating a batch");
        }
        if (cycle.getCreatedBatchId() != null) {
            return batchService.getForFarm(cycle.getCreatedBatchId(), farmId);
        }
        String batchName = cycle.getCycleName() + " Hatch";
        CreateBatchRequest request = new CreateBatchRequest(batchName, cycle.getHatchedCount(),
                cycle.getActualHatchDate(), null, cycle.getBloodline(), "INCUBATION:" + cycle.getCycleName(), List.of());
        BatchResponse batch = batchService.create(request, farmId);
        cycle.setCreatedBatchId(batch.id());
        repository.save(cycle);
        return batch;
    }

    public double hatchRate(IncubationCycle c) {
        return c.getEggsLoaded() == 0 || c.getHatchedCount() == null ? 0.0
                : round(c.getHatchedCount() * 100.0 / c.getEggsLoaded());
    }

    public long durationDays(IncubationCycle c) {
        return c.getActualHatchDate() == null ? 0
                : ChronoUnit.DAYS.between(c.getLoadedDate(), c.getActualHatchDate());
    }

    private void requireFarm(Long farmId) {
        if (farmId == null) throw new BadRequestException("Join a farm before using incubation tracking");
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
