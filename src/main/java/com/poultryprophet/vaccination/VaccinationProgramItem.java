package com.poultryprophet.vaccination;

import jakarta.persistence.*;

@Entity
@Table(name = "vaccination_program_item")
public class VaccinationProgramItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "program_id", nullable = false) private Long programId;
    @Column(name = "sequence_number", nullable = false) private int sequenceNumber;
    @Column(name = "vaccine_name", nullable = false) private String vaccineName;
    @Column(name = "farm_product_id") private Long farmProductId;
    @Column(name = "age_offset_value", nullable = false) private int ageOffsetValue;
    @Column(name = "age_offset_unit", nullable = false) private String ageOffsetUnit;
    @Column(name = "normalized_offset_days", nullable = false) private int normalizedOffsetDays;
    @Column(name = "reminder_lead_days", nullable = false) private int reminderLeadDays = 1;
    private String route;
    @Column(name = "dose_guidance", length = 500) private String doseGuidance;
    @Column(columnDefinition = "text") private String instructions;
    @Column(nullable = false) private boolean active = true;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getProgramId(){return programId;} public void setProgramId(Long v){programId=v;}
    public int getSequenceNumber(){return sequenceNumber;} public void setSequenceNumber(int v){sequenceNumber=v;} public String getVaccineName(){return vaccineName;} public void setVaccineName(String v){vaccineName=v;}
    public Long getFarmProductId(){return farmProductId;} public void setFarmProductId(Long v){farmProductId=v;} public int getAgeOffsetValue(){return ageOffsetValue;} public void setAgeOffsetValue(int v){ageOffsetValue=v;}
    public String getAgeOffsetUnit(){return ageOffsetUnit;} public void setAgeOffsetUnit(String v){ageOffsetUnit=v;} public int getNormalizedOffsetDays(){return normalizedOffsetDays;} public void setNormalizedOffsetDays(int v){normalizedOffsetDays=v;}
    public int getReminderLeadDays(){return reminderLeadDays;} public void setReminderLeadDays(int v){reminderLeadDays=v;} public String getRoute(){return route;} public void setRoute(String v){route=v;}
    public String getDoseGuidance(){return doseGuidance;} public void setDoseGuidance(String v){doseGuidance=v;} public String getInstructions(){return instructions;} public void setInstructions(String v){instructions=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
