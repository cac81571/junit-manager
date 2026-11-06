package dev.junitmanager.examples.calc;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.opentest4j.TestAbortedException;

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

    public static void main(String[] args) {
        int problems = 0;
        for (String method : TestExecutionOrder.METHODS) {
            try {
                OrderedTestExecutor.execute(method);
            } catch (TestAbortedException ignored) {
                // スキップ
            } catch (Throwable ex) {
                problems++;
                ex.printStackTrace(System.out);
            }
        }
        System.exit(problems == 0 ? 0 : 1);
    }
}
