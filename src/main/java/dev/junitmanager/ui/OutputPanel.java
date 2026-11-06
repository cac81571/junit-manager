package dev.junitmanager.ui;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

public final class OutputPanel extends JPanel {
    private static final Color DARK_ERROR = new Color(0xF1, 0x4C, 0x4C);
    private static final Color LIGHT_ERROR = new Color(0xC7, 0x2E, 0x29);
    private static final Color DARK_WARNING = new Color(0xD2, 0x99, 0x22);
    private static final Color LIGHT_WARNING = new Color(0x9A, 0x67, 0x00);
    private static final Color DARK_INFO = new Color(0x56, 0xA8, 0xF5);
    private static final Color LIGHT_INFO = new Color(0x17, 0x50, 0xEB);
    private static final Color DARK_DEBUG = new Color(0x7A, 0x7E, 0x85);
    private static final Color LIGHT_DEBUG = new Color(0x6E, 0x6E, 0x6E);

    private final LogPane area = new LogPane();
    private final JScrollPane scroll;
    private final StringBuilder pending = new StringBuilder();
    private final AnsiText.State ansi = new AnsiText.State();

    public OutputPanel(Runnable onClear) {
        super(new BorderLayout());
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(Ui.bottomLine());
        JLabel title = new JLabel("出力");
        title.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        header.add(title, BorderLayout.WEST);
        JButton clear = new JButton("クリア");
        clear.addActionListener(event -> onClear.run());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        actions.setOpaque(false);
        actions.add(clear);
        header.add(actions, BorderLayout.EAST);
        header.setOpaque(false);
        add(header, BorderLayout.NORTH);

        area.setEditable(false);
        area.setFont(Ui.mono());
        area.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        scroll = new JScrollPane(area);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);
        setOpaque(false);
    }

    public void applyTheme() {
        area.setFont(Ui.mono());
        Color background = UIManager.getColor("TextArea.background");
        Color foreground = UIManager.getColor("TextArea.foreground");
        if (background != null) {
            area.setBackground(background);
        }
        if (foreground != null) {
            area.setForeground(foreground);
            area.setCaretColor(foreground);
        }
        restyleAll();
    }

    public void append(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        javax.swing.JScrollBar bar = scroll.getVerticalScrollBar();
        boolean follow = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 32;
        pending.append(text);
        int lastBreak = pending.lastIndexOf("\n");
        if (lastBreak < 0) {
            return;
        }
        String ready = pending.substring(0, lastBreak + 1);
        pending.delete(0, lastBreak + 1);
        insertColored(ready);
        trim();
        if (follow) {
            area.setCaretPosition(area.getDocument().getLength());
        }
    }

    public void clear() {
        pending.setLength(0);
        ansi.reset();
        area.setText("");
    }

    private void insertColored(String ready) {
        if (ready.indexOf('\u001B') >= 0) {
            for (AnsiText.Span span : AnsiText.parse(ready, ansi)) {
                insert(span.text(), ansiStyle(span));
            }
            return;
        }
        int start = 0;
        for (int index = 0; index < ready.length(); index++) {
            if (ready.charAt(index) != '\n') {
                continue;
            }
            String line = ready.substring(start, index);
            insert(line + "\n", style(line));
            start = index + 1;
        }
    }

    private void insert(String text, AttributeSet attributes) {
        try {
            area.getStyledDocument().insertString(area.getDocument().getLength(), text, attributes);
        } catch (BadLocationException ignored) {
            // 末尾への追加に失敗した場合は捨てる
        }
    }

    private void restyleAll() {
        StyledDocument document = area.getStyledDocument();
        List<Piece> pieces = new ArrayList<>();
        collect(document.getDefaultRootElement(), pieces);
        for (Piece piece : pieces) {
            AttributeSet current = piece.attributes;
            SimpleAttributeSet next;
            if ("ansi".equals(current.getAttribute(AnsiText.MODE))) {
                int fg = current.getAttribute(AnsiText.FG) instanceof Integer code ? code : -1;
                boolean bold = Boolean.TRUE.equals(current.getAttribute(AnsiText.BOLD));
                next = ansiStyle(new AnsiText.Span("", fg, bold));
            } else {
                String line;
                try {
                    line = document.getText(piece.start, piece.length).replace("\n", "");
                } catch (BadLocationException ex) {
                    continue;
                }
                next = style(line);
            }
            document.setCharacterAttributes(piece.start, piece.length, next, true);
        }
    }

    private static void collect(Element element, List<Piece> pieces) {
        if (element.isLeaf()) {
            int length = element.getEndOffset() - element.getStartOffset();
            if (length > 0) {
                pieces.add(new Piece(element.getStartOffset(), length, element.getAttributes()));
            }
            return;
        }
        for (int index = 0; index < element.getElementCount(); index++) {
            collect(element.getElement(index), pieces);
        }
    }

    private SimpleAttributeSet ansiStyle(AnsiText.Span span) {
        SimpleAttributeSet attributes = fonted();
        StyleConstants.setForeground(attributes, span.fg() < 0 ? plain() : AnsiText.color(span.fg(), FlatLaf.isLafDark()));
        if (span.bold()) {
            StyleConstants.setBold(attributes, true);
            attributes.addAttribute(AnsiText.BOLD, Boolean.TRUE);
        }
        attributes.addAttribute(AnsiText.MODE, "ansi");
        attributes.addAttribute(AnsiText.FG, span.fg());
        return attributes;
    }

    private SimpleAttributeSet style(String line) {
        Color plain = area.getForeground();
        if (plain == null) {
            plain = FlatLaf.isLafDark() ? new Color(0xBC, 0xBE, 0xC4) : new Color(0x08, 0x08, 0x08);
        }
        boolean dark = FlatLaf.isLafDark();
        Color color = switch (LogLines.level(line)) {
            case ERROR -> dark ? DARK_ERROR : LIGHT_ERROR;
            case WARNING -> dark ? DARK_WARNING : LIGHT_WARNING;
            case INFO -> dark ? DARK_INFO : LIGHT_INFO;
            case DEBUG -> dark ? DARK_DEBUG : LIGHT_DEBUG;
            case PLAIN -> plain;
        };
        SimpleAttributeSet attributes = fonted();
        StyleConstants.setForeground(attributes, color);
        attributes.addAttribute(AnsiText.MODE, "level");
        return attributes;
    }

    private SimpleAttributeSet fonted() {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        Font font = area.getFont() == null ? Ui.mono() : area.getFont();
        StyleConstants.setFontFamily(attributes, font.getFamily());
        StyleConstants.setFontSize(attributes, font.getSize());
        return attributes;
    }

    private Color plain() {
        Color color = area.getForeground();
        if (color != null) {
            return color;
        }
        return FlatLaf.isLafDark() ? new Color(0xBC, 0xBE, 0xC4) : new Color(0x08, 0x08, 0x08);
    }

    private void trim() {
        int extra = area.getDocument().getLength() - 400_000;
        if (extra <= 0) {
            return;
        }
        try {
            area.getDocument().remove(0, extra);
            insert("…古い出力を省略しました\n", style(""));
        } catch (BadLocationException ignored) {
            clear();
        }
    }

    private record Piece(int start, int length, AttributeSet attributes) {
    }

    private static final class LogPane extends JTextPane {
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
