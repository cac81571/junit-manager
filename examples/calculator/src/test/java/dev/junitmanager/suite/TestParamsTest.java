package dev.junitmanager.suite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestParamsTest {
    @AfterEach
    void clear() {
        System.clearProperty("PARAM");
        System.clearProperty("FLAG");
    }

    @Test
    void getReturnsNullWhenUnsetAndAllowsEmpty() {
        assertNull(TestParams.get("PARAM"));
        assertEquals("1000", TestParams.get("PARAM", "1000"));
        System.setProperty("PARAM", "");
        assertEquals("", TestParams.get("PARAM"));
        assertEquals("", TestParams.get("PARAM", "1000"));
        System.setProperty("PARAM", "2000");
        assertEquals("2000", TestParams.get("PARAM"));
    }

    @Test
    void getIntUsesNullDefaultAndOverride() {
        assertNull(TestParams.getInt("PARAM"));
        assertEquals(1000, TestParams.getInt("PARAM", 1000));
        System.setProperty("PARAM", "");
        assertNull(TestParams.getInt("PARAM"));
        assertEquals(1000, TestParams.getInt("PARAM", 1000));
        System.setProperty("PARAM", "2000");
        assertEquals(2000, TestParams.getInt("PARAM"));
        assertEquals(2000, TestParams.getInt("PARAM", 1000));
    }

    @Test
    void getBooleanReadsProperty() {
        assertNull(TestParams.getBoolean("FLAG"));
        assertEquals(false, TestParams.getBoolean("FLAG", false));
        System.setProperty("FLAG", "true");
        assertTrue(TestParams.getBoolean("FLAG"));
    }
}
