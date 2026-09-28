package dev.portableagent.connection.provider;

public class ProviderCallFailed extends RuntimeException {
    public ProviderCallFailed(String message) {
        super(message);
    }
}
