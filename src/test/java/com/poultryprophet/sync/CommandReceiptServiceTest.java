package com.poultryprophet.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandReceiptServiceTest {
    @Test void disabledRolloutDoesNotConnectOrRunDomainWork() {
        var jdbc = mock(JdbcTemplate.class); var transactions = mock(PlatformTransactionManager.class);
        var service = new CommandReceiptService(jdbc, new ObjectMapper(), transactions, false);
        assertThat(service.isReady()).isFalse();
        assertThatThrownBy(() -> service.executeOnce(null, null, String.class, () -> { throw new AssertionError("Domain work must not run"); }))
                .isInstanceOf(CommandReceiptsUnavailableException.class);
        verifyNoInteractions(jdbc, transactions);
    }
}
