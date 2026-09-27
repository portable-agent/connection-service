package dev.portableagent.connection.model;

public enum ConnectionStatus {
    ACTIVE("active"),
    RECONNECT_REQUIRED("reconnect_required"),
    DISCONNECTED("disconnected");

    private final String value;

    ConnectionStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ConnectionStatus fromValue(String value) {
        for (var status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown connection status: " + value);
    }
}
