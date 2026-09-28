package dev.portableagent.connection.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record OAuthSession(
        String stateHash,
        UUID tenantId,
        UUID actorId,
        Provider provider,
        EncryptedToken encryptedVerifier,
        Instant createdAt,
        Instant expiresAt) {
    private static final Pattern HASH = Pattern.compile("[a-f0-9]{64}");

    public OAuthSession {
        Objects.requireNonNull(stateHash);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(provider);
        Objects.requireNonNull(encryptedVerifier);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(expiresAt);
        if (!HASH.matcher(stateHash).matches()) {
            throw new IllegalArgumentException("State hash must be lowercase SHA-256");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("OAuth session expiry must be after creation");
        }
    }
}
