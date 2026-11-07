package dev.junitmanager.discover;

import dev.junitmanager.model.Framework;
import dev.junitmanager.model.TestKind;
import dev.junitmanager.model.TestNode;
import dev.junitmanager.model.Workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TestDiscovery {
    private TestDiscovery() {
    }

    public static Workspace discover(Path root) throws IOException {
        List<String> warnings = new ArrayList<>();
        Path input = root.toAbsolutePath().normalize();
        if (Files.isRegularFile(input) && "pom.xml".equalsIgnoreCase(fileName(input))) {
            input = input.getParent();
        }
        if (input == null || !Files.isRegularFile(input.resolve("pom.xml"))) {
            throw new IOException("pom.xml がありません: " + root);
        }
        MavenLayout.Project project = MavenLayout.read(input, warnings);
        Map<Path, List<ScannedFile>> tests = new LinkedHashMap<>();
        List<Path> sourceRoots = new ArrayList<>();
        for (MavenLayout.Module module : MavenLayout.flatten(project.module())) {
            tests.put(module.dir(), scan(module, warnings));
            addRoot(sourceRoots, module.testSources());
            addRoot(sourceRoots, module.mainSources());
        }
        TestNode tree = TestNode.root(project.name());
        if (project.multiModule()) {
            if (hasOwnTests(tests.get(project.module().dir()))) {
                TestNode own = moduleNode(project.module());
                tree.addChild(own);
                addPackages(own, project.module(), tests.get(project.module().dir()));
            }
            for (MavenLayout.Module child : project.module().children()) {
                addModule(tree, child, tests);
            }
        } else {
            addPackages(tree, project.module(), tests.get(project.module().dir()));
        }
        sortContainers(tree);
        tree.rollup();
        return new Workspace(project.module().dir(), project.name(), tree, project.multiModule(), sourceRoots, warnings);
    }

    private static void addModule(TestNode parent, MavenLayout.Module module, Map<Path, List<ScannedFile>> tests) {
        if (!subtreeHasTests(module, tests)) {
            return;
        }
        TestNode node = moduleNode(module);
        parent.addChild(node);
        List<ScannedFile> own = tests.get(module.dir());
        if (hasOwnTests(own)) {
            addPackages(node, module, own);
        }
        for (MavenLayout.Module child : module.children()) {
            addModule(node, child, tests);
        }
    }

    private static boolean subtreeHasTests(MavenLayout.Module module, Map<Path, List<ScannedFile>> tests) {
        if (hasOwnTests(tests.get(module.dir()))) {
            return true;
        }
        for (MavenLayout.Module child : module.children()) {
            if (subtreeHasTests(child, tests)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOwnTests(List<ScannedFile> files) {
        if (files == null) {
            return false;
        }
        for (ScannedFile file : files) {
            for (JavaTests.TypeUnit type : file.unit.types) {
                if (JavaTests.hasTests(type) || JavaTests.isSuite(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static TestNode moduleNode(MavenLayout.Module module) {
        return new TestNode(
                TestKind.MODULE,
                module.artifactId(),
                module.artifactId(),
                null,
                null,
                module.dir(),
                null,
                0,
                false,
                null,
                Framework.UNKNOWN,
                false
        );
    }

    private static void addPackages(TestNode parent, MavenLayout.Module module, List<ScannedFile> files) {
        if (files == null) {
            return;
        }
        Map<String, TestNode> packages = new LinkedHashMap<>();
        for (ScannedFile file : files) {
            String packageName = file.unit.packageName == null ? "" : file.unit.packageName;
            String label = packageName.isEmpty() ? "(デフォルトパッケージ)" : packageName;
            TestNode packageNode = packages.computeIfAbsent(label, name -> {
                TestNode node = new TestNode(
                        TestKind.PACKAGE,
                        name,
                        name,
                        packageName,
                        null,
                        module.dir(),
                        null,
                        0,
                        false,
                        null,
                        Framework.UNKNOWN,
                        false
                );
                parent.addChild(node);
                return node;
            });
            for (JavaTests.TypeUnit type : file.unit.types) {
                addType(packageNode, type, packageName, null, module.dir(), file.path, false, null);
            }
        }
        attachSuites(parent, module, files);
    }

    private static void addType(
            TestNode parent,
            JavaTests.TypeUnit type,
            String packageName,
            String parentFqcn,
            Path moduleDir,
            Path source,
            boolean parentDisabled,
            String parentReason
    ) {
        if (!JavaTests.hasTests(type)) {
            return;
        }
        String fqcn = parentFqcn == null
                ? (packageName.isEmpty() ? type.name : packageName + "." + type.name)
                : parentFqcn + "$" + type.name;
        boolean disabled = parentDisabled || type.disabled;
        String reason = type.ignoreReason != null && !type.ignoreReason.isBlank() ? type.ignoreReason : parentReason;
        TestNode classNode = new TestNode(
                TestKind.CLASS,
                type.name,
                type.displayName,
                fqcn,
                null,
                moduleDir,
                source,
                type.line,
                disabled,
                reason,
                firstFramework(type),
                false
        );
        if (type.order != null) {
            classNode.setOrder(type.order);
        }
        parent.addChild(classNode);
        for (JavaTests.MethodUnit method : type.methods) {
            boolean methodDisabled = disabled || method.disabled;
            String methodReason = method.ignoreReason != null && !method.ignoreReason.isBlank()
                    ? method.ignoreReason
                    : (methodDisabled ? reason : null);
            TestNode methodNode = new TestNode(
                    TestKind.METHOD,
                    method.name,
                    method.displayName,
                    fqcn,
                    method.name,
                    moduleDir,
                    source,
                    method.line,
                    methodDisabled,
                    methodReason,
                    method.framework,
                    false
            );
            if (method.order != null) {
                methodNode.setOrder(method.order);
            }
            methodNode.setFactory(method.factory);
            classNode.addChild(methodNode);
        }
        for (JavaTests.TypeUnit nested : type.nested) {
            addType(classNode, nested, packageName, fqcn, moduleDir, source, disabled, reason);
        }
        classNode.sortChildren(Comparator
                .comparingInt(TestNode::order)
                .thenComparingInt(TestNode::line)
                .thenComparing(TestNode::displayName, String.CASE_INSENSITIVE_ORDER));
    }

    private static void attachSuites(TestNode parent, MavenLayout.Module module, List<ScannedFile> files) {
        Map<String, TestNode> byFqcn = new LinkedHashMap<>();
        Map<String, List<TestNode>> bySimple = new LinkedHashMap<>();
        parent.walk(node -> {
            if (node.kind() != TestKind.CLASS || node.fqcn() == null || node.ancestor(TestKind.SUITE) != null) {
                return;
            }
            byFqcn.put(node.fqcn(), node);
            String simple = node.fqcn().substring(node.fqcn().lastIndexOf('.') + 1);
            bySimple.computeIfAbsent(simple, key -> new ArrayList<>()).add(node);
        });
        for (ScannedFile file : files) {
            String packageName = file.unit.packageName == null ? "" : file.unit.packageName;
            for (JavaTests.TypeUnit type : file.unit.types) {
                if (!JavaTests.isSuite(type)) {
                    continue;
                }
                String label = packageName.isEmpty() ? "(デフォルトパッケージ)" : packageName;
                TestNode packageNode = findPackage(parent, label);
                if (packageNode == null) {
                    continue;
                }
                String fqcn = packageName.isEmpty() ? type.name : packageName + "." + type.name;
                TestNode suite = new TestNode(
                        TestKind.SUITE,
                        type.name,
                        type.displayName,
                        fqcn,
                        null,
                        module.dir(),
                        file.path,
                        type.line,
                        type.disabled,
                        type.ignoreReason,
                        Framework.UNKNOWN,
                        false
                );
                LinkedHashMap<String, TestNode> members = new LinkedHashMap<>();
                for (String ref : type.suiteClasses) {
                    TestNode found = resolveClass(byFqcn, bySimple, packageName, ref);
                    if (found != null && !fqcn.equals(found.fqcn())) {
                        members.putIfAbsent(found.fqcn(), found);
                    } else if (found == null) {
                        String memberFqcn = ref.contains(".") ? ref : (packageName.isEmpty() ? ref : packageName + "." + ref);
                        members.putIfAbsent(memberFqcn, placeholder(memberFqcn, module.dir()));
                    }
                }
                for (String selected : type.suitePackages) {
                    for (TestNode node : byFqcn.values()) {
                        if (node.fqcn().contains("$") || fqcn.equals(node.fqcn())) {
                            continue;
                        }
                        if (inPackage(packageOf(node.fqcn()), selected)) {
                            members.putIfAbsent(node.fqcn(), node);
                        }
                    }
                }
                attachSelectedMethods(type, packageName, module, byFqcn, bySimple, members);
                for (TestNode member : members.values()) {
                    suite.addChild(member.kind() == TestKind.CLASS && member.parent() != null ? copyTree(member) : member);
                }
                packageNode.addChild(suite);
            }
        }
    }

    private static TestNode findPackage(TestNode parent, String label) {
        for (TestNode child : parent.children()) {
            if (child.kind() == TestKind.PACKAGE && label.equals(child.name())) {
                return child;
            }
            if (child.kind() == TestKind.MODULE || child.kind() == TestKind.ROOT) {
                TestNode nested = findPackage(child, label);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static void attachSelectedMethods(
            JavaTests.TypeUnit type,
            String packageName,
            MavenLayout.Module module,
            Map<String, TestNode> byFqcn,
            Map<String, List<TestNode>> bySimple,
            Map<String, TestNode> members
    ) {
        Map<String, TestNode> partials = new LinkedHashMap<>();
        for (JavaTests.SuiteMethod ref : type.suiteMethods) {
            TestNode found = resolveClass(byFqcn, bySimple, packageName, ref.typeName);
            String memberFqcn = found != null ? found.fqcn() : fallbackFqcn(packageName, ref.typeName);
            if (members.containsKey(memberFqcn)) {
                continue;
            }
            TestNode shell = partials.get(memberFqcn);
            if (shell == null) {
                shell = found != null ? shallowClass(found) : placeholder(memberFqcn, module.dir());
                partials.put(memberFqcn, shell);
            }
            if (directMethod(shell, ref.methodName) != null) {
                continue;
            }
            TestNode method = found == null ? null : directMethod(found, ref.methodName);
            shell.addChild(method != null ? copyTree(method) : placeholderMethod(shell, ref.methodName));
        }
        members.putAll(partials);
    }

    private static TestNode resolveClass(
            Map<String, TestNode> byFqcn,
            Map<String, List<TestNode>> bySimple,
            String packageName,
            String ref
    ) {
        if (ref.contains(".") || ref.contains("$")) {
            TestNode found = lookupFlexible(byFqcn, ref);
            if (found == null && !packageName.isEmpty() && Character.isUpperCase(ref.charAt(0))) {
                found = lookupFlexible(byFqcn, packageName + "." + ref);
            }
            return found;
        }
        String fqcn = packageName.isEmpty() ? ref : packageName + "." + ref;
        TestNode found = byFqcn.get(fqcn);
        if (found != null) {
            return found;
        }
        List<TestNode> candidates = bySimple.get(ref);
        if (candidates != null && candidates.size() == 1) {
            return candidates.get(0);
        }
        return null;
    }

    private static TestNode lookupFlexible(Map<String, TestNode> byFqcn, String name) {
        TestNode found = byFqcn.get(name);
        if (found != null) {
            return found;
        }
        String candidate = name;
        int dot;
        while ((dot = candidate.lastIndexOf('.')) >= 0) {
            candidate = candidate.substring(0, dot) + "$" + candidate.substring(dot + 1);
            found = byFqcn.get(candidate);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String fallbackFqcn(String packageName, String ref) {
        boolean qualified = !ref.isEmpty() && Character.isLowerCase(ref.charAt(0)) && (ref.contains(".") || ref.contains("$"));
        if (qualified || packageName.isEmpty()) {
            return ref;
        }
        return packageName + "." + ref;
    }

    private static TestNode directMethod(TestNode classNode, String name) {
        for (TestNode child : classNode.children()) {
            if (child.kind() == TestKind.METHOD && name.equals(child.methodName())) {
                return child;
            }
        }
        return null;
    }

    private static TestNode shallowClass(TestNode node) {
        return new TestNode(
                TestKind.CLASS,
                node.name(),
                node.displayName(),
                node.fqcn(),
                null,
                node.moduleDir(),
                node.sourceFile(),
                node.line(),
                node.disabled(),
                node.ignoreReason(),
                node.framework(),
                false
        );
    }

    private static TestNode placeholderMethod(TestNode classNode, String name) {
        return new TestNode(
                TestKind.METHOD,
                name,
                name,
                classNode.fqcn(),
                name,
                classNode.moduleDir(),
                classNode.sourceFile(),
                0,
                false,
                null,
                classNode.framework(),
                false
        );
    }

    private static boolean inPackage(String classPackage, String selected) {
        return classPackage.equals(selected) || classPackage.startsWith(selected + ".");
    }

    private static String packageOf(String fqcn) {
        int dot = fqcn.lastIndexOf('.');
        return dot < 0 ? "" : fqcn.substring(0, dot);
    }

    private static TestNode placeholder(String fqcn, Path moduleDir) {
        String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        return new TestNode(TestKind.CLASS, simple, simple, fqcn, null, moduleDir, null, 0, false, null, Framework.UNKNOWN, false);
    }

    private static TestNode copyTree(TestNode node) {
        TestNode copy = new TestNode(
                node.kind(),
                node.name(),
                node.displayName(),
                node.fqcn(),
                node.methodName(),
                node.moduleDir(),
                node.sourceFile(),
                node.line(),
                node.disabled(),
                node.ignoreReason(),
                node.framework(),
                false
        );
        copy.setOrder(node.order());
        copy.setFactory(node.isFactory());
        for (TestNode child : node.children()) {
            if (!child.synthetic()) {
                copy.addChild(copyTree(child));
            }
        }
        return copy;
    }

    private static Framework firstFramework(JavaTests.TypeUnit type) {
        for (JavaTests.MethodUnit method : type.methods) {
            if (method.framework != Framework.UNKNOWN) {
                return method.framework;
            }
        }
        for (JavaTests.TypeUnit nested : type.nested) {
            Framework framework = firstFramework(nested);
            if (framework != Framework.UNKNOWN) {
                return framework;
            }
        }
        return Framework.UNKNOWN;
    }

    private static void sortContainers(TestNode node) {
        boolean onlyPackages = !node.children().isEmpty()
                && node.children().stream().allMatch(child -> child.kind() == TestKind.PACKAGE);
        if (onlyPackages) {
            node.sortChildren((left, right) -> {
                boolean leftDefault = "(デフォルトパッケージ)".equals(left.name());
                boolean rightDefault = "(デフォルトパッケージ)".equals(right.name());
                if (leftDefault != rightDefault) {
                    return leftDefault ? -1 : 1;
                }
                return left.displayName().compareToIgnoreCase(right.displayName());
            });
        }
        for (TestNode child : node.children()) {
            if (child.kind() == TestKind.PACKAGE) {
                child.sortChildren(Comparator.comparing(TestNode::displayName, String.CASE_INSENSITIVE_ORDER));
            } else if (child.kind() == TestKind.ROOT || child.kind() == TestKind.MODULE) {
                sortContainers(child);
            }
        }
    }

    private static List<ScannedFile> scan(MavenLayout.Module module, List<String> warnings) {
        List<ScannedFile> files = new ArrayList<>();
        Path root = module.testSources();
        if (!Files.isDirectory(root)) {
            return files;
        }
        try (var stream = Files.walk(root)) {
            List<Path> paths = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = fileName(path);
                        return name.endsWith(".java") && !name.equals("package-info.java") && !name.equals("module-info.java");
                    })
                    .sorted()
                    .toList();
            for (Path path : paths) {
                try {
                    if (Files.size(path) > 2_000_000) {
                        warnings.add("大きすぎるため読み飛ばしました: " + path.getFileName());
                        continue;
                    }
                    JavaTests.FileUnit unit = JavaTests.parse(TextFiles.read(path));
                    if (unit.types.stream().anyMatch(type -> JavaTests.hasTests(type) || JavaTests.isSuite(type))) {
                        files.add(new ScannedFile(path.toAbsolutePath().normalize(), unit));
                    }
                } catch (RuntimeException | IOException ex) {
                    warnings.add(path.getFileName() + ": " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            warnings.add(root + ": " + ex.getMessage());
        }
        return files;
    }

    private static void addRoot(List<Path> roots, Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return;
        }
        try {
            roots.add(path.toRealPath());
        } catch (IOException ex) {
            roots.add(path.toAbsolutePath().normalize());
        }
    }

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name == null ? "" : name.toString();
    }

    private record ScannedFile(Path path, JavaTests.FileUnit unit) {
    }
}
