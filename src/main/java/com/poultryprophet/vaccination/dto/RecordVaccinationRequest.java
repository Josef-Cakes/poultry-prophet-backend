package com.poultryprophet.vaccination.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RecordVaccinationRequest(Instant recordedAt, BigDecimal quantity, String unit, String notes, UUID operationId) {}
