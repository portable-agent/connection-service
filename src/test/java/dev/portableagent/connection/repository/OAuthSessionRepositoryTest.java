package dev.portableagent.connection.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portableagent.connection.model.EncryptedToken;
import dev.portableagent.connection.model.OAuthSession;
import dev.portableagent.connection.model.Provider;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OAuthSessionRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6-alpine3.23");

    private static Connection connection;
    private static OAuthSessionRepository repository;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        repository = new OAuthSessionRepository(DSL.using(connection, SQLDialect.POSTGRES));
    }

    @AfterAll
    static void closeDatabase() throws Exception {
        connection.close();
    }

    @Test
    void consume_whenSessionIsValid_shouldReturnItOnlyOnce() {
        var session = session("a".repeat(64), Instant.parse("2026-09-28T10:10:00Z"));
        repository.save(session);

        var first = repository.consume(session.stateHash(), Instant.parse("2026-09-28T10:05:00Z"));
        var second = repository.consume(session.stateHash(), Instant.parse("2026-09-28T10:06:00Z"));

        assertThat(first).contains(session);
        assertThat(second).isEmpty();
    }

    @Test
    void consume_whenSessionExpired_shouldNotReturnIt() {
        var session = session("b".repeat(64), Instant.parse("2026-09-28T10:01:00Z"));
        repository.save(session);

        assertThat(repository.consume(session.stateHash(), Instant.parse("2026-09-28T10:01:00Z")))
                .isEmpty();
    }

    @Test
    void consume_whenRequestsRace_shouldReturnSessionOnce() throws Exception {
        var session = session("c".repeat(64), Instant.parse("2026-09-28T10:10:00Z"));
        repository.save(session);
        var start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> consumeAfterStart(session.stateHash(), start));
            var second = pool.submit(() -> consumeAfterStart(session.stateHash(), start));
            start.countDown();

            assertThat(List.of(first.get().isPresent(), second.get().isPresent()))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    private java.util.Optional<OAuthSession> consumeAfterStart(String stateHash, CountDownLatch start)
            throws Exception {
        start.await();
        try (var taskConnection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            return new OAuthSessionRepository(DSL.using(taskConnection, SQLDialect.POSTGRES))
                    .consume(stateHash, Instant.parse("2026-09-28T10:05:00Z"));
        }
    }

    private OAuthSession session(String stateHash, Instant expiresAt) {
        return new OAuthSession(
                stateHash,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Provider.GOOGLE_CALENDAR,
                new EncryptedToken(new byte[] {1, 2, 3}, new byte[] {4, 5, 6}, 1),
                Instant.parse("2026-09-28T10:00:00Z"),
                expiresAt);
    }
}
