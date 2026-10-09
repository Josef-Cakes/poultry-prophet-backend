package com.poultryprophet.vaccination;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface VaccinationProgramRepository extends JpaRepository<VaccinationProgram, Long> {
    List<VaccinationProgram> findByFarmIdAndActiveOrderByNameAscVersionNumberDesc(Long farmId, boolean active);
    Optional<VaccinationProgram> findByIdAndFarmId(Long id, Long farmId);
    Optional<VaccinationProgram> findTopByFarmIdAndSeriesIdOrderByVersionNumberDesc(Long farmId, UUID seriesId);
}
