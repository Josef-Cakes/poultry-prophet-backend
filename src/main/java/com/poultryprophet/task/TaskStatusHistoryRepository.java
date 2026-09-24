package com.poultryprophet.task;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskStatusHistoryRepository extends JpaRepository<TaskStatusHistory, Long> {
    List<TaskStatusHistory> findByTaskIdOrderByChangedAtAsc(Long taskId);
}
