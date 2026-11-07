package dev.junitmanager.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class UserSettings {
    private boolean dark = true;
    private boolean watch;
    private final List<Path> recent = new ArrayList<>();
    private int x = Integer.MIN_VALUE;
    private int y = Integer.MIN_VALUE;
    private int width;
    private int height;
    private int dividerMain = 360;
    private int dividerVertical = 420;
    private boolean externalEditor;
    private String editorCommand = "";
    private boolean physicalNames;
    private String runProperties = "";

    public static Path file() {
        String override = System.getProperty("junitmanager.settings");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".junit-manager", "settings.properties");
    }

    public static UserSettings load() {
        UserSettings settings = new UserSettings();
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return settings;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException ex) {
            return settings;
        }
        settings.dark = !"light".equalsIgnoreCase(properties.getProperty("theme"));
        settings.watch = Boolean.parseBoolean(properties.getProperty("watch"));
        settings.x = parseInt(properties.getProperty("window.x"), Integer.MIN_VALUE);
        settings.y = parseInt(properties.getProperty("window.y"), Integer.MIN_VALUE);
        settings.width = parseInt(properties.getProperty("window.w"), 0);
        settings.height = parseInt(properties.getProperty("window.h"), 0);
        settings.dividerMain = parseInt(properties.getProperty("divider.main"), 360);
        settings.dividerVertical = parseInt(properties.getProperty("divider.vertical"), 420);
        settings.externalEditor = Boolean.parseBoolean(properties.getProperty("editor.external"));
        String command = properties.getProperty("editor.command");
        settings.editorCommand = command == null ? "" : command;
        settings.physicalNames = "physical".equalsIgnoreCase(properties.getProperty("tree.labels"));
        String runProperties = properties.getProperty("run.properties");
        settings.runProperties = runProperties == null ? "" : runProperties;
        for (int i = 0; i < 8; i++) {
            String value = properties.getProperty("recent." + i);
            if (value != null && !value.isBlank()) {
                settings.recent.add(Path.of(value));
            }
        }
        return settings;
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty("theme", dark ? "dark" : "light");
        properties.setProperty("watch", Boolean.toString(watch));
        properties.setProperty("window.x", Integer.toString(x));
        properties.setProperty("window.y", Integer.toString(y));
        properties.setProperty("window.w", Integer.toString(width));
        properties.setProperty("window.h", Integer.toString(height));
        properties.setProperty("divider.main", Integer.toString(dividerMain));
        properties.setProperty("divider.vertical", Integer.toString(dividerVertical));
        properties.setProperty("editor.external", Boolean.toString(externalEditor));
        properties.setProperty("editor.command", editorCommand == null ? "" : editorCommand);
        properties.setProperty("tree.labels", physicalNames ? "physical" : "display");
        properties.setProperty("run.properties", runProperties == null ? "" : runProperties);
        for (int i = 0; i < recent.size() && i < 8; i++) {
            properties.setProperty("recent." + i, recent.get(i).toString());
        }
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "JUnit Manager");
            }
        } catch (IOException ignored) {
            // 設定が書けなくてもアプリの操作は続ける
        }
    }

    public boolean dark() {
        return dark;
    }

    public void setDark(boolean dark) {
        this.dark = dark;
    }

    public boolean watch() {
        return watch;
    }

    public void setWatch(boolean watch) {
        this.watch = watch;
    }

    public List<Path> recent() {
        return List.copyOf(recent);
    }

    public Path lastProject() {
        return recent.isEmpty() ? null : recent.get(0);
    }

    public void remember(Path project) {
        if (project == null) {
            return;
        }
        Path absolute = project.toAbsolutePath().normalize();
        recent.removeIf(path -> path.toAbsolutePath().normalize().equals(absolute));
        recent.add(0, absolute);
        while (recent.size() > 8) {
            recent.remove(recent.size() - 1);
        }
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void setWindow(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int dividerMain() {
        return dividerMain;
    }

    public int dividerVertical() {
        return dividerVertical;
    }

    public boolean externalEditor() {
        return externalEditor;
    }

    public void setExternalEditor(boolean externalEditor) {
        this.externalEditor = externalEditor;
    }

    public String editorCommand() {
        return editorCommand == null ? "" : editorCommand;
    }

    public void setEditorCommand(String editorCommand) {
        this.editorCommand = editorCommand == null ? "" : editorCommand.trim();
    }

    public boolean physicalNames() {
        return physicalNames;
    }

    public void setPhysicalNames(boolean physicalNames) {
        this.physicalNames = physicalNames;
    }

    public String runProperties() {
        return runProperties == null ? "" : runProperties;
    }

    public void setRunProperties(String runProperties) {
        this.runProperties = runProperties == null ? "" : runProperties;
    }

    public void setDividers(int main, int vertical) {
        if (main > 80) {
            this.dividerMain = main;
        }
        if (vertical > 80) {
            this.dividerVertical = vertical;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
