package dev.portableagent.connection.service;

import dev.portableagent.connection.crypto.TokenCipher;
import dev.portableagent.connection.crypto.TokenOwner;
import dev.portableagent.connection.model.AccountConnection;
import dev.portableagent.connection.model.ConnectionStatus;
import dev.portableagent.connection.provider.OAuthProvider;
import dev.portableagent.connection.provider.OAuthProviders;
import dev.portableagent.connection.provider.ProviderTokens;
import dev.portableagent.connection.repository.ConnectionRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(TokenCipher.class)
public class ConnectionService {
    private final OAuthSessionService sessions;
    private final OAuthProviders providers;
    private final TokenCipher cipher;
    private final ConnectionRepository repository;
    private final Clock clock;

    @Autowired
    public ConnectionService(
            OAuthSessionService sessions,
            OAuthProviders providers,
            TokenCipher cipher,
            ConnectionRepository repository) {
        this(sessions, providers, cipher, repository, Clock.systemUTC());
    }

    ConnectionService(
            OAuthSessionService sessions,
            OAuthProviders providers,
            TokenCipher cipher,
            ConnectionRepository repository,
            Clock clock) {
        this.sessions = sessions;
        this.providers = providers;
        this.cipher = cipher;
        this.repository = repository;
        this.clock = clock;
    }

    public ConnectionStart start(TokenOwner owner) {
        var provider = providers.get(owner.provider());
        var session = sessions.start(owner);
        return new ConnectionStart(provider.authorizationUrl(session), session.expiresAt());
    }

    AccountConnection complete(String state, String code) {
        var session = sessions.complete(state);
        var owner = session.owner();
        var provider = providers.get(owner.provider());
        var tokens = provider.exchange(code, session.verifier());
        try {
            return save(owner, tokens);
        } catch (RuntimeException failure) {
            revokeAfterFailure(provider, tokens.refreshToken(), failure);
            throw failure;
        }
    }

    void reject(String state) {
        sessions.reject(state);
    }

    public ConnectionResult finish(String state, String code, String error) {
        if ((code == null) == (error == null)) {
            throw new InvalidOAuthCallback();
        }
        if (error != null) {
            reject(state);
            return ConnectionResult.DENIED;
        }
        complete(state, code);
        return ConnectionResult.CONNECTED;
    }

    public List<AccountConnection> list(UUID tenantId, UUID actorId) {
        return repository.findAll(tenantId, actorId);
    }

    public void disconnect(UUID tenantId, UUID actorId, UUID connectionId) {
        var connection = repository.findById(tenantId, actorId, connectionId).orElseThrow(ConnectionNotFound::new);
        if (!repository.markDisconnected(connection, clock.instant())) {
            throw new ConnectionNotFound();
        }
        var owner = new TokenOwner(tenantId, actorId, connection.provider());
        var provider = providers.get(connection.provider());
        var refreshToken = cipher.decrypt(connection.encryptedToken(), owner);
        provider.revoke(refreshToken);
        repository.deleteDisconnected(connection);
    }

    private AccountConnection save(TokenOwner owner, ProviderTokens tokens) {
        var now = clock.instant();
        var encryptedToken = cipher.encrypt(tokens.refreshToken(), owner);
        return repository.saveOrUpdate(new AccountConnection(
                UUID.randomUUID(),
                owner.tenantId(),
                owner.actorId(),
                owner.provider(),
                tokens.accountId(),
                ConnectionStatus.ACTIVE,
                encryptedToken,
                now,
                now));
    }

    private void revokeAfterFailure(OAuthProvider provider, String refreshToken, RuntimeException failure) {
        try {
            provider.revoke(refreshToken);
        } catch (RuntimeException revokeFailure) {
            failure.addSuppressed(revokeFailure);
        }
    }
}
