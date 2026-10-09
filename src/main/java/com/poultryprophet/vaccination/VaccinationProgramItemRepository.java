package com.poultryprophet.vaccination;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface VaccinationProgramItemRepository extends JpaRepository<VaccinationProgramItem, Long> {
    List<VaccinationProgramItem> findByProgramIdAndActiveOrderBySequenceNumberAsc(Long programId, boolean active);
}
