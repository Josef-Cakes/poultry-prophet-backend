package com.poultryprophet.event;

public enum EventType {
    /** Legacy ambiguous value retained for read-only reconciliation of existing rows. */
    @Deprecated
    MORTALITY(-1, false, true),
    HEALTH_DEATH(-1, true, true),
    ACCIDENTAL_DEATH(-1, false, true),
    SUSPECTED_PREDATION(-1, false, true),
    CONFIRMED_PREDATION(-1, false, true),
    MISSING(-1, false, true),
    FOUND_RETURNED(1, false, true),
    TRANSFER_OUT(-1, false, true),
    TRANSFER_IN(1, false, true),
    SALE(-1, false, true),
    CULLING(-1, false, true),
    COUNT_CORRECTION(0, false, true),
    HEALTH_CONCERN,
    VACCINE_MEDICINE,
    BEHAVIOR_OBSERVATION;

    private final int populationSign;
    private final boolean healthMortality;
    private final boolean populationLedgerEvent;

    EventType() {
        this(0, false, false);
    }

    EventType(int populationSign, boolean healthMortality, boolean populationLedgerEvent) {
        this.populationSign = populationSign;
        this.healthMortality = healthMortality;
        this.populationLedgerEvent = populationLedgerEvent;
    }

    public boolean isHealthMortality() {
        return healthMortality;
    }

    public boolean isPopulationLedgerEvent() {
        return populationLedgerEvent;
    }

    public int populationDelta(int affectedCount, Integer requestedDelta) {
        if (this == COUNT_CORRECTION) {
            if (requestedDelta == null || requestedDelta == 0) {
                throw new IllegalArgumentException("COUNT_CORRECTION requires a non-zero populationDelta");
            }
            return requestedDelta;
        }
        return populationSign * affectedCount;
    }
}
