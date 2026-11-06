package dev.junitmanager.ui;

import dev.junitmanager.model.TestKind;
import dev.junitmanager.model.TestNode;
import dev.junitmanager.model.TestStatus;
import dev.junitmanager.model.Workspace;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;

public final class TestExplorerPanel extends JPanel {
    private static final String TREE = "tree";
    private static final String EMPTY = "empty";
    private static final String[] FILTERS = {"すべての状態", "失敗", "成功", "スキップ", "未実行"};

    private final UserActions actions;
    private final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree tree = new JTree(model);
    private final JTextField filter = new JTextField();
    private final JComboBox<String> statusFilter = new JComboBox<>(FILTERS);
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JLabel empty = new JLabel("", JLabel.CENTER);
    private final JLabel project = new JLabel("プロジェクト未選択");
    private final JButton runAll;
    private final JButton runSelected;
    private final JButton runFailed;
    private final JButton debug;
    private final JButton stop;
    private final JButton refresh;
    private final JButton collapse;
    private final JToggleButton watch;
    private final Timer filterTimer = new Timer(120, event -> reload(false));
    private int spin;
    private final Timer spinTimer = new Timer(80, event -> {
        spin = (spin + 30) % 360;
        tree.repaint();
    });
    private Workspace workspace;
    private boolean applying;

    public TestExplorerPanel(UserActions actions) {
        super(new BorderLayout());
        this.actions = actions;
        runAll = button(AppIcon.Kind.PLAY, "すべて実行 (F5)", actions::runAll);
        runSelected = button(AppIcon.Kind.CURRENT, "選択を実行 (F6)", actions::runSelected);
        runFailed = button(AppIcon.Kind.RERUN, "失敗を再実行 (F7)", actions::runFailed);
        debug = button(AppIcon.Kind.DEBUG, "デバッグ (Ctrl+F5)", actions::debugSelected);
        stop = button(AppIcon.Kind.STOP, "停止 (Shift+F5)", actions::stop);
        refresh = button(AppIcon.Kind.REFRESH, "再検出 (Ctrl+Shift+R)", actions::refresh);
        collapse = button(AppIcon.Kind.COLLAPSE, "すべて折りたたむ", this::collapseAll);
        watch = new JToggleButton(new AppIcon(AppIcon.Kind.WATCH));
        filterTimer.setRepeats(false);
        spinTimer.setRepeats(true);
        buildToolbar();
        buildTree();
        empty.setFont(empty.getFont().deriveFont(Font.PLAIN, empty.getFont().getSize2D() + 1f));
        JPanel emptyWrap = new JPanel(new BorderLayout());
        emptyWrap.setOpaque(false);
        empty.setBorder(BorderFactory.createEmptyBorder(24, 16, 24, 16));
        emptyWrap.add(empty, BorderLayout.CENTER);
        JButton open = new JButton("プロジェクトを開く");
        open.addActionListener(event -> actions.openProject());
        JPanel openRow = new JPanel();
        openRow.setOpaque(false);
        openRow.add(open);
        emptyWrap.add(openRow, BorderLayout.SOUTH);
        center.setOpaque(false);
        center.add(new JScrollPane(tree), TREE);
        center.add(emptyWrap, EMPTY);
        add(center, BorderLayout.CENTER);
        showEmpty("Maven プロジェクトを開くと、テストがここに表示されます。", true);
        setBusy(false, false);
    }

    public JTextField filterField() {
        return filter;
    }

    public void setWatchSelected(boolean selected) {
        watch.setSelected(selected);
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
        project.setText(workspace == null ? "プロジェクト未選択" : workspace.name());
        project.setToolTipText(workspace == null ? null : workspace.root().toString());
        filter.setText("");
        statusFilter.setSelectedIndex(0);
        reload(false);
    }

