package com.poultryprophet.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long>, JpaSpecificationExecutor<FinancialTransaction> {
    List<FinancialTransaction> findByFarmIdOrderByTransactionDateDescCreatedAtDesc(Long farmId);
    List<FinancialTransaction> findByFarmIdAndTransactionDateBetweenOrderByTransactionDateAsc(Long farmId, LocalDate start, LocalDate end);
    List<FinancialTransaction> findByFarmIdAndBatchIdAndTransactionDateBetweenOrderByTransactionDateAsc(
            Long farmId, Long batchId, LocalDate start, LocalDate end);
    Optional<FinancialTransaction> findByIdAndFarmId(Long id, Long farmId);
    boolean existsByFarmIdAndBatchIdAndCreatedAtAfter(Long farmId, Long batchId, Instant cutoff);
    Optional<FinancialTransaction> findByFarmIdAndSourceOperationId(Long farmId, String sourceOperationId);
    Optional<FinancialTransaction> findByFarmIdAndSourceEventId(Long farmId, Long sourceEventId);
}
