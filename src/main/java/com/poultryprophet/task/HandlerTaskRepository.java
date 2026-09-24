package com.poultryprophet.task;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HandlerTaskRepository extends JpaRepository<HandlerTask, Long> {
    List<HandlerTask> findByFarmIdOrderByDueAtAscCreatedAtDesc(Long farmId);
    List<HandlerTask> findByFarmIdAndAssignedHandlerIdOrderByDueAtAscCreatedAtDesc(Long farmId, Long handlerId);
    Optional<HandlerTask> findByIdAndFarmId(Long id, Long farmId);
}
