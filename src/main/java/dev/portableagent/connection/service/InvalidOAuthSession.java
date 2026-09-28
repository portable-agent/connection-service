package dev.portableagent.connection.service;

public class InvalidOAuthSession extends RuntimeException {
    public InvalidOAuthSession() {
        super("OAuth session is invalid or expired");
    }
}
