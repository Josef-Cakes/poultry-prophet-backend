package com.poultryprophet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poultryprophet.event.dto.BatchEventResponse;
import com.poultryprophet.input.dto.FarmInputLogResponse;
import com.poultryprophet.inventory.dto.FarmProductResponse;
import com.poultryprophet.inventory.InventoryController;
import com.poultryprophet.inventory.InventoryService;
import com.poultryprophet.user.Role;
import com.poultryprophet.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialProjectionTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void handlerProjectionsRemoveAmountsWhileManagersKeepThem() {
        var product = new FarmProductResponse(1L, 2L, null, "brand", "feed", null, "kg",
                BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("30.50"), new BigDecimal("305.00"),
                "PHP", "VALUED", false, true, true);
        var input = new FarmInputLogResponse(1L, 2L, 3L, null, null, null, "brand", "feed",
                BigDecimal.ONE, "kg", null, null, null, 4L, null, null, 1L, null,
                new BigDecimal("30.50"), new BigDecimal("30.50"), "VALUED", null, null);
        var event = new BatchEventResponse(1L, 3L, 4L, "Handler", null, null, null, null,
                1, "sale", null, null, null, null, -1, 9, 9, null, null, null, null,
                null, new BigDecimal("100.00"), "PHP", "Buyer", "Reference");

        var handlerProduct = json.valueToTree(product.forRole(Role.HANDLER));
        var handlerInput = json.valueToTree(input.forRole(Role.HANDLER));
        var handlerEvent = json.valueToTree(event.forRole(Role.HANDLER));
        assertThat(handlerProduct.path("averageUnitCost").isNull()).isTrue();
        assertThat(handlerProduct.path("inventoryValue").isNull()).isTrue();
        assertThat(handlerInput.path("unitCostSnapshot").isNull()).isTrue();
        assertThat(handlerInput.path("calculatedCost").isNull()).isTrue();
        assertThat(handlerEvent.path("saleAmount").isNull()).isTrue();
        assertThat(handlerEvent.path("saleBuyer").isNull()).isTrue();
        assertThat(product.forRole(Role.MANAGER).averageUnitCost()).isEqualByComparingTo("30.50");
        assertThat(input.forRole(Role.MANAGER).calculatedCost()).isEqualByComparingTo("30.50");
        assertThat(event.forRole(Role.MANAGER).saleAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void inventoryEndpointUsesTheAuthenticatedRole() {
        InventoryService service = mock(InventoryService.class);
        var product = new FarmProductResponse(1L, 2L, null, "brand", "feed", null, "kg",
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("100"),
                "PHP", "VALUED", false, true, true);
        when(service.listProducts(2L, false)).thenReturn(java.util.List.of(product));
        User user = new User();
        user.setFarmId(2L);
        user.setRole(Role.HANDLER);
        var response = new InventoryController(service).products(false, new CustomUserDetails(user));
        assertThat(response.getFirst().averageUnitCost()).isNull();
        assertThat(response.getFirst().inventoryValue()).isNull();
        user.setRole(Role.MANAGER);
        assertThat(new InventoryController(service).products(false, new CustomUserDetails(user))
                .getFirst().inventoryValue()).isEqualByComparingTo("100");
    }
}
