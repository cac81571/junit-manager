package dev.junitmanager.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ExternalEditor {
    private ExternalEditor() {
    }

    static List<String> arguments(String template, Path file, int line) {
        String path = file.toAbsolutePath().normalize().toString();
        String row = Integer.toString(Math.max(1, line));
        List<String> tokens = tokenize(template == null ? "" : template.trim());
        boolean sawFile = false;
        List<String> arguments = new ArrayList<>();
        for (String token : tokens) {
            if (token.contains("{file}")) {
                sawFile = true;
            }
            arguments.add(token.replace("{file}", path).replace("{line}", row));
        }
        if (!sawFile && !path.isBlank()) {
            arguments.add(path);
        }
        return arguments;
    }

    static void open(String template, Path file, int line) throws IOException {
        List<String> arguments = arguments(template, file, line);
        if (arguments.isEmpty() || arguments.get(0).isBlank()) {
            throw new IOException("コマンドが空です");
        }
        new ProcessBuilder(arguments).start();
    }

    static List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < command.length(); index++) {
            char character = command.charAt(index);
            if (character == '"') {
                quoted = !quoted;
                continue;
            }
            if (!quoted && Character.isWhitespace(character)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(character);
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
