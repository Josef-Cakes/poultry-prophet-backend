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

    public VersionController(ReleaseProperties release) {
        this.release = release;
    }

    @GetMapping
    public VersionResponse version() {
        return new VersionResponse(
                "poultry-prophet-backend",
                release.getVersion(),
                release.getCommitSha(),
                release.getBuildTime(),
                release.getEnvironment(),
                release.getFrontendVersion());
    }
}
