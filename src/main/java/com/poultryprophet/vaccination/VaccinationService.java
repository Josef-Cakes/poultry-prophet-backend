package com.poultryprophet.vaccination;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.input.FarmInputService;
import com.poultryprophet.input.InputProductType;
import com.poultryprophet.input.dto.CreateFarmInputRequest;
import com.poultryprophet.input.dto.FarmInputLogResponse;
import com.poultryprophet.inventory.FarmProductRepository;
import com.poultryprophet.task.HandlerTask;
import com.poultryprophet.task.HandlerTaskRepository;
import com.poultryprophet.task.TaskPriority;
import com.poultryprophet.task.TaskStatus;
import com.poultryprophet.task.TaskStatusHistory;
import com.poultryprophet.task.TaskStatusHistoryRepository;
import com.poultryprophet.user.Role;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import com.poultryprophet.vaccination.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.math.BigDecimal;
import java.util.*;

@Service
public class VaccinationService {
    private static final ZoneId FARM_ZONE = ZoneId.of("Asia/Manila");
    private final VaccinationProgramRepository programRepository;
    private final VaccinationProgramItemRepository programItemRepository;
    private final BatchVaccinationPlanItemRepository planRepository;
    private final HandlerTaskRepository taskRepository;
    private final TaskStatusHistoryRepository historyRepository;
    private final BatchService batchService;
    private final FarmInputService inputService;
    private final UserRepository userRepository;
    private final FarmProductRepository farmProductRepository;

    public VaccinationService(VaccinationProgramRepository programRepository, VaccinationProgramItemRepository programItemRepository,
                              BatchVaccinationPlanItemRepository planRepository, HandlerTaskRepository taskRepository,
                              TaskStatusHistoryRepository historyRepository, BatchService batchService,
                              FarmInputService inputService, UserRepository userRepository, FarmProductRepository farmProductRepository) {
        this.programRepository=programRepository; this.programItemRepository=programItemRepository; this.planRepository=planRepository;
        this.taskRepository=taskRepository; this.historyRepository=historyRepository; this.batchService=batchService;
        this.inputService=inputService; this.userRepository=userRepository; this.farmProductRepository=farmProductRepository;
    }

    @Transactional(readOnly = true)
    public List<VaccinationProgramResponse> listPrograms(Long farmId) {
        return programRepository.findByFarmIdAndActiveOrderByNameAscVersionNumberDesc(farmId, true).stream()
                .map(p -> VaccinationProgramResponse.from(p, programItemRepository.findByProgramIdAndActiveOrderBySequenceNumberAsc(p.getId(), true))).toList();
    }

    @Transactional
    public VaccinationProgramResponse createProgram(Long farmId, Long userId, CreateVaccinationProgramRequest request) {
        UUID seriesId = request.seriesId() == null ? UUID.randomUUID() : request.seriesId();
        VaccinationProgram latest = programRepository.findTopByFarmIdAndSeriesIdOrderByVersionNumberDesc(farmId, seriesId).orElse(null);
        if (request.supersedesProgramId() != null && (latest == null || !request.supersedesProgramId().equals(latest.getId()))) {
            throw new BadRequestException("The program version does not match the latest farm version");
        }
        if (latest != null) latest.setActive(false);
        VaccinationProgram program = new VaccinationProgram();
        program.setFarmId(farmId); program.setSeriesId(seriesId); program.setName(request.name().trim());
        program.setDescription(trim(request.description())); program.setVersionNumber(latest == null ? 1 : latest.getVersionNumber()+1);
        program.setSupersedesProgramId(latest == null ? null : latest.getId()); program.setDefaultForNewBatches(Boolean.TRUE.equals(request.defaultForNewBatches())); program.setCreatedBy(userId);
        VaccinationProgram saved = programRepository.save(program);
        List<VaccinationProgramItem> items = new ArrayList<>(); int sequence=1;
        for (VaccinationProgramItemRequest item : request.items()) {
            int offset = normalizedDays(item.ageOffsetValue(), item.ageOffsetUnit());
            if (item.farmProductId() != null) {
                farmProductRepository.findByIdAndFarmId(item.farmProductId(), farmId)
                        .orElseThrow(() -> new BadRequestException("The selected vaccine product is not in this farm's inventory"));
            }
            VaccinationProgramItem row = new VaccinationProgramItem(); row.setProgramId(saved.getId()); row.setSequenceNumber(sequence++);
            row.setVaccineName(item.vaccineName().trim()); row.setFarmProductId(item.farmProductId()); row.setAgeOffsetValue(item.ageOffsetValue());
            row.setAgeOffsetUnit(item.ageOffsetUnit().trim().toUpperCase(Locale.ROOT)); row.setNormalizedOffsetDays(offset);
            row.setReminderLeadDays(item.reminderLeadDays() == null ? 1 : item.reminderLeadDays()); row.setRoute(trim(item.route()));
            row.setDoseGuidance(trim(item.doseGuidance())); row.setInstructions(trim(item.instructions())); items.add(row);
        }
        programItemRepository.saveAll(items);
        return VaccinationProgramResponse.from(saved, items);
    }

