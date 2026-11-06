package dev.junitmanager.suite;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

public class OrderedSuiteRunner {
    @TestFactory
    Stream<DynamicTest> orderedTests() {
        return TestExecutionOrder.METHODS.stream()
                .map(method ->
                        DynamicTest.dynamicTest(
                                method,
                                () -> OrderedTestExecutor.execute(method)
                        )
                );
    }

}
