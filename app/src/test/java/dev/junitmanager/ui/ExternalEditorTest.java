package dev.junitmanager.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalEditorTest {
    @Test
    void buildsSakuraCommandWithFileAndLine() {
        Path file = Path.of("C:\\work\\Calculator Test.java");
        List<String> arguments = ExternalEditor.arguments(
                "\"C:\\Program Files\\sakura\\sakura.exe\" \"{file}\" -Y={line}",
                file,
                25);

        assertEquals(List.of(
                "C:\\Program Files\\sakura\\sakura.exe",
                file.toAbsolutePath().normalize().toString(),
                "-Y=25"), arguments);
    }

    @Test
    void appendsFileWhenPlaceholderIsMissing() {
        Path file = Path.of("src\\Test.java");
        List<String> arguments = ExternalEditor.arguments("notepad", file, 1);
        assertEquals(List.of("notepad", file.toAbsolutePath().normalize().toString()), arguments);
    }
}
