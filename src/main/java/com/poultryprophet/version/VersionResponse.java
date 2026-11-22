package com.poultryprophet.version;

public record VersionResponse(
        String application,
        String version,
        String commitSha,
        String buildTime,
        String environment,
        String compatibleFrontendVersion,
        OfflineCapabilities offlineCapabilities
) {
    public record OfflineCapabilities(java.util.List<Integer> protocolVersions, boolean commandReceipts,
                                      boolean schemaReady, java.util.List<String> receiptEntityTypes) {}
}
