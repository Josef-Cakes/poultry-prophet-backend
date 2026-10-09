package com.poultryprophet.report;

import com.poultryprophet.alert.AlertRepository;
import com.poultryprophet.alert.dto.AlertResponse;
import com.poultryprophet.analytics.Indicator;
import com.poultryprophet.analytics.IndicatorRepository;
import com.poultryprophet.analytics.dto.IndicatorResponse;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.report.dto.ReportPayload;
import com.poultryprophet.report.dto.ReportResponse;
import com.poultryprophet.user.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** SDD 3.2: aggregates indicators, records and alerts for a period into a report payload. */
@Service
@ConditionalOnProperty(name = "app.features.legacy-indicators-enabled", havingValue = "true")
public class ReportService {

    private final ReportRepository reportRepository;
    private final IndicatorRepository indicatorRepository;
    private final com.poultryprophet.record.DailyRecordRepository recordRepository;
    private final AlertRepository alertRepository;
    private final BatchService batchService;
    private final UserRepository userRepository;

    public ReportService(ReportRepository reportRepository,
                         IndicatorRepository indicatorRepository,
                         com.poultryprophet.record.DailyRecordRepository recordRepository,
                         AlertRepository alertRepository,
                         BatchService batchService,
                         UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.indicatorRepository = indicatorRepository;
        this.recordRepository = recordRepository;
        this.alertRepository = alertRepository;
        this.batchService = batchService;
        this.userRepository = userRepository;
    }

    @Transactional
    public ReportResponse build(Long batchId, Long farmId, LocalDate periodStart, LocalDate periodEnd, Long userId) {
        if (periodStart.isAfter(periodEnd)) {
            throw new BadRequestException("periodStart must not be after periodEnd");
        }
        Batch batch = batchService.requireWritableBatch(batchId, farmId);

        Instant startInstant = periodStart.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endInstant = periodEnd.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        Double avgBhi = round(indicatorRepository.avgBhiBetween(batchId, startInstant, endInstant), 1);
        Double avgWfr = round(indicatorRepository.avgWfrBetween(batchId, startInstant, endInstant), 2);
        long totalMortality = recordRepository.sumMortalityBetween(batchId, periodStart, periodEnd);

        List<Indicator> indicators = indicatorRepository
                .findByBatchIdAndComputedAtBetweenOrderByComputedAtAsc(batchId, startInstant, endInstant);
        List<IndicatorResponse> trend = indicators.stream().map(IndicatorResponse::from).toList();

        Double readinessScore = indicators.isEmpty()
                ? avgBhi
                : round(indicators.get(indicators.size() - 1).getReadinessScore(), 1);

        List<AlertResponse> significantAlerts = alertRepository
                .findByBatchIdAndCreatedAtBetweenOrderByCreatedAtAsc(batchId, startInstant, endInstant)
                .stream()
                .map(AlertResponse::from)
                .toList();

        Report report = new Report();
        report.setBatch(batch);
        report.setGeneratedBy(userRepository.getReferenceById(userId));
        report.setPeriodStart(periodStart);
        report.setPeriodEnd(periodEnd);
        report.setAvgBhi(avgBhi);
        report.setAvgWfr(avgWfr);
        report.setTotalMortality(totalMortality);
        report.setReadinessScore(readinessScore);
        reportRepository.save(report);

        ReportPayload payload = new ReportPayload(
                batch.getId(), batch.getName(), periodStart, periodEnd,
                avgBhi, avgWfr, totalMortality, readinessScore, trend, significantAlerts);
        return new ReportResponse(report.getId(), payload);
    }

    private Double round(Double value, int decimals) {
        if (value == null) {
            return null;
        }
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
