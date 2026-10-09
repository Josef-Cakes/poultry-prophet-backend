package com.poultryprophet.selection;

import com.poultryprophet.bird.Bird;
import com.poultryprophet.bird.BirdService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.scoring.BirdScore;
import com.poultryprophet.scoring.BirdScoreRepository;
import com.poultryprophet.scoring.ScoringProperties;
import com.poultryprophet.scoring.ScoringService;
import com.poultryprophet.selection.dto.SelectionDecisionRequest;
import com.poultryprophet.selection.dto.SelectionRowResponse;
import com.poultryprophet.selection.dto.SelectionViewResponse;
import com.poultryprophet.user.User;
import com.poultryprophet.user.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Blueprint Module 3: produces the ranked month-5 selection view and records the manager's
 * confirm/override decision. The system recommends advancement when CRS meets the configured
 * cut-line; the breeder always makes the final call, and overrides must carry a reason.
 */
@Service
@ConditionalOnProperty(name = "app.features.legacy-individual-selection-enabled", havingValue = "true")
public class SelectionService {

    private final ScoringService scoringService;
    private final BirdScoreRepository birdScoreRepository;
    private final SelectionRecordRepository selectionRepository;
    private final BirdService birdService;
    private final UserRepository userRepository;
    private final ScoringProperties props;

    public SelectionService(ScoringService scoringService,
                            BirdScoreRepository birdScoreRepository,
                            SelectionRecordRepository selectionRepository,
                            BirdService birdService,
                            UserRepository userRepository,
                            ScoringProperties props) {
        this.scoringService = scoringService;
        this.birdScoreRepository = birdScoreRepository;
        this.selectionRepository = selectionRepository;
        this.birdService = birdService;
        this.userRepository = userRepository;
        this.props = props;
    }

    /** Recomputes scores, ranks birds by CRS desc, and attaches recommendations + any decisions. */
    @Transactional
    public SelectionViewResponse getSelectionView(Long batchId, Long farmId) {
        List<BirdScore> ranked = scoringService.recomputeForBatch(batchId, farmId);
        double cutLine = props.getCutLineCrs();

        Map<Long, SelectionRecord> decisions = new HashMap<>();
        for (SelectionRecord record : selectionRepository.findByBatchId(batchId)) {
            decisions.put(record.getBird().getId(), record);
        }

        List<SelectionRowResponse> rows = new java.util.ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            BirdScore score = ranked.get(i);
            boolean recommended = score.getCrs() >= cutLine;
            rows.add(SelectionRowResponse.from(
                    i + 1, score, recommended, decisions.get(score.getBird().getId())));
        }
        return new SelectionViewResponse(batchId, cutLine, rows);
    }

    /** Records a confirm/override for one bird. An override (decision != recommendation) needs a reason. */
    @Transactional
    public SelectionRowResponse decide(Long batchId, Long birdId, Long farmId, Long managerId,
                                       SelectionDecisionRequest request) {
        Bird bird = birdService.requireWritableBird(birdId, batchId, farmId);
        BirdScore score = birdScoreRepository.findByBirdId(birdId)
                .orElseThrow(() -> new BadRequestException(
                        "Bird " + birdId + " has not been scored yet; open the selection view first"));

        boolean recommended = score.getCrs() >= props.getCutLineCrs();
        boolean advance = request.advance();
        boolean overridden = advance != recommended;
        if (overridden && (request.reason() == null || request.reason().isBlank())) {
            throw new BadRequestException("An override requires a reason");
        }

        SelectionRecord record = selectionRepository.findByBirdId(birdId).orElseGet(SelectionRecord::new);
        User manager = userRepository.getReferenceById(managerId);
        record.setBird(bird);
        record.setBatch(bird.getBatch());
        record.setCrsAtDecision(score.getCrs());
        record.setRecommendedAdvance(recommended);
        record.setOutcome(advance ? SelectionOutcome.ADVANCE : SelectionOutcome.REJECT);
        record.setOverridden(overridden);
        record.setReason(overridden ? request.reason() : null);
        record.setDecidedBy(manager);
        record.setDecidedAt(Instant.now());
        SelectionRecord saved = selectionRepository.save(record);

        int rank = rankOf(batchId, birdId);
        return SelectionRowResponse.from(rank, score, recommended, saved);
    }

    private int rankOf(Long batchId, Long birdId) {
        List<BirdScore> ranked = birdScoreRepository.findByBatchIdOrderByCrsDesc(batchId);
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).getBird().getId().equals(birdId)) {
                return i + 1;
            }
        }
        return 0;
    }
}
