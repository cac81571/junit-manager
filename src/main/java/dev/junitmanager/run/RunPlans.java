package dev.junitmanager.run;

import dev.junitmanager.model.TestKind;
import dev.junitmanager.model.TestNode;
import dev.junitmanager.model.TestStatus;
import dev.junitmanager.model.Workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class RunPlans {
    private RunPlans() {
    }

    public enum Kind {
        ALL, NODE, FAILED
    }

    public record Spec(Kind kind, String nodeKey) {
        public static Spec all() {
            return new Spec(Kind.ALL, null);
        }

        public static Spec node(TestNode node) {
            TestNode target = node != null && node.synthetic() ? node.parent() : node;
            if (target == null || target.kind() == TestKind.ROOT) {
                return all();
            }
            return new Spec(Kind.NODE, target.key());
        }

        public static Spec failed() {
            return new Spec(Kind.FAILED, null);
        }
    }

    public record Plan(
            String label,
            Path workingDirectory,
            List<String> mavenArgs,
            List<Path> reportModules,
            List<TestNode> scope,
            boolean clearSynthetics,
            boolean broadened
    ) {
    }

    public static Plan build(Workspace workspace, Spec spec, boolean debug) {
        if (workspace == null || spec == null) {
            return null;
        }
        return switch (spec.kind) {
            case ALL -> fromMethods(workspace, "すべてのテスト", workspace.realMethods(), null, true, debug, false);
            case FAILED -> failed(workspace, debug);
            case NODE -> node(workspace, workspace.findMethod(spec.nodeKey), debug);
        };
    }

    private static Plan failed(Workspace workspace, boolean debug) {
        LinkedHashSet<TestNode> methods = new LinkedHashSet<>();
        workspace.tree().walk(node -> {
            if (node.kind() != TestKind.METHOD) {
                return;
            }
            if (!node.synthetic() && node.status() == TestStatus.FAILED) {
                methods.add(node);
            }
            if (node.synthetic() && node.status() == TestStatus.FAILED && node.parent() != null) {
                methods.addAll(node.parent().realMethods());
            }
        });
        if (methods.isEmpty()) {
            return null;
        }
        return fromMethods(workspace, "失敗したテスト", new ArrayList<>(methods), methodSelector(methods), false, debug, false);
    }

    private static Plan node(Workspace workspace, TestNode node, boolean debug) {
        if (node == null) {
            return null;
        }
        if (node.synthetic() && node.parent() != null) {
            node = node.parent();
        }
        return switch (node.kind()) {
            case ROOT -> fromMethods(workspace, "すべてのテスト", workspace.realMethods(), null, true, debug, false);
            case MODULE -> fromMethods(workspace, node.displayName(), node.realMethods(), null, true, debug, false);
            case PACKAGE -> fromMethods(
                    workspace,
                    node.displayName(),
                    node.realMethods(),
                    classSelector(node.realMethods(), true),
                    true,
                    debug,
                    false
            );
            case CLASS -> fromMethods(workspace, node.fqcn(), node.realMethods(), node.fqcn(), true, debug, false);
            case METHOD -> fromMethods(
                    workspace,
                    node.fqcn() + "#" + node.methodName(),
                    List.of(node),
                    node.fqcn() + "#" + node.methodName(),
                    false,
                    debug,
                    false
            );
        };
    }

    private static Plan fromMethods(
            Workspace workspace,
            String label,
            List<TestNode> methods,
            String selector,
            boolean clearSynthetics,
            boolean debug,
            boolean broadened
    ) {
        if (methods.isEmpty() && selector == null && !"すべてのテスト".equals(label)) {
            return null;
        }
        boolean widened = broadened;
        String effective = selector;
        if (effective != null && effective.length() > 5000) {
            effective = classSelector(methods, true);
            widened = true;
        }
        if (effective != null && effective.length() > 5000) {
            effective = null;
            widened = true;
        }
        Path root = workspace.root();
        Path common = commonModule(methods, root);
        Path directory = common != null ? common : root;
        List<String> projects = common == null ? projects(workspace, methods) : List.of();
        List<String> args = new ArrayList<>();
        args.add("-B");
        args.add("--fail-at-end");
        args.add("-Dstyle.color=always");
        args.add("-DtrimStackTrace=false");
        if (!projects.isEmpty()) {
            args.add("-pl");
            args.add(String.join(",", projects));
            args.add("-am");
        }
        if (effective != null) {
            args.add("-Dsurefire.failIfNoSpecifiedTests=false");
            args.add("-Dtest=" + effective);
        }
        if (debug) {
            args.add("-Dmaven.surefire.debug");
        }
        args.add("test");
        Set<Path> modules = new LinkedHashSet<>();
        for (TestNode method : methods) {
            if (method.moduleDir() != null) {
                modules.add(method.moduleDir());
            }
        }
        if (modules.isEmpty()) {
            modules.add(directory);
        }
        return new Plan(label, directory, args, List.copyOf(modules), List.copyOf(methods), clearSynthetics, widened);
    }

    private static Path commonModule(List<TestNode> methods, Path root) {
        Path common = null;
        for (TestNode method : methods) {
            Path dir = method.moduleDir() == null ? root : method.moduleDir();
            if (common == null) {
                common = dir;
            } else if (!common.equals(dir)) {
                return null;
            }
        }
        return common == null ? root : common;
    }

    private static List<String> projects(Workspace workspace, List<TestNode> methods) {
        LinkedHashSet<String> projects = new LinkedHashSet<>();
        for (TestNode method : methods) {
            projects.add(workspace.projectSelector(method.moduleDir()));
        }
        return List.copyOf(projects);
    }

    static String methodSelector(Iterable<TestNode> methods) {
        java.util.Map<String, List<String>> byClass = new java.util.LinkedHashMap<>();
        for (TestNode method : methods) {
            if (method.synthetic() || method.fqcn() == null || method.methodName() == null) {
                continue;
            }
            byClass.computeIfAbsent(method.fqcn(), key -> new ArrayList<>()).add(method.methodName());
        }
        return byClass.entrySet().stream()
                .map(entry -> entry.getKey() + "#" + entry.getValue().stream().distinct().collect(Collectors.joining("+")))
                .collect(Collectors.joining(","));
    }

    static String classSelector(List<TestNode> methods, boolean topLevel) {
        LinkedHashSet<String> classes = new LinkedHashSet<>();
        for (TestNode method : methods) {
            if (method.fqcn() == null) {
                continue;
            }
            String fqcn = method.fqcn();
            if (topLevel) {
                int dollar = fqcn.indexOf('$');
                if (dollar >= 0) {
                    fqcn = fqcn.substring(0, dollar);
                }
            }
            classes.add(fqcn);
        }
        return String.join(",", classes);
    }
}
