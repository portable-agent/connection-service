package dev.portableagent.connection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.portableagent.connection.crypto.TokenCipher;
import dev.portableagent.connection.crypto.TokenOwner;
import dev.portableagent.connection.model.EncryptedToken;
import dev.portableagent.connection.model.OAuthSession;
import dev.portableagent.connection.model.Provider;
import dev.portableagent.connection.repository.OAuthSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthSessionServiceTest {
    @Mock
    OAuthSessionRepository repository;

    @Mock
    TokenCipher cipher;

    private final Instant now = Instant.parse("2026-09-28T12:00:00Z");
    private final TokenOwner owner = new TokenOwner(UUID.randomUUID(), UUID.randomUUID(), Provider.GOOGLE_CALENDAR);
    private final EncryptedToken encrypted = new EncryptedToken(new byte[] {1}, new byte[] {2}, 1);
    private OAuthSessionService service;

    @BeforeEach
    void setUp() {
        service = new OAuthSessionService(
                repository, cipher, new SecureRandom(), Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(10));
    }

    @Test
    void start_whenOwnerIsValid_shouldStoreHashAndEncryptedVerifier() throws Exception {
        when(cipher.encrypt(any(), any())).thenReturn(encrypted);

        var result = service.start(owner);

        var saved = ArgumentCaptor.forClass(OAuthSession.class);
        verify(repository).save(saved.capture());
        assertThat(result.state()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(result.challenge()).hasSize(43);
        assertThat(result.method()).isEqualTo("S256");
        assertThat(result.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(10)));
        assertThat(saved.getValue().stateHash()).hasSize(64).doesNotContain(result.state());
        assertThat(saved.getValue().encryptedVerifier()).isEqualTo(encrypted);
        var verifier = ArgumentCaptor.forClass(String.class);
        verify(cipher).encrypt(verifier.capture(), org.mockito.ArgumentMatchers.eq(owner));
        var expectedChallenge = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256")
                        .digest(verifier.getValue().getBytes(StandardCharsets.US_ASCII)));
        assertThat(result.challenge()).isEqualTo(expectedChallenge);
        assertThat(result.toString())
                .doesNotContain(result.state(), result.challenge())
                .contains("<redacted>");
    }

    @Test
    void complete_whenStateIsValid_shouldReturnOwnerAndVerifier() {
        var session = new OAuthSession(
                "a".repeat(64),
                owner.tenantId(),
                owner.actorId(),
                owner.provider(),
                encrypted,
                now,
                now.plusSeconds(600));
        when(repository.consume(any(), org.mockito.ArgumentMatchers.eq(now))).thenReturn(Optional.of(session));
        when(cipher.decrypt(encrypted, owner)).thenReturn("pkce-verifier");

        var result = service.complete("valid-state-value-with-enough-length");

        assertThat(result.owner()).isEqualTo(owner);
        assertThat(result.verifier()).isEqualTo("pkce-verifier");
        assertThat(result.toString()).doesNotContain("pkce-verifier").contains("<redacted>");
    }

    @Test
    void complete_whenStateIsMissingOrExpired_shouldRejectIt() {
        when(repository.consume(any(), org.mockito.ArgumentMatchers.eq(now))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete("valid-state-value-with-enough-length"))
                .isInstanceOf(InvalidOAuthSession.class)
                .hasMessage("OAuth session is invalid or expired");
    }
}
