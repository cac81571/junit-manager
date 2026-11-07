package dev.junitmanager.examples.calc;

import dev.junitmanager.suite.DynamicTestExecutor;
import dev.junitmanager.suite.FailFast;
import dev.junitmanager.suite.RunConfirm;
import dev.junitmanager.suite.TestParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.stream.Stream;

class CalculatorOrderedTests {
    @Test
    @DisplayName("パラメータチェック")
    void paramCheck() {
        assertNotEquals("", TestParams.get("PARAM"), "PARAM を設定してください");
    }
	
    @DisplayName("シナリオ1-1")
    @TestFactory
    Stream<DynamicTest> orderedTests() {
        RunConfirm.ask("""
                事前条件:
                - Calculator の基本演算を順に確認します
                - PARAM=%s
                """.formatted(TestParams.get("PARAM")));
        return FailFast.of(
            DynamicTest.dynamicTest("CalculatorOrderedTests#paramCheck", () -> DynamicTestExecutor.execute(CalculatorOrderedTests::paramCheck)),
            DynamicTest.dynamicTest("CalculatorTest#subtracts", () -> DynamicTestExecutor.execute(CalculatorTest::subtracts)),
            DynamicTest.dynamicTest("CalculatorTest#div", () -> DynamicTestExecutor.execute(CalculatorTest.Div::divides)),
            DynamicTest.dynamicTest("CalculatorTest#subtracts", () -> DynamicTestExecutor.execute(CalculatorTest::subtracts))
        );
    }
}
