package dev.junitmanager.suite;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

public final class DynamicTestExecutor {
    private DynamicTestExecutor() {
    }

    /**
     * インスタンスメソッド参照で実行する。メソッドが無いとコンパイルエラーになる。
     * 例: {@code execute(CalculatorTest::adds)}
     */
    public static <T> void execute(InstanceMethod<T> method) {
        run(selectorOf(method));
    }

    /**
     * static メソッド参照で実行する。メソッドが無いとコンパイルエラーになる。
     * 例: {@code execute(SomeTest::staticCase)}
     */
    public static void execute(StaticMethod method) {
        run(selectorOf(method));
    }

    @FunctionalInterface
    public interface InstanceMethod<T> extends Serializable {
        void call(T instance) throws Throwable;
    }

    @FunctionalInterface
    public interface StaticMethod extends Serializable {
        void call() throws Throwable;
    }

    private static void run(String selector) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectMethod(selector))
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
            throw new IllegalStateException("テストが見つかりません: " + selector);
        }
        if (skipped.get() != null) {
            abort(skipped.get());
        }
    }

    private static String selectorOf(Serializable methodRef) {
        SerializedLambda lambda = serialized(methodRef);
        String name = lambda.getImplMethodName();
        if (name.startsWith("lambda$") || name.equals("$deserializeLambda$")) {
            throw new IllegalArgumentException("メソッド参照を渡してください（ラムダ式は使えません）");
        }
        String className = lambda.getImplClass().replace('/', '.');
        String params = javaParameterList(lambda.getImplMethodSignature());
        if (params.isEmpty()) {
            return className + "#" + name;
        }
        return className + "#" + name + "(" + params + ")";
    }

    private static SerializedLambda serialized(Serializable methodRef) {
        try {
            Method writeReplace = methodRef.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object replacement = writeReplace.invoke(methodRef);
            if (replacement instanceof SerializedLambda lambda) {
                return lambda;
            }
            throw new IllegalArgumentException("メソッド参照を渡せませんでした: " + replacement);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("メソッド参照の解析に失敗しました", ex);
        }
    }

    /** JVM メソッド記述子 {@code (ILjava/lang/String;)V} → {@code int,java.lang.String} */
    private static String javaParameterList(String descriptor) {
        int end = descriptor.indexOf(')');
        if (end <= 1) {
            return "";
        }
        StringBuilder params = new StringBuilder();
        int i = 1;
        while (i < end) {
            if (!params.isEmpty()) {
                params.append(',');
            }
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int semi = descriptor.indexOf(';', i);
                params.append(descriptor.substring(i + 1, semi).replace('/', '.'));
                i = semi + 1;
            } else if (c == '[') {
                int start = i;
                while (descriptor.charAt(i) == '[') {
                    i++;
                }
                if (descriptor.charAt(i) == 'L') {
                    int semi = descriptor.indexOf(';', i);
                    i = semi + 1;
                } else {
                    i++;
                }
                params.append(descriptor.substring(start, i).replace('/', '.'));
            } else {
                params.append(switch (c) {
                    case 'Z' -> "boolean";
                    case 'B' -> "byte";
                    case 'C' -> "char";
                    case 'S' -> "short";
                    case 'I' -> "int";
                    case 'J' -> "long";
                    case 'F' -> "float";
                    case 'D' -> "double";
                    default -> throw new IllegalArgumentException("未知の型: " + c);
                });
                i++;
            }
        }
        return params.toString();
    }
}