    public void reload(boolean keepState) {
        Set<String> expanded = keepState ? expandedKeys() : Set.of();
        String selected = keepState ? selectedKey() : null;
        int scroll = keepState ? scrollValue() : 0;
        applying = true;
        try {
            if (workspace == null) {
                model.setRoot(new DefaultMutableTreeNode());
                showEmpty("Maven プロジェクトを開くと、テストがここに表示されます。", true);
                return;
            }
            DefaultMutableTreeNode root = build(workspace.tree(), filter.getText().trim(), selectedStatus(), false);
            model.setRoot(root == null ? new DefaultMutableTreeNode(workspace.tree()) : root);
            boolean noTests = workspace.methodCount() == 0;
            boolean filteredOut = !noTests && showingCount() == 0;
            if (noTests) {
                showEmpty("@Test の付いたメソッドは見つかりませんでした。", false);
            } else if (filteredOut) {
                showEmpty("一致するテストがありません。", false);
            } else {
                cards.show(center, TREE);
                if (keepState && !expanded.isEmpty()) {
                    restore(expanded);
                } else if (!keepState) {
                    expandDefault();
                } else {
                    restore(expanded);
                }
                if (selected != null) {
                    selectKey(selected);
                } else if (tree.getSelectionPath() == null && model.getRoot() != null) {
                    tree.setSelectionPath(new TreePath(model.getRoot()));
                }
                if (keepState) {
                    SwingUtilities.invokeLater(() -> treeScroll().setValue(scroll));
                }
            }
        } finally {
            applying = false;
        }
    }

    public void refreshStatuses() {
        tree.repaint();
    }

    public TestNode selected() {
        TreePath path = tree.getSelectionPath();
        return path == null ? null : nodeOf(path.getLastPathComponent());
    }

    public void select(TestNode node) {
        if (node == null) {
            return;
        }
        cards.show(center, TREE);
        selectKey(node.key());
    }

    public int showingCount() {
        Object root = model.getRoot();
        if (!(root instanceof DefaultMutableTreeNode treeNode)) {
            return 0;
        }
        return countMethods(treeNode);
    }

    public void setBusy(boolean discovering, boolean running) {
        boolean idle = !discovering && !running;
        boolean hasProject = workspace != null;
        runAll.setEnabled(idle && hasProject);
        runSelected.setEnabled(idle && hasProject);
        runFailed.setEnabled(idle && hasProject);
        debug.setEnabled(idle && hasProject);
        refresh.setEnabled(idle && hasProject);
        collapse.setEnabled(hasProject && !discovering);
        stop.setEnabled(running);
        watch.setEnabled(hasProject && !discovering);
        filter.setEnabled(hasProject && !discovering);
        statusFilter.setEnabled(hasProject && !discovering);
        if (running) {
            spinTimer.start();
        } else {
            spinTimer.stop();
            tree.repaint();
        }
    }

