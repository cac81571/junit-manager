package dev.junitmanager.examples.calc;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import dev.junitmanager.suite.TestParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("計算機")
class CalculatorTest {
    private final Calculator calculator = new Calculator();

    @Test
    @Order(2)
    @DisplayName("足し算")
    void adds() {
        assertEquals(3, calculator.add(1, 2));
    }

    @Test
    @Order(1)
    @DisplayName("引き算")
    void subtracts() {
        assertEquals(TestParams.getInt("PARAM", 0), calculator.sub(2, 1), "2 - 1 は 1");
    }

    @Test
    @Disabled("保留")
    void disabledOne() {
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    @DisplayName("掛け算")
    void multiplies(int value) {
        assertEquals(value * 2, calculator.mul(value, 2));
    }

    @Nested
    @Order(3)
    @DisplayName("割り算")
    class Div {
        @Test
        void divides() {
            assertEquals(2, calculator.div(4, 2));
        }
    }
}
