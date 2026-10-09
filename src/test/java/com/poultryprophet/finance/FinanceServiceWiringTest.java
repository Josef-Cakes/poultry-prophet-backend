package com.poultryprophet.finance;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceServiceWiringTest {
    @Test
    void exposesOneProductionConstructorForSpringInjection() {
        Constructor<?>[] constructors = FinanceService.class.getConstructors();

        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes())
                .containsExactly(
                        FinancialTransactionRepository.class,
                        com.poultryprophet.batch.BatchService.class,
                        com.poultryprophet.incubation.IncubationService.class,
                        com.poultryprophet.inventory.InventoryMovementRepository.class
                );
    }
}
