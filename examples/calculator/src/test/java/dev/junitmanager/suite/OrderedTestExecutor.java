package dev.junitmanager.suite;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

public final class OrderedTestExecutor {
    private OrderedTestExecutor() {
    }

    public static void execute(String method) {
        String resolved = resolve(method);
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectMethod(resolved))
                .filters(EngineFilter.includeEngines("junit-jupiter"))
                .build();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> skipped = new AtomicReference<>();
        int[] found = {0};
        try (LauncherSession session = LauncherFactory.openSession()) {
            session.getLauncher().execute(request, new org.junit.platform.launcher.TestExecutionListener() {
                @Override
                public void executionSkipped(TestIdentifier identifier, String reason) {
                    if (identifier.isTest()) {
                        found[0]++;
                        skipped.compareAndSet(null, reason == null ? "" : reason);
                    }
                }

                @Override
                public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
                    if (!identifier.isTest() || result.getStatus() == TestExecutionResult.Status.SUCCESSFUL) {
                        if (identifier.isTest()) {
                            found[0]++;
                        }
                        return;
                    }
                    found[0]++;
                    failure.compareAndSet(null, result.getThrowable().orElseGet(() -> new AssertionError(identifier.getDisplayName())));
                }
            });
        }
        if (failure.get() != null) {
            Throwable error = failure.get();
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (error instanceof Error thrown) {
                throw thrown;
            }
            throw new IllegalStateException(error);
        }
        if (found[0] == 0) {
            throw new IllegalStateException("テストが見つかりません: " + method);
        }
        if (skipped.get() != null) {
            abort(skipped.get());
        }
    }

    private static String resolve(String selector) {
        int hash = selector.indexOf('#');
        String className = selector.substring(0, hash);
        String methodName = selector.substring(hash + 1);
        if (methodName.indexOf('(') >= 0) {
            return selector;
        }
        List<Method> matches = new ArrayList<>();
        Class<?> type;
        try {
            type = Class.forName(className);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("クラスが見つかりません: " + className, ex);
        }
        for (Method candidate : type.getDeclaredMethods()) {
            if (candidate.getName().equals(methodName)) {
                matches.add(candidate);
            }
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("同名のメソッドが複数あります。引数の型を書いてください: " + selector);
        }
        if (matches.isEmpty() || matches.get(0).getParameterCount() == 0) {
            return selector;
        }
        String types = Arrays.stream(matches.get(0).getParameterTypes()).map(Class::getName).collect(Collectors.joining(","));
        return className + "#" + methodName + "(" + types + ")";
    }
}
