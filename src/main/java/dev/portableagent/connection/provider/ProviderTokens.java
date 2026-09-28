package dev.portableagent.connection.provider;

import java.time.Instant;

public record ProviderTokens(String accountId, String refreshToken, String accessToken, Instant expiresAt) {
    @Override
    public String toString() {
        return "ProviderTokens[accountId=" + accountId + ", refreshToken=<redacted>, accessToken=<redacted>, expiresAt="
                + expiresAt + "]";
    }
}
