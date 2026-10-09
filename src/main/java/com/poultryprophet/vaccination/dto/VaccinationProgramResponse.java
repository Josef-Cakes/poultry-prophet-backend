package com.poultryprophet.vaccination.dto;
import com.poultryprophet.vaccination.*; import java.time.Instant; import java.util.*;
public record VaccinationProgramResponse(Long id, Long farmId, UUID seriesId, String name, String description, int versionNumber, boolean defaultForNewBatches, Instant createdAt, List<Item> items) {
 public record Item(Long id, int sequenceNumber, String vaccineName, Long farmProductId, int ageOffsetValue, String ageOffsetUnit, int normalizedOffsetDays, int reminderLeadDays, String route, String doseGuidance, String instructions) {}
 public static VaccinationProgramResponse from(VaccinationProgram p, List<VaccinationProgramItem> rows) { return new VaccinationProgramResponse(p.getId(),p.getFarmId(),p.getSeriesId(),p.getName(),p.getDescription(),p.getVersionNumber(),p.isDefaultForNewBatches(),p.getCreatedAt(),rows.stream().map(i->new Item(i.getId(),i.getSequenceNumber(),i.getVaccineName(),i.getFarmProductId(),i.getAgeOffsetValue(),i.getAgeOffsetUnit(),i.getNormalizedOffsetDays(),i.getReminderLeadDays(),i.getRoute(),i.getDoseGuidance(),i.getInstructions())).toList()); }
}
