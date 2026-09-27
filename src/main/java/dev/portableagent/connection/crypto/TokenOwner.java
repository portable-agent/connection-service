package dev.portableagent.connection.crypto;

import dev.portableagent.connection.model.Provider;
import java.util.Objects;
import java.util.UUID;

public record TokenOwner(UUID tenantId, UUID actorId, Provider provider) {
    public TokenOwner {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(provider);
    }
}
