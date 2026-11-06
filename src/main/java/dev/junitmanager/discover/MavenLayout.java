package dev.junitmanager.discover;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MavenLayout {
    private static final Pattern TAG = Pattern.compile("<([A-Za-z0-9_:-]+)\\s*>([^<]*)</\\1>", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STRIP = Set.of(
            "parent",
            "dependencies",
            "dependencymanagement",
            "build",
            "reporting",
            "repositories",
            "pluginrepositories"
    );

    private MavenLayout() {
    }

    public record Module(Path dir, String artifactId, Path testSources, Path mainSources, List<Module> children) {
    }

    public record Project(Path root, String name, Module module, boolean multiModule) {
    }

    public static Project read(Path root, List<String> warnings) throws IOException {
        Path dir = root.toAbsolutePath().normalize();
        Module module = readModule(dir, warnings, new java.util.HashSet<>(), 0);
        if (module == null) {
            throw new IOException("pom.xml を読めません: " + dir);
        }
        String name = dir.getFileName() == null ? dir.toString() : dir.getFileName().toString();
        return new Project(dir, name, module, count(module) > 1);
    }

    private static int count(Module module) {
        int total = 1;
        for (Module child : module.children) {
            total += count(child);
        }
        return total;
    }

    private static Module readModule(Path dir, List<String> warnings, java.util.Set<Path> seen, int depth) throws IOException {
        if (depth > 12) {
            return null;
        }
        if (!Files.isDirectory(dir)) {
            warnings.add("モジュールが見つかりません: " + dir);
            return null;
        }
        Path real;
        try {
            real = dir.toRealPath();
        } catch (IOException ex) {
            warnings.add("ディレクトリを開けません: " + dir);
            return null;
        }
        if (!seen.add(real)) {
            warnings.add("モジュールの循環参照を無視しました: " + dir);
            return null;
        }
        Path pom = dir.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            warnings.add("pom.xml がありません: " + dir);
            return null;
        }
        String xml = stripComments(TextFiles.read(pom));
        String stripped = XmlSnips.stripBlocks(xml, STRIP);
        String artifactId = first(stripped, "artifactId");
        if (artifactId == null || artifactId.isBlank()) {
            artifactId = dir.getFileName() == null ? "module" : dir.getFileName().toString();
        }
        Path testSources = sourceDir(dir, xml, "testSourceDirectory", "src/test/java");
        Path mainSources = sourceDir(dir, xml, "sourceDirectory", "src/main/java");
        List<Module> children = new ArrayList<>();
        Matcher modules = Pattern.compile("<module\\s*>([^<]+)</module>", Pattern.CASE_INSENSITIVE).matcher(stripped);
        while (modules.find()) {
            String relative = modules.group(1).trim();
            if (relative.isEmpty()) {
                continue;
            }
            Path childDir = dir.resolve(relative).normalize();
            Module child = readModule(childDir, warnings, seen, depth + 1);
            if (child != null) {
                children.add(child);
            }
        }
        return new Module(real, artifactId.trim(), testSources, mainSources, List.copyOf(children));
    }

    private static Path sourceDir(Path module, String xml, String tag, String fallback) {
        Path fallbackPath = module.resolve(fallback).normalize();
        String raw = first(xml, tag);
        if (raw == null || raw.isBlank()) {
            return fallbackPath;
        }
        String base = module.toString().replace('\\', '/');
        raw = raw.trim().replace("${project.basedir}", base).replace("${basedir}", base);
        if (raw.contains("${")) {
            return fallbackPath;
        }
        Path path = Path.of(raw);
        if (!path.isAbsolute()) {
            path = module.resolve(raw);
        }
        path = path.normalize();
        return Files.isDirectory(path) ? path : fallbackPath;
    }

    private static String first(String xml, String tag) {
        Matcher matcher = TAG.matcher(xml);
        while (matcher.find()) {
            if (matcher.group(1).equalsIgnoreCase(tag)) {
                return matcher.group(2).trim();
            }
        }
        return null;
    }

    private static String stripComments(String xml) {
        return xml.replaceAll("(?s)<!--.*?-->", "");
    }

    static List<Module> flatten(Module module) {
        List<Module> all = new ArrayList<>();
        flattenInto(module, all);
        return all;
    }

    private static void flattenInto(Module module, List<Module> all) {
        all.add(module);
        for (Module child : module.children) {
            flattenInto(child, all);
        }
    }

    }
