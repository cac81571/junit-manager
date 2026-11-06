package dev.junitmanager.model;

public record CaseResult(
        String name,
        TestStatus status,
        long durationMillis,
        String message,
        String type,
        String trace,
        String systemOut
) {
    public String summary() {
        String time = durationMillis < 0 ? "" : " · " + DurationFormat.of(durationMillis);
        return name + " — " + status.label() + time;
    }
}
