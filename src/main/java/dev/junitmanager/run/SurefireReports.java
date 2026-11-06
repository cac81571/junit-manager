package dev.junitmanager.run;

import dev.junitmanager.model.TestStatus;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SurefireReports {
    private SurefireReports() {
    }

    public static Map<Path, Long> snapshot(Collection<Path> moduleDirs) {
        Map<Path, Long> times = new LinkedHashMap<>();
        for (Path module : moduleDirs) {
            if (module == null) {
                continue;
            }
            Path dir = module.resolve("target").resolve("surefire-reports");
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var stream = Files.list(dir)) {
                stream.filter(SurefireReports::isReport).forEach(path -> {
                    try {
                        times.put(path.toAbsolutePath().normalize(), Files.getLastModifiedTime(path).toMillis());
                    } catch (IOException ignored) {
                        // 読めないレポートは実行後の差分判定から外す
                    }
                });
            } catch (IOException ignored) {
                // ディレクトリが消えた場合は空のスナップショットのままにする
            }
        }
        return times;
    }

    public static List<ReportedTest> parseUpdated(Collection<Path> moduleDirs, Map<Path, Long> before) {
        List<ReportedTest> tests = new ArrayList<>();
        for (Path module : moduleDirs) {
            if (module == null) {
                continue;
            }
            Path dir = module.resolve("target").resolve("surefire-reports");
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var stream = Files.list(dir)) {
                List<Path> files = stream.filter(SurefireReports::isReport).toList();
                for (Path file : files) {
                    Path key = file.toAbsolutePath().normalize();
                    long modified;
                    try {
                        modified = Files.getLastModifiedTime(file).toMillis();
                    } catch (IOException ex) {
                        continue;
                    }
                    long previous = before.getOrDefault(key, Long.MIN_VALUE);
                    if (modified <= previous) {
                        continue;
                    }
                    tests.addAll(parseFile(module, file));
                }
            } catch (IOException ignored) {
                // 途中でディレクトリが消えても、取れた結果だけ返す
            }
        }
        return tests;
    }

    static List<ReportedTest> parseFile(Path moduleDir, Path file) {
        List<ReportedTest> tests = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            } catch (Exception ignored) {
                // 実装が機能フラグを持たない場合でも、外部実体の展開は止めている
            }
            Document document = factory.newDocumentBuilder().parse(file.toFile());
            collect(moduleDir, document.getDocumentElement(), tests);
        } catch (Exception ignored) {
            // 壊れた XML はスキップする。コンソール側に Maven のログが残る
        }
        return tests;
    }

    private static void collect(Path moduleDir, Element element, List<ReportedTest> tests) {
        if (element == null) {
            return;
        }
        if ("testcase".equals(element.getTagName())) {
            tests.add(toTest(moduleDir, element));
            return;
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element child) {
                collect(moduleDir, child, tests);
            }
        }
    }

    private static ReportedTest toTest(Path moduleDir, Element element) {
        String name = element.getAttribute("name");
        String className = element.getAttribute("classname");
        long duration = secondsToMillis(element.getAttribute("time"));
        String message = null;
        String type = null;
        String trace = null;
        String systemOut = null;
        boolean failed = false;
        boolean skipped = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element child)) {
                continue;
            }
            String tag = child.getTagName();
            if (tag.equals("failure") || tag.equals("error") || tag.equals("flakyFailure")
                    || tag.equals("flakyError") || tag.equals("rerunFailure")) {
                failed = true;
                if (message == null) {
                    message = emptyToNull(child.getAttribute("message"));
                    type = emptyToNull(child.getAttribute("type"));
                    trace = emptyToNull(child.getTextContent());
                }
            } else if (tag.equals("skipped")) {
                skipped = true;
                if (message == null) {
                    message = emptyToNull(child.getAttribute("message"));
                }
            } else if (tag.equals("system-out") || tag.equals("system-err")) {
                String text = child.getTextContent();
                if (text != null && !text.isBlank()) {
                    systemOut = systemOut == null ? text : systemOut + "\n" + text;
                }
            }
        }
        TestStatus status = failed ? TestStatus.FAILED : skipped ? TestStatus.SKIPPED : TestStatus.PASSED;
        return new ReportedTest(
                moduleDir,
                className,
                name == null || name.isBlank() ? "(無名)" : name,
                status,
                duration,
                trim(message, 8_000),
                type,
                trim(trace, 100_000),
                trim(systemOut, 16_000)
        );
    }

    private static long secondsToMillis(String seconds) {
        if (seconds == null || seconds.isBlank()) {
            return -1;
        }
        try {
            return Math.round(Double.parseDouble(seconds.trim()) * 1000d);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static boolean isReport(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.startsWith("TEST-") && name.endsWith(".xml");
    }

    private static String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max) + "\n…";
    }
}
