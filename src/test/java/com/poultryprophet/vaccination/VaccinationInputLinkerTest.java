package com.poultryprophet.vaccination;

import com.poultryprophet.input.FarmInputLog;
import com.poultryprophet.input.InputProductType;
import com.poultryprophet.task.HandlerTaskRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;

class VaccinationInputLinkerTest {
    @Test void doesNotGuessWhichOfTwoDosesWasGiven() {
        var plans = mock(BatchVaccinationPlanItemRepository.class);
        var tasks = mock(HandlerTaskRepository.class);
        var first = new BatchVaccinationPlanItem(); first.setStatus("SCHEDULED"); first.setFarmProductId(7L); first.setVaccineName("Vaccine");
        var second = new BatchVaccinationPlanItem(); second.setStatus("SCHEDULED"); second.setFarmProductId(7L); second.setVaccineName("Vaccine");
        when(plans.findByBatchIdAndFarmIdOrderByDueDateAsc(2L, 1L)).thenReturn(List.of(first, second));
        var input = new FarmInputLog(); input.setBatchId(2L); input.setFarmId(1L); input.setProductType(InputProductType.VACCINE); input.setFarmProductId(7L); input.setBrandName("Vaccine");
        new VaccinationInputLinker(plans, tasks).linkIfScheduled(input);
        verify(plans, never()).save(any());
    }
}
