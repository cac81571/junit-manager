package dev.junitmanager.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public final class TestNode {
    private final TestKind kind;
    private final String name;
    private final String displayName;
    private final String fqcn;
    private final String methodName;
    private final Path moduleDir;
    private final Path sourceFile;
    private final int line;
    private final boolean disabled;
    private final String ignoreReason;
    private final Framework framework;
    private final boolean synthetic;
    private final List<TestNode> children = new ArrayList<>();
    private final List<CaseResult> cases = new ArrayList<>();
    private TestNode parent;
    private TestStatus status;
    private long durationMillis = -1;

    public TestNode(
            TestKind kind,
            String name,
            String displayName,
            String fqcn,
            String methodName,
            Path moduleDir,
            Path sourceFile,
            int line,
            boolean disabled,
            String ignoreReason,
            Framework framework,
            boolean synthetic
    ) {
        this.kind = kind;
        this.name = name == null || name.isBlank() ? "(無名)" : name;
        this.displayName = displayName == null || displayName.isBlank() ? this.name : displayName;
        this.fqcn = fqcn;
        this.methodName = methodName;
        this.moduleDir = moduleDir;
        this.sourceFile = sourceFile;
        this.line = line;
        this.disabled = disabled;
        this.ignoreReason = ignoreReason;
        this.framework = framework == null ? Framework.UNKNOWN : framework;
        this.synthetic = synthetic;
        this.status = kind == TestKind.METHOD && disabled ? TestStatus.SKIPPED : TestStatus.NOT_RUN;
    }

    public static TestNode root(String name) {
        return new TestNode(TestKind.ROOT, name, name, null, null, null, null, 0, false, null, Framework.UNKNOWN, false);
    }

    public static TestNode synthetic(TestNode classNode, String reportedName, CaseResult result) {
        TestNode node = new TestNode(
                TestKind.METHOD,
                reportedName,
                reportedName,
                classNode.fqcn(),
                reportedName,
                classNode.moduleDir(),
                classNode.sourceFile(),
                classNode.line(),
                false,
                null,
                classNode.framework(),
                true
        );
        node.setCases(List.of(result));
        return node;
    }

    public TestKind kind() {
        return kind;
    }

    public String name() {
        return name;
    }

    public String displayName() {
        return displayName;
    }

    public String fqcn() {
        return fqcn;
    }

    public String methodName() {
        return methodName;
    }

    public Path moduleDir() {
        return moduleDir;
    }

    public Path sourceFile() {
        return sourceFile;
    }

    public int line() {
        return line;
    }

    public boolean disabled() {
        return disabled;
    }

    public String ignoreReason() {
        return ignoreReason;
    }

    public Framework framework() {
        return framework;
    }

    public boolean synthetic() {
        return synthetic;
    }

    public TestNode parent() {
        return parent;
    }

    public List<TestNode> children() {
        return Collections.unmodifiableList(children);
    }

    public void addChild(TestNode child) {
        child.parent = this;
        children.add(child);
    }

    public void sortChildren(Comparator<TestNode> comparator) {
        children.sort(comparator);
    }

    public void removeSyntheticChildren() {
        children.removeIf(TestNode::synthetic);
    }

    public TestStatus status() {
        return status;
    }

    public void setStatus(TestStatus status) {
        this.status = status;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public String durationLabel() {
        return DurationFormat.of(durationMillis);
    }

    public List<CaseResult> cases() {
        return Collections.unmodifiableList(cases);
    }

    public void clearCases() {
        cases.clear();
    }

    public void setCases(List<CaseResult> next) {
        cases.clear();
        cases.addAll(next);
        if (cases.isEmpty()) {
            status = disabled ? TestStatus.SKIPPED : TestStatus.NOT_RUN;
            durationMillis = -1;
            return;
        }
        boolean failed = false;
        boolean passed = false;
        boolean skipped = false;
        long duration = 0;
        boolean completeDuration = true;
        for (CaseResult result : cases) {
            switch (result.status()) {
                case FAILED -> failed = true;
                case PASSED -> passed = true;
                case SKIPPED -> skipped = true;
                default -> {
                }
            }
            if (result.durationMillis() >= 0) {
                duration += result.durationMillis();
            } else {
                completeDuration = false;
            }
        }
        if (failed) {
            status = TestStatus.FAILED;
        } else if (passed) {
            status = TestStatus.PASSED;
        } else if (skipped) {
            status = TestStatus.SKIPPED;
        } else {
            status = TestStatus.NOT_RUN;
        }
        durationMillis = completeDuration ? duration : -1;
    }

    public String key() {
        String module = moduleDir == null ? "" : moduleDir.toAbsolutePath().normalize().toString();
        return switch (kind) {
            case METHOD -> module + "|M|" + fqcn + "#" + methodName;
            case CLASS -> module + "|C|" + fqcn;
            case PACKAGE -> module + "|P|" + name;
            case MODULE -> module + "|D|";
            case ROOT -> "|R|";
        };
    }

    public String selector() {
        return switch (kind) {
            case METHOD -> fqcn + "#" + methodName;
            case CLASS -> fqcn;
            default -> displayName;
        };
    }

    public void walk(Consumer<TestNode> consumer) {
        consumer.accept(this);
        for (TestNode child : children) {
            child.walk(consumer);
        }
    }

    public List<TestNode> realMethods() {
        List<TestNode> methods = new ArrayList<>();
        walk(node -> {
            if (node.kind == TestKind.METHOD && !node.synthetic) {
                methods.add(node);
            }
        });
        return methods;
    }

    public int count(TestStatus wanted) {
        if (kind == TestKind.METHOD) {
            return status == wanted ? 1 : 0;
        }
        int total = 0;
        for (TestNode child : children) {
            total += child.count(wanted);
        }
        return total;
    }

    public int methodCount() {
        if (kind == TestKind.METHOD) {
            return 1;
        }
        int total = 0;
        for (TestNode child : children) {
            total += child.methodCount();
        }
        return total;
    }

    public TestNode ancestor(TestKind wanted) {
        TestNode current = this;
        while (current != null && current.kind != wanted) {
            current = current.parent;
        }
        return current;
    }

    public void rollup() {
        for (TestNode child : children) {
            child.rollup();
        }
        if (kind == TestKind.METHOD || children.isEmpty()) {
            return;
        }
        boolean failed = false;
        boolean running = false;
        boolean notRun = false;
        boolean passed = false;
        boolean skipped = false;
        long duration = 0;
        boolean anyDuration = false;
        boolean allDuration = true;
        for (TestNode child : children) {
            switch (child.status) {
                case FAILED -> failed = true;
                case RUNNING -> running = true;
                case NOT_RUN -> notRun = true;
                case PASSED -> passed = true;
                case SKIPPED -> skipped = true;
            }
            if (child.durationMillis >= 0) {
                duration += child.durationMillis;
                anyDuration = true;
            } else {
                allDuration = false;
            }
        }
        if (failed) {
            status = TestStatus.FAILED;
        } else if (running) {
            status = TestStatus.RUNNING;
        } else if (notRun) {
            status = TestStatus.NOT_RUN;
        } else if (passed) {
            status = TestStatus.PASSED;
        } else if (skipped) {
            status = TestStatus.SKIPPED;
        } else {
            status = TestStatus.NOT_RUN;
        }
        durationMillis = anyDuration && allDuration ? duration : -1;
    }
}
