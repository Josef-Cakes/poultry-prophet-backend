package com.poultryprophet.vaccination;
import com.poultryprophet.security.CustomUserDetails;
import com.poultryprophet.user.Role;
import com.poultryprophet.vaccination.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class VaccinationController {
    private final VaccinationService service;
    public VaccinationController(VaccinationService service){this.service=service;}
    @GetMapping("/api/vaccination-programs") public List<VaccinationProgramResponse> programs(@AuthenticationPrincipal CustomUserDetails p){return service.listPrograms(p.getFarmId());}
    @PostMapping("/api/vaccination-programs") @PreAuthorize("hasAnyRole('MANAGER','HANDLER')") public VaccinationProgramResponse create(@Valid @RequestBody CreateVaccinationProgramRequest r,@AuthenticationPrincipal CustomUserDetails p){return service.createProgram(p.getFarmId(),p.getId(),r);}
    @GetMapping("/api/batches/{batchId}/vaccination-plan") public List<VaccinationPlanItemResponse> plan(@PathVariable Long batchId,@AuthenticationPrincipal CustomUserDetails p){return service.plan(batchId,p.getFarmId());}
    @PostMapping("/api/batches/{batchId}/vaccination-plan") @PreAuthorize("hasAnyRole('MANAGER','HANDLER')") public List<VaccinationPlanItemResponse> assign(@PathVariable Long batchId,@Valid @RequestBody AssignVaccinationProgramRequest r,@AuthenticationPrincipal CustomUserDetails p){return service.assign(batchId,p.getFarmId(),p.getId(),r);}
    @PutMapping("/api/batches/{batchId}/vaccination-plan") @PreAuthorize("hasAnyRole('MANAGER','HANDLER')") public List<VaccinationPlanItemResponse> replace(@PathVariable Long batchId,@Valid @RequestBody ReplaceVaccinationPlanRequest r,@AuthenticationPrincipal CustomUserDetails p){return service.replace(batchId,p.getFarmId(),p.getId(),r);}
    @PostMapping("/api/vaccination-plan-items/{id}/record") public VaccinationPlanItemResponse record(@PathVariable Long id,@RequestBody RecordVaccinationRequest r,@AuthenticationPrincipal CustomUserDetails p){return service.record(id,p.getFarmId(),p.getId(),p.getUser().getRole(),r);}
    @PostMapping("/api/vaccination-plan-items/{id}/skip") @PreAuthorize("hasRole('MANAGER')") public VaccinationPlanItemResponse skip(@PathVariable Long id,@Valid @RequestBody SkipVaccinationRequest r,@AuthenticationPrincipal CustomUserDetails p){return service.skip(id,p.getFarmId(),p.getId(),r);}
}
