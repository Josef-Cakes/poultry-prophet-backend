package com.poultryprophet.batch;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
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
}
