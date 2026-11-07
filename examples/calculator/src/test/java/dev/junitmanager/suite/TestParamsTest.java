package dev.junitmanager.suite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestParamsTest {
    @AfterEach
    void clear() {
        System.clearProperty("PARAM");
        System.clearProperty("FLAG");
    }

    @Test
    void getIntUsesDefaultAndOverride() {
        assertEquals(1000, TestParams.getInt("PARAM", 1000));
        System.setProperty("PARAM", "2000");
        assertEquals(2000, TestParams.getInt("PARAM", 1000));
        assertEquals("2000", TestParams.get("PARAM", "1000"));
    }

    @Test
    void getBooleanReadsProperty() {
        assertEquals(false, TestParams.getBoolean("FLAG", false));
        System.setProperty("FLAG", "true");
        assertEquals(true, TestParams.getBoolean("FLAG", false));
    }
}
