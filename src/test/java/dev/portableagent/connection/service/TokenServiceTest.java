package dev.portableagent.connection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.portableagent.connection.crypto.TokenCipher;
import dev.portableagent.connection.crypto.TokenOwner;
import dev.portableagent.connection.model.AccountConnection;
import dev.portableagent.connection.model.ConnectionStatus;
import dev.portableagent.connection.model.EncryptedToken;
import dev.portableagent.connection.model.Provider;
import dev.portableagent.connection.provider.AccessToken;
import dev.portableagent.connection.provider.OAuthProvider;
import dev.portableagent.connection.provider.OAuthProviders;
import dev.portableagent.connection.repository.ConnectionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {
    @Mock
    ConnectionRepository repository;

    @Mock
    TokenCipher cipher;

    @Mock
    OAuthProviders providers;

    @Mock
    OAuthProvider provider;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private TokenService service;

    @BeforeEach
    void setUp() {
        service = new TokenService(repository, cipher, providers);
    }

    @Test
    void issue_whenOneActiveConnectionExists_shouldRefreshAccessToken() {
        var connection = connection("google-user-1");
        var owner = new TokenOwner(tenantId, actorId, Provider.GOOGLE_CALENDAR);
        var accessToken = new AccessToken("access-secret", Instant.parse("2026-09-29T12:00:00Z"));
        when(repository.findActive(tenantId, actorId, Provider.GOOGLE_CALENDAR)).thenReturn(List.of(connection));
        when(cipher.decrypt(connection.encryptedToken(), owner)).thenReturn("refresh-secret");
        when(providers.get(Provider.GOOGLE_CALENDAR)).thenReturn(provider);
        when(provider.refresh("refresh-secret")).thenReturn(accessToken);

        assertThat(service.issue(tenantId, actorId, Provider.GOOGLE_CALENDAR)).isSameAs(accessToken);
        verify(provider).refresh("refresh-secret");
    }

    @Test
    void issue_whenNoActiveConnectionExists_shouldRequireConnection() {
        when(repository.findActive(tenantId, actorId, Provider.GOOGLE_CALENDAR)).thenReturn(List.of());

        assertThatThrownBy(() -> service.issue(tenantId, actorId, Provider.GOOGLE_CALENDAR))
                .isInstanceOf(ConnectionRequired.class)
                .hasMessage("Active connection is required");
        verifyNoInteractions(cipher, providers, provider);
    }

    @Test
    void issue_whenSeveralActiveConnectionsExist_shouldRequireExplicitChoice() {
        when(repository.findActive(tenantId, actorId, Provider.GOOGLE_CALENDAR))
                .thenReturn(List.of(connection("google-user-1"), connection("google-user-2")));

        assertThatThrownBy(() -> service.issue(tenantId, actorId, Provider.GOOGLE_CALENDAR))
                .isInstanceOf(ConnectionAmbiguous.class)
                .hasMessage("Several active connections require an explicit choice");
        verifyNoInteractions(cipher, providers, provider);
    }

    private AccountConnection connection(String accountId) {
        var now = Instant.parse("2026-09-29T10:00:00Z");
        return new AccountConnection(
                UUID.randomUUID(),
                tenantId,
                actorId,
                Provider.GOOGLE_CALENDAR,
                accountId,
                ConnectionStatus.ACTIVE,
                new EncryptedToken(new byte[] {1}, new byte[] {2}, 1),
                now,
                now);
    }
}
