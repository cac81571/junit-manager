package dev.junitmanager.ui;

import com.formdev.flatlaf.FlatLaf;
import dev.junitmanager.discover.TextFiles;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SourceDialog extends JDialog {
    private final SourcePane area = new SourcePane();
    private DefaultHighlighter.DefaultHighlightPainter linePainter = linePainter();
    private int currentLine = 1;
    private boolean code;

    public SourceDialog(Frame owner) {
        super(owner, "ソース");
        setModal(false);
        area.setEditable(false);
        area.setFont(Ui.mono());
        area.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        setLayout(new BorderLayout());
        add(scroll, BorderLayout.CENTER);
        setSize(880, 640);
        setLocationRelativeTo(owner);
    }

    public void applyTheme() {
        SwingUtilities.updateComponentTreeUI(this);
        restyle();
        if (isVisible()) {
            SwingUtilities.invokeLater(() -> highlight(currentLine));
        }
    }

    public void showFile(Path file, int line) {
        currentLine = Math.max(1, line);
        try {
            if (Files.size(file) > 2_000_000) {
                code = false;
                area.setText("ファイルが大きすぎます: " + file);
            } else {
                code = true;
                area.setText(TextFiles.read(file));
                area.setCaretPosition(0);
            }
        } catch (Exception ex) {
            code = false;
            area.setText("ソースを読めません: " + ex.getMessage());
        }
        setTitle(file.getFileName() + ":" + currentLine);
        restyle();
        if (!isVisible()) {
            setVisible(true);
        }
        toFront();
        SwingUtilities.invokeLater(() -> highlight(currentLine));
    }

    private void restyle() {
        area.setFont(Ui.mono());
        Color background = UIManager.getColor("TextArea.background");
        Color foreground = UIManager.getColor("TextArea.foreground");
        if (background != null) {
            area.setBackground(background);
            getContentPane().setBackground(background);
        }
        if (foreground != null) {
            area.setForeground(foreground);
            area.setCaretColor(foreground);
        }
        Color selection = UIManager.getColor("TextArea.selectionBackground");
        Color selectionText = UIManager.getColor("TextArea.selectionForeground");
        if (selection != null) {
            area.setSelectionColor(selection);
        }
        if (selectionText != null) {
            area.setSelectedTextColor(selectionText);
        }
        linePainter = linePainter();
        if (code && area.getDocument().getLength() > 0) {
            JavaSyntax.apply(area.getStyledDocument(), FlatLaf.isLafDark(), area.getFont());
        }
    }

    private static DefaultHighlighter.DefaultHighlightPainter linePainter() {
        Color color = FlatLaf.isLafDark() ? new Color(0x32, 0x36, 0x3B) : new Color(0xE8, 0xF2, 0xFE);
        return new DefaultHighlighter.DefaultHighlightPainter(color);
    }

    private void highlight(int line) {
        area.getHighlighter().removeAllHighlights();
        try {
            int index = Math.min(line, area.getDocument().getDefaultRootElement().getElementCount()) - 1;
            if (index < 0) {
                return;
            }
            Element row = area.getDocument().getDefaultRootElement().getElement(index);
            area.getHighlighter().addHighlight(row.getStartOffset(), row.getEndOffset(), linePainter);
            area.setCaretPosition(row.getStartOffset());
            Rectangle view = area.modelToView2D(row.getStartOffset()).getBounds();
            view.y = Math.max(0, view.y - 80);
            view.height = 160;
            area.scrollRectToVisible(view);
        } catch (BadLocationException | NullPointerException ignored) {
            // まだレイアウト前、または行が無い
        }
    }

    private static final class SourcePane extends JTextPane {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            if (!(getParent() instanceof JViewport)) {
                return false;
            }
            Dimension preferred = getUI().getPreferredSize(this);
            return preferred != null && preferred.width <= getParent().getWidth();
        }
    }
}
