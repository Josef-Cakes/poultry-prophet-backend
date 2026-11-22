package com.poultryprophet.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long>, JpaSpecificationExecutor<InventoryMovement> {
    Optional<InventoryMovement> findByOperationId(UUID operationId);
    List<InventoryMovement> findByFarmIdAndFarmProductIdOrderByOccurredAtDescCreatedAtDesc(Long farmId, Long productId);
    List<InventoryMovement> findByFarmIdAndBatchIdOrderByOccurredAtDescCreatedAtDesc(Long farmId, Long batchId);
    List<InventoryMovement> findByFarmIdOrderByOccurredAtDescCreatedAtDesc(Long farmId);
    List<InventoryMovement> findByFarmIdAndBatchIdAndOccurredAtBetweenOrderByOccurredAtAsc(Long farmId, Long batchId, java.time.Instant start, java.time.Instant end);
    boolean existsByFarmIdAndFarmInputLogId(Long farmId, Long farmInputLogId);
    Optional<InventoryMovement> findFirstByFarmIdAndFarmInputLogId(Long farmId, Long farmInputLogId);
    boolean existsByFarmIdAndReversesMovementId(Long farmId, Long movementId);
    @Query("select distinct m.farmProductId from InventoryMovement m where m.farmId = :farmId and m.movementType = :type and lower(trim(m.reason)) like 'demo / test data;%'")
    List<Long> findProductsWithDemoStockHistory(@Param("farmId") Long farmId, @Param("type") InventoryMovementType type);
    @Query("""
            select case when count(u) > 0 then true else false end from InventoryMovement u
            where u.farmId = :farmId and u.batchId = :batchId and u.movementType = :usage
              and u.occurredAt >= :start and u.occurredAt < :end and u.createdAt <= :cutoff
              and exists (select d.id from InventoryMovement d where d.farmId = u.farmId
                and d.farmProductId = u.farmProductId and d.movementType = :stockIn
                and lower(trim(d.reason)) like 'demo / test data;%' and d.createdAt <= u.createdAt)
            """)
    boolean existsUseAfterDemoStock(@Param("farmId") Long farmId, @Param("batchId") Long batchId,
            @Param("start") java.time.Instant start, @Param("end") java.time.Instant end,
            @Param("cutoff") java.time.Instant cutoff, @Param("usage") InventoryMovementType usage,
            @Param("stockIn") InventoryMovementType stockIn);
}
