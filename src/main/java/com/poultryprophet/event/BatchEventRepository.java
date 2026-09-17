package com.poultryprophet.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchEventRepository extends JpaRepository<BatchEvent, Long> {

    List<BatchEvent> findByBatchIdOrderByEventDateDescCreatedAtDesc(Long batchId, Pageable pageable);

    List<BatchEvent> findByBatchIdAndEventDateAndEventType(Long batchId, LocalDate date, EventType type);

    Optional<BatchEvent> findByOperationId(UUID operationId);

    @Query("select coalesce(sum(e.affectedCount), 0) from BatchEvent e "
            + "where e.batchId = :batchId and e.eventDate = :date and e.eventType = :type")
    long sumAffectedCountByBatchIdAndEventDateAndEventType(@Param("batchId") Long batchId,
                                                            @Param("date") LocalDate date,
                                                            @Param("type") EventType type);

}