    @Transactional(readOnly = true)
    public List<VaccinationPlanItemResponse> plan(Long batchId, Long farmId) {
        batchService.requireBatch(batchId, farmId);
        LocalDate today = LocalDate.now(FARM_ZONE);
        return planRepository.findByBatchIdAndFarmIdOrderByDueDateAsc(batchId, farmId).stream()
                .filter(p -> !"CANCELLED".equals(p.getStatus()))
                .map(p -> VaccinationPlanItemResponse.from(p, today)).toList();
    }

    @Transactional
    public List<VaccinationPlanItemResponse> assign(Long batchId, Long farmId, Long managerId, AssignVaccinationProgramRequest request) {
        Batch batch = batchService.requireWritableBatch(batchId, farmId);
        VaccinationProgram program = programRepository.findByIdAndFarmId(request.programId(), farmId).orElseThrow(() -> new NotFoundException("Vaccination program not found"));
        List<VaccinationProgramItem> items = programItemRepository.findByProgramIdAndActiveOrderBySequenceNumberAsc(program.getId(), true);
        if (items.isEmpty()) throw new BadRequestException("Add at least one vaccine to this program");
        if (!planRepository.findByBatchIdAndFarmIdOrderByDueDateAsc(batchId, farmId).isEmpty()) throw new ConflictException("This batch already has a vaccination plan");
        createPlanItems(batch, farmId, managerId, program, items);
        return plan(batchId, farmId);
    }

    /**
     * Replaces the open part of a schedule. Completed steps remain immutable for traceability;
     * unfinished steps are cancelled and the new schedule is added.
     */
    @Transactional
    public List<VaccinationPlanItemResponse> replace(Long batchId, Long farmId, Long managerId,
                                                      ReplaceVaccinationPlanRequest request) {
        Batch batch = batchService.requireWritableBatch(batchId, farmId);
        VaccinationProgram program = programRepository.findByIdAndFarmId(request.programId(), farmId)
                .orElseThrow(() -> new NotFoundException("Vaccination program not found"));
        List<VaccinationProgramItem> items = programItemRepository.findByProgramIdAndActiveOrderBySequenceNumberAsc(program.getId(), true);
        if (items.isEmpty()) throw new BadRequestException("Add at least one vaccine to this program");

        List<BatchVaccinationPlanItem> existing = planRepository.findByBatchIdAndFarmIdOrderByDueDateAsc(batchId, farmId);
        for (BatchVaccinationPlanItem item : existing) {
            boolean openItem = !"COMPLETED".equals(item.getStatus()) && !"CANCELLED".equals(item.getStatus());
            if (openItem) {
                item.setStatus("CANCELLED");
                item.setSkippedReason("Schedule replaced by manager");
                planRepository.save(item);
                if (item.getTaskId() != null) {
                    taskRepository.findByIdAndFarmId(item.getTaskId(), farmId).ifPresent(task -> {
                        if (task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.CANCELLED) {
                            task.setStatus(TaskStatus.CANCELLED);
                            task.setCompletionNote("Vaccination schedule replaced");
                            task.setUpdatedAt(Instant.now());
                            taskRepository.save(task);
                            TaskStatusHistory history = new TaskStatusHistory();
                            history.setTaskId(task.getId()); history.setChangedBy(managerId);
                            history.setStatus(TaskStatus.CANCELLED); history.setNote("Vaccination schedule replaced");
                            historyRepository.save(history);
                        }
                    });
                }
            }
        }
        createPlanItems(batch, farmId, managerId, program, items);
        return plan(batchId, farmId);
    }

    private void createPlanItems(Batch batch, Long farmId, Long managerId,
                                 VaccinationProgram program, List<VaccinationProgramItem> items) {
        Long batchId = batch.getId();
        for (VaccinationProgramItem item : items) {
            LocalDate due = batch.getStartDate().plusDays(item.getNormalizedOffsetDays());
            LocalDate remind = due.minusDays(item.getReminderLeadDays());
            BatchVaccinationPlanItem plan = new BatchVaccinationPlanItem(); plan.setFarmId(farmId); plan.setBatchId(batchId); plan.setProgramId(program.getId());
            plan.setProgramItemId(item.getId()); plan.setVaccineName(item.getVaccineName()); plan.setFarmProductId(item.getFarmProductId()); plan.setHatchDateSnapshot(batch.getStartDate());
            plan.setNormalizedOffsetDays(item.getNormalizedOffsetDays()); plan.setDueDate(due); plan.setRemindOn(remind.isBefore(batch.getStartDate()) ? batch.getStartDate() : remind);
            BatchVaccinationPlanItem saved = planRepository.save(plan);
            HandlerTask task = new HandlerTask(); task.setFarmId(farmId); task.setBatchId(batchId); task.setTitle("Vaccination: " + item.getVaccineName());
            task.setInstructions(item.getInstructions() == null ? "Record when done. Follow the farm's vaccine label and manager guidance." : item.getInstructions());
            task.setAssignedManagerId(managerId); task.setAssignmentScope("BATCH_TEAM"); task.setSourceType("VACCINATION_PLAN"); task.setSourceId(saved.getId());
            task.setDueAt(due.atStartOfDay(FARM_ZONE).toInstant()); task.setVisibleFrom(plan.getRemindOn().atStartOfDay(FARM_ZONE).toInstant()); task.setPriority(TaskPriority.NORMAL);
            task.setStatus(TaskStatus.TODO); HandlerTask taskSaved = taskRepository.save(task); saved.setTaskId(taskSaved.getId()); planRepository.save(saved);
            TaskStatusHistory history = new TaskStatusHistory(); history.setTaskId(taskSaved.getId()); history.setChangedBy(managerId); history.setStatus(TaskStatus.TODO); historyRepository.save(history);
        }
    }

