package com.poultryprophet.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FinanceSummaryResponse(LocalDate startDate, LocalDate endDate, BigDecimal income,
                                     BigDecimal expense, BigDecimal net, long transactionCount,
                                     List<CurrencyTotal> byCurrency) {
    public record CurrencyTotal(String currency, BigDecimal income, BigDecimal expense, BigDecimal net) {}
}
