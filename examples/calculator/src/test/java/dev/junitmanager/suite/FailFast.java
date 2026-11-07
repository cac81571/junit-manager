package dev.junitmanager.suite;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.function.Executable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** 動的テストを順に実行し、失敗した時点で以降をスキップする。 */
public final class FailFast {
    private FailFast() {
    }

    public static Stream<DynamicTest> of(DynamicTest... tests) {
        AtomicBoolean failed = new AtomicBoolean();
        return Stream.of(tests).map(test -> DynamicTest.dynamicTest(test.getDisplayName(), wrap(test.getExecutable(), failed)));
    }

    private static Executable wrap(Executable executable, AtomicBoolean failed) {
        return () -> {
            assumeFalse(failed.get(), "前のテストが失敗したため実行しません");
            try {
                executable.execute();
            } catch (Throwable error) {
                failed.set(true);
                throw error;
            }
        };
    }
}
