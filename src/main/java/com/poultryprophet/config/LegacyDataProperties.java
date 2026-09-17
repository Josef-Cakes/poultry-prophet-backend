package com.poultryprophet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicit opt-in for owner-reviewed legacy mortality reconciliation. */
@ConfigurationProperties(prefix = "app.legacy")
public class LegacyDataProperties {

    private boolean allowMortalityReconciliation;

    public boolean isAllowMortalityReconciliation() {
        return allowMortalityReconciliation;
    }

    public void setAllowMortalityReconciliation(boolean allowMortalityReconciliation) {
        this.allowMortalityReconciliation = allowMortalityReconciliation;
    }
}
