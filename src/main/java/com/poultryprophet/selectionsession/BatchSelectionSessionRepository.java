package com.poultryprophet.selectionsession;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchSelectionSessionRepository extends JpaRepository<BatchSelectionSession, Long> {
    List<BatchSelectionSession> findByFarmIdAndBatchIdOrderBySelectionDateDescCreatedAtDesc(Long farmId, Long batchId);

    Optional<BatchSelectionSession> findByIdAndFarmIdAndBatchId(Long id, Long farmId, Long batchId);

    Optional<BatchSelectionSession> findByOperationId(UUID operationId);

    Optional<BatchSelectionSession> findTopByFarmIdAndBatchIdAndStatusAndSelectionDateLessThanEqualOrderBySelectionDateDescCreatedAtDesc(
            Long farmId, Long batchId, SelectionSessionStatus status, LocalDate selectionDate);

    boolean existsByFarmIdAndBatchIdAndUpdatedAtAfter(Long farmId, Long batchId, Instant cutoff);
}
