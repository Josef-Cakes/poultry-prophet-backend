package com.poultryprophet.incubation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface IncubationCycleRepository extends JpaRepository<IncubationCycle, Long> {
    boolean existsByFarmIdAndCycleNameIgnoreCase(Long farmId, String cycleName);
    List<IncubationCycle> findByFarmIdOrderByLoadedDateDescCreatedAtDesc(Long farmId);
    Optional<IncubationCycle> findByIdAndFarmId(Long id, Long farmId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cycle from IncubationCycle cycle where cycle.id = :id and cycle.farmId = :farmId")
    Optional<IncubationCycle> findForUpdate(@Param("id") Long id, @Param("farmId") Long farmId);
    List<IncubationCycle> findByFarmIdAndCreatedBatchId(Long farmId, Long createdBatchId);
    List<IncubationCycle> findByFarmIdAndLoadedDateBetweenOrderByLoadedDateAsc(Long farmId,
                                                                                 java.time.LocalDate start,
                                                                                 java.time.LocalDate end);
    List<IncubationCycle> findByFarmIdAndStatusAndActualHatchDateBetweenOrderByActualHatchDateAsc(
            Long farmId, IncubationStatus status, java.time.LocalDate start, java.time.LocalDate end);
}
