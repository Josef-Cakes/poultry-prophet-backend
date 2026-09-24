package com.poultryprophet.farm;

import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.farm.dto.FarmResponse;
import com.poultryprophet.farm.dto.UpdateFarmRequest;
import com.poultryprophet.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own farm profile (name/location/description), used by farm onboarding. */
@RestController
@RequestMapping("/api/farm")
public class FarmController {

    private final FarmRepository farmRepository;

    public FarmController(FarmRepository farmRepository) {
        this.farmRepository = farmRepository;
    }

    @GetMapping
    public FarmResponse current(@AuthenticationPrincipal CustomUserDetails principal) {
        return FarmResponse.from(loadFarm(principal));
    }

    @PutMapping
    @PreAuthorize("hasRole('MANAGER')")
    public FarmResponse update(@Valid @RequestBody UpdateFarmRequest request,
                               @AuthenticationPrincipal CustomUserDetails principal) {
        Farm farm = loadFarm(principal);
        farm.setName(request.name().trim());
        farm.setLocation(blankToNull(request.location()));
        farm.setDescription(blankToNull(request.description()));
        return FarmResponse.from(farmRepository.save(farm));
    }

    private Farm loadFarm(CustomUserDetails principal) {
        Long farmId = principal.getFarmId();
        if (farmId == null) {
            throw new BadRequestException("You are not assigned to a farm");
        }
        return farmRepository.findById(farmId)
                .orElseThrow(() -> new NotFoundException("Farm " + farmId + " not found"));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
