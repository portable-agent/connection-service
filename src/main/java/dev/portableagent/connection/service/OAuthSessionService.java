package dev.portableagent.connection.service;

import dev.portableagent.connection.config.OAuthProperties;
import dev.portableagent.connection.crypto.TokenCipher;
import dev.portableagent.connection.crypto.TokenOwner;
import dev.portableagent.connection.model.OAuthSession;
import dev.portableagent.connection.repository.OAuthSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBean(TokenCipher.class)
public class OAuthSessionService {
    private static final int RANDOM_BYTES = 32;

    private final OAuthSessionRepository repository;
    private final TokenCipher cipher;
    private final SecureRandom random;
    private final Clock clock;
    private final Duration ttl;

    @Autowired
    public OAuthSessionService(OAuthSessionRepository repository, TokenCipher cipher, OAuthProperties properties) {
        this(repository, cipher, new SecureRandom(), Clock.systemUTC(), properties.sessionTtl());
    }

    OAuthSessionService(
            OAuthSessionRepository repository, TokenCipher cipher, SecureRandom random, Clock clock, Duration ttl) {
        this.repository = repository;
        this.cipher = cipher;
        this.random = random;
        this.clock = clock;
        this.ttl = ttl;
    }

    public OAuthStart start(TokenOwner owner) {
        var state = randomText();
        var verifier = randomText();
        var now = clock.instant();
        var expiresAt = now.plus(ttl);
        var encryptedVerifier = cipher.encrypt(verifier, owner);
        repository.save(new OAuthSession(
                hash(state), owner.tenantId(), owner.actorId(), owner.provider(), encryptedVerifier, now, expiresAt));
        return new OAuthStart(state, base64(digest(verifier)), "S256", expiresAt);
    }

    @Transactional
    public CompletedOAuthSession complete(String state) {
        var session = consume(state);
        var owner = new TokenOwner(session.tenantId(), session.actorId(), session.provider());
        return new CompletedOAuthSession(owner, cipher.decrypt(session.encryptedVerifier(), owner));
    }

    @Transactional
    public void reject(String state) {
        consume(state);
    }

    private OAuthSession consume(String state) {
        if (state == null || state.length() < 32 || state.length() > 512) {
            throw new InvalidOAuthSession();
        }
        return repository.consume(hash(state), clock.instant()).orElseThrow(InvalidOAuthSession::new);
    }

    private String randomText() {
        var bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return base64(bytes);
    }

    private String hash(String value) {
        return HexFormat.of().formatHex(digest(value));
    }

    private byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available", error);
        }
    }

    private String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
