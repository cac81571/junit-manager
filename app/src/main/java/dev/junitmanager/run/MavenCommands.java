package dev.junitmanager.run;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MavenCommands {
    private MavenCommands() {
    }

    public record Launch(List<String> command, Path directory, String display) {
    }

    public static Launch launch(Path projectRoot, Path workingDirectory, List<String> mavenArgs) {
        Path directory = workingDirectory == null ? projectRoot : workingDirectory;
        Path wrapper = resolveWrapper(directory, projectRoot);
        boolean windows = isWindows();
        String executable = wrapper == null ? (windows ? "mvn.cmd" : "mvn") : wrapper.toString();
        String shownName = wrapper == null ? (windows ? "mvn" : "mvn") : wrapper.getFileName().toString();
        List<String> command = windows ? windowsCommand(executable, mavenArgs) : unixCommand(executable, mavenArgs);
        String folder = directory.getFileName() == null ? directory.toString() : directory.getFileName().toString();
        String display = folder + "> " + shownName + " " + String.join(" ", mavenArgs);
        return new Launch(command, directory, display);
    }

    public enum ClipboardShell {
        CMD, POWERSHELL
    }

    /**
     * シェルに貼り付けて実行できるコマンドを返す。
     * 作業ディレクトリへの {@code cd} と {@code mvn test} を連結する。
     */
    public static String clipboardCommand(Path workingDirectory, List<String> mavenArgs, ClipboardShell shell) {
        if (shell == ClipboardShell.POWERSHELL) {
            return powershellClipboard(workingDirectory, mavenArgs);
        }
        return cmdClipboard(workingDirectory, mavenArgs);
    }

    private static String cmdClipboard(Path workingDirectory, List<String> mavenArgs) {
        String mvn = testInvocation(mavenArgs, false);
        if (workingDirectory == null) {
            return mvn;
        }
        String dir = workingDirectory.toAbsolutePath().normalize().toString();
        if (isWindows()) {
            return "cd /d " + quoteForCmd(dir) + " && " + mvn;
        }
        return "cd " + quoteForShell(dir) + " && " + mvn;
    }

    private static String powershellClipboard(Path workingDirectory, List<String> mavenArgs) {
        String mvn = testInvocation(mavenArgs, true);
        if (workingDirectory == null) {
            return mvn;
        }
        String dir = workingDirectory.toAbsolutePath().normalize().toString().replace("'", "''");
        return "Set-Location -LiteralPath '" + dir + "'; " + mvn;
    }

    private static String testInvocation(List<String> mavenArgs, boolean powerShell) {
        String selector = null;
        for (String arg : mavenArgs) {
            if (arg.startsWith("-Dtest=")) {
                selector = arg.substring("-Dtest=".length());
            }
        }
        String extras = " -Dconfirm=console -DforkCount=0";
        if (selector == null || selector.isBlank()) {
            return "mvn test" + extras;
        }
        if (powerShell) {
            return "mvn test '-Dtest=" + selector.replace("'", "''") + "'" + extras;
        }
        return "mvn test -Dtest=" + selector + extras;
    }

    static String quoteForCmd(String value) {
        if (value.indexOf(' ') < 0 && value.indexOf('\t') < 0 && value.indexOf('&') < 0
                && value.indexOf('^') < 0 && value.indexOf('(') < 0 && value.indexOf(')') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "") + "\"";
    }

    static String quoteForShell(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    static Path resolveWrapper(Path start, Path root) {
        boolean windows = isWindows();
        Path dir = start;
        while (dir != null) {
            Path wrapper = dir.resolve(windows ? "mvnw.cmd" : "mvnw");
            if (Files.isRegularFile(wrapper)) {
                return wrapper.toAbsolutePath().normalize();
            }
            if (root != null && dir.toAbsolutePath().normalize().equals(root.toAbsolutePath().normalize())) {
                break;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static List<String> unixCommand(String executable, List<String> args) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(args);
        return List.copyOf(command);
    }

    private static List<String> windowsCommand(String executable, List<String> args) {
        StringBuilder command = new StringBuilder(quote(executable));
        for (String arg : args) {
            command.append(' ').append(quote(arg));
        }
        return List.of("cmd.exe", "/c", command.toString());
    }

    static String quote(String value) {
        String escaped = value.replace("%", "%%");
        boolean special = false;
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c == ' ' || c == '\t' || c == '"' || c == '&' || c == '|' || c == '<' || c == '>' || c == '^') {
                special = true;
                break;
            }
        }
        if (!special) {
            return escaped;
        }
        return "\"" + escaped.replace("\"", "\\\"") + "\"";
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static String explain(IOException error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (message.contains("error=2") || message.contains("CreateProcess") || message.contains("No such file")) {
            return "Maven が見つかりません。PATH に mvn を通すか、プロジェクト直下に mvnw を置いてください。";
        }
        return "Maven を起動できません: " + message;
    }
}
