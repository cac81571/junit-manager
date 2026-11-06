package dev.junitmanager.ui;

import dev.junitmanager.model.CaseResult;
import dev.junitmanager.model.TestKind;
import dev.junitmanager.model.TestNode;
import dev.junitmanager.model.TestStatus;
import dev.junitmanager.model.Workspace;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public final class TestDetailPanel extends JPanel {
    private final UserActions actions;
    private final JPanel stripe = new JPanel();
    private final JLabel title = new JLabel("テストを選択してください");
    private final JLabel subtitle = new JLabel(" ");
    private final JLabel meta = new JLabel(" ");
    private final JComboBox<CaseResult> cases = new JComboBox<>();
    private final JTextArea body = new JTextArea();
    private final JTextArea output = new JTextArea();
    private final JPanel outputBox = new JPanel(new BorderLayout());
    private final DefaultListModel<TestNode> failures = new DefaultListModel<>();
    private final JList<TestNode> failureList = new JList<>(failures);
    private final JPanel failureBox = new JPanel(new BorderLayout());
    private final JButton runButton = new JButton("実行");
    private final JButton sourceButton = new JButton("ソースを表示");
    private final JButton copyButton = new JButton("セレクタをコピー");
    private final JButton commandButton = new JButton("mvn test をコピー");
    private TestNode current;
    private Workspace workspace;

    public TestDetailPanel(UserActions actions) {
        super(new BorderLayout());
        this.actions = actions;
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 4f));
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        subtitle.setForeground(muted());
        meta.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        stripe.setPreferredSize(new Dimension(4, 1));
        stripe.setBackground(muted());

        JPanel headerText = new JPanel(new BorderLayout());
        headerText.setOpaque(false);
        headerText.add(title, BorderLayout.NORTH);
        headerText.add(subtitle, BorderLayout.CENTER);
        headerText.add(meta, BorderLayout.SOUTH);
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBorder(BorderFactory.createEmptyBorder(12, 8, 8, 16));
        header.setOpaque(false);
        header.add(stripe, BorderLayout.WEST);
        header.add(headerText, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        cases.setVisible(false);
        cases.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof CaseResult result) {
                    setText(result.summary());
                }
                return this;
            }
        });
        cases.addActionListener(event -> showCase(selectedCase()));
        failureList.setCellRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(value.displayName() + "  —  " + value.selector(), new AppIcon(AppIcon.Kind.FAILED), JLabel.LEFT);
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            if (selected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            return label;
        });
        failureList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                TestNode node = failureList.getSelectedValue();
                if (node != null && event.getClickCount() >= 1) {
                    actions.selectNode(node);
                }
            }
        });
        failureBox.setOpaque(false);
        JLabel failureTitle = new JLabel("失敗");
        failureTitle.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));
        failureBox.add(failureTitle, BorderLayout.NORTH);
        JScrollPane failureScroll = new JScrollPane(failureList);
        failureScroll.setPreferredSize(new Dimension(100, 140));
        failureBox.add(failureScroll, BorderLayout.CENTER);
        failureBox.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));
        failureBox.setVisible(false);

        body.setEditable(false);
        body.setFont(Ui.mono());
        body.setLineWrap(true);
        body.setWrapStyleWord(false);
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        body.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                String line = lineAt(event);
                if (Sources.parse(line) != null) {
                    actions.openStackLine(line);
                }
            }
        });
        body.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                body.setCursor(Sources.parse(lineAt(event)) != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
            }
        });

        output.setEditable(false);
        output.setFont(Ui.mono());
        output.setLineWrap(true);
        output.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel outputTitle = new JLabel("標準出力");
        outputTitle.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));
        outputBox.setOpaque(false);
        outputBox.add(outputTitle, BorderLayout.NORTH);
        outputBox.add(new JScrollPane(output), BorderLayout.CENTER);
        outputBox.setVisible(false);
        outputBox.setPreferredSize(new Dimension(100, 120));

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));
        center.add(cases, BorderLayout.NORTH);
        JPanel middle = new JPanel(new BorderLayout(0, 8));
        middle.setOpaque(false);
        middle.add(failureBox, BorderLayout.NORTH);
        middle.add(new JScrollPane(body), BorderLayout.CENTER);
        middle.add(outputBox, BorderLayout.SOUTH);
        center.add(middle, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        runButton.addActionListener(event -> {
            if (current != null) {
                actions.runNode(current, false);
            }
        });
        sourceButton.addActionListener(event -> {
            if (current != null) {
                actions.openSource(current);
            }
        });
        copyButton.addActionListener(event -> {
            if (current != null) {
                actions.copySelector(current);
            }
        });
        commandButton.addActionListener(event -> actions.copyTestCommand(current));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.setOpaque(false);
        buttons.add(runButton);
        buttons.add(sourceButton);
        buttons.add(copyButton);
        buttons.add(commandButton);
        add(buttons, BorderLayout.SOUTH);
        show(null, null);
    }

    public void show(TestNode node, Workspace workspace) {
        this.current = node;
        this.workspace = workspace;
        if (node == null) {
            title.setText(workspace == null ? "プロジェクトを開いてください" : "テストを選択してください");
            subtitle.setText(workspace == null
                    ? "pom.xml がある Maven プロジェクトを開くと、JUnit のテストがここに出ます。"
                    : "左のツリーでテストを選ぶと、結果とスタックトレースを表示します。");
            meta.setText(" ");
            stripe.setBackground(muted());
            body.setText("");
            cases.setVisible(false);
            failureBox.setVisible(false);
            outputBox.setVisible(false);
            sourceButton.setEnabled(false);
            copyButton.setEnabled(false);
            commandButton.setEnabled(workspace != null);
            runButton.setEnabled(workspace != null);
            return;
        }
        title.setText(node.displayName());
        subtitle.setText(subtitle(node));
        meta.setText(meta(node));
        stripe.setBackground(color(node.status()));
        sourceButton.setEnabled(node.sourceFile() != null || node.kind() == TestKind.METHOD || node.kind() == TestKind.CLASS);
        copyButton.setEnabled(node.kind() == TestKind.METHOD || node.kind() == TestKind.CLASS || node.kind() == TestKind.SUITE);
        commandButton.setEnabled(true);
        runButton.setEnabled(true);
        if (node.kind() == TestKind.METHOD) {
            showMethod(node);
        } else {
            showContainer(node);
        }
    }

    public void refresh() {
        show(current, workspace);
    }

    private void showMethod(TestNode node) {
        failureBox.setVisible(false);
        failures.clear();
        List<CaseResult> results = node.cases();
        cases.removeAllItems();
        if (results.size() > 1) {
            for (CaseResult result : results) {
                cases.addItem(result);
            }
            cases.setVisible(true);
            CaseResult preferred = results.stream().filter(item -> item.status() == TestStatus.FAILED).findFirst().orElse(results.get(0));
            cases.setSelectedItem(preferred);
            showCase(preferred);
        } else {
            cases.setVisible(false);
            showCase(results.isEmpty() ? null : results.get(0));
        }
        if (results.isEmpty()) {
            body.setText(emptyMethodText(node));
            outputBox.setVisible(false);
        }
    }

    private void showContainer(TestNode node) {
        cases.setVisible(false);
        outputBox.setVisible(false);
        int total = node.methodCount();
        int passed = node.count(TestStatus.PASSED);
        int failed = node.count(TestStatus.FAILED);
        int skipped = node.count(TestStatus.SKIPPED);
        int notRun = node.count(TestStatus.NOT_RUN);
        body.setText("テスト " + total + "\n成功 " + passed + "\n失敗 " + failed + "\nスキップ " + skipped + "\n未実行 " + notRun);
        failures.clear();
        List<TestNode> failedNodes = new ArrayList<>();
        node.walk(child -> {
            if (child.kind() != TestKind.METHOD || child.status() != TestStatus.FAILED) {
                return;
            }
            if (node.kind() != TestKind.SUITE && node.ancestor(TestKind.SUITE) == null && child.ancestor(TestKind.SUITE) != null) {
                return;
            }
            failedNodes.add(child);
        });
        for (TestNode failedNode : failedNodes) {
            failures.addElement(failedNode);
        }
        failureBox.setVisible(!failedNodes.isEmpty());
    }

    private void showCase(CaseResult result) {
        if (result == null) {
            if (current != null && current.kind() == TestKind.METHOD) {
                body.setText(emptyMethodText(current));
            }
            outputBox.setVisible(false);
            return;
        }
        body.setText(format(result));
        body.setCaretPosition(0);
        String systemOut = result.systemOut();
        boolean hasOut = systemOut != null && !systemOut.isBlank();
        output.setText(hasOut ? systemOut : "");
        outputBox.setVisible(hasOut);
    }

    private CaseResult selectedCase() {
        Object item = cases.getSelectedItem();
        return item instanceof CaseResult result ? result : null;
    }

    private static String emptyMethodText(TestNode node) {
        if (node.disabled()) {
            return node.ignoreReason() == null || node.ignoreReason().isBlank()
                    ? "無効なテストです。実行すると JUnit はスキップします。"
                    : "無効: " + node.ignoreReason();
        }
        if (node.status() == TestStatus.RUNNING) {
            return "実行中です。";
        }
        return "まだ実行していません。";
    }

    private static String format(CaseResult result) {
        String message = result.message();
        String trace = result.trace();
        if (trace != null && !trace.isBlank()) {
            if (message == null || message.isBlank() || trace.contains(message)) {
                return trace;
            }
            return message.strip() + "\n\n" + trace;
        }
        if (message != null && !message.isBlank()) {
            return message.strip();
        }
        return result.status().label();
    }

    private static String subtitle(TestNode node) {
        return switch (node.kind()) {
            case METHOD -> node.fqcn() + "#" + node.methodName();
            case SUITE, CLASS -> node.fqcn();
            case PACKAGE -> node.fqcn() == null || node.fqcn().isBlank() ? "デフォルトパッケージ" : node.fqcn();
            case MODULE -> String.valueOf(node.moduleDir());
            case ROOT -> "プロジェクト";
        };
    }

    private static String meta(TestNode node) {
        StringBuilder text = new StringBuilder(node.status().label());
        if (!node.durationLabel().isEmpty()) {
            text.append("  ·  ").append(node.durationLabel());
        }
        if (node.hasOrder()) {
            text.append("  ·  @Order ").append(node.order());
        }
        if (node.kind() == TestKind.SUITE) {
            text.append("  ·  スイート");
        }
        if (node.kind() == TestKind.METHOD && !node.framework().label().isEmpty()) {
            text.append("  ·  ").append(node.framework().label());
        }
        if (node.disabled()) {
            text.append("  ·  無効");
            if (node.ignoreReason() != null && !node.ignoreReason().isBlank()) {
                text.append("（").append(node.ignoreReason()).append('）');
            }
        }
        if (node.sourceFile() != null && node.line() > 0) {
            text.append("  ·  ").append(node.sourceFile().getFileName()).append(':').append(node.line());
        }
        return text.toString();
    }

    private static Color color(TestStatus status) {
        return switch (status) {
            case PASSED -> AppIcon.PASSED_COLOR;
            case FAILED -> AppIcon.FAILED_COLOR;
            case SKIPPED -> AppIcon.SKIPPED_COLOR;
            case RUNNING -> AppIcon.RUNNING_COLOR;
            case NOT_RUN -> muted();
        };
    }

    private static Color muted() {
        Color color = javax.swing.UIManager.getColor("Label.disabledForeground");
        return color == null ? new Color(0x8B, 0x94, 0x9E) : color;
    }

    private String lineAt(MouseEvent event) {
        try {
            int offset = body.viewToModel2D(event.getPoint());
            if (offset < 0) {
                return "";
            }
            int line = body.getLineOfOffset(offset);
            int start = body.getLineStartOffset(line);
            int end = body.getLineEndOffset(line);
            return body.getText(start, end - start);
        } catch (BadLocationException ex) {
            return "";
        }
    }
}
