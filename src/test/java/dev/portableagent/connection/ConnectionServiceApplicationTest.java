package dev.portableagent.connection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConnectionServiceApplicationTest {
    @Test
    void application_shouldHaveSpringBootEntryPoint() {
        assertThat(ConnectionServiceApplication.class).isNotNull();
    }
}
