package dev.junitmanager.run;

import dev.junitmanager.model.TestStatus;

import java.nio.file.Path;

public record ReportedTest(
        Path moduleDir,
        String className,
        String name,
        TestStatus status,
        long durationMillis,
        String message,
        String type,
        String trace,
        String systemOut
) {
}
