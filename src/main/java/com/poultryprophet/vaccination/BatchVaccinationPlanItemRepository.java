package com.poultryprophet.vaccination;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface BatchVaccinationPlanItemRepository extends JpaRepository<BatchVaccinationPlanItem, Long> {
    List<BatchVaccinationPlanItem> findByBatchIdAndFarmIdOrderByDueDateAsc(Long batchId, Long farmId);
    Optional<BatchVaccinationPlanItem> findByIdAndFarmId(Long id, Long farmId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BatchVaccinationPlanItem p where p.id = :id and p.farmId = :farmId")
    Optional<BatchVaccinationPlanItem> findByIdAndFarmIdForUpdate(@Param("id") Long id, @Param("farmId") Long farmId);
    boolean existsByBatchIdAndProgramItemId(Long batchId, Long programItemId);
}
