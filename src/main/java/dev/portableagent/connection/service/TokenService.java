package dev.portableagent.connection.service;

import dev.portableagent.connection.crypto.TokenCipher;
import dev.portableagent.connection.crypto.TokenOwner;
import dev.portableagent.connection.model.Provider;
import dev.portableagent.connection.provider.AccessToken;
import dev.portableagent.connection.provider.OAuthProviders;
import dev.portableagent.connection.repository.ConnectionRepository;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(TokenCipher.class)
public class TokenService {
    private final ConnectionRepository repository;
    private final TokenCipher cipher;
    private final OAuthProviders providers;

    public TokenService(ConnectionRepository repository, TokenCipher cipher, OAuthProviders providers) {
        this.repository = repository;
        this.cipher = cipher;
        this.providers = providers;
    }

    public AccessToken issue(UUID tenantId, UUID actorId, Provider providerType) {
        var connections = repository.findActive(tenantId, actorId, providerType);
        if (connections.isEmpty()) {
            throw new ConnectionRequired();
        }
        if (connections.size() > 1) {
            throw new ConnectionAmbiguous();
        }
        var connection = connections.getFirst();
        var owner = new TokenOwner(tenantId, actorId, providerType);
        var refreshToken = cipher.decrypt(connection.encryptedToken(), owner);
        return providers.get(providerType).refresh(refreshToken);
    }
}
