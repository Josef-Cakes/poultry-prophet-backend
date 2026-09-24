package com.poultryprophet.selectionreview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BatchSelectionReviewRepository extends JpaRepository<BatchSelectionReview, Long> {
    Optional<BatchSelectionReview> findByIdAndFarmIdAndBatchId(Long id, Long farmId, Long batchId);
    List<BatchSelectionReview> findByFarmIdAndBatchIdOrderByGeneratedAtDesc(Long farmId, Long batchId);
    Optional<BatchSelectionReview> findByFarmIdAndBatchIdAndIdempotencyKey(Long farmId, Long batchId, String idempotencyKey);
    long countByFarmIdAndBatchId(Long farmId, Long batchId);
}
