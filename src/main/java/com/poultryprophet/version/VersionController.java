package com.poultryprophet.version;

import com.poultryprophet.config.ReleaseProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only, non-secret release metadata for staging/release compatibility checks. */
@RestController
@RequestMapping("/api/version")
public class VersionController {

    private final ReleaseProperties release;
    private final com.poultryprophet.sync.CommandReceiptService receipts;

    @org.springframework.beans.factory.annotation.Autowired
    public VersionController(ReleaseProperties release, com.poultryprophet.sync.CommandReceiptService receipts) {
        this.release = release; this.receipts = receipts;
    }
    public VersionController(ReleaseProperties release) { this(release, null); }

    @GetMapping
    public VersionResponse version() {
        boolean ready = receipts != null && receipts.isReady();
        return new VersionResponse(
                "poultry-prophet-backend",
                release.getVersion(),
                release.getCommitSha(),
                release.getBuildTime(),
                release.getEnvironment(),
                release.getFrontendVersion(),
                new VersionResponse.OfflineCapabilities(java.util.List.of(2), ready, ready,
                        ready ? java.util.List.of("BATCH_EVENT", "FARM_INPUT", "SELECTION_SESSION", "SELECTION_SESSION_UPDATE", "SEX_COMPOSITION", "VACCINATION_PLAN", "VACCINATION_SCHEDULE", "TASK_STATUS", "DAILY_RECORD") : java.util.List.of()));
    }
}
