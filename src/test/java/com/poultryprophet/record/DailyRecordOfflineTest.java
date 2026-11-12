package com.poultryprophet.record;

import com.poultryprophet.batch.BatchService;
import com.poultryprophet.common.ConflictException;
import com.poultryprophet.common.DateValidationService;
import com.poultryprophet.config.LegacyDataProperties;
import com.poultryprophet.event.MortalityAccountingService;
import com.poultryprophet.record.dto.CreateRecordRequest;
import com.poultryprophet.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DailyRecordOfflineTest {
    @Test
    void anExistingDayIsNotOverwrittenByAnOfflineMeasurement() {
        var repository = mock(DailyRecordRepository.class);
        var batches = mock(BatchService.class);
        var service = new DailyRecordService(repository, batches, mock(MortalityAccountingService.class),
                mock(UserRepository.class), mock(ApplicationEventPublisher.class), mock(DateValidationService.class),
                mock(LegacyDataProperties.class));
        var date = LocalDate.of(2026, 10, 7);
        when(repository.findByBatchIdAndRecordDate(7L, date)).thenReturn(Optional.of(new DailyRecord()));

        assertThrows(ConflictException.class, () -> service.recordOffline(7L, 2L,
                new CreateRecordRequest(date, 30.0, null, null, null, null), 4L));
        verify(batches).requireBatchForUpdate(7L, 2L);
        verify(repository, never()).save(any());
    }
}
