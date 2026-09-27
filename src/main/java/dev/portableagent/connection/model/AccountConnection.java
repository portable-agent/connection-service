package dev.portableagent.connection.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AccountConnection(
        UUID id,
        UUID tenantId,
        UUID actorId,
        Provider provider,
        String providerAccountId,
        ConnectionStatus status,
        EncryptedToken encryptedToken,
        Instant createdAt,
        Instant updatedAt) {
    public AccountConnection {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(provider);
        Objects.requireNonNull(providerAccountId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(encryptedToken);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (providerAccountId.isBlank()) {
            throw new IllegalArgumentException("Provider account id must not be blank");
        }
    }

    public AccountConnection withStatus(ConnectionStatus newStatus, Instant changedAt) {
        return new AccountConnection(
                id, tenantId, actorId, provider, providerAccountId, newStatus, encryptedToken, createdAt, changedAt);
    }
}
