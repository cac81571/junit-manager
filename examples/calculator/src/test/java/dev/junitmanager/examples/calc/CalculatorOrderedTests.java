package dev.junitmanager.examples.calc;

import dev.junitmanager.suite.DynamicTestExecutor;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class CalculatorOrderedTests {
    @TestFactory
    Stream<DynamicTest> orderedTests() {
        return Stream.of(
                DynamicTest.dynamicTest("adds", () -> DynamicTestExecutor.execute(CalculatorTest::adds)),
                DynamicTest.dynamicTest("divides", () -> DynamicTestExecutor.execute(CalculatorTest.Div::divides))
        );
    }
}
