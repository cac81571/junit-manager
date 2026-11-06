package dev.junitmanager.ui;

import com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme;

import javax.swing.UIManager;

public final class ThemeManager {
    private ThemeManager() {
    }

    public static void install(boolean dark) {
        if (dark) {
            FlatDarkFlatIJTheme.setup();
        } else {
            FlatLightFlatIJTheme.setup();
        }
        UIManager.put("Tree.paintLines", true);
        UIManager.put("Tree.wideSelection", true);
    }
}
