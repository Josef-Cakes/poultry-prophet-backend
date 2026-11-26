package com.poultryprophet.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poultryprophet.finance.dto.CreateFinancialTransactionRequest;
import com.poultryprophet.security.CustomUserDetails;
import com.poultryprophet.sync.CommandReceiptService;
import com.poultryprophet.sync.CommandReceiptsUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FinanceCreateIdempotencyTest {
    @Test void keyedRequestFailsClosedWithoutReceiptFeatureAndDoesNotCreateExpense() {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var service = mock(FinanceService.class);
        var receipts = new CommandReceiptService(mock(JdbcTemplate.class), mapper, mock(PlatformTransactionManager.class), false);
        var principal = mock(CustomUserDetails.class);
        when(principal.getFarmId()).thenReturn(7L);
        when(principal.getId()).thenReturn(8L);
        var request = new CreateFinancialTransactionRequest(9L, null, LocalDate.of(2026, 11, 26),
                FinanceTransactionType.EXPENSE, "Transport", new BigDecimal("100"), "PHP", null, null);
        var controller = new FinanceController(service, receipts, mapper);

        assertThatThrownBy(() -> controller.create(request, UUID.randomUUID(), principal))
                .isInstanceOf(CommandReceiptsUnavailableException.class);
        verifyNoInteractions(service);
    }
}
