package com.poultryprophet.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceSummaryResponse(LocalDate startDate, LocalDate endDate, BigDecimal income,
                                     BigDecimal expense, BigDecimal net, long transactionCount) {}
