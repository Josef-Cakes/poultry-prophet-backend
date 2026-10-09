package com.poultryprophet.bird;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.bird.dto.BirdResponse;
import com.poultryprophet.bird.dto.CreateBirdRequest;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Blueprint 5.4: registers and bands individual birds within a batch. Banding happens around
 * week 4 (start of ranging) so that growth, health and behaviour can be tracked per bird.
 */
@Service
public class BirdService {

    private final BirdRepository birdRepository;
    private final BatchService batchService;

    public BirdService(BirdRepository birdRepository, BatchService batchService) {
        this.birdRepository = birdRepository;
        this.batchService = batchService;
    }

    @Transactional
    public BirdResponse band(Long batchId, Long farmId, CreateBirdRequest request) {
        Batch batch = batchService.requireWritableBatch(batchId, farmId);
        if (birdRepository.existsByBatchIdAndBandNumberIgnoreCase(batchId, request.bandNumber())) {
            throw new BadRequestException(
                    "Band number '" + request.bandNumber() + "' already exists in this batch");
        }
        Bird bird = new Bird();
        bird.setBatch(batch);
        bird.setBandNumber(request.bandNumber());
        bird.setNotes(request.notes());
        return BirdResponse.from(birdRepository.save(bird));
    }

    @Transactional(readOnly = true)
    public List<BirdResponse> listForBatch(Long batchId, Long farmId) {
        batchService.requireBatch(batchId, farmId);
        return birdRepository.findByBatchIdOrderByBandNumberAsc(batchId).stream()
                .map(BirdResponse::from)
                .toList();
    }

    /** Shared accessor enforcing batch + farm scoping. */
    @Transactional(readOnly = true)
    public Bird requireBird(Long birdId, Long batchId, Long farmId) {
        batchService.requireBatch(batchId, farmId);
        return birdRepository.findByIdAndBatchId(birdId, batchId)
                .orElseThrow(() -> new NotFoundException("Bird " + birdId + " not found in batch " + batchId));
    }

    /** Same lookup for ranging/selection writes; archived batches remain viewable but immutable. */
    @Transactional(readOnly = true)
    public Bird requireWritableBird(Long birdId, Long batchId, Long farmId) {
        batchService.requireWritableBatch(batchId, farmId);
        return birdRepository.findByIdAndBatchId(birdId, batchId)
                .orElseThrow(() -> new NotFoundException("Bird " + birdId + " not found in batch " + batchId));
    }
}
