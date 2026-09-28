package dev.portableagent.connection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.portableagent.connection.crypto.TokenCipher;
import dev.portableagent.connection.crypto.TokenCryptoException;
import dev.portableagent.connection.crypto.TokenOwner;
import dev.portableagent.connection.model.AccountConnection;
import dev.portableagent.connection.model.ConnectionStatus;
import dev.portableagent.connection.model.EncryptedToken;
import dev.portableagent.connection.model.Provider;
import dev.portableagent.connection.provider.OAuthProvider;
import dev.portableagent.connection.provider.OAuthProviders;
import dev.portableagent.connection.provider.ProviderCallFailed;
import dev.portableagent.connection.provider.ProviderTokens;
import dev.portableagent.connection.repository.ConnectionRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectionServiceTest {
    @Mock
    OAuthSessionService sessions;

    @Mock
    OAuthProviders providers;

    @Mock
    OAuthProvider provider;

    @Mock
    TokenCipher cipher;

    @Mock
    ConnectionRepository repository;

    private final Instant now = Instant.parse("2026-09-28T13:00:00Z");
    private final TokenOwner owner = new TokenOwner(UUID.randomUUID(), UUID.randomUUID(), Provider.GOOGLE_CALENDAR);
    private ConnectionService service;

    @BeforeEach
    void setUp() {
        service = new ConnectionService(sessions, providers, cipher, repository, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void start_whenProviderIsReady_shouldCreateSessionAndReturnSafeUrl() {
        var oauthStart = new OAuthStart("secret-state", "challenge", "S256", now.plusSeconds(600));
        var url = URI.create("https://accounts.example/authorize?state=secret-state");
        when(providers.get(owner.provider())).thenReturn(provider);
        when(sessions.start(owner)).thenReturn(oauthStart);
        when(provider.authorizationUrl(oauthStart)).thenReturn(url);

        var result = service.start(owner);

        assertThat(result.url()).isEqualTo(url);
        assertThat(result.expiresAt()).isEqualTo(oauthStart.expiresAt());
        assertThat(result.toString())
                .doesNotContain(url.toString(), "secret-state")
                .contains("<redacted>");
        var order = inOrder(providers, sessions, provider);
        order.verify(providers).get(owner.provider());
        order.verify(sessions).start(owner);
        order.verify(provider).authorizationUrl(oauthStart);
    }

    @Test
    void complete_whenCodeIsValid_shouldEncryptAndStoreActiveConnection() {
        var completed = new CompletedOAuthSession(owner, "verifier");
        var tokens = new ProviderTokens("google-user", "refresh-secret", "access-secret", now.plusSeconds(3600));
        var encrypted = new EncryptedToken(new byte[] {1}, new byte[] {2}, 1);
        when(sessions.complete("state-value")).thenReturn(completed);
        when(providers.get(owner.provider())).thenReturn(provider);
        when(provider.exchange("code-value", "verifier")).thenReturn(tokens);
        when(cipher.encrypt("refresh-secret", owner)).thenReturn(encrypted);
        when(repository.saveOrUpdate(any())).thenAnswer(call -> call.getArgument(0));

        var result = service.complete("state-value", "code-value");

        assertThat(result.tenantId()).isEqualTo(owner.tenantId());
        assertThat(result.actorId()).isEqualTo(owner.actorId());
        assertThat(result.provider()).isEqualTo(owner.provider());
        assertThat(result.providerAccountId()).isEqualTo("google-user");
        assertThat(result.status()).isEqualTo(ConnectionStatus.ACTIVE);
        assertThat(result.encryptedToken()).isEqualTo(encrypted);
        assertThat(result.createdAt()).isEqualTo(now);
        assertThat(result.updatedAt()).isEqualTo(now);
        verify(provider, never()).revoke(any());
    }

    @Test
    void complete_whenRepositoryFindsExistingAccount_shouldReturnItsStableId() {
        var completed = new CompletedOAuthSession(owner, "verifier");
        var tokens = new ProviderTokens("google-user", "refresh-secret", "access-secret", now.plusSeconds(3600));
        var encrypted = new EncryptedToken(new byte[] {1}, new byte[] {2}, 1);
        var existing = new AccountConnection(
                UUID.randomUUID(),
                owner.tenantId(),
                owner.actorId(),
                owner.provider(),
                tokens.accountId(),
                ConnectionStatus.ACTIVE,
                encrypted,
                now.minusSeconds(100),
                now);
        when(sessions.complete("state-value")).thenReturn(completed);
        when(providers.get(owner.provider())).thenReturn(provider);
        when(provider.exchange("code-value", "verifier")).thenReturn(tokens);
        when(cipher.encrypt(tokens.refreshToken(), owner)).thenReturn(encrypted);
        when(repository.saveOrUpdate(any())).thenReturn(existing);

        assertThat(service.complete("state-value", "code-value")).isSameAs(existing);
    }

    @Test
    void complete_whenEncryptionFails_shouldRevokeIssuedRefreshToken() {
        var completed = new CompletedOAuthSession(owner, "verifier");
        var tokens = new ProviderTokens("google-user", "refresh-secret", "access-secret", now.plusSeconds(3600));
        var failure = new TokenCryptoException("cannot encrypt");
        when(sessions.complete("state-value")).thenReturn(completed);
        when(providers.get(owner.provider())).thenReturn(provider);
        when(provider.exchange("code-value", "verifier")).thenReturn(tokens);
        when(cipher.encrypt(tokens.refreshToken(), owner)).thenThrow(failure);

        assertThatThrownBy(() -> service.complete("state-value", "code-value")).isSameAs(failure);
        verify(provider).revoke(tokens.refreshToken());
        verify(repository, never()).saveOrUpdate(any());
    }

    @Test
    void complete_whenSaveAndRevokeFail_shouldKeepSaveFailureAsMainError() {
        var completed = new CompletedOAuthSession(owner, "verifier");
        var tokens = new ProviderTokens("google-user", "refresh-secret", "access-secret", now.plusSeconds(3600));
        var encrypted = new EncryptedToken(new byte[] {1}, new byte[] {2}, 1);
        var saveFailure = new IllegalStateException("database unavailable");
        var revokeFailure = new ProviderCallFailed("Provider revoke failed");
        when(sessions.complete("state-value")).thenReturn(completed);
        when(providers.get(owner.provider())).thenReturn(provider);
        when(provider.exchange("code-value", "verifier")).thenReturn(tokens);
        when(cipher.encrypt(tokens.refreshToken(), owner)).thenReturn(encrypted);
        when(repository.saveOrUpdate(any())).thenThrow(saveFailure);
        doThrow(revokeFailure).when(provider).revoke(tokens.refreshToken());

        assertThatThrownBy(() -> service.complete("state-value", "code-value"))
                .isSameAs(saveFailure)
                .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(revokeFailure));
    }

    @Test
    void complete_whenProviderExchangeFails_shouldNotTryToRevokeUnknownToken() {
        var completed = new CompletedOAuthSession(owner, "verifier");
        var failure = new ProviderCallFailed("Provider exchange failed");
        when(sessions.complete("state-value")).thenReturn(completed);
        when(providers.get(owner.provider())).thenReturn(provider);
        when(provider.exchange("code-value", "verifier")).thenThrow(failure);

        assertThatThrownBy(() -> service.complete("state-value", "code-value")).isSameAs(failure);
        verify(provider, never()).revoke(any());
        verify(cipher, never()).encrypt(any(), any());
        verify(repository, never()).saveOrUpdate(any());
    }

    @Test
    void reject_whenUserDeniedAccess_shouldOnlyConsumeSession() {
        service.reject("state-value");

        verify(sessions).reject("state-value");
        verify(providers, never()).get(any());
    }

    @Test
    void list_whenOwnerHasConnections_shouldReturnRepositoryResult() {
        var connection = connection();
        when(repository.findAll(owner.tenantId(), owner.actorId())).thenReturn(List.of(connection));

        assertThat(service.list(owner.tenantId(), owner.actorId())).containsExactly(connection);
    }

    @Test
    void disconnect_whenConnectionExists_shouldBlockItBeforeRevokeAndDelete() {
        var connection = connection();
        when(repository.findById(owner.tenantId(), owner.actorId(), connection.id()))
                .thenReturn(Optional.of(connection));
        when(repository.markDisconnected(connection, now)).thenReturn(true);
        when(cipher.decrypt(connection.encryptedToken(), owner)).thenReturn("refresh-secret");
        when(providers.get(connection.provider())).thenReturn(provider);
        when(repository.deleteDisconnected(connection)).thenReturn(true);

        service.disconnect(owner.tenantId(), owner.actorId(), connection.id());

        var order = inOrder(repository, cipher, providers, provider);
        order.verify(repository).markDisconnected(connection, now);
        order.verify(providers).get(connection.provider());
        order.verify(cipher).decrypt(connection.encryptedToken(), owner);
        order.verify(provider).revoke("refresh-secret");
        order.verify(repository).deleteDisconnected(connection);
    }

    @Test
    void disconnect_whenProviderRevokeFails_shouldKeepDisconnectedConnectionForRetry() {
        var connection = connection();
        var failure = new ProviderCallFailed("Provider revoke failed");
        when(repository.findById(owner.tenantId(), owner.actorId(), connection.id()))
                .thenReturn(Optional.of(connection));
        when(repository.markDisconnected(connection, now)).thenReturn(true);
        when(cipher.decrypt(connection.encryptedToken(), owner)).thenReturn("refresh-secret");
        when(providers.get(connection.provider())).thenReturn(provider);
        doThrow(failure).when(provider).revoke("refresh-secret");

        assertThatThrownBy(() -> service.disconnect(owner.tenantId(), owner.actorId(), connection.id()))
                .isSameAs(failure);
        verify(repository, never()).deleteDisconnected(any());
    }

    @Test
    void disconnect_whenConnectionDoesNotBelongToOwner_shouldReturnSameNotFoundError() {
        var connectionId = UUID.randomUUID();
        when(repository.findById(owner.tenantId(), owner.actorId(), connectionId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disconnect(owner.tenantId(), owner.actorId(), connectionId))
                .isInstanceOf(ConnectionNotFound.class)
                .hasMessage("Connection was not found");
        verifyNoInteractions(cipher, providers, provider);
    }

    private AccountConnection connection() {
        return new AccountConnection(
                UUID.randomUUID(),
                owner.tenantId(),
                owner.actorId(),
                owner.provider(),
                "google-user",
                ConnectionStatus.ACTIVE,
                new EncryptedToken(new byte[] {1}, new byte[] {2}, 1),
                now.minusSeconds(60),
                now.minusSeconds(60));
    }
}
