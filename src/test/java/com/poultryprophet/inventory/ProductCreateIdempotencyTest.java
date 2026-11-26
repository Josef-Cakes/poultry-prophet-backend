package com.poultryprophet.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poultryprophet.input.InputProductType;
import com.poultryprophet.inventory.dto.CreateFarmProductRequest;
import com.poultryprophet.security.CustomUserDetails;
import com.poultryprophet.sync.CommandReceiptService;
import com.poultryprophet.sync.CommandReceiptsUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductCreateIdempotencyTest {
    @Test void keyedProductFailsClosedWhenReceiptsAreDisabled() {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var service = mock(InventoryService.class);
        var receipts = new CommandReceiptService(mock(JdbcTemplate.class), mapper, mock(PlatformTransactionManager.class), false);
        var principal = mock(CustomUserDetails.class);
        when(principal.getFarmId()).thenReturn(7L); when(principal.getId()).thenReturn(8L);
        var request = new CreateFarmProductRequest(InputProductType.VACCINE, "Vax", null, null,
                "vial", BigDecimal.ONE, BigDecimal.TEN, null, false);
        var controller = new InventoryController(service, receipts, mapper);

        assertThatThrownBy(() -> controller.create(request, UUID.randomUUID(), principal))
                .isInstanceOf(CommandReceiptsUnavailableException.class);
        verifyNoInteractions(service);
    }
}