    private void buildToolbar() {
        JPanel title = new JPanel(new BorderLayout());
        JLabel heading = new JLabel("テストエクスプローラー");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        heading.setBorder(BorderFactory.createEmptyBorder(8, 12, 0, 12));
        title.setOpaque(false);
        title.add(heading, BorderLayout.WEST);
        project.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 12));
        title.add(project, BorderLayout.CENTER);

        watch.setToolTipText("変更を監視して再実行");
        Ui.toolbar(watch);
        watch.addActionListener(event -> actions.watch(watch.isSelected()));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));
        for (JButton button : new JButton[]{runAll, runSelected, runFailed, debug, stop, refresh, collapse}) {
            buttons.add(button);
        }
        buttons.add(watch);

        filter.putClientProperty("JTextField.placeholderText", "テストをフィルター");
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                schedule();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                schedule();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                schedule();
            }
        });
        statusFilter.addActionListener(event -> {
            if (!applying) {
                reload(true);
            }
        });
        JPanel filters = new JPanel(new BorderLayout(8, 0));
        filters.setOpaque(false);
        filters.setBorder(BorderFactory.createEmptyBorder(6, 12, 8, 12));
        filters.add(filter, BorderLayout.CENTER);
        filters.add(statusFilter, BorderLayout.EAST);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.setBorder(Ui.bottomLine());
        north.add(title, BorderLayout.NORTH);
        north.add(buttons, BorderLayout.CENTER);
        north.add(filters, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
    }

    private void buildTree() {
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(0);
        tree.setCellRenderer(new Renderer());
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addTreeSelectionListener(event -> {
            if (!applying) {
                actions.selectNode(selected());
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (event.isPopupTrigger() || SwingUtilities.isRightMouseButton(event)) {
                    showPopup(event);
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    showPopup(event);
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    TestNode node = nodeAt(event);
                    if (node != null && node.kind() == TestKind.METHOD) {
                        actions.runNode(node, false);
                    }
                }
            }
        });
    }

    private void showPopup(MouseEvent event) {
        TreePath path = tree.getPathForLocation(event.getX(), event.getY());
        if (path == null) {
            return;
        }
        tree.setSelectionPath(path);
        TestNode node = nodeOf(path.getLastPathComponent());
        if (node == null) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        item(menu, "実行", () -> actions.runNode(node, false));
        item(menu, "デバッグ", () -> actions.runNode(node, true));
        if (node.sourceFile() != null) {
            item(menu, "ソースを表示", () -> actions.openSource(node));
        }
        if (node.kind() == TestKind.METHOD || node.kind() == TestKind.CLASS) {
            item(menu, "セレクタをコピー", () -> actions.copySelector(node));
        }
        item(menu, "フォルダを開く", () -> actions.reveal(node));
        menu.show(tree, event.getX(), event.getY());
    }

    private static void item(JPopupMenu menu, String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(event -> action.run());
        menu.add(item);
    }

    private JButton button(AppIcon.Kind kind, String tip, Runnable action) {
        JButton button = new JButton(new AppIcon(kind));
        button.setToolTipText(tip);
        Ui.toolbar(button);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void schedule() {
        if (!applying) {
            filterTimer.restart();
        }
    }

    private void showEmpty(String message, boolean offerOpen) {
        empty.setText("<html><div style='text-align:center;width:240px'>" + Ui.escape(message) + "</div></html>");
        cards.show(center, EMPTY);
        if (center.getComponentCount() > 1 && center.getComponent(1) instanceof JPanel panel && panel.getComponentCount() > 1) {
            panel.getComponent(1).setVisible(offerOpen);
        }
    }

    private DefaultMutableTreeNode build(TestNode node, String query, TestStatus status, boolean ancestorMatch) {
        boolean self = ancestorMatch || matches(node, query);
        if (node.kind() == TestKind.METHOD) {
            if (status != null && node.status() != status) {
                return null;
            }
            if (!query.isEmpty() && !self) {
                return null;
            }
            return new DefaultMutableTreeNode(node);
        }
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
        for (TestNode child : node.children()) {
            DefaultMutableTreeNode built = build(child, query, status, self && !query.isEmpty());
            if (built != null) {
                treeNode.add(built);
            }
        }
        if (treeNode.getChildCount() == 0 && node.kind() != TestKind.ROOT) {
            return null;
        }
        return treeNode;
    }

    private static boolean matches(TestNode node, String query) {
        if (query.isEmpty()) {
            return false;
        }
        String needle = query.toLowerCase();
        if (contains(node.displayName(), needle) || contains(node.name(), needle) || contains(node.fqcn(), needle)) {
            return true;
        }
        return node.kind() == TestKind.METHOD && contains(node.methodName(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private TestStatus selectedStatus() {
        return switch (statusFilter.getSelectedIndex()) {
            case 1 -> TestStatus.FAILED;
            case 2 -> TestStatus.PASSED;
            case 3 -> TestStatus.SKIPPED;
            case 4 -> TestStatus.NOT_RUN;
            default -> null;
        };
    }

    private void expandDefault() {
        int methods = workspace == null ? 0 : workspace.methodCount();
        if (methods > 0 && methods <= 40) {
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
            return;
        }
        tree.expandRow(0);
        for (int i = 0; i < tree.getRowCount(); i++) {
            TestNode node = nodeOf(tree.getPathForRow(i).getLastPathComponent());
            if (node != null && (node.kind() == TestKind.ROOT || node.kind() == TestKind.MODULE || node.kind() == TestKind.PACKAGE)) {
                tree.expandRow(i);
            }
        }
    }

    private void collapseAll() {
        for (int i = tree.getRowCount() - 1; i > 0; i--) {
            tree.collapseRow(i);
        }
    }

    private Set<String> expandedKeys() {
        Set<String> keys = new HashSet<>();
        Object root = model.getRoot();
        if (root instanceof DefaultMutableTreeNode treeNode) {
            collectExpanded(treeNode, new TreePath(treeNode), keys);
        }
        return keys;
    }

    private void collectExpanded(DefaultMutableTreeNode node, TreePath path, Set<String> keys) {
        if (tree.isExpanded(path)) {
            TestNode test = nodeOf(node);
            if (test != null) {
                keys.add(test.key());
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            collectExpanded(child, path.pathByAddingChild(child), keys);
        }
    }

    private void restore(Set<String> keys) {
        Object root = model.getRoot();
        if (root instanceof DefaultMutableTreeNode treeNode) {
            expandMatching(treeNode, new TreePath(treeNode), keys);
        }
    }

    private void expandMatching(DefaultMutableTreeNode node, TreePath path, Set<String> keys) {
        TestNode test = nodeOf(node);
        if (test != null && keys.contains(test.key())) {
            tree.expandPath(path);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            expandMatching(child, path.pathByAddingChild(child), keys);
        }
    }

    private void selectKey(String key) {
        Object root = model.getRoot();
        if (root instanceof DefaultMutableTreeNode treeNode) {
            TreePath path = find(treeNode, new TreePath(treeNode), key);
            if (path != null) {
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
            }
        }
    }

    private TreePath find(DefaultMutableTreeNode node, TreePath path, String key) {
        TestNode test = nodeOf(node);
        if (test != null && key.equals(test.key())) {
            return path;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            TreePath found = find(child, path.pathByAddingChild(child), key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String selectedKey() {
        TestNode node = selected();
        return node == null ? null : node.key();
    }

    private int scrollValue() {
        return treeScroll().getValue();
    }

    private javax.swing.JScrollBar treeScroll() {
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tree);
        return scroll == null ? new javax.swing.JScrollBar() : scroll.getVerticalScrollBar();
    }

    private TestNode nodeAt(MouseEvent event) {
        TreePath path = tree.getPathForLocation(event.getX(), event.getY());
        return path == null ? null : nodeOf(path.getLastPathComponent());
    }

    private static TestNode nodeOf(Object value) {
        if (value instanceof DefaultMutableTreeNode treeNode && treeNode.getUserObject() instanceof TestNode node) {
            return node;
        }
        return null;
    }

    private static int countMethods(DefaultMutableTreeNode node) {
        int count = nodeOf(node) != null && nodeOf(node).kind() == TestKind.METHOD ? 1 : 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            count += countMethods((DefaultMutableTreeNode) node.getChildAt(i));
        }
        return count;
    }

    private final class Renderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            TestNode node = nodeOf(value);
            if (node == null) {
                return this;
            }
            setIcon(AppIcon.status(node.status(), spin));
            String duration = node.durationLabel();
            if (selected || duration.isEmpty()) {
                setText(duration.isEmpty() ? node.displayName() : node.displayName() + "    " + duration);
            } else {
                setText("<html>" + Ui.escape(node.displayName())
                        + " <font color='#8B949E'>" + Ui.escape(duration) + "</font></html>");
            }
            setToolTipText(tooltip(node));
            return this;
        }
    }

    private static String tooltip(TestNode node) {
        String kind = switch (node.kind()) {
            case ROOT -> "プロジェクト";
            case MODULE -> "モジュール";
            case PACKAGE -> "パッケージ";
            case CLASS -> "クラス";
            case METHOD -> "テスト";
        };
        StringBuilder text = new StringBuilder(kind).append('\n').append(node.displayName());
        if (node.kind() == TestKind.METHOD) {
            text.append('\n').append(node.selector());
        } else if (node.kind() == TestKind.CLASS && node.fqcn() != null) {
            text.append('\n').append(node.fqcn());
        }
        text.append('\n').append(node.status().label());
        if (!node.durationLabel().isEmpty()) {
            text.append(" · ").append(node.durationLabel());
        }
        if (node.disabled() && node.ignoreReason() != null && !node.ignoreReason().isBlank()) {
            text.append("\n無効: ").append(node.ignoreReason());
        }
        if (node.sourceFile() != null && node.line() > 0) {
            text.append('\n').append(node.sourceFile().getFileName()).append(':').append(node.line());
        }
        return text.toString();
    }
}
