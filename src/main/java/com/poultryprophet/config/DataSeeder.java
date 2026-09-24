package com.poultryprophet.config;

import com.poultryprophet.analytics.ThresholdConfig;
import com.poultryprophet.analytics.ThresholdConfigRepository;
import com.poultryprophet.batch.LifecycleStage;
import com.poultryprophet.batch.LifecycleStageRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Seeds reference data (lifecycle stages and default alert thresholds) on first start. */
@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner seedReferenceData(LifecycleStageRepository stageRepository,
                                               ThresholdConfigRepository thresholdRepository) {
        return args -> {
            if (stageRepository.count() == 0) {
                // Lifecycle reference data. Later stages are retained for compatibility but are not
                // evidence that selection or prediction is an MVP capability.
                stageRepository.saveAll(List.of(
                        new LifecycleStage("brooding", 0),
                        new LifecycleStage("ranging", 1),
                        new LifecycleStage("pre-conditioning", 2),
                        new LifecycleStage("maintenance", 3),
                        new LifecycleStage("conditioning", 4)));
            }

            // Global default thresholds (farmId null). Provisional bands per SDD preface.
            seedThreshold(thresholdRepository, "BHI", 60.0, 100.0);
            seedThreshold(thresholdRepository, "BSI", 0.0, 40.0);
            seedThreshold(thresholdRepository, "WFR", 1.5, 2.5);
            seedThreshold(thresholdRepository, "CRS", 60.0, 100.0);
        };
    }

    private void seedThreshold(ThresholdConfigRepository repository, String indicator, double min, double max) {
        if (repository.findByFarmIdIsNullAndIndicator(indicator).isEmpty()) {
            repository.save(new ThresholdConfig(null, indicator, min, max));
        }
    }
}
