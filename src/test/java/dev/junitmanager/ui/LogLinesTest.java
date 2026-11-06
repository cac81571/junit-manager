package dev.junitmanager.ui;

import org.junit.jupiter.api.Test;

import static dev.junitmanager.ui.LogLines.Level.DEBUG;
import static dev.junitmanager.ui.LogLines.Level.ERROR;
import static dev.junitmanager.ui.LogLines.Level.INFO;
import static dev.junitmanager.ui.LogLines.Level.PLAIN;
import static dev.junitmanager.ui.LogLines.Level.WARNING;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LogLinesTest {
    @Test
    void classifiesMavenLevelTags() {
        assertEquals(ERROR, LogLines.level("[ERROR] Failed to execute goal"));
        assertEquals(WARNING, LogLines.level("[WARNING] Some problems were encountered"));
        assertEquals(WARNING, LogLines.level("[WARN] deprecated"));
        assertEquals(INFO, LogLines.level("[INFO] Scanning for projects..."));
        assertEquals(DEBUG, LogLines.level("[DEBUG] classpath"));
        assertEquals(INFO, LogLines.level("[INFO] BUILD SUCCESS"));
        assertEquals(INFO, LogLines.level("[INFO] BUILD FAILURE"));
        assertEquals(PLAIN, LogLines.level("calculator> mvn -B test"));
        assertEquals(PLAIN, LogLines.level("at org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:51)"));
    }
}
