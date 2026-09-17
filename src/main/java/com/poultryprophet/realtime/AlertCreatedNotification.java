package com.poultryprophet.realtime;

import com.poultryprophet.alert.dto.AlertEvent;

/** Fully materialized alert payload waiting for the surrounding transaction to commit. */
public record AlertCreatedNotification(Long farmId, AlertEvent payload) {
}
