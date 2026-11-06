package dev.junitmanager.run;

import dev.junitmanager.model.CaseResult;
import dev.junitmanager.model.TestKind;
import dev.junitmanager.model.TestNode;
import dev.junitmanager.model.TestStatus;
import dev.junitmanager.model.Workspace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ResultApplier {
    private ResultApplier() {
    }

    public static boolean markRunning(List<TestNode> scope, boolean clearSynthetics) {
        boolean structural = false;
        if (clearSynthetics) {
            Set<TestNode> classes = new LinkedHashSet<>();
            for (TestNode method : scope) {
                TestNode classNode = method.ancestor(TestKind.CLASS);
                if (classNode != null) {
                    classes.add(classNode);
                }
            }
            for (TestNode classNode : classes) {
                boolean hadSynthetic = classNode.children().stream().anyMatch(TestNode::synthetic);
                if (hadSynthetic) {
                    classNode.removeSyntheticChildren();
                    structural = true;
                }
            }
        }
        for (TestNode method : scope) {
            if (method.synthetic() || method.kind() != TestKind.METHOD) {
                continue;
            }
            method.setStatus(TestStatus.RUNNING);
            method.clearCases();
            method.setDurationMillis(-1);
        }
        rollup(scope);
        return structural;
    }

    public static boolean apply(Workspace workspace, List<TestNode> scope, List<ReportedTest> reports) {
        Set<TestNode> scopeSet = new HashSet<>(scope);
        Map<TestNode, List<ReportedTest>> matched = new LinkedHashMap<>();
        List<ReportedTest> unmatched = new ArrayList<>();
        for (ReportedTest report : reports) {
            TestNode method = locate(workspace, report);
            if (method != null && scopeSet.contains(method)) {
                matched.computeIfAbsent(method, key -> new ArrayList<>()).add(report);
            } else if (method == null) {
                unmatched.add(report);
            }
        }
        for (TestNode method : scope) {
            if (method.synthetic() || method.kind() != TestKind.METHOD) {
                continue;
            }
            List<ReportedTest> found = matched.get(method);
            if (found == null) {
                if (method.status() == TestStatus.RUNNING) {
                    method.setStatus(method.disabled() ? TestStatus.SKIPPED : TestStatus.NOT_RUN);
                    method.clearCases();
                    method.setDurationMillis(-1);
                }
                continue;
            }
            method.setCases(found.stream().map(ResultApplier::toCase).toList());
        }
        boolean structural = false;
        for (ReportedTest report : unmatched) {
            TestNode classNode = classInScope(workspace, report, scopeSet);
            if (classNode == null) {
                continue;
            }
            classNode.addChild(TestNode.synthetic(classNode, report.name(), toCase(report)));
            structural = true;
        }
        workspace.tree().rollup();
        return structural;
    }

    private static void rollup(List<TestNode> scope) {
        if (scope.isEmpty()) {
            return;
        }
        TestNode root = scope.get(0);
        while (root.parent() != null) {
            root = root.parent();
        }
        root.rollup();
    }

    private static TestNode locate(Workspace workspace, ReportedTest report) {
        TestNode classNode = workspace.findClass(report.moduleDir(), report.className());
        if (classNode == null && report.className() != null && report.className().contains("$")) {
            classNode = workspace.findClass(report.moduleDir(), report.className().substring(0, report.className().indexOf('$')));
        }
        if (classNode == null) {
            return null;
        }
        return match(classNode, report.name());
    }

    private static TestNode match(TestNode classNode, String name) {
        List<TestNode> methods = new ArrayList<>();
        classNode.walk(node -> {
            if (node.kind() == TestKind.METHOD && !node.synthetic()) {
                methods.add(node);
            }
        });
        for (TestNode method : methods) {
            if (method.methodName().equals(name)) {
                return method;
            }
        }
        TestNode prefix = null;
        for (TestNode method : methods) {
            String methodName = method.methodName();
            if (name.startsWith(methodName + "(") || name.startsWith(methodName + "[")) {
                if (prefix == null || methodName.length() > prefix.methodName().length()) {
                    prefix = method;
                }
            }
        }
        if (prefix != null) {
            return prefix;
        }
        for (TestNode method : methods) {
            if (name.equals(method.displayName()) && !name.equals(method.methodName())) {
                return method;
            }
        }
        return null;
    }

    private static TestNode classInScope(Workspace workspace, ReportedTest report, Set<TestNode> scope) {
        TestNode classNode = workspace.findClass(report.moduleDir(), report.className());
        if (classNode == null && report.className() != null && report.className().contains("$")) {
            classNode = workspace.findClass(report.moduleDir(), report.className().substring(0, report.className().indexOf('$')));
        }
        if (classNode == null) {
            return null;
        }
        for (TestNode method : scope) {
            TestNode owner = method.ancestor(TestKind.CLASS);
            while (owner != null) {
                if (owner == classNode) {
                    return classNode;
                }
                owner = owner.parent() == null ? null : owner.parent().ancestor(TestKind.CLASS);
            }
        }
        return null;
    }

    private static CaseResult toCase(ReportedTest report) {
        return new CaseResult(
                report.name(),
                report.status(),
                report.durationMillis(),
                report.message(),
                report.type(),
                report.trace(),
                report.systemOut()
        );
    }
}
