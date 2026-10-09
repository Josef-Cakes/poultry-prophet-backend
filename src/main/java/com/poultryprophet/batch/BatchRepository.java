package com.poultryprophet.batch;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {

    boolean existsByFarmIdAndNameIgnoreCase(Long farmId, String name);

    List<Batch> findByFarmIdOrderByCreatedAtDesc(Long farmId);

    // Working dashboard list: everything except retired batches.
    List<Batch> findByFarmIdAndStatusNotOrderByCreatedAtDesc(Long farmId, BatchStatus status);

    // Retired list: archived batches only.
    List<Batch> findByFarmIdAndStatusOrderByCreatedAtDesc(Long farmId, BatchStatus status);

    Optional<Batch> findByIdAndFarmId(Long id, Long farmId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Batch b where b.id = :id and b.farmId = :farmId")
    Optional<Batch> findByIdAndFarmIdForUpdate(@Param("id") Long id, @Param("farmId") Long farmId);

    /**
     * Updates only lifecycle columns.  Do not use entity save for archive/restore: older
     * batches can contain legacy values in unrelated columns, and flushing the complete entity
     * can make a harmless lifecycle transition fail on those values.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Batch b
               set b.preArchiveStatus = :previousStatus,
                   b.status = :nextStatus,
                   b.archivedAt = :archivedAt,
                   b.archivedByUserId = :archivedByUserId,
                   b.archiveReason = :archiveReason
             where b.id = :batchId and b.farmId = :farmId and b.status = :previousStatus
            """)
    int updateArchiveLifecycle(@Param("batchId") Long batchId,
                               @Param("farmId") Long farmId,
                               @Param("previousStatus") BatchStatus previousStatus,
                               @Param("nextStatus") BatchStatus nextStatus,
                               @Param("archivedAt") java.time.Instant archivedAt,
                               @Param("archivedByUserId") Long archivedByUserId,
                               @Param("archiveReason") String archiveReason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Batch b
               set b.status = :restoredStatus,
                   b.archivedAt = null,
                   b.archivedByUserId = null,
                   b.archiveReason = null,
                   b.preArchiveStatus = null
             where b.id = :batchId and b.farmId = :farmId and b.status = :archivedStatus
            """)
    int updateRestoreLifecycle(@Param("batchId") Long batchId,
                               @Param("farmId") Long farmId,
                               @Param("archivedStatus") BatchStatus archivedStatus,
                               @Param("restoredStatus") BatchStatus restoredStatus);
}
