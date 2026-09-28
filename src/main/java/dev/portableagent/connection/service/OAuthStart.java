package dev.portableagent.connection.service;

import java.time.Instant;

public record OAuthStart(String state, String challenge, String method, Instant expiresAt) {
    @Override
    public String toString() {
        return "OAuthStart[state=<redacted>, challenge=<redacted>, method=" + method + ", expiresAt=" + expiresAt + "]";
    }
}
