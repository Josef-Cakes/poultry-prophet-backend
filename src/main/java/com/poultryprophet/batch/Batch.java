package com.poultryprophet.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "batch", uniqueConstraints = @UniqueConstraint(columnNames = {"farm_id", "name"}))
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int initialPopulation;

    @Column(nullable = false)
    private int currentPopulation;

    @Column(nullable = false)
    private LocalDate startDate;

    /**
     * Blueprint 5.3: bloodline/breed is collected as descriptive, filterable metadata only.
     * It is deliberately NOT used in scoring (breed-based scoring cannot be validated without
     * large cross-breed outcome data). Nullable.
     */
    @Column
    private String bloodline;

    /** Blueprint 5.2 Stage 0: provenance, e.g. "own hatch" vs "purchased". Nullable. */
    @Column
    private String source;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private LifecycleStage stage;

    /**
     * Legacy compatibility column. Stage resolution is now always age-based; this flag is no
     * longer used to permit manual stage overrides on existing rows.
     */
    @Column(nullable = false)
    private boolean stageManual = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status = BatchStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** SDD 2.2: stage helper consumed by the worker before invoking computeBsi. */
    public boolean isBrooding() {
        return stage != null && "brooding".equalsIgnoreCase(stage.getName());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFarmId() {
        return farmId;
    }

    public void setFarmId(Long farmId) {
        this.farmId = farmId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getInitialPopulation() {
        return initialPopulation;
    }

    public void setInitialPopulation(int initialPopulation) {
        this.initialPopulation = initialPopulation;
    }

    public int getCurrentPopulation() {
        return currentPopulation;
    }

    public void setCurrentPopulation(int currentPopulation) {
        this.currentPopulation = currentPopulation;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public String getBloodline() {
        return bloodline;
    }

    public void setBloodline(String bloodline) {
        this.bloodline = bloodline;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LifecycleStage getStage() {
        return stage;
    }

    public void setStage(LifecycleStage stage) {
        this.stage = stage;
    }

    public boolean isStageManual() {
        return stageManual;
    }

    public void setStageManual(boolean stageManual) {
        this.stageManual = stageManual;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public void setStatus(BatchStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
