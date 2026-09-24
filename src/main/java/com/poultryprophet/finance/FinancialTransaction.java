package com.poultryprophet.finance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "farm_financial_transaction")
public class FinancialTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long farmId;
    private Long batchId;
    private Long incubationCycleId;
    @Column(nullable = false) private LocalDate transactionDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private FinanceTransactionType type;
    @Column(nullable = false) private String category;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency = "PHP";
    private String counterparty;
    @Column(columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private FinanceTransactionStatus status = FinanceTransactionStatus.POSTED;
    @Column(nullable = false) private Long enteredBy;
    @Column(columnDefinition = "text") private String voidReason;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFarmId() { return farmId; }
    public void setFarmId(Long farmId) { this.farmId = farmId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getIncubationCycleId() { return incubationCycleId; }
    public void setIncubationCycleId(Long incubationCycleId) { this.incubationCycleId = incubationCycleId; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    public FinanceTransactionType getType() { return type; }
    public void setType(FinanceTransactionType type) { this.type = type; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCounterparty() { return counterparty; }
    public void setCounterparty(String counterparty) { this.counterparty = counterparty; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public FinanceTransactionStatus getStatus() { return status; }
    public void setStatus(FinanceTransactionStatus status) { this.status = status; }
    public Long getEnteredBy() { return enteredBy; }
    public void setEnteredBy(Long enteredBy) { this.enteredBy = enteredBy; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
