package dev.portableagent.connection.service;

public class InvalidOAuthCallback extends RuntimeException {
    public InvalidOAuthCallback() {
        super("Exactly one of code or error is required");
    }
}
