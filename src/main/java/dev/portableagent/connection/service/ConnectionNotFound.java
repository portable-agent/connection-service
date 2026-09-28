package dev.portableagent.connection.service;

public class ConnectionNotFound extends RuntimeException {
    public ConnectionNotFound() {
        super("Connection was not found");
    }
}
