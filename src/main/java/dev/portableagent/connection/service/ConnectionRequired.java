package dev.portableagent.connection.service;

public class ConnectionRequired extends RuntimeException {
    public ConnectionRequired() {
        super("Active connection is required");
    }
}
