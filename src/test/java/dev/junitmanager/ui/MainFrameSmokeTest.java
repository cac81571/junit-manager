package dev.junitmanager.ui;

import dev.junitmanager.discover.TestDiscovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainFrameSmokeTest {
    @Test
    @EnabledIf("headed")
    void opensExampleProject() throws Exception {
        Path settings = Files.createTempDirectory("junit-manager-ui").resolve("settings.properties");
        System.setProperty("junitmanager.settings", settings.toString());
        System.setProperty("junitmanager.noexit", "true");
        try {
            Path example = Path.of("examples/calculator").toAbsolutePath().normalize();
            assertEquals(5, TestDiscovery.discover(example).methodCount());
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger shown = new AtomicInteger();
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                frame.openProject(example, () -> {
                    shown.set(frame.methodCount());
                    frame.dispose();
                    latch.countDown();
                });
            });
            assertTrue(latch.await(20, TimeUnit.SECONDS));
            assertEquals(5, shown.get());
        } finally {
            System.clearProperty("junitmanager.settings");
        }
    }

    static boolean headed() {
        return !GraphicsEnvironment.isHeadless();
    }
}
