package com.poultryprophet.task;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface HandlerTaskRepository extends JpaRepository<HandlerTask, Long> {
    List<HandlerTask> findByFarmIdOrderByDueAtAscCreatedAtDesc(Long farmId);
    List<HandlerTask> findByFarmIdAndAssignedHandlerIdOrderByDueAtAscCreatedAtDesc(Long farmId, Long handlerId);
    @org.springframework.data.jpa.repository.Query("select t from HandlerTask t where t.farmId = :farmId and (t.assignedHandlerId = :handlerId or t.assignmentScope in ('BATCH_TEAM','FARM_TEAM')) and (t.visibleFrom is null or t.visibleFrom <= :now) order by t.dueAt asc nulls last, t.createdAt desc")
    List<HandlerTask> findVisibleToHandler(Long farmId, Long handlerId, Instant now);
    Optional<HandlerTask> findByIdAndFarmId(Long id, Long farmId);
}
