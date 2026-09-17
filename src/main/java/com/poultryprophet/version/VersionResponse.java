package com.poultryprophet.version;

public record VersionResponse(
        String application,
        String version,
        String commitSha,
        String buildTime,
        String environment,
        String compatibleFrontendVersion
) {
}
