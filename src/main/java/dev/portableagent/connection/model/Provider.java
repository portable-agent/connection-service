package dev.portableagent.connection.model;

public enum Provider {
    GOOGLE_CALENDAR("google-calendar");

    private final String value;

    Provider(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Provider fromValue(String value) {
        for (var provider : values()) {
            if (provider.value.equals(value)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown provider: " + value);
    }
}
