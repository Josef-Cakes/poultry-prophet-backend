package com.poultryprophet.ranging;

import com.poultryprophet.bird.Bird;
import com.poultryprophet.bird.BirdService;
import com.poultryprophet.ranging.dto.CreateRangingRecordRequest;
import com.poultryprophet.ranging.dto.RangingRecordResponse;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Blueprint 5.2 Stage 2: weekly per-bird ranging milestone capture with idempotent upsert
 * keyed on (bird, record_date). These records feed the per-bird growth, health-history and
 * behavioural sub-scores computed by the scoring engine.
 */
@Service
public class RangingRecordService {

    private final RangingRecordRepository recordRepository;
    private final BirdService birdService;
    private final UserRepository userRepository;

    public RangingRecordService(RangingRecordRepository recordRepository,
                                BirdService birdService,
                                UserRepository userRepository) {
        this.recordRepository = recordRepository;
        this.birdService = birdService;
        this.userRepository = userRepository;
    }

    @Transactional
    public RangingRecordResponse record(Long batchId, Long birdId, Long farmId,
                                        CreateRangingRecordRequest req, Long handlerId) {
        Bird bird = birdService.requireWritableBird(birdId, batchId, farmId);
        LocalDate date = req.recordDate() != null ? req.recordDate() : LocalDate.now();

        RangingRecord record = recordRepository.findByBirdIdAndRecordDate(birdId, date)
                .orElseGet(RangingRecord::new);

        User handler = userRepository.getReferenceById(handlerId);
        record.setBird(bird);
        record.setHandler(handler);
        record.setRecordDate(date);
        record.setWeightG(req.weightG());
        record.setHealthEvent(req.healthEvent() != null ? req.healthEvent() : HealthEventSeverity.NONE);
        record.setTemperamentNotes(req.temperamentNotes());
        record.setQualityRating(req.qualityRating());
        record.setUpdatedAt(Instant.now());

        return RangingRecordResponse.from(recordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public List<RangingRecordResponse> listForBird(Long batchId, Long birdId, Long farmId) {
        birdService.requireBird(birdId, batchId, farmId);
        return recordRepository.findByBirdIdOrderByRecordDateAsc(birdId).stream()
                .map(RangingRecordResponse::from)
                .toList();
    }
}
