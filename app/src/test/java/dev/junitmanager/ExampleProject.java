package dev.junitmanager;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ExampleProject {
    private ExampleProject() {
    }

    public static Path calculator() {
        Path dir = Path.of("").toAbsolutePath().normalize();
        while (dir != null) {
            Path pom = dir.resolve("examples").resolve("calculator").resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                return pom.getParent();
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("examples/calculator が見つかりません");
    }
}
