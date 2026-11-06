package dev.junitmanager.run;

import dev.junitmanager.model.TestKind;
import dev.junitmanager.model.TestNode;
import dev.junitmanager.model.TestStatus;
import dev.junitmanager.model.Workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code order.csv} と {@code orderedTests} ファクトリを GUI の実行対象にする。
 * Surefire は動的テストを {@code orderedTests()[n]} と報告するので、行番号で本来のメソッドへ戻す。
 */
public final class ExecutionOrder {
    private static final Pattern INDEXED = Pattern.compile("^orderedTests\\(\\)\\[(\\d+)\\].*");

    private ExecutionOrder() {
    }

    public record Entry(String className, String methodName) {
    }

    public static boolean isRunner(TestNode classNode) {
        if (classNode == null || classNode.kind() != TestKind.CLASS || classNode.moduleDir() == null) {
            return false;
        }
        if (classNode.ancestor(TestKind.SUITE) != null) {
            return false;
        }
        boolean factory = false;
        for (TestNode child : classNode.children()) {
            if (child.kind() == TestKind.METHOD && "orderedTests".equals(child.methodName()) && !child.synthetic()) {
                factory = true;
                break;
            }
        }
        return factory && !read(classNode.moduleDir()).isEmpty();
    }

    public static List<Entry> read(Path moduleDir) {
        if (moduleDir == null) {
            return List.of();
        }
        Path csv = moduleDir.resolve("order.csv");
        if (!Files.isRegularFile(csv)) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(csv, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                    continue;
                }
                int hash = trimmed.indexOf('#');
                if (hash <= 0 || hash != trimmed.lastIndexOf('#') || hash == trimmed.length() - 1) {
                    continue;
                }
                String className = trimmed.substring(0, hash).trim();
                String methodName = trimmed.substring(hash + 1).trim();
                int paren = methodName.indexOf('(');
                if (paren > 0) {
                    methodName = methodName.substring(0, paren).trim();
                }
                if (!className.isEmpty() && !methodName.isEmpty()) {
                    entries.add(new Entry(className, methodName));
                }
            }
        } catch (IOException ex) {
            return List.of();
        }
        return List.copyOf(entries);
    }

    public static List<TestNode> scope(Workspace workspace, TestNode classNode) {
        LinkedHashSet<TestNode> scope = new LinkedHashSet<>();
        Path module = classNode.moduleDir().toAbsolutePath().normalize();
        for (Entry entry : read(classNode.moduleDir())) {
            TestNode method = workspace.findMethod(module + "|M|" + entry.className() + "#" + entry.methodName());
            if (method != null && method.ancestor(TestKind.SUITE) == null) {
                scope.add(method);
            }
        }
        for (TestNode child : classNode.children()) {
            if (child.kind() == TestKind.METHOD && !child.synthetic()) {
                scope.add(child);
            }
        }
        return List.copyOf(scope);
    }

    public static List<TestNode> targets(Workspace workspace, TestNode classNode) {
        List<TestNode> targets = new ArrayList<>();
        for (TestNode method : scope(workspace, classNode)) {
            if (method.ancestor(TestKind.CLASS) != classNode) {
                targets.add(method);
            }
        }
        return targets;
    }

    public static String description(TestNode node) {
        TestNode classNode = node != null && node.kind() == TestKind.CLASS ? node : node == null ? null : node.ancestor(TestKind.CLASS);
        if (!isRunner(classNode)) {
            return "";
        }
        if (node.kind() == TestKind.METHOD && !"orderedTests".equals(node.methodName())) {
            return "";
        }
        StringBuilder text = new StringBuilder("order.csv の順に実行します。");
        int index = 1;
        for (Entry entry : read(classNode.moduleDir())) {
            text.append('\n').append(index++).append(". ").append(entry.className()).append('#').append(entry.methodName());
        }
        return text.toString();
    }

    public static List<ReportedTest> retarget(List<ReportedTest> reports) {
        if (reports == null || reports.isEmpty()) {
            return reports == null ? List.of() : reports;
        }
        Map<String, List<Entry>> orders = new LinkedHashMap<>();
        Map<String, List<ReportedTest>> indexed = new LinkedHashMap<>();
        List<ReportedTest> rewritten = new ArrayList<>();
        for (ReportedTest report : reports) {
            Matcher matcher = INDEXED.matcher(report.name() == null ? "" : report.name());
            if (!matcher.matches() || report.moduleDir() == null || report.className() == null) {
                rewritten.add(report);
                continue;
            }
            String cacheKey = report.moduleDir().toAbsolutePath().normalize() + "\n" + report.className();
            List<Entry> entries = orders.computeIfAbsent(cacheKey, key -> read(report.moduleDir()));
            int index = Integer.parseInt(matcher.group(1));
            if (entries.isEmpty() || index < 1 || index > entries.size()) {
                rewritten.add(report);
                continue;
            }
            Entry entry = entries.get(index - 1);
            rewritten.add(new ReportedTest(
                    report.moduleDir(),
                    entry.className(),
                    entry.methodName(),
                    report.status(),
                    report.durationMillis(),
                    report.message(),
                    report.type(),
                    report.trace(),
                    report.systemOut()
            ));
            indexed.computeIfAbsent(cacheKey, key -> new ArrayList<>()).add(report);
        }
        for (List<ReportedTest> group : indexed.values()) {
            rewritten.add(summary(group));
        }
        return rewritten;
    }

    private static ReportedTest summary(List<ReportedTest> group) {
        ReportedTest first = group.get(0);
        boolean failed = false;
        boolean allSkipped = true;
        long duration = 0;
        boolean complete = true;
        for (ReportedTest report : group) {
            if (report.status() == TestStatus.FAILED) {
                failed = true;
            }
            if (report.status() != TestStatus.SKIPPED) {
                allSkipped = false;
            }
            if (report.durationMillis() >= 0) {
                duration += report.durationMillis();
            } else {
                complete = false;
            }
        }
        TestStatus status = failed ? TestStatus.FAILED : allSkipped ? TestStatus.SKIPPED : TestStatus.PASSED;
        return new ReportedTest(
                first.moduleDir(),
                first.className(),
                "orderedTests",
                status,
                complete ? duration : -1,
                null,
                null,
                null,
                null
        );
    }
}
