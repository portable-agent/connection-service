package dev.portableagent.connection.repository;

import static dev.portableagent.connection.db.tables.AccountConnections.ACCOUNT_CONNECTIONS;

import dev.portableagent.connection.model.AccountConnection;
import dev.portableagent.connection.model.ConnectionStatus;
import dev.portableagent.connection.model.EncryptedToken;
import dev.portableagent.connection.model.Provider;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class ConnectionRepository {
    private final DSLContext db;

    public ConnectionRepository(DSLContext db) {
        this.db = db;
    }

    public void save(AccountConnection connection) {
        db.insertInto(ACCOUNT_CONNECTIONS)
                .set(ACCOUNT_CONNECTIONS.ID, connection.id())
                .set(ACCOUNT_CONNECTIONS.TENANT_ID, connection.tenantId())
                .set(ACCOUNT_CONNECTIONS.ACTOR_ID, connection.actorId())
                .set(ACCOUNT_CONNECTIONS.PROVIDER, connection.provider().value())
                .set(ACCOUNT_CONNECTIONS.PROVIDER_ACCOUNT_ID, connection.providerAccountId())
                .set(ACCOUNT_CONNECTIONS.STATUS, connection.status().value())
                .set(
                        ACCOUNT_CONNECTIONS.ENCRYPTED_REFRESH_TOKEN,
                        connection.encryptedToken().data())
                .set(
                        ACCOUNT_CONNECTIONS.TOKEN_NONCE,
                        connection.encryptedToken().nonce())
                .set(
                        ACCOUNT_CONNECTIONS.KEY_VERSION,
                        connection.encryptedToken().keyVersion())
                .set(ACCOUNT_CONNECTIONS.CREATED_AT, utc(connection.createdAt()))
                .set(ACCOUNT_CONNECTIONS.UPDATED_AT, utc(connection.updatedAt()))
                .execute();
    }

    public AccountConnection saveOrUpdate(AccountConnection connection) {
        return Objects.requireNonNull(db.insertInto(ACCOUNT_CONNECTIONS)
                .set(ACCOUNT_CONNECTIONS.ID, connection.id())
                .set(ACCOUNT_CONNECTIONS.TENANT_ID, connection.tenantId())
                .set(ACCOUNT_CONNECTIONS.ACTOR_ID, connection.actorId())
                .set(ACCOUNT_CONNECTIONS.PROVIDER, connection.provider().value())
                .set(ACCOUNT_CONNECTIONS.PROVIDER_ACCOUNT_ID, connection.providerAccountId())
                .set(ACCOUNT_CONNECTIONS.STATUS, connection.status().value())
                .set(
                        ACCOUNT_CONNECTIONS.ENCRYPTED_REFRESH_TOKEN,
                        connection.encryptedToken().data())
                .set(
                        ACCOUNT_CONNECTIONS.TOKEN_NONCE,
                        connection.encryptedToken().nonce())
                .set(
                        ACCOUNT_CONNECTIONS.KEY_VERSION,
                        connection.encryptedToken().keyVersion())
                .set(ACCOUNT_CONNECTIONS.CREATED_AT, utc(connection.createdAt()))
                .set(ACCOUNT_CONNECTIONS.UPDATED_AT, utc(connection.updatedAt()))
                .onConflict(
                        ACCOUNT_CONNECTIONS.TENANT_ID,
                        ACCOUNT_CONNECTIONS.ACTOR_ID,
                        ACCOUNT_CONNECTIONS.PROVIDER,
                        ACCOUNT_CONNECTIONS.PROVIDER_ACCOUNT_ID)
                .doUpdate()
                .set(ACCOUNT_CONNECTIONS.STATUS, connection.status().value())
                .set(
                        ACCOUNT_CONNECTIONS.ENCRYPTED_REFRESH_TOKEN,
                        connection.encryptedToken().data())
                .set(
                        ACCOUNT_CONNECTIONS.TOKEN_NONCE,
                        connection.encryptedToken().nonce())
                .set(
                        ACCOUNT_CONNECTIONS.KEY_VERSION,
                        connection.encryptedToken().keyVersion())
                .set(ACCOUNT_CONNECTIONS.UPDATED_AT, utc(connection.updatedAt()))
                .returning()
                .fetchOne(this::toConnection));
    }

    public Optional<AccountConnection> findById(UUID tenantId, UUID actorId, UUID connectionId) {
        return db.selectFrom(ACCOUNT_CONNECTIONS)
                .where(ACCOUNT_CONNECTIONS.ID.eq(connectionId))
                .and(ACCOUNT_CONNECTIONS.TENANT_ID.eq(tenantId))
                .and(ACCOUNT_CONNECTIONS.ACTOR_ID.eq(actorId))
                .fetchOptional(this::toConnection);
    }

    public List<AccountConnection> findAll(UUID tenantId, UUID actorId) {
        return db.selectFrom(ACCOUNT_CONNECTIONS)
                .where(ACCOUNT_CONNECTIONS.TENANT_ID.eq(tenantId))
                .and(ACCOUNT_CONNECTIONS.ACTOR_ID.eq(actorId))
                .orderBy(ACCOUNT_CONNECTIONS.CREATED_AT, ACCOUNT_CONNECTIONS.ID)
                .fetch(this::toConnection);
    }

    public List<AccountConnection> findActive(UUID tenantId, UUID actorId, Provider provider) {
        return db.selectFrom(ACCOUNT_CONNECTIONS)
                .where(ACCOUNT_CONNECTIONS.TENANT_ID.eq(tenantId))
                .and(ACCOUNT_CONNECTIONS.ACTOR_ID.eq(actorId))
                .and(ACCOUNT_CONNECTIONS.PROVIDER.eq(provider.value()))
                .and(ACCOUNT_CONNECTIONS.STATUS.eq(ConnectionStatus.ACTIVE.value()))
                .orderBy(ACCOUNT_CONNECTIONS.CREATED_AT, ACCOUNT_CONNECTIONS.ID)
                .fetch(this::toConnection);
    }

    public boolean updateStatus(
            UUID tenantId, UUID actorId, UUID connectionId, ConnectionStatus status, Instant changedAt) {
        return db.update(ACCOUNT_CONNECTIONS)
                        .set(ACCOUNT_CONNECTIONS.STATUS, status.value())
                        .set(ACCOUNT_CONNECTIONS.UPDATED_AT, utc(changedAt))
                        .where(ACCOUNT_CONNECTIONS.ID.eq(connectionId))
                        .and(ACCOUNT_CONNECTIONS.TENANT_ID.eq(tenantId))
                        .and(ACCOUNT_CONNECTIONS.ACTOR_ID.eq(actorId))
                        .execute()
                == 1;
    }

    public boolean markDisconnected(AccountConnection connection, Instant changedAt) {
        return db.update(ACCOUNT_CONNECTIONS)
                        .set(ACCOUNT_CONNECTIONS.STATUS, ConnectionStatus.DISCONNECTED.value())
                        .set(ACCOUNT_CONNECTIONS.UPDATED_AT, utc(changedAt))
                        .where(sameStoredConnection(connection))
                        .execute()
                == 1;
    }

    public boolean deleteDisconnected(AccountConnection connection) {
        return db.deleteFrom(ACCOUNT_CONNECTIONS)
                        .where(sameStoredConnection(connection))
                        .and(ACCOUNT_CONNECTIONS.STATUS.eq(ConnectionStatus.DISCONNECTED.value()))
                        .execute()
                == 1;
    }

    private Condition sameStoredConnection(AccountConnection connection) {
        return ACCOUNT_CONNECTIONS
                .ID
                .eq(connection.id())
                .and(ACCOUNT_CONNECTIONS.TENANT_ID.eq(connection.tenantId()))
                .and(ACCOUNT_CONNECTIONS.ACTOR_ID.eq(connection.actorId()))
                .and(ACCOUNT_CONNECTIONS.ENCRYPTED_REFRESH_TOKEN.eq(
                        connection.encryptedToken().data()))
                .and(ACCOUNT_CONNECTIONS.TOKEN_NONCE.eq(
                        connection.encryptedToken().nonce()))
                .and(ACCOUNT_CONNECTIONS.KEY_VERSION.eq(
                        connection.encryptedToken().keyVersion()));
    }

    private AccountConnection toConnection(Record row) {
        return new AccountConnection(
                row.get(ACCOUNT_CONNECTIONS.ID),
                row.get(ACCOUNT_CONNECTIONS.TENANT_ID),
                row.get(ACCOUNT_CONNECTIONS.ACTOR_ID),
                Provider.fromValue(row.get(ACCOUNT_CONNECTIONS.PROVIDER)),
                row.get(ACCOUNT_CONNECTIONS.PROVIDER_ACCOUNT_ID),
                ConnectionStatus.fromValue(row.get(ACCOUNT_CONNECTIONS.STATUS)),
                new EncryptedToken(
                        row.get(ACCOUNT_CONNECTIONS.ENCRYPTED_REFRESH_TOKEN),
                        row.get(ACCOUNT_CONNECTIONS.TOKEN_NONCE),
                        row.get(ACCOUNT_CONNECTIONS.KEY_VERSION)),
                row.get(ACCOUNT_CONNECTIONS.CREATED_AT).toInstant(),
                row.get(ACCOUNT_CONNECTIONS.UPDATED_AT).toInstant());
    }

    private OffsetDateTime utc(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}
