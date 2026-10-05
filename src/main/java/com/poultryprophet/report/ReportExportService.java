package com.poultryprophet.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.poultryprophet.alert.Alert;
import com.poultryprophet.alert.AlertRepository;
import com.poultryprophet.analytics.Indicator;
import com.poultryprophet.analytics.IndicatorRepository;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.common.NotFoundException;
import com.poultryprophet.config.ReportProperties;
import com.poultryprophet.report.dto.ExportResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SDD 3.2 ReportExportService: renders a persisted report into PDF (OpenPDF) or CSV and
 * stores the artifact on the local filesystem (stands in for object storage).
 */
@Service
@ConditionalOnProperty(name = "app.features.legacy-indicators-enabled", havingValue = "true")
public class ReportExportService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private final ReportRepository reportRepository;
    private final IndicatorRepository indicatorRepository;
    private final AlertRepository alertRepository;
    private final ReportProperties properties;

    public ReportExportService(ReportRepository reportRepository,
                               IndicatorRepository indicatorRepository,
                               AlertRepository alertRepository,
                               ReportProperties properties) {
        this.reportRepository = reportRepository;
        this.indicatorRepository = indicatorRepository;
        this.alertRepository = alertRepository;
        this.properties = properties;
    }

    @Transactional
    public ExportResult export(Long reportId, Long farmId, String format) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report " + reportId + " not found"));
        if (!report.getBatch().getFarmId().equals(farmId)) {
            throw new NotFoundException("Report " + reportId + " not found");
        }

        String normalized = format == null ? "pdf" : format.toLowerCase();
        if (!normalized.equals("pdf") && !normalized.equals("csv")) {
            throw new BadRequestException("Unsupported export format: " + format);
        }

        Long batchId = report.getBatch().getId();
        Instant start = report.getPeriodStart().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = report.getPeriodEnd().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<Indicator> indicators = indicatorRepository
                .findByBatchIdAndComputedAtBetweenOrderByComputedAtAsc(batchId, start, end);
        List<Alert> alerts = alertRepository
                .findByBatchIdAndCreatedAtBetweenOrderByCreatedAtAsc(batchId, start, end);

        byte[] content = normalized.equals("pdf")
                ? renderPdf(report, indicators, alerts)
                : renderCsv(report, indicators, alerts).getBytes(StandardCharsets.UTF_8);

        String filename = "report-" + reportId + "." + normalized;
        store(filename, content);

        report.setExportFormat(normalized);

        String contentType = normalized.equals("pdf") ? "application/pdf" : "text/csv";
        return new ExportResult(filename, contentType, content);
    }

    private byte[] renderPdf(Report report, List<Indicator> indicators, List<Alert> alerts) {
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("Poultry Prophet — Batch Summary Report", title));
            document.add(new Paragraph("Batch: " + report.getBatch().getName()));
            document.add(new Paragraph("Period: " + report.getPeriodStart() + " to " + report.getPeriodEnd()));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Key Performance Indicators", heading));
            document.add(new Paragraph("Average BHI: " + nullable(report.getAvgBhi())));
            document.add(new Paragraph("Average WFR: " + nullable(report.getAvgWfr())));
            document.add(new Paragraph("Total Mortality: " + report.getTotalMortality()));
            document.add(new Paragraph("Readiness Score: " + nullable(report.getReadinessScore())));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Indicator Trend", heading));
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            addHeaderCells(table, "Date", "BHI", "BSI", "WFR", "Readiness");
            for (Indicator indicator : indicators) {
                table.addCell(DATE_FMT.format(indicator.getComputedAt()));
                table.addCell(String.valueOf(indicator.getBhi()));
                table.addCell(nullable(indicator.getBsi()));
                table.addCell(nullable(indicator.getWfr()));
                table.addCell(String.valueOf(indicator.getReadinessScore()));
            }
            document.add(table);
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Significant Alerts", heading));
            if (alerts.isEmpty()) {
                document.add(new Paragraph("None recorded in this period."));
            } else {
                for (Alert alert : alerts) {
                    document.add(new Paragraph(String.format("[%s] %s — %s (%s)",
                            alert.getSeverity(), alert.getIndicatorType(), alert.getMessage(),
                            DATE_FMT.format(alert.getCreatedAt()))));
                }
            }
            document.close();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Failed to render PDF report", ex);
        }
        return out.toByteArray();
    }

    private void addHeaderCells(PdfPTable table, String... headers) {
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
        for (String header : headers) {
            com.lowagie.text.pdf.PdfPCell cell =
                    new com.lowagie.text.pdf.PdfPCell(new Paragraph(header, bold));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private String renderCsv(Report report, List<Indicator> indicators, List<Alert> alerts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Poultry Prophet - Batch Summary Report\n");
        sb.append("Batch,").append(escape(report.getBatch().getName())).append('\n');
        sb.append("Period,").append(report.getPeriodStart()).append(" to ").append(report.getPeriodEnd()).append('\n');
        sb.append("Average BHI,").append(nullable(report.getAvgBhi())).append('\n');
        sb.append("Average WFR,").append(nullable(report.getAvgWfr())).append('\n');
        sb.append("Total Mortality,").append(report.getTotalMortality()).append('\n');
        sb.append("Readiness Score,").append(nullable(report.getReadinessScore())).append('\n');
        sb.append('\n');

        sb.append("Date,BHI,BSI,WFR,Readiness\n");
        for (Indicator indicator : indicators) {
            sb.append(DATE_FMT.format(indicator.getComputedAt())).append(',')
                    .append(indicator.getBhi()).append(',')
                    .append(nullable(indicator.getBsi())).append(',')
                    .append(nullable(indicator.getWfr())).append(',')
                    .append(indicator.getReadinessScore()).append('\n');
        }
        sb.append('\n');

        sb.append("Alerts\n");
        sb.append("Date,Severity,Indicator,Message\n");
        for (Alert alert : alerts) {
            sb.append(DATE_FMT.format(alert.getCreatedAt())).append(',')
                    .append(alert.getSeverity()).append(',')
                    .append(escape(alert.getIndicatorType())).append(',')
                    .append(escape(alert.getMessage())).append('\n');
        }
        return sb.toString();
    }

    private void store(String filename, byte[] content) {
        try {
            Path dir = Paths.get(properties.getOutputDir());
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), content);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to store report artifact", ex);
        }
    }

    private String nullable(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
