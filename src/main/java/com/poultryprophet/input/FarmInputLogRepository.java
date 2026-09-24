package com.poultryprophet.input;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface FarmInputLogRepository extends JpaRepository<FarmInputLog, Long> {
    List<FarmInputLog> findByFarmIdOrderByRecordedAtDesc(Long farmId);
    List<FarmInputLog> findByFarmIdAndBatchIdOrderByRecordedAtDesc(Long farmId, Long batchId);
    List<FarmInputLog> findByFarmIdAndIncubationCycleIdOrderByRecordedAtDesc(Long farmId, Long cycleId);
    List<FarmInputLog> findByFarmIdAndRecordedAtBetweenOrderByRecordedAtAsc(Long farmId, Instant start, Instant end);
    boolean existsByFarmIdAndBatchIdAndCreatedAtAfter(Long farmId, Long batchId, Instant cutoff);
}
