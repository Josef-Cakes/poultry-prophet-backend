package com.poultryprophet.incubation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "incubation_cycle",
        uniqueConstraints = @UniqueConstraint(columnNames = {"farm_id", "cycle_name"}))
public class IncubationCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(name = "cycle_name", nullable = false)
    private String cycleName;

    @Column(name = "incubator_code", nullable = false)
    private String incubatorCode;

    @Column(name = "egg_source")
    private String eggSource;

    @Column
    private String bloodline;

    @Column(name = "loaded_date", nullable = false)
    private LocalDate loadedDate;

    @Column(name = "eggs_loaded", nullable = false)
    private int eggsLoaded;

    @Column(name = "expected_hatch_date")
    private LocalDate expectedHatchDate;

    @Column(name = "actual_hatch_date")
    private LocalDate actualHatchDate;

    @Column(name = "hatched_count")
    private Integer hatchedCount;

    @Column(name = "unhatched_count")
    private Integer unhatchedCount;

    @Column(name = "removed_damaged_count")
    private Integer removedDamagedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncubationStatus status = IncubationStatus.LOADED;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_batch_id")
    private Long createdBatchId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFarmId() { return farmId; }
    public void setFarmId(Long farmId) { this.farmId = farmId; }
    public String getCycleName() { return cycleName; }
    public void setCycleName(String cycleName) { this.cycleName = cycleName; }
    public String getIncubatorCode() { return incubatorCode; }
    public void setIncubatorCode(String incubatorCode) { this.incubatorCode = incubatorCode; }
    public String getEggSource() { return eggSource; }
    public void setEggSource(String eggSource) { this.eggSource = eggSource; }
    public String getBloodline() { return bloodline; }
    public void setBloodline(String bloodline) { this.bloodline = bloodline; }
    public LocalDate getLoadedDate() { return loadedDate; }
    public void setLoadedDate(LocalDate loadedDate) { this.loadedDate = loadedDate; }
    public int getEggsLoaded() { return eggsLoaded; }
    public void setEggsLoaded(int eggsLoaded) { this.eggsLoaded = eggsLoaded; }
    public LocalDate getExpectedHatchDate() { return expectedHatchDate; }
    public void setExpectedHatchDate(LocalDate expectedHatchDate) { this.expectedHatchDate = expectedHatchDate; }
    public LocalDate getActualHatchDate() { return actualHatchDate; }
    public void setActualHatchDate(LocalDate actualHatchDate) { this.actualHatchDate = actualHatchDate; }
    public Integer getHatchedCount() { return hatchedCount; }
    public void setHatchedCount(Integer hatchedCount) { this.hatchedCount = hatchedCount; }
    public Integer getUnhatchedCount() { return unhatchedCount; }
    public void setUnhatchedCount(Integer unhatchedCount) { this.unhatchedCount = unhatchedCount; }
    public Integer getRemovedDamagedCount() { return removedDamagedCount; }
    public void setRemovedDamagedCount(Integer removedDamagedCount) { this.removedDamagedCount = removedDamagedCount; }
    public IncubationStatus getStatus() { return status; }
    public void setStatus(IncubationStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getClosedBy() { return closedBy; }
    public void setClosedBy(Long closedBy) { this.closedBy = closedBy; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public Long getCreatedBatchId() { return createdBatchId; }
    public void setCreatedBatchId(Long createdBatchId) { this.createdBatchId = createdBatchId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
