package com.poultryprophet.selectionsession;

import jakarta.validation.constraints.Min;

/** Optional for legacy clients; current clients supply the exact reviewed revision. */
public record FinalizeSelectionSessionRequest(@Min(0) Long expectedVersion) {}
