package dev.junitmanager.ui;

import com.formdev.flatlaf.icons.FlatAbstractIcon;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class AppIcon extends FlatAbstractIcon {
    public enum Kind {
        PLAY, CURRENT, RERUN, STOP, REFRESH, DEBUG, COLLAPSE, WATCH,
        PASSED, FAILED, SKIPPED, NOT_RUN, RUNNING
    }

    public static final Color PASSED_COLOR = new Color(0x2E, 0xA0, 0x43);
    public static final Color FAILED_COLOR = new Color(0xF8, 0x51, 0x49);
    public static final Color SKIPPED_COLOR = new Color(0xD2, 0x99, 0x22);
    public static final Color RUNNING_COLOR = new Color(0x37, 0x94, 0xFF);

    private final Kind kind;
    private final int phase;

    public AppIcon(Kind kind) {
        this(kind, 0);
    }

    public AppIcon(Kind kind, int phase) {
        super(16, 16, null);
        this.kind = kind;
        this.phase = phase;
    }

    @Override
    protected void paintIcon(Component c, Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        switch (kind) {
            case PLAY -> play(g, foreground());
            case CURRENT -> {
                play(g, foreground());
                g.fillRect(13, 3, 2, 10);
            }
            case RERUN -> rerun(g, FAILED_COLOR);
            case STOP -> {
                g.setColor(FAILED_COLOR);
                g.fillRoundRect(4, 4, 8, 8, 2, 2);
            }
            case REFRESH -> rerun(g, foreground());
            case DEBUG -> debug(g, foreground());
            case COLLAPSE -> collapse(g, foreground());
            case WATCH -> watch(g, foreground());
            case PASSED -> badge(g, PASSED_COLOR, Badge.CHECK);
            case FAILED -> badge(g, FAILED_COLOR, Badge.CROSS);
            case SKIPPED -> badge(g, SKIPPED_COLOR, Badge.MINUS);
            case NOT_RUN -> {
                g.setColor(new Color(0x8B, 0x94, 0x9E));
                g.setStroke(new BasicStroke(1.4f));
                g.drawOval(2, 2, 12, 12);
            }
            case RUNNING -> {
                g.setColor(RUNNING_COLOR);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawArc(2, 2, 12, 12, phase, 110);
            }
        }
    }

    public static AppIcon status(dev.junitmanager.model.TestStatus status, int phase) {
        return switch (status) {
            case PASSED -> new AppIcon(Kind.PASSED);
            case FAILED -> new AppIcon(Kind.FAILED);
            case SKIPPED -> new AppIcon(Kind.SKIPPED);
            case RUNNING -> new AppIcon(Kind.RUNNING, phase);
            case NOT_RUN -> new AppIcon(Kind.NOT_RUN);
        };
    }

    private static Color foreground() {
        Color color = UIManager.getColor("Label.foreground");
        return color == null ? new Color(0xCC, 0xCC, 0xCC) : color;
    }

    private static void play(Graphics2D g, Color color) {
        g.setColor(color);
        g.fillPolygon(new int[]{4, 4, 12}, new int[]{3, 13, 8}, 3);
    }

    private static void rerun(Graphics2D g, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(3, 3, 10, 10, 40, 260);
        g.fillPolygon(new int[]{11, 15, 11}, new int[]{2, 4, 7}, 3);
    }

    private static void debug(Graphics2D g, Color color) {
        g.setColor(color);
        g.fillOval(4, 5, 8, 6);
        g.fillOval(6, 3, 4, 3);
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(5, 4, 3, 2);
        g.drawLine(11, 4, 13, 2);
        g.drawLine(4, 8, 2, 10);
        g.drawLine(12, 8, 14, 10);
    }

    private static void collapse(Graphics2D g, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolyline(new int[]{3, 8, 13}, new int[]{5, 9, 5}, 3);
        g.drawPolyline(new int[]{3, 8, 13}, new int[]{9, 13, 9}, 3);
    }

    private static void watch(Graphics2D g, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawOval(2, 5, 12, 6);
        g.fillOval(6, 6, 4, 4);
    }

    private enum Badge { CHECK, CROSS, MINUS }

    private static void badge(Graphics2D g, Color color, Badge badge) {
        g.setColor(color);
        g.fillOval(1, 1, 14, 14);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (badge) {
            case CHECK -> g.drawPolyline(new int[]{4, 7, 12}, new int[]{8, 11, 5}, 3);
            case CROSS -> {
                g.drawLine(5, 5, 11, 11);
                g.drawLine(11, 5, 5, 11);
            }
            case MINUS -> g.drawLine(4, 8, 12, 8);
        }
    }

    public static int scaled(int value) {
        return UIScale.scale(value);
    }
}
