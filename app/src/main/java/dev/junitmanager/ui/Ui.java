package dev.junitmanager.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

final class Ui {
    private Ui() {
    }

    static Font mono() {
        return mono(0);
    }

    static Font mono(int larger) {
        Font base = UIManager.getFont("defaultFont");
        int size = (base == null ? 13 : base.getSize()) + larger;
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    static Color borderColor() {
        Color color = UIManager.getColor("Component.borderColor");
        return color == null ? new Color(0x45, 0x45, 0x45) : color;
    }

    static Border bottomLine() {
        return BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor());
    }

    static Border topLine() {
        return BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor());
    }

    static void toolbar(JButton button) {
        button.setFocusable(false);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setMargin(new Insets(4, 4, 4, 4));
    }

    static void toolbar(JToggleButton button) {
        button.setFocusable(false);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setMargin(new Insets(4, 4, 4, 4));
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
