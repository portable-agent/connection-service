package dev.portableagent.connection.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portableagent.connection.model.AccountConnection;
import dev.portableagent.connection.model.ConnectionStatus;
import dev.portableagent.connection.model.EncryptedToken;
import dev.portableagent.connection.model.Provider;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ConnectionRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6-alpine3.23");

    private static Connection databaseConnection;
    private static ConnectionRepository repository;

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        databaseConnection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        DSLContext db = DSL.using(databaseConnection, SQLDialect.POSTGRES);
        repository = new ConnectionRepository(db);
    }

    @AfterAll
    static void closeDatabase() throws SQLException {
        databaseConnection.close();
    }

    @Test
    void save_whenConnectionIsValid_shouldReadSameConnection() {
        var connection = connection(UUID.randomUUID(), UUID.randomUUID(), "google-user-1");

        repository.save(connection);

        var saved = repository
                .findById(connection.tenantId(), connection.actorId(), connection.id())
                .orElseThrow();
        assertThat(saved).isEqualTo(connection);
    }

    @Test
    void findById_whenTenantOrActorDiffers_shouldNotExposeConnection() {
        var connection = connection(UUID.randomUUID(), UUID.randomUUID(), "google-user-2");
        repository.save(connection);

        assertThat(repository.findById(UUID.randomUUID(), connection.actorId(), connection.id()))
                .isEmpty();
        assertThat(repository.findById(connection.tenantId(), UUID.randomUUID(), connection.id()))
                .isEmpty();
    }

    @Test
    void findActive_whenUserHasSeveralAccounts_shouldReturnAllWithoutHiddenChoice() {
        var tenantId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        var first = connection(tenantId, actorId, "google-user-3");
        var second = connection(tenantId, actorId, "google-user-4");
        var disconnected = connection(tenantId, actorId, "google-user-5")
                .withStatus(ConnectionStatus.DISCONNECTED, Instant.parse("2026-09-27T10:10:00Z"));
        repository.save(first);
        repository.save(second);
        repository.save(disconnected);

        assertThat(repository.findActive(tenantId, actorId, Provider.GOOGLE_CALENDAR))
                .extracting(AccountConnection::id)
                .containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void updateStatus_whenConnectionIsOwnedByUser_shouldKeepEncryptedToken() {
        var connection = connection(UUID.randomUUID(), UUID.randomUUID(), "google-user-6");
        repository.save(connection);
        var changedAt = Instant.parse("2026-09-27T10:10:00Z");

        boolean changed = repository.updateStatus(
                connection.tenantId(),
                connection.actorId(),
                connection.id(),
                ConnectionStatus.RECONNECT_REQUIRED,
                changedAt);

        assertThat(changed).isTrue();
        var saved = repository
                .findById(connection.tenantId(), connection.actorId(), connection.id())
                .orElseThrow();
        assertThat(saved.status()).isEqualTo(ConnectionStatus.RECONNECT_REQUIRED);
        assertThat(saved.encryptedToken()).isEqualTo(connection.encryptedToken());
        assertThat(saved.updatedAt()).isEqualTo(changedAt);
    }

    @Test
    void saveOrUpdate_whenAccountIsReconnected_shouldKeepIdentityAndReplaceToken() {
        var first = connection(UUID.randomUUID(), UUID.randomUUID(), "google-user-7");
        var disconnectedAt = first.createdAt().plusSeconds(60);
        repository.save(first);
        repository.updateStatus(
                first.tenantId(), first.actorId(), first.id(), ConnectionStatus.RECONNECT_REQUIRED, disconnectedAt);
        var newToken = new EncryptedToken(new byte[] {7, 8}, new byte[] {9, 10}, 2);
        var reconnect = new AccountConnection(
                UUID.randomUUID(),
                first.tenantId(),
                first.actorId(),
                first.provider(),
                first.providerAccountId(),
                ConnectionStatus.ACTIVE,
                newToken,
                disconnectedAt.plusSeconds(60),
                disconnectedAt.plusSeconds(60));

        var saved = repository.saveOrUpdate(reconnect);

        assertThat(saved.id()).isEqualTo(first.id());
        assertThat(saved.createdAt()).isEqualTo(first.createdAt());
        assertThat(saved.updatedAt()).isEqualTo(reconnect.updatedAt());
        assertThat(saved.status()).isEqualTo(ConnectionStatus.ACTIVE);
        assertThat(saved.encryptedToken()).isEqualTo(newToken);
        assertThat(repository.findActive(first.tenantId(), first.actorId(), first.provider()))
                .containsExactly(saved);
    }

    @Test
    void findAll_whenSeveralUsersExist_shouldReturnOnlyOwnersConnections() {
        var tenantId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        var first = connection(tenantId, actorId, "google-user-8");
        var second = connection(tenantId, actorId, "google-user-9")
                .withStatus(
                        ConnectionStatus.RECONNECT_REQUIRED, first.createdAt().plusSeconds(1));
        repository.save(first);
        repository.save(second);
        repository.save(connection(tenantId, UUID.randomUUID(), "other-user"));

        assertThat(repository.findAll(tenantId, actorId)).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void disconnect_whenReconnectReplacedToken_shouldNotChangeOrDeleteNewConnection() {
        var first = connection(UUID.randomUUID(), UUID.randomUUID(), "google-user-11");
        repository.save(first);
        var reconnect = new AccountConnection(
                UUID.randomUUID(),
                first.tenantId(),
                first.actorId(),
                first.provider(),
                first.providerAccountId(),
                ConnectionStatus.ACTIVE,
                new EncryptedToken(new byte[] {11}, new byte[] {12}, 2),
                first.createdAt().plusSeconds(1),
                first.updatedAt().plusSeconds(1));
        var savedReconnect = repository.saveOrUpdate(reconnect);

        assertThat(repository.markDisconnected(first, first.updatedAt().plusSeconds(2)))
                .isFalse();
        assertThat(repository.deleteDisconnected(first)).isFalse();
        assertThat(repository.findById(first.tenantId(), first.actorId(), first.id()))
                .contains(savedReconnect);
    }

    @Test
    void disconnect_whenStoredTokenIsUnchanged_shouldMarkAndDeleteConnection() {
        var connection = connection(UUID.randomUUID(), UUID.randomUUID(), "google-user-12");
        repository.save(connection);

        assertThat(repository.markDisconnected(
                        connection, connection.updatedAt().plusSeconds(1)))
                .isTrue();
        assertThat(repository.deleteDisconnected(connection)).isTrue();
        assertThat(repository.findById(connection.tenantId(), connection.actorId(), connection.id()))
                .isEmpty();
    }

    private AccountConnection connection(UUID tenantId, UUID actorId, String providerAccountId) {
        var now = Instant.parse("2026-09-27T10:00:00Z");
        return new AccountConnection(
                UUID.randomUUID(),
                tenantId,
                actorId,
                Provider.GOOGLE_CALENDAR,
                providerAccountId,
                ConnectionStatus.ACTIVE,
                new EncryptedToken(new byte[] {1, 2, 3}, new byte[] {4, 5, 6}, 1),
                now,
                now);
    }
}
