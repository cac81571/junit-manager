package dev.junitmanager.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Workspace {
    private final Path root;
    private final String name;
    private final TestNode tree;
    private final boolean multiModule;
    private final List<Path> sourceRoots;
    private final List<String> warnings;
    private final Map<String, TestNode> classesByKey;

    public Workspace(
            Path root,
            String name,
            TestNode tree,
            boolean multiModule,
            List<Path> sourceRoots,
            List<String> warnings
    ) {
        this.root = root.toAbsolutePath().normalize();
        this.name = name;
        this.tree = tree;
        this.multiModule = multiModule;
        this.sourceRoots = List.copyOf(sourceRoots);
        this.warnings = List.copyOf(warnings);
        this.classesByKey = new LinkedHashMap<>();
        tree.walk(node -> {
            if (node.kind() == TestKind.CLASS && node.ancestor(TestKind.SUITE) == null
                    && node.moduleDir() != null && node.fqcn() != null) {
                classesByKey.putIfAbsent(classKey(node.moduleDir(), node.fqcn()), node);
                classesByKey.putIfAbsent(classKey(node.moduleDir(), node.fqcn().replace('$', '.')), node);
            }
        });
    }

    public Path root() {
        return root;
    }

    public String name() {
        return name;
    }

    public TestNode tree() {
        return tree;
    }

    public boolean multiModule() {
        return multiModule;
    }

    public List<Path> sourceRoots() {
        return sourceRoots;
    }

    public List<String> warnings() {
        return warnings;
    }

    public List<TestNode> realMethods() {
        return tree.realMethods();
    }

    public int methodCount() {
        return tree.methodCount();
    }

    public String projectSelector(Path moduleDir) {
        if (moduleDir == null) {
            return ".";
        }
        Path relative = root.relativize(moduleDir.toAbsolutePath().normalize());
        String text = relative.toString();
        if (text.isBlank() || text.equals(".")) {
            return ".";
        }
        return text.replace('\\', '/');
    }

    public TestNode findClass(Path moduleDir, String className) {
        if (moduleDir == null || className == null) {
            return null;
        }
        TestNode found = classesByKey.get(classKey(moduleDir, className));
        if (found != null) {
            return found;
        }
        return classesByKey.get(classKey(moduleDir, className.replace('$', '.')));
    }

    public TestNode findMethod(String key) {
        if (key == null) {
            return null;
        }
        TestNode[] found = new TestNode[1];
        tree.walk(node -> {
            if (found[0] == null && key.equals(node.key())) {
                found[0] = node;
            }
        });
        return found[0];
    }

    public Map<String, Snapshot> snapshotResults() {
        Map<String, Snapshot> snapshots = new LinkedHashMap<>();
        tree.walk(node -> {
            if (node.kind() == TestKind.METHOD && !node.synthetic() && node.ancestor(TestKind.SUITE) == null
                    && node.status() != TestStatus.NOT_RUN) {
                snapshots.put(node.key(), new Snapshot(node.status(), node.durationMillis(), node.cases()));
            }
        });
        return snapshots;
    }

    public void restoreResults(Map<String, Snapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            tree.rollup();
            return;
        }
        tree.walk(node -> {
            if (node.kind() != TestKind.METHOD || node.synthetic()) {
                return;
            }
            Snapshot snapshot = snapshots.get(node.key());
            if (snapshot == null || snapshot.status == TestStatus.RUNNING) {
                return;
            }
            if (snapshot.status == TestStatus.SKIPPED && node.disabled() && snapshot.cases.isEmpty()) {
                return;
            }
            node.setStatus(snapshot.status);
            node.setDurationMillis(snapshot.durationMillis);
            node.setCases(snapshot.cases);
        });
        tree.syncDuplicates();
    }

    private static String classKey(Path moduleDir, String className) {
        return moduleDir.toAbsolutePath().normalize() + "|" + className;
    }

    public record Snapshot(TestStatus status, long durationMillis, List<CaseResult> cases) {
        public Snapshot {
            cases = cases == null ? List.of() : List.copyOf(cases);
        }
    }

    public List<Path> moduleDirectories() {
        List<Path> dirs = new ArrayList<>();
        tree.walk(node -> {
            if (node.kind() == TestKind.MODULE && node.moduleDir() != null) {
                dirs.add(node.moduleDir());
            }
        });
        if (dirs.isEmpty()) {
            dirs.add(root);
        }
        return dirs;
    }
}
