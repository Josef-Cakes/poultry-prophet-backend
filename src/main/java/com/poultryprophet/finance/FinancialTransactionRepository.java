package com.poultryprophet.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {
    List<FinancialTransaction> findByFarmIdOrderByTransactionDateDescCreatedAtDesc(Long farmId);
    List<FinancialTransaction> findByFarmIdAndTransactionDateBetweenOrderByTransactionDateAsc(Long farmId, LocalDate start, LocalDate end);
    List<FinancialTransaction> findByFarmIdAndBatchIdAndTransactionDateBetweenOrderByTransactionDateAsc(
            Long farmId, Long batchId, LocalDate start, LocalDate end);
    Optional<FinancialTransaction> findByIdAndFarmId(Long id, Long farmId);
    boolean existsByFarmIdAndBatchIdAndCreatedAtAfter(Long farmId, Long batchId, Instant cutoff);
}
