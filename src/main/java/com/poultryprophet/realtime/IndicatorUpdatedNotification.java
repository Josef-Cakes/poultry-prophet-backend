package com.poultryprophet.realtime;

import com.poultryprophet.analytics.dto.IndicatorResponse;

/** Fully materialized indicator payload waiting for the surrounding transaction to commit. */
public record IndicatorUpdatedNotification(Long farmId, IndicatorResponse payload) {
}
