package com.poultryprophet.analytics;

import com.poultryprophet.analytics.dto.ThresholdResponse;
import com.poultryprophet.analytics.dto.UpdateThresholdRequest;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Legacy provisional indicator thresholds retained outside the primary MVP workflow. */
@RestController
@RequestMapping("/api/legacy/thresholds")
public class ThresholdController {

    private static final Set<String> MVP_INDICATORS = Set.of("BHI", "BSI", "WFR");

    private final ThresholdConfigRepository thresholdRepository;

    public ThresholdController(ThresholdConfigRepository thresholdRepository) {
        this.thresholdRepository = thresholdRepository;
    }

    @GetMapping
    public List<ThresholdResponse> list(@AuthenticationPrincipal CustomUserDetails principal) {
        Map<String, ThresholdConfig> effective = new LinkedHashMap<>();
        thresholdRepository.findByFarmIdIsNull()
                .forEach(threshold -> effective.put(threshold.getIndicator(), threshold));
        Long farmId = principal.getFarmId();
        if (farmId != null) {
            thresholdRepository.findByFarmId(farmId)
                    .forEach(threshold -> effective.put(threshold.getIndicator(), threshold));
        }
        return effective.values().stream()
                .filter(threshold -> MVP_INDICATORS.contains(threshold.getIndicator()))
                .map(ThresholdResponse::from)
                .toList();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ThresholdResponse update(@PathVariable Long id,
                                    @Valid @RequestBody UpdateThresholdRequest request,
                                    @AuthenticationPrincipal CustomUserDetails principal) {
        validateCommonRange(request);
        Long farmId = principal.getFarmId();
        if (farmId == null) {
            throw new BadRequestException("You must belong to a farm to edit thresholds");
        }

        ThresholdConfig threshold = thresholdRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Threshold " + id + " not found"));
        if (!MVP_INDICATORS.contains(threshold.getIndicator())) {
            throw new BadRequestException("This threshold is not part of the controlled MVP");
        }
        validateIndicatorRange(threshold.getIndicator(), request);

        ThresholdConfig target;
        if (threshold.getFarmId() == null) {
            target = thresholdRepository.findByFarmIdAndIndicator(farmId, threshold.getIndicator())
                    .orElseGet(() -> new ThresholdConfig(
                            farmId, threshold.getIndicator(), request.minValue(), request.maxValue()));
        } else if (threshold.getFarmId().equals(farmId)) {
            target = threshold;
        } else {
            throw new BadRequestException("Cannot edit a threshold outside your farm");
        }

        target.setMinValue(request.minValue());
        target.setMaxValue(request.maxValue());
        return ThresholdResponse.from(thresholdRepository.save(target));
    }

    private static void validateCommonRange(UpdateThresholdRequest request) {
        if (request == null
                || request.minValue() == null
                || request.maxValue() == null
                || !Double.isFinite(request.minValue())
                || !Double.isFinite(request.maxValue())) {
            throw new BadRequestException("Threshold values must be finite numbers");
        }
        if (request.minValue() < 0 || request.maxValue() < 0) {
            throw new BadRequestException("Threshold values must be greater than or equal to 0");
        }
        if (request.minValue() > request.maxValue()) {
            throw new BadRequestException("minValue must not exceed maxValue");
        }
    }

    private static void validateIndicatorRange(String indicator, UpdateThresholdRequest request) {
        if (("BHI".equals(indicator) || "BSI".equals(indicator))
                && (request.minValue() > 100 || request.maxValue() > 100)) {
            throw new BadRequestException(indicator + " thresholds must be between 0 and 100");
        }
    }
}
