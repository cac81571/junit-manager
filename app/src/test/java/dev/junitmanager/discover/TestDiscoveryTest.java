package dev.junitmanager.discover;

import dev.junitmanager.model.Framework;
import dev.junitmanager.model.TestKind;
import dev.junitmanager.model.TestNode;
import dev.junitmanager.model.TestStatus;
import dev.junitmanager.model.Workspace;
import dev.junitmanager.run.ExecutionOrder;
import dev.junitmanager.run.ReportedTest;
import dev.junitmanager.run.ResultApplier;
import dev.junitmanager.run.RunPlans;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDiscoveryTest {
    @TempDir
    Path temp;

    @Test
    void discoversJUnit4AndJUnit5AndSkipsTestNg() throws Exception {
        writePom(temp, "demo", null);
        write(temp.resolve("src/test/java/com/example/SampleTest.java"), sample());
        write(temp.resolve("src/test/java/com/example/LegacyTest.java"), """
                package com.example;
                import org.junit.Ignore;
                import org.junit.Test;
                import org.junit.experimental.theories.Theory;
                public class LegacyTest {
                    @Test
                    public void adds() {}
                    @Test
                    @Ignore("later")
                    public void later() {}
                    @Theory
                    public void theory(int x) {}
                }
                """);
        write(temp.resolve("src/test/java/com/example/NgTest.java"), """
                package com.example;
                import org.testng.annotations.Test;
                public class NgTest {
                    @Test
                    public void notJUnit() {}
                }
                """);
        write(temp.resolve("src/test/java/NoPackageTest.java"), """
                import org.junit.jupiter.api.Test;
                class NoPackageTest {
                    @Test
                    void bare() {}
                }
                """);

        Workspace workspace = TestDiscovery.discover(temp);
        assertEquals(5 + 3 + 1, workspace.methodCount(), dump(workspace.tree()));
        TestNode params = method(workspace, "params");
        assertEquals("params", params.displayName());
        assertEquals(Framework.JUNIT5, params.framework());
        TestNode rec = method(workspace, "rec");
        assertTrue(rec.fqcn().endsWith("SampleTest$Inner"), rec.fqcn());
        TestNode later = method(workspace, "later");
        assertTrue(later.disabled());
        assertEquals("later", later.ignoreReason());
        assertEquals(Framework.JUNIT4, later.framework());
        assertTrue(workspace.realMethods().stream().noneMatch(node -> node.methodName().equals("notJUnit")));
        assertTrue(workspace.realMethods().stream().noneMatch(node -> node.methodName().equals("commented")));
        assertTrue(workspace.realMethods().stream().noneMatch(node -> node.methodName().equals("fake")));
        TestNode bare = method(workspace, "bare");
        assertEquals("(デフォルトパッケージ)", bare.ancestor(TestKind.PACKAGE).name());

        RunPlans.Plan plan = RunPlans.build(workspace, RunPlans.Spec.node(params), false);
        assertTrue(plan.mavenArgs().contains("-Dtest=" + params.fqcn() + "#params"), plan.mavenArgs().toString());
    }

    @Test
    void readsMultiModuleAndIgnoresParentArtifact() throws Exception {
        write(temp.resolve("pom.xml"), """
                <project>
                  <parent>
                    <groupId>org.example</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.3.0</version>
                  </parent>
                  <artifactId>demo</artifactId>
                  <dependencies>
                    <dependency>
                      <artifactId>junit-jupiter</artifactId>
                    </dependency>
                  </dependencies>
                  <modules>
                    <module>a</module>
                    <module>missing</module>
                  </modules>
                </project>
                """);
        write(temp.resolve("a/pom.xml"), """
                <project>
                  <artifactId>module-a</artifactId>
                </project>
                """);
        write(temp.resolve("a/src/test/java/a/ATest.java"), """
                package a;
                import org.junit.jupiter.api.Test;
                class ATest {
                    @Test void alpha() {}
                }
                """);
        write(temp.resolve("b-not-a-module/src/test/java/b/BTest.java"), """
                package b;
                import org.junit.jupiter.api.Test;
                class BTest { @Test void beta() {} }
                """);

        List<String> warnings = new ArrayList<>();
        MavenLayout.Project project = MavenLayout.read(temp, warnings);
        assertEquals("demo", project.module().artifactId());
        assertEquals(1, project.module().children().size());
        assertEquals("module-a", project.module().children().get(0).artifactId());
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("missing")));

        Workspace workspace = TestDiscovery.discover(temp);
        assertTrue(workspace.multiModule());
        assertEquals(1, workspace.methodCount(), dump(workspace.tree()));
        TestNode alpha = method(workspace, "alpha");
        assertEquals("module-a", alpha.ancestor(TestKind.MODULE).displayName());
    }

    @Test
    void discoversExampleProject() throws Exception {
        Path example = Path.of("examples/calculator").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(example.resolve("pom.xml")));
        Workspace workspace = TestDiscovery.discover(example);
        assertEquals(6, workspace.methodCount(), dump(workspace.tree()));
        TestNode adds = method(workspace, "adds");
        assertEquals("足し算", adds.displayName());
        TestNode divides = method(workspace, "divides");
        assertTrue(divides.fqcn().endsWith("CalculatorTest$Div"));
        assertTrue(method(workspace, "disabledOne").disabled());
        assertEquals("保留", method(workspace, "disabledOne").ignoreReason());
    }

    @Test
    void orderedSuiteRunsCsvTargetsFromTheGui() throws Exception {
        Path example = Path.of("examples/calculator").toAbsolutePath().normalize();
        Workspace workspace = TestDiscovery.discover(example);
        TestNode runner = method(workspace, "orderedTests");
        TestNode type = runner.ancestor(TestKind.CLASS);
        RunPlans.Plan fromMethod = RunPlans.build(workspace, RunPlans.Spec.node(runner), false);
        RunPlans.Plan fromClass = RunPlans.build(workspace, RunPlans.Spec.node(type), false);
        String selector = "-Dtest=dev.junitmanager.suite.OrderedSuiteRunner";
        assertEquals(selector, fromMethod.mavenArgs().stream().filter(arg -> arg.startsWith("-Dtest=")).findFirst().orElseThrow());
        assertEquals(selector, fromClass.mavenArgs().stream().filter(arg -> arg.startsWith("-Dtest=")).findFirst().orElseThrow());
        assertEquals(example, fromMethod.workingDirectory());
        assertEquals(
                List.of("adds", "divides", "multiplies", "disabledOne", "subtracts", "orderedTests"),
                fromMethod.scope().stream().map(TestNode::methodName).toList()
        );
        assertEquals(
                List.of("adds", "divides", "multiplies", "disabledOne", "subtracts"),
                ExecutionOrder.targets(workspace, type).stream().map(TestNode::methodName).toList()
        );

        ResultApplier.markRunning(fromMethod.scope(), fromMethod.clearSynthetics(), fromMethod.suite(), type);
        assertEquals(TestStatus.RUNNING, method(workspace, "adds").status());
        assertEquals(TestStatus.RUNNING, method(workspace, "orderedTests").status());
        assertEquals(TestStatus.NOT_RUN, method(workspace, "adds").ancestor(TestKind.PACKAGE).status());

        ResultApplier.apply(workspace, fromMethod.scope(), List.of(
                report(example, "orderedTests()[1]", TestStatus.PASSED, 4),
                report(example, "orderedTests()[2]", TestStatus.PASSED, 1),
                report(example, "orderedTests()[3]", TestStatus.PASSED, 2),
                report(example, "orderedTests()[4]", TestStatus.SKIPPED, 0),
                report(example, "orderedTests()[5]", TestStatus.PASSED, 3)
        ), null);
        assertEquals(TestStatus.PASSED, method(workspace, "adds").status());
        assertEquals(TestStatus.PASSED, method(workspace, "divides").status());
        assertEquals(TestStatus.PASSED, method(workspace, "multiplies").status());
        assertEquals(TestStatus.SKIPPED, method(workspace, "disabledOne").status());
        assertEquals(TestStatus.PASSED, method(workspace, "subtracts").status());
        assertEquals(TestStatus.PASSED, method(workspace, "orderedTests").status());
        assertEquals(10, method(workspace, "orderedTests").durationMillis());
    }

    private static ReportedTest report(Path module, String name, TestStatus status, long duration) {
        return new ReportedTest(
                module,
                "dev.junitmanager.suite.OrderedSuiteRunner",
                name,
                status,
                duration,
                status == TestStatus.SKIPPED ? "保留" : null,
                null,
                null,
                null
        );
    }

    @Test
    void listsJUnit4AndJUnit5Suites() throws Exception {
        writePom(temp, "demo", null);
        write(temp.resolve("src/test/java/com/example/ATest.java"), """
                package com.example;
                import org.junit.jupiter.api.Test;
                class ATest {
                    @Test void alpha() {}
                }
                """);
        write(temp.resolve("src/test/java/com/example/AllLegacy.java"), """
                package com.example;
                import org.junit.runner.RunWith;
                import org.junit.runners.Suite;
                @RunWith(Suite.class)
                @Suite.SuiteClasses(ATest.class)
                public class AllLegacy {}
                """);
        write(temp.resolve("src/test/java/com/example/AllJupiter.java"), """
                package com.example;
                import org.junit.platform.suite.api.SelectClasses;
                import org.junit.platform.suite.api.SelectPackages;
                import org.junit.platform.suite.api.Suite;
                @Suite
                @SelectClasses(ATest.class)
                @SelectPackages("com.example")
                class AllJupiter {}
                """);

        Workspace workspace = TestDiscovery.discover(temp);
        assertEquals(1, workspace.methodCount(), dump(workspace.tree()));
        TestNode legacy = suite(workspace, "AllLegacy");
        assertEquals("com.example.AllLegacy", legacy.fqcn());
        assertEquals(List.of("ATest"), legacy.children().stream().map(TestNode::name).toList());
        assertEquals(1, legacy.methodCount());
        TestNode jupiter = suite(workspace, "AllJupiter");
        assertEquals(List.of("ATest"), jupiter.children().stream().map(TestNode::name).toList());
        RunPlans.Plan plan = RunPlans.build(workspace, RunPlans.Spec.node(legacy), false);
        assertTrue(plan.mavenArgs().stream().anyMatch(arg -> arg.equals("-Dtest=com.example.AllLegacy")), plan.mavenArgs().toString());
    }

    @Test
    void listsSelectMethodWithoutTheRestOfTheClass() throws Exception {
        writePom(temp, "demo", null);
        write(temp.resolve("src/test/java/com/example/ATest.java"), """
                package com.example;
                import org.junit.jupiter.api.Nested;
                import org.junit.jupiter.api.Test;
                class ATest {
                    @Test void alpha() {}
                    @Test void beta() {}
                    @Nested class Inner {
                        @Test void inner() {}
                    }
                }
                """);
        write(temp.resolve("src/test/java/com/example/Picked.java"), """
                package com.example;
                import org.junit.platform.suite.api.SelectMethod;
                import org.junit.platform.suite.api.SelectMethods;
                import org.junit.platform.suite.api.Suite;
                @Suite
                @SelectMethod(type = ATest.class, name = "beta")
                @SelectMethod("com.example.ATest#alpha()")
                @SelectMethod(type = ATest.Inner.class, name = "inner")
                @SelectMethods({
                    @SelectMethod(typeName = "com.example.ATest", name = "beta"),
                    @SelectMethod("com.example.ATest$Inner#inner")
                })
                class Picked {}
                """);

        Workspace workspace = TestDiscovery.discover(temp);
        assertEquals(3, workspace.methodCount(), dump(workspace.tree()));
        TestNode suite = suite(workspace, "Picked");
        assertEquals(List.of("ATest", "Inner"), suite.children().stream().map(TestNode::name).toList());
        TestNode aTest = suite.children().get(0);
        assertEquals(List.of("beta", "alpha"), aTest.children().stream().map(TestNode::methodName).toList());
        TestNode inner = suite.children().get(1);
        assertEquals("com.example.ATest$Inner", inner.fqcn());
        assertEquals(List.of("inner"), inner.children().stream().map(TestNode::methodName).toList());
        assertEquals(3, suite.methodCount());
        RunPlans.Plan partial = RunPlans.build(workspace, RunPlans.Spec.node(aTest), false);
        assertTrue(partial.mavenArgs().stream().anyMatch(arg -> arg.equals("-Dtest=com.example.ATest#beta+alpha")), partial.mavenArgs().toString());
        RunPlans.Plan whole = RunPlans.build(workspace, RunPlans.Spec.node(workspace.tree().children().get(0).children().stream()
                .filter(node -> node.kind() == TestKind.CLASS && "ATest".equals(node.name()))
                .findFirst()
                .orElseThrow()), false);
        assertTrue(whole.mavenArgs().stream().anyMatch(arg -> arg.equals("-Dtest=com.example.ATest")), whole.mavenArgs().toString());
    }

    @Test
    void runningOneSuiteLeavesTheOtherSuiteUntouched() throws Exception {
        writePom(temp, "demo", null);
        write(temp.resolve("src/test/java/com/example/ATest.java"), """
                package com.example;
                import org.junit.jupiter.api.Test;
                class ATest {
                    @Test void alpha() {}
                    @Test void beta() {}
                }
                """);
        write(temp.resolve("src/test/java/com/example/Picked.java"), """
                package com.example;
                import org.junit.platform.suite.api.SelectMethod;
                import org.junit.platform.suite.api.Suite;
                @Suite
                @SelectMethod(type = ATest.class, name = "alpha")
                class Picked {}
                """);
        write(temp.resolve("src/test/java/com/example/Other.java"), """
                package com.example;
                import org.junit.platform.suite.api.SelectClasses;
                import org.junit.platform.suite.api.Suite;
                @Suite
                @SelectClasses(ATest.class)
                class Other {}
                """);

        Workspace workspace = TestDiscovery.discover(temp);
        TestNode picked = suite(workspace, "Picked");
        TestNode other = suite(workspace, "Other");
        RunPlans.Plan plan = RunPlans.build(workspace, RunPlans.Spec.node(picked), false);
        assertEquals(picked, plan.suite());
        TestNode alpha = workspace.realMethods().stream().filter(node -> "alpha".equals(node.methodName())).findFirst().orElseThrow();
        ResultApplier.apply(workspace, plan.scope(), List.of(new ReportedTest(
                alpha.moduleDir(),
                "com.example.ATest",
                "alpha",
                TestStatus.FAILED,
                12,
                "fail",
                "AssertionError",
                "trace",
                ""
        )), plan.suite());

        assertEquals(TestStatus.FAILED, picked.status());
        assertEquals(TestStatus.NOT_RUN, other.status());
        assertEquals(TestStatus.NOT_RUN, other.children().get(0).children().stream()
                .filter(node -> "alpha".equals(node.methodName()))
                .findFirst()
                .orElseThrow()
                .status());
        assertEquals(TestStatus.FAILED, workspace.tree().statusAmongSuites());
    }

    @Test
    void clearResultsReturnsMethodsSuitesAndSyntheticsToNotRun() throws Exception {
        writePom(temp, "demo", null);
        write(temp.resolve("src/test/java/com/example/ATest.java"), """
                package com.example;
                import org.junit.jupiter.api.Disabled;
                import org.junit.jupiter.api.Test;
                class ATest {
                    @Test void alpha() {}
                    @Test @Disabled void held() {}
                }
                """);
        write(temp.resolve("src/test/java/com/example/Picked.java"), """
                package com.example;
                import org.junit.platform.suite.api.SelectClasses;
                import org.junit.platform.suite.api.Suite;
                @Suite
                @SelectClasses(ATest.class)
                class Picked {}
                """);

        Workspace workspace = TestDiscovery.discover(temp);
        TestNode alpha = method(workspace, "alpha");
        TestNode held = method(workspace, "held");
        TestNode type = alpha.ancestor(TestKind.CLASS);
        RunPlans.Plan plan = RunPlans.build(workspace, RunPlans.Spec.node(type), false);
        ResultApplier.apply(workspace, plan.scope(), List.of(
                new ReportedTest(alpha.moduleDir(), "com.example.ATest", "alpha", TestStatus.FAILED, 12, "fail", "AssertionError", "trace", ""),
                new ReportedTest(alpha.moduleDir(), "com.example.ATest", "extra", TestStatus.FAILED, 4, "fail", "AssertionError", "trace", "")
        ), plan.suite());
        assertEquals(TestStatus.FAILED, alpha.status());
        assertTrue(type.children().stream().anyMatch(TestNode::synthetic));
        assertEquals(TestStatus.FAILED, suite(workspace, "Picked").status());

        workspace.clearResults();

        assertEquals(TestStatus.NOT_RUN, alpha.status());
        assertEquals(-1, alpha.durationMillis());
        assertEquals(List.of(), alpha.cases());
        assertEquals(TestStatus.SKIPPED, held.status());
        assertEquals(List.of(), held.cases());
        assertFalse(type.children().stream().anyMatch(TestNode::synthetic));
        assertEquals(TestStatus.NOT_RUN, type.status());
        assertEquals(TestStatus.NOT_RUN, suite(workspace, "Picked").status());
        TestNode suiteAlpha = suite(workspace, "Picked").children().stream()
                .filter(node -> node.kind() == TestKind.CLASS)
                .findFirst()
                .orElseThrow()
                .children()
                .stream()
                .filter(node -> "alpha".equals(node.methodName()))
                .findFirst()
                .orElseThrow();
        assertEquals(TestStatus.NOT_RUN, suiteAlpha.status());
        assertEquals(-1, suiteAlpha.durationMillis());
        assertEquals(TestStatus.NOT_RUN, workspace.tree().status());
    }

    @Test
    void runningAClassDoesNotMarkPackageOrProjectRunning() throws Exception {
        writePom(temp, "demo", null);
        write(temp.resolve("src/test/java/com/example/ATest.java"), """
                package com.example;
                import org.junit.jupiter.api.Test;
                class ATest {
                    @Test void alpha() {}
                    @Test void beta() {}
                }
                """);
        Workspace workspace = TestDiscovery.discover(temp);
        TestNode type = workspace.tree().children().get(0).children().get(0);
        assertEquals("ATest", type.name());
        RunPlans.Plan plan = RunPlans.build(workspace, RunPlans.Spec.node(type), false);
        ResultApplier.markRunning(plan.scope(), plan.clearSynthetics(), plan.suite(), type);
        assertEquals(TestStatus.RUNNING, type.status());
        assertEquals(TestStatus.RUNNING, method(workspace, "alpha").status());
        assertEquals(TestStatus.RUNNING, method(workspace, "beta").status());
        assertEquals(TestStatus.NOT_RUN, type.parent().status());
        assertEquals(TestStatus.NOT_RUN, workspace.tree().status());
    }

    @Test
    void runningAClassMarksEveryMethodRunning() throws Exception {
        Workspace workspace = TestDiscovery.discover(Path.of("examples/calculator"));
        TestNode type = null;
        for (TestNode method : workspace.realMethods()) {
            if ("subtracts".equals(method.methodName()) && method.ancestor(TestKind.SUITE) == null) {
                type = method.ancestor(TestKind.CLASS);
            }
        }
        assertEquals("CalculatorTest", type.name());
        RunPlans.Plan plan = RunPlans.build(workspace, RunPlans.Spec.node(type), false);
        ResultApplier.markRunning(plan.scope(), plan.clearSynthetics(), plan.suite(), type);
        List<String> idle = new ArrayList<>();
        type.walk(node -> {
            if (node.kind() == TestKind.METHOD && node.status() != TestStatus.RUNNING) {
                idle.add(node.methodName());
            }
        });
        assertEquals(List.of(), idle);
        assertEquals(TestStatus.NOT_RUN, type.parent().status());
        assertEquals(TestStatus.NOT_RUN, workspace.tree().status());
    }

    @Test
    void runningASuiteMarksThatSuiteRunning() throws Exception {
        Workspace workspace = TestDiscovery.discover(Path.of("examples/calculator"));
        TestNode suite = suite(workspace, "CalculatorMethods");
        RunPlans.Plan plan = RunPlans.build(workspace, RunPlans.Spec.node(suite), false);
        ResultApplier.markRunning(plan.scope(), plan.clearSynthetics(), plan.suite(), suite);
        assertEquals(TestStatus.RUNNING, suite.status(), suite.children().stream().map(child -> child.name() + ":" + child.status()).toList().toString());
        List<String> idle = new ArrayList<>();
        suite.walk(node -> {
            if (node.kind() == TestKind.METHOD && node.status() != TestStatus.RUNNING) {
                idle.add(node.methodName());
            }
        });
        assertEquals(List.of(), idle);
        assertEquals(TestStatus.NOT_RUN, suite(workspace, "CalculatorSuite").status());
    }

    @Test
    void listsMethodsAndNestedClassesInOrder() throws Exception {
        writePom(temp, "demo", null);
        write(temp.resolve("src/test/java/com/example/OrderedTest.java"), """
                package com.example;
                import org.junit.jupiter.api.Nested;
                import org.junit.jupiter.api.Order;
                import org.junit.jupiter.api.Test;
                class OrderedTest {
                    @Test @Order(3) void third() {}
                    @Test void plain() {}
                    @Test @Order(1) void first() {}
                    @Test @Order(value = 1) void alsoFirst() {}
                    @Nested @Order(2) class Second {
                        @Test void nested() {}
                    }
                    @Test @Order(-1) void veryFirst() {}
                }
                """);

        Workspace workspace = TestDiscovery.discover(temp);
        TestNode type = workspace.tree().children().get(0).children().get(0);
        assertEquals("OrderedTest", type.name());
        assertEquals(
                List.of("veryFirst", "first", "alsoFirst", "Second", "third", "plain"),
                type.children().stream().map(TestNode::name).toList()
        );
        assertTrue(type.children().get(0).hasOrder());
        assertEquals(-1, type.children().get(0).order());
        assertEquals(2, type.children().get(3).order());
        assertFalse(type.children().get(5).hasOrder());
    }

    private static TestNode suite(Workspace workspace, String name) {
        TestNode[] found = new TestNode[1];
        workspace.tree().walk(node -> {
            if (found[0] == null && node.kind() == TestKind.SUITE && name.equals(node.name())) {
                found[0] = node;
            }
        });
        return found[0];
    }

    private static TestNode method(Workspace workspace, String name) {
        return workspace.realMethods().stream()
                .filter(node -> node.methodName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing " + name + "\n" + dump(workspace.tree())));
    }

    private static void writePom(Path root, String artifactId, String modules) throws Exception {
        String body = modules == null ? "" : "<modules>" + modules + "</modules>";
        write(root.resolve("pom.xml"), "<project><artifactId>" + artifactId + "</artifactId>" + body + "</project>");
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static String sample() {
        return """
                package com.example;

                import org.junit.jupiter.api.*;
                import org.junit.jupiter.params.ParameterizedTest;
                import org.junit.jupiter.params.provider.CsvSource;

                @DisplayName("top")
                class SampleTest {
                    // @Test void commented() {}

                    @Test
                    void blockCommentDoesNotCount() {
                        /*
                        @Test void inner() {}
                        */
                        String s = \"\"\"
                            { not a brace
                            @Test void fake() {}
                            \"\"\";
                    }

                    @ParameterizedTest
                    @DisplayName("params")
                    void params(int a) {}

                    @ParameterizedTest
                    @CsvSource({
                        "a, b",
                        "c, d"
                    })
                    void csv(String a, String b) {}

                    record Inner(int v) {
                        @Test
                        void rec() {}
                    }
                }

                enum ModeTest {
                    A, B;

                    @Test
                    void hasValues() {}
                }
                """;
    }

    private static String dump(TestNode node) {
        return dump(node, 0);
    }

    private static String dump(TestNode node, int indent) {
        StringBuilder builder = new StringBuilder();
        builder.append(" ".repeat(indent))
                .append(node.kind())
                .append(' ')
                .append(node.displayName());
        if (node.fqcn() != null) {
            builder.append(" [").append(node.fqcn()).append(']');
        }
        if (node.methodName() != null) {
            builder.append('#').append(node.methodName());
        }
        builder.append(" disabled=").append(node.disabled());
        builder.append('\n');
        for (TestNode child : node.children()) {
            builder.append(dump(child, indent + 2));
        }
        return builder.toString();
    }
}
