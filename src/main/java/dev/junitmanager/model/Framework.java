package dev.junitmanager.model;

public enum Framework {
    JUNIT4,
    JUNIT5,
    UNKNOWN;

    public String label() {
        return switch (this) {
            case JUNIT4 -> "JUnit 4";
            case JUNIT5 -> "JUnit 5";
            case UNKNOWN -> "";
        };
    }
}
