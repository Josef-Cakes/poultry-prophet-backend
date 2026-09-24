package com.poultryprophet.analytics;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.record.DailyRecord;
import com.poultryprophet.record.ObservationQuality;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** SDD 2.1: computed indicators for a daily record (one-to-one with DailyRecord). */
@Entity
@Table(name = "indicator")
public class Indicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false, unique = true)
    private DailyRecord record;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    private Double bhi;

    /** Only populated while the batch is in the brooding stage. */
    private Double bsi;

    /** Null when the day's feed intake was zero (anomalous). */
    private Double wfr;

    private Double readinessScore;

    /** Raw observations and component scores retained for an explainable validation output. */
    private Double temperatureC;
    private Integer mortalityCount;
    private Double feedIntakeG;
    private Double waterIntakeMl;
    private Double temperatureScore;
    private Double mortalityScore;
    private Double feedScore;
    private Double waterScore;
    private Double temperatureContribution;
    private Double mortalityContribution;
    private Double feedContribution;
    private Double waterContribution;

    @Enumerated(EnumType.STRING)
    private ObservationQuality temperatureQuality;

    @Enumerated(EnumType.STRING)
    private ObservationQuality feedQuality;

    @Enumerated(EnumType.STRING)
    private ObservationQuality waterQuality;

    @Column(length = 64)
    private String formulaVersion;

    @Column
    private Boolean sufficientData;

    @Column(columnDefinition = "text")
    private String missingDataWarning;

    @Column(nullable = false)
    private Instant computedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DailyRecord getRecord() {
        return record;
    }

    public void setRecord(DailyRecord record) {
        this.record = record;
    }

    public Batch getBatch() {
        return batch;
    }

    public void setBatch(Batch batch) {
        this.batch = batch;
    }

    public Double getBhi() {
        return bhi;
    }

    public void setBhi(Double bhi) {
        this.bhi = bhi;
    }

    public Double getBsi() {
        return bsi;
    }

    public void setBsi(Double bsi) {
        this.bsi = bsi;
    }

    public Double getWfr() {
        return wfr;
    }

    public void setWfr(Double wfr) {
        this.wfr = wfr;
    }

    public Double getReadinessScore() {
        return readinessScore;
    }

    public void setReadinessScore(Double readinessScore) {
        this.readinessScore = readinessScore;
    }

    public Double getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(Double temperatureC) {
        this.temperatureC = temperatureC;
    }

    public Integer getMortalityCount() {
        return mortalityCount;
    }

    public void setMortalityCount(Integer mortalityCount) {
        this.mortalityCount = mortalityCount;
    }

    public Double getFeedIntakeG() {
        return feedIntakeG;
    }

    public void setFeedIntakeG(Double feedIntakeG) {
        this.feedIntakeG = feedIntakeG;
    }

    public Double getWaterIntakeMl() {
        return waterIntakeMl;
    }

    public void setWaterIntakeMl(Double waterIntakeMl) {
        this.waterIntakeMl = waterIntakeMl;
    }

    public Double getTemperatureScore() {
        return temperatureScore;
    }

    public void setTemperatureScore(Double temperatureScore) {
        this.temperatureScore = temperatureScore;
    }

    public Double getMortalityScore() {
        return mortalityScore;
    }

    public void setMortalityScore(Double mortalityScore) {
        this.mortalityScore = mortalityScore;
    }

    public Double getFeedScore() {
        return feedScore;
    }

    public void setFeedScore(Double feedScore) {
        this.feedScore = feedScore;
    }

    public Double getWaterScore() {
        return waterScore;
    }

    public void setWaterScore(Double waterScore) {
        this.waterScore = waterScore;
    }

    public Double getTemperatureContribution() {
        return temperatureContribution;
    }

    public void setTemperatureContribution(Double temperatureContribution) {
        this.temperatureContribution = temperatureContribution;
    }

    public Double getMortalityContribution() {
        return mortalityContribution;
    }

    public void setMortalityContribution(Double mortalityContribution) {
        this.mortalityContribution = mortalityContribution;
    }

    public Double getFeedContribution() {
        return feedContribution;
    }

    public void setFeedContribution(Double feedContribution) {
        this.feedContribution = feedContribution;
    }

    public Double getWaterContribution() {
        return waterContribution;
    }

    public void setWaterContribution(Double waterContribution) {
        this.waterContribution = waterContribution;
    }

    public ObservationQuality getTemperatureQuality() {
        return temperatureQuality;
    }

    public void setTemperatureQuality(ObservationQuality temperatureQuality) {
        this.temperatureQuality = temperatureQuality;
    }

    public ObservationQuality getFeedQuality() {
        return feedQuality;
    }

    public void setFeedQuality(ObservationQuality feedQuality) {
        this.feedQuality = feedQuality;
    }

    public ObservationQuality getWaterQuality() {
        return waterQuality;
    }

    public void setWaterQuality(ObservationQuality waterQuality) {
        this.waterQuality = waterQuality;
    }

    public String getFormulaVersion() {
        return formulaVersion;
    }

    public void setFormulaVersion(String formulaVersion) {
        this.formulaVersion = formulaVersion;
    }

    public boolean isSufficientData() {
        return Boolean.TRUE.equals(sufficientData);
    }

    public void setSufficientData(boolean sufficientData) {
        this.sufficientData = sufficientData;
    }

    public String getMissingDataWarning() {
        return missingDataWarning;
    }

    public void setMissingDataWarning(String missingDataWarning) {
        this.missingDataWarning = missingDataWarning;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
