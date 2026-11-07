package dev.junitmanager.examples.calc;

import dev.junitmanager.suite.DynamicTestExecutor;
import dev.junitmanager.suite.RunConfirm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class CalculatorOrderedTests {
    
	@DisplayName("シナリオ1-1")
	@TestFactory
    Stream<DynamicTest> orderedTests() {
        RunConfirm.ask("""
                事前条件:
                - Calculator の基本演算を順に確認します
                """);
        return Stream.of(
                DynamicTest.dynamicTest("CalculatorTest#adds", () -> DynamicTestExecutor.execute(CalculatorTest::adds)),
                DynamicTest.dynamicTest("CalculatorTest#div", () -> DynamicTestExecutor.execute(CalculatorTest.Div::divides)),
                DynamicTest.dynamicTest("CalculatorTest#subtracts", () -> DynamicTestExecutor.execute(CalculatorTest::subtracts))
        );
    }
}
