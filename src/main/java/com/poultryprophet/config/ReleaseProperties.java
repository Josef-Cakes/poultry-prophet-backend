package com.poultryprophet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Public, non-secret build metadata used to verify a frontend/backend release pair. */
@ConfigurationProperties(prefix = "app.release")
public class ReleaseProperties {

    private String version = "0.1.0";
    private String commitSha = "unknown";
    private String buildTime = "unknown";
    private String environment = "local";
    private String frontendVersion = "0.1.0";

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }

    public String getBuildTime() { return buildTime; }
    public void setBuildTime(String buildTime) { this.buildTime = buildTime; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getFrontendVersion() { return frontendVersion; }
    public void setFrontendVersion(String frontendVersion) { this.frontendVersion = frontendVersion; }
}
