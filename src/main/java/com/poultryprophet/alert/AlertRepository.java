package com.poultryprophet.alert;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    boolean existsByBatchIdAndIndicatorTypeAndAcknowledgedAtIsNull(Long batchId, String indicatorType);

    boolean existsBySourceEvent_Id(Long sourceEventId);

    List<Alert> findByBatchIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(Long batchId);

    List<Alert> findByBatchIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(Long batchId, Pageable pageable);

    List<Alert> findByBatchIdOrderByCreatedAtDesc(Long batchId, Pageable pageable);

    List<Alert> findByBatchIdAndCreatedAtBetweenOrderByCreatedAtAsc(
            Long batchId, Instant from, Instant to);

    // Farm-wide views (across every batch) backing the notifications centre.
    List<Alert> findByBatch_FarmIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(Long farmId, Pageable pageable);

    List<Alert> findByBatch_FarmIdOrderByCreatedAtDesc(Long farmId, Pageable pageable);
}