    @Transactional
    public VaccinationPlanItemResponse record(Long planId, Long farmId, Long userId, Role role, RecordVaccinationRequest request) {
        requireActiveUser(userId, farmId, role);
        BatchVaccinationPlanItem plan = planRepository.findByIdAndFarmIdForUpdate(planId, farmId).orElseThrow(() -> new NotFoundException("Vaccination plan item not found"));
        if ("COMPLETED".equals(plan.getStatus())) return VaccinationPlanItemResponse.from(plan, LocalDate.now(FARM_ZONE));
        if (!"SCHEDULED".equals(plan.getStatus())) throw new ConflictException("This vaccination item is no longer open");
        UUID operationId = request.operationId() == null ? UUID.randomUUID() : request.operationId();
        String inventoryUnit = request.unit();
        if (plan.getFarmProductId() != null) {
            var product = farmProductRepository.findByIdAndFarmId(plan.getFarmProductId(), farmId)
                    .orElseThrow(() -> new BadRequestException("The scheduled vaccine is no longer linked to a farm product"));
            if (request.quantity() == null || request.quantity().signum() <= 0) {
                throw new BadRequestException("Enter the number of vaccine units used before marking this vaccine done");
            }
            inventoryUnit = product.getStockUnit();
        }
        CreateFarmInputRequest input = new CreateFarmInputRequest(plan.getBatchId(), null, request.recordedAt(), InputProductType.VACCINE,
                plan.getVaccineName(), plan.getVaccineName(), request.quantity(), inventoryUnit, null, "Vaccination", request.notes(), operationId, plan.getFarmProductId(), null);
        FarmInputLogResponse savedInput = inputService.create(farmId, userId, role, input);
        plan.setStatus("COMPLETED"); plan.setCompletedInputLogId(savedInput.id()); plan.setCompletedBy(userId); plan.setCompletedAt(Instant.now()); plan.setCompletionOperationId(operationId);
        if (plan.getTaskId() != null) taskRepository.findByIdAndFarmId(plan.getTaskId(), farmId).ifPresent(task -> { task.setStatus(TaskStatus.COMPLETED); task.setCompletedAt(Instant.now()); task.setCompletedBy(userId); task.setCompletionNote("Vaccination recorded"); task.setUpdatedAt(Instant.now()); taskRepository.save(task); });
        return VaccinationPlanItemResponse.from(planRepository.save(plan), LocalDate.now(FARM_ZONE));
    }

    @Transactional
    public VaccinationPlanItemResponse skip(Long planId, Long farmId, Long managerId, SkipVaccinationRequest request) {
        BatchVaccinationPlanItem plan = planRepository.findByIdAndFarmIdForUpdate(planId, farmId).orElseThrow(() -> new NotFoundException("Vaccination plan item not found"));
        if ("COMPLETED".equals(plan.getStatus())) throw new ConflictException("Completed vaccination cannot be skipped");
        plan.setStatus("SKIPPED"); plan.setSkippedReason(request.reason().trim());
        if (plan.getTaskId() != null) taskRepository.findByIdAndFarmId(plan.getTaskId(), farmId).ifPresent(task -> { task.setStatus(TaskStatus.CANCELLED); task.setCompletionNote("Skipped: " + request.reason().trim()); task.setUpdatedAt(Instant.now()); taskRepository.save(task); });
        return VaccinationPlanItemResponse.from(planRepository.save(plan), LocalDate.now(FARM_ZONE));
    }

    private void requireActiveUser(Long userId, Long farmId, Role role) { User user = userRepository.findById(userId).orElseThrow(() -> new BadRequestException("User not found")); if (!farmId.equals(user.getFarmId()) || (role != Role.HANDLER && role != Role.MANAGER)) throw new BadRequestException("User is not active in this farm"); }
    private int normalizedDays(int value, String unit) { String normalized=unit.trim().toUpperCase(Locale.ROOT); if (!normalized.equals("DAY")&&!normalized.equals("WEEK")) throw new BadRequestException("Age unit must be DAY or WEEK"); return Math.multiplyExact(value, normalized.equals("WEEK") ? 7 : 1); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
