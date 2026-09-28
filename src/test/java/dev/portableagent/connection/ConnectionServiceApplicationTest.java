package dev.portableagent.connection;

import static org.assertj.core.api.Assertions.assertThat;

import dev.portableagent.connection.service.OAuthSessionService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "connection.token-keys.enabled=true",
            "connection.token-keys.current-version=1",
            "connection.token-keys.items[0].version=1",
            "connection.token-keys.items[0].value=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        })
@Testcontainers
class ConnectionServiceApplicationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6-alpine3.23");

    @Autowired
    private DSLContext db;

    @Autowired
    private OAuthSessionService oauthSessionService;

    @LocalServerPort
    private int port;

    @Test
    void application_whenStarted_shouldConnectToPostgres() {
        assertThat(db.fetchOne("select 1").get(0, Integer.class)).isEqualTo(1);
        assertThat(oauthSessionService).isNotNull();
    }

    @Test
    void health_withoutLogin_shouldReturnUp() throws Exception {
        var response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void metrics_withoutLogin_shouldBeBlocked() throws Exception {
        assertThat(get("/actuator/prometheus").statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
