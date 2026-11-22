package com.poultryprophet.batch.dto;

import java.time.LocalDate;

/** A null date clears the optional schedule. */
public record UpdateDerbyDateRequest(LocalDate derbyDate) {
}
