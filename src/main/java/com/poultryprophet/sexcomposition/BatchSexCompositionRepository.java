package com.poultryprophet.sexcomposition;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchSexCompositionRepository extends JpaRepository<BatchSexComposition, Long> {
    Optional<BatchSexComposition> findByOperationId(UUID operationId);
    Optional<BatchSexComposition> findByIdAndFarmId(Long id, Long farmId);
    Optional<BatchSexComposition> findByBatchIdAndFarmIdAndStatus(Long batchId, Long farmId, String status);
    List<BatchSexComposition> findByBatchIdAndFarmIdOrderByObservedOnDescCreatedAtDesc(Long batchId, Long farmId);
    Optional<BatchSexComposition> findFirstByBatchIdAndFarmIdAndObservedOnLessThanEqualOrderByObservedOnDescCreatedAtDesc(
            Long batchId, Long farmId, java.time.LocalDate observedOn);
    boolean existsByBatchIdAndFarmIdAndCreatedAtAfter(Long batchId, Long farmId, Instant createdAt);
}
