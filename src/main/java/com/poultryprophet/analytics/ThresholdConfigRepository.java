package com.poultryprophet.analytics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ThresholdConfigRepository extends JpaRepository<ThresholdConfig, Long> {

    Optional<ThresholdConfig> findByFarmIdAndIndicator(Long farmId, String indicator);

    Optional<ThresholdConfig> findByFarmIdIsNullAndIndicator(String indicator);

    List<ThresholdConfig> findByFarmId(Long farmId);

    /** Global default rows (farmId null) shared by every farm until overridden. */
    List<ThresholdConfig> findByFarmIdIsNull();

    /** Effective threshold for a farm/indicator, falling back to the global default. */
    default Optional<ThresholdConfig> findEffective(Long farmId, String indicator) {
        Optional<ThresholdConfig> specific = findByFarmIdAndIndicator(farmId, indicator);
        return specific.isPresent() ? specific : findByFarmIdIsNullAndIndicator(indicator);
    }
}
