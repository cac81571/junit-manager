package dev.junitmanager;

import dev.junitmanager.ui.MainFrame;

import javax.swing.SwingUtilities;
import java.nio.file.Path;

public final class JUnitManagerApp {
    private JUnitManagerApp() {
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            if (args.length > 0) {
                frame.openProject(Path.of(args[0]));
            } else {
                frame.restoreLastProject();
            }
        });
    }
}
