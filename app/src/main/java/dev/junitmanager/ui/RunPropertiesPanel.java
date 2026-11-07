package dev.junitmanager.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.util.function.Consumer;

/** 出力の上に置くシステムプロパティ入力。1行 {@code KEY=値}。 */
public final class RunPropertiesPanel extends JPanel {
    private final JTextArea area = new JTextArea(3, 20);
    private final Timer saveTimer;
    private boolean applying;

    public RunPropertiesPanel(String initial, Consumer<String> onChange) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(Ui.topLine());
        JLabel title = new JLabel("システムプロパティ");
        title.setBorder(BorderFactory.createEmptyBorder(6, 12, 2, 12));
        JLabel hint = new JLabel("1行に1つ KEY=値（例: PARAM=1）");
        hint.setBorder(BorderFactory.createEmptyBorder(0, 12, 4, 12));
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(title, BorderLayout.NORTH);
        north.add(hint, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        area.setText(initial == null ? "" : initial);
        area.setFont(Ui.mono());
        area.setLineWrap(false);
        area.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        area.setToolTipText("実行時とコピーした mvn コマンドに -DKEY=値 として渡します");
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        scroll.setPreferredSize(new java.awt.Dimension(100, 72));
        add(scroll, BorderLayout.CENTER);

        saveTimer = new Timer(400, event -> {
            if (!applying) {
                onChange.accept(area.getText());
            }
        });
        saveTimer.setRepeats(false);
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                saveTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                saveTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                saveTimer.restart();
            }
        });
    }

    public String text() {
        return area.getText();
    }

    public void setText(String text) {
        applying = true;
        try {
            area.setText(text == null ? "" : text);
            area.setCaretPosition(0);
        } finally {
            applying = false;
        }
    }

    public void focusEditor() {
        area.requestFocusInWindow();
    }

    public void applyTheme() {
        area.setFont(Ui.mono());
    }
}
