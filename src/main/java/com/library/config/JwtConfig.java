package com.library.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// @ConfigurationProperties instead of scattered @Value - one typed, validated bean.
@Validated
@ConfigurationProperties("library.jwt")
public class JwtConfig {

    @NotBlank
    private String secret;

    // Fail fast at startup - a non-positive ttl would silently issue expired tokens.
    @Min(1)
    private int ttlMinutes = 15;

    @Min(1)
    private int refreshTtlDays = 7;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(int ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    public int getRefreshTtlDays() {
        return refreshTtlDays;
    }

    public void setRefreshTtlDays(int refreshTtlDays) {
        this.refreshTtlDays = refreshTtlDays;
    }
}
