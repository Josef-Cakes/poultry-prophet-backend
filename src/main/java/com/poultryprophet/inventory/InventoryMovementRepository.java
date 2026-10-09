package com.poultryprophet.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    Optional<InventoryMovement> findByOperationId(UUID operationId);
    List<InventoryMovement> findByFarmIdAndFarmProductIdOrderByOccurredAtDescCreatedAtDesc(Long farmId, Long productId);
    List<InventoryMovement> findByFarmIdAndBatchIdOrderByOccurredAtDescCreatedAtDesc(Long farmId, Long batchId);
    List<InventoryMovement> findByFarmIdAndBatchIdAndOccurredAtBetweenOrderByOccurredAtAsc(Long farmId, Long batchId, java.time.Instant start, java.time.Instant end);
    boolean existsByFarmIdAndFarmInputLogId(Long farmId, Long farmInputLogId);
    Optional<InventoryMovement> findFirstByFarmIdAndFarmInputLogId(Long farmId, Long farmInputLogId);
    boolean existsByFarmIdAndReversesMovementId(Long farmId, Long movementId);
}
