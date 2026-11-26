package com.poultryprophet.vaccination;

import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.input.InputProductType;
import com.poultryprophet.inventory.FarmProduct;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VaccineProductValidationTest {
    @Test void rejectsInactiveAndNonVaccineProducts() {
        FarmProduct product = new FarmProduct();
        product.setActive(true); product.setProductType(InputProductType.VACCINE);
        assertThatCode(() -> VaccinationService.requireActiveVaccine(product)).doesNotThrowAnyException();
        product.setProductType(InputProductType.MEDICINE);
        assertThatThrownBy(() -> VaccinationService.requireActiveVaccine(product)).isInstanceOf(BadRequestException.class);
        product.setProductType(InputProductType.VACCINE); product.setActive(false);
        assertThatThrownBy(() -> VaccinationService.requireActiveVaccine(product)).isInstanceOf(BadRequestException.class);
    }
}
