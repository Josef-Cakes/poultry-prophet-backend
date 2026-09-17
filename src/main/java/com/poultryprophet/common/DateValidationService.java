package com.poultryprophet.common;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/** Central date policy for user-selected batch records and events. */
@Service
public class DateValidationService {

    private final Clock clock;

    public DateValidationService(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDate resolve(LocalDate selectedDate) {
        return selectedDate != null ? selectedDate : today();
    }

    public void validate(LocalDate selectedDate, LocalDate batchStartDate) {
        selectedDate = resolve(selectedDate);
        LocalDate today = today();
        if (selectedDate.isBefore(batchStartDate) || selectedDate.isAfter(today)) {
            throw new BadRequestException("Date must be between " + batchStartDate + " and " + today);
        }
    }
}
