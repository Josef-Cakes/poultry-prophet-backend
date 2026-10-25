package com.poultryprophet.task;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskStatusHistoryRepository extends JpaRepository<TaskStatusHistory, Long> {
    List<TaskStatusHistory> findByTaskIdOrderByChangedAtAsc(Long taskId);
    Optional<TaskStatusHistory> findByOperationId(UUID operationId);
}
