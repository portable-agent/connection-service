package dev.portableagent.connection.repository;

import static dev.portableagent.connection.db.tables.OauthSessions.OAUTH_SESSIONS;

import dev.portableagent.connection.model.EncryptedToken;
import dev.portableagent.connection.model.OAuthSession;
import dev.portableagent.connection.model.Provider;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class OAuthSessionRepository {
    private final DSLContext db;

    public OAuthSessionRepository(DSLContext db) {
        this.db = db;
    }

    public void save(OAuthSession session) {
        db.insertInto(OAUTH_SESSIONS)
                .set(OAUTH_SESSIONS.STATE_HASH, session.stateHash())
                .set(OAUTH_SESSIONS.TENANT_ID, session.tenantId())
                .set(OAUTH_SESSIONS.ACTOR_ID, session.actorId())
                .set(OAUTH_SESSIONS.PROVIDER, session.provider().value())
                .set(
                        OAUTH_SESSIONS.ENCRYPTED_VERIFIER,
                        session.encryptedVerifier().data())
                .set(OAUTH_SESSIONS.VERIFIER_NONCE, session.encryptedVerifier().nonce())
                .set(OAUTH_SESSIONS.KEY_VERSION, session.encryptedVerifier().keyVersion())
                .set(OAUTH_SESSIONS.CREATED_AT, utc(session.createdAt()))
                .set(OAUTH_SESSIONS.EXPIRES_AT, utc(session.expiresAt()))
                .execute();
    }

    public Optional<OAuthSession> consume(String stateHash, Instant now) {
        return db.update(OAUTH_SESSIONS)
                .set(OAUTH_SESSIONS.CONSUMED_AT, utc(now))
                .where(OAUTH_SESSIONS.STATE_HASH.eq(stateHash))
                .and(OAUTH_SESSIONS.CONSUMED_AT.isNull())
                .and(OAUTH_SESSIONS.EXPIRES_AT.gt(utc(now)))
                .returning()
                .fetchOptional(this::toSession);
    }

    private OAuthSession toSession(Record row) {
        return new OAuthSession(
                row.get(OAUTH_SESSIONS.STATE_HASH),
                row.get(OAUTH_SESSIONS.TENANT_ID),
                row.get(OAUTH_SESSIONS.ACTOR_ID),
                Provider.fromValue(row.get(OAUTH_SESSIONS.PROVIDER)),
                new EncryptedToken(
                        row.get(OAUTH_SESSIONS.ENCRYPTED_VERIFIER),
                        row.get(OAUTH_SESSIONS.VERIFIER_NONCE),
                        row.get(OAUTH_SESSIONS.KEY_VERSION)),
                row.get(OAUTH_SESSIONS.CREATED_AT).toInstant(),
                row.get(OAUTH_SESSIONS.EXPIRES_AT).toInstant());
    }

    private OffsetDateTime utc(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}
