package com.poultryprophet.incubation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncubationCycleRepository extends JpaRepository<IncubationCycle, Long> {
    boolean existsByFarmIdAndCycleNameIgnoreCase(Long farmId, String cycleName);
    List<IncubationCycle> findByFarmIdOrderByLoadedDateDescCreatedAtDesc(Long farmId);
    Optional<IncubationCycle> findByIdAndFarmId(Long id, Long farmId);
    List<IncubationCycle> findByFarmIdAndCreatedBatchId(Long farmId, Long createdBatchId);
    List<IncubationCycle> findByFarmIdAndLoadedDateBetweenOrderByLoadedDateAsc(Long farmId,
                                                                                 java.time.LocalDate start,
                                                                                 java.time.LocalDate end);
}
