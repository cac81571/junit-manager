package dev.junitmanager.ui;

import dev.junitmanager.model.DurationFormat;
import dev.junitmanager.model.TestKind;
import dev.junitmanager.model.TestNode;
import dev.junitmanager.model.TestStatus;
import dev.junitmanager.model.Workspace;
import dev.junitmanager.run.ExecutionOrder;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
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
import java.awt.Dimension;
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
    private final DefaultTreeModel memberModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree memberTree = new JTree(memberModel);
    private final CardLayout memberCards = new CardLayout();
    private final JPanel memberCenter = new JPanel(memberCards);
    private final JSplitPane suiteSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    private final JTextField filter = new JTextField();
    private final JComboBox<String> statusFilter = new JComboBox<>(FILTERS);
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JTabbedPane tabs = new JTabbedPane();
    private final JPanel testTab = new JPanel(new BorderLayout());
    private final JPanel suiteTab = new JPanel(new BorderLayout());
    private final JPanel orderTab = new JPanel(new BorderLayout());
    private final JLabel memberHint = new JLabel("", JLabel.CENTER);
    private final JLabel memberNone = new JLabel("", JLabel.CENTER);
    private final JLabel empty = new JLabel("", JLabel.CENTER);
    private final JLabel project = new JLabel("プロジェクト未選択");
    private final JButton runAll;
    private final JButton runSelected;
    private final JButton runFailed;
    private final JButton debug;
    private final JButton stop;
    private final JButton refresh;
    private final JButton collapse;
    private final JButton clearResults;
    private final JToggleButton watch;
    private final Timer filterTimer = new Timer(120, event -> reload(false));
    private int spin;
    private final Timer spinTimer = new Timer(80, event -> {
        spin = (spin + 30) % 360;
        tree.repaint();
        memberTree.repaint();
    });
    private Workspace workspace;
    private boolean applying;
    private boolean physicalNames;
    private Page page = Page.TEST;
    private TestNode memberSelection;

    private enum Page {
        TEST, SUITE, ORDER
    }

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
        clearResults = button(AppIcon.Kind.CLEAR, "結果をクリア", actions::clearResults);
        watch = new JToggleButton(new AppIcon(AppIcon.Kind.WATCH));
        filterTimer.setRepeats(false);
        spinTimer.setRepeats(true);
        buildToolbar();
        buildTree(tree, false);
        buildTree(memberTree, true);
        buildMemberPane();
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
        testTab.setOpaque(false);
        suiteTab.setOpaque(false);
        orderTab.setOpaque(false);
        tabs.addTab("テスト", testTab);
        tabs.addTab("スイート", suiteTab);
        tabs.addTab("順序", orderTab);
        tabs.addChangeListener(event -> {
            if (applying) {
                return;
            }
            Page next = pageAt(tabs.getSelectedIndex());
            if (next == page) {
                return;
            }
            page = next;
            attachPages();
            reload(false);
            actions.selectNode(selected());
        });
        attachPages();
        add(tabs, BorderLayout.CENTER);
        showEmpty("Maven プロジェクトを開くと、テストがここに表示されます。", true);
        setBusy(false, false);
    }

    public JTextField filterField() {
        return filter;
    }

    public void setWatchSelected(boolean selected) {
        watch.setSelected(selected);
    }

    public void setPhysicalNames(boolean physicalNames) {
        this.physicalNames = physicalNames;
        remeasureRows();
    }

    public void applyTheme() {
        SwingUtilities.updateComponentTreeUI(suiteSplit);
        suiteSplit.setBorder(null);
    }

    private void remeasureRows() {
        int rowHeight = tree.getRowHeight();
        tree.setRowHeight(rowHeight == 0 ? 1 : 0);
        tree.setRowHeight(rowHeight);
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
        TestNode selectedNode = keepState ? selected() : null;
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
            boolean noSuites = !hasSuite(workspace.tree());
            boolean noOrders = !hasOrder(workspace.tree());
            boolean expectContent = switch (page) {
                case SUITE -> !noSuites;
                case ORDER -> !noOrders;
                case TEST -> !noTests;
            };
            boolean filteredOut = expectContent && (root == null || root.getChildCount() == 0);
            if (page == Page.SUITE && noSuites) {
                showEmpty("テストスイートは見つかりませんでした。", false);
                showMembers(null);
            } else if (page == Page.ORDER && noOrders) {
                showEmpty("@TestFactory の順序は見つかりませんでした。", false);
                showMembers(null);
            } else if (page == Page.TEST && noTests) {
                showEmpty("@Test の付いたメソッドは見つかりませんでした。", false);
            } else if (filteredOut) {
                showEmpty(switch (page) {
                    case SUITE -> "一致するスイートがありません。";
                    case ORDER -> "一致する順序がありません。";
                    case TEST -> "一致するテストがありません。";
                }, false);
                if (page != Page.TEST) {
                    showMembers(null);
                }
            } else {
                cards.show(center, TREE);
                if (keepState && !expanded.isEmpty()) {
                    restore(expanded);
                } else if (!keepState) {
                    expandDefault();
                } else {
                    restore(expanded);
                }
                if (selectedNode != null) {
                    restoreSelection(selectedNode);
                } else if (tree.getSelectionPath() == null && model.getRoot() != null) {
                    tree.setSelectionPath(new TreePath(model.getRoot()));
                    if (page != Page.TEST) {
                        showMembers(selectedFrom(tree));
                    }
                } else if (page != Page.TEST) {
                    showMembers(selectedFrom(tree));
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
        memberTree.repaint();
    }

    public TestNode selected() {
        if (page != Page.TEST && memberSelection != null) {
            return memberSelection;
        }
        return selectedFrom(tree);
    }

    public void select(TestNode node) {
        if (node == null) {
            return;
        }
        Page want = pageFor(node);
        if (page == Page.ORDER && orderRunner(node) != null) {
            want = Page.ORDER;
        }
        if (want != null && want != page) {
            page = want;
            applying = true;
            tabs.setSelectedIndex(indexOf(want));
            applying = false;
            attachPages();
            reload(false);
        }
        cards.show(center, TREE);
        applying = true;
        try {
            if (page == Page.SUITE && node.ancestor(TestKind.SUITE) != null && node.kind() != TestKind.SUITE) {
                TestNode suite = node.ancestor(TestKind.SUITE);
                selectIdentity(suite);
                showMembers(suite);
                selectMember(node);
            } else if (page == Page.ORDER && node.kind() != TestKind.CLASS) {
                TestNode runner = ExecutionOrder.isRunner(node.ancestor(TestKind.CLASS))
                        ? node.ancestor(TestKind.CLASS)
                        : orderRunner(node);
                if (runner != null) {
                    selectIdentity(runner);
                    showMembers(runner);
                    if (orderRunner(node) != null) {
                        selectMember(node);
                    }
                }
            } else {
                if (selectedFrom(tree) != node) {
                    selectIdentity(node);
                }
                if (page != Page.TEST) {
                    boolean show = node.kind() == TestKind.SUITE || ExecutionOrder.isRunner(node);
                    showMembers(show ? node : null);
                }
            }
        } finally {
            applying = false;
        }
    }

    private Page pageFor(TestNode node) {
        if (node.ancestor(TestKind.SUITE) != null) {
            return Page.SUITE;
        }
        TestNode type = node.kind() == TestKind.CLASS ? node : node.ancestor(TestKind.CLASS);
        if (ExecutionOrder.isRunner(type)) {
            return Page.ORDER;
        }
        if (node.kind() == TestKind.CLASS || node.kind() == TestKind.METHOD) {
            return Page.TEST;
        }
        return null;
    }

    private static Page pageAt(int index) {
        return switch (index) {
            case 1 -> Page.SUITE;
            case 2 -> Page.ORDER;
            default -> Page.TEST;
        };
    }

    private static int indexOf(Page page) {
        return switch (page) {
            case SUITE -> 1;
            case ORDER -> 2;
            case TEST -> 0;
        };
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
        clearResults.setEnabled(idle && hasProject);
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
            memberTree.repaint();
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
        for (JButton button : new JButton[]{runAll, runSelected, runFailed, debug, stop, refresh, clearResults, collapse}) {
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

    private void buildTree(JTree target, boolean members) {
        target.setRootVisible(!members);
        target.setShowsRootHandles(true);
        target.setRowHeight(0);
        target.setCellRenderer(new Renderer());
        ToolTipManager.sharedInstance().registerComponent(target);
        target.addTreeSelectionListener(event -> {
            if (applying) {
                return;
            }
            if (members) {
                TestNode node = selectedFrom(target);
                if (node == null) {
                    memberSelection = null;
                    return;
                }
                memberSelection = node;
                actions.selectNode(node);
                return;
            }
            if (page != Page.TEST) {
                showMembers(selectedFrom(target));
            }
            actions.selectNode(selected());
        });
        target.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (event.isPopupTrigger() || SwingUtilities.isRightMouseButton(event)) {
                    showPopup(target, event);
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    showPopup(target, event);
                }
            }
        });
    }

    private void buildMemberPane() {
        JLabel title = new JLabel("実行されるテスト");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        memberHint.setBorder(BorderFactory.createEmptyBorder(16, 12, 16, 12));
        memberNone.setBorder(BorderFactory.createEmptyBorder(16, 12, 16, 12));
        memberCenter.setOpaque(false);
        memberCenter.add(new JScrollPane(memberTree), TREE);
        memberCenter.add(memberHint, "hint");
        memberCenter.add(memberNone, "none");
        memberCards.show(memberCenter, "hint");
        JPanel members = new JPanel(new BorderLayout());
        members.setOpaque(false);
        members.add(title, BorderLayout.NORTH);
        members.add(memberCenter, BorderLayout.CENTER);
        suiteSplit.setTopComponent(new JPanel());
        suiteSplit.setBottomComponent(members);
        suiteSplit.setResizeWeight(0.45);
        suiteSplit.setContinuousLayout(true);
        suiteSplit.setBorder(null);
    }

    private void showMembers(TestNode selection) {
        boolean guarded = applying;
        applying = true;
        try {
            memberTree.clearSelection();
            memberSelection = null;
            if (page == Page.ORDER) {
                showOrderMembers(selection);
                return;
            }
            memberHint.setText("スイートを選ぶと、実行されるクラスとメソッドを表示します。");
            memberNone.setText("このスイートが実行するテストはありません。");
            TestNode suite = selection == null ? null : selection.kind() == TestKind.SUITE ? selection : selection.ancestor(TestKind.SUITE);
            if (suite == null) {
                memberModel.setRoot(new DefaultMutableTreeNode());
                memberCards.show(memberCenter, "hint");
                return;
            }
            String query = filter.getText().trim();
            boolean suiteMatch = query.isEmpty() || matches(suite, query);
            DefaultMutableTreeNode root = new DefaultMutableTreeNode(suite);
            for (TestNode child : suite.children()) {
                DefaultMutableTreeNode built = build(child, query, selectedStatus(), suiteMatch);
                if (built != null) {
                    root.add(built);
                }
            }
            memberModel.setRoot(root);
            if (root.getChildCount() == 0) {
                memberCards.show(memberCenter, "none");
                return;
            }
            memberCards.show(memberCenter, TREE);
            for (int i = 0; i < memberTree.getRowCount(); i++) {
                memberTree.expandRow(i);
            }
        } finally {
            if (!guarded) {
                applying = false;
            }
        }
    }

    private void showOrderMembers(TestNode selection) {
        memberHint.setText("順序を選ぶと、order.csv のテストを表示します。");
        memberNone.setText("order.csv に実行するテストはありません。");
        TestNode runner = selection != null && ExecutionOrder.isRunner(selection) ? selection : null;
        if (runner == null || workspace == null) {
            memberModel.setRoot(new DefaultMutableTreeNode());
            memberCards.show(memberCenter, "hint");
            return;
        }
        String query = filter.getText().trim();
        boolean runnerMatch = query.isEmpty() || matches(runner, query);
        TestStatus status = selectedStatus();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(runner);
        for (TestNode method : ExecutionOrder.targets(workspace, runner)) {
            TestNode type = method.ancestor(TestKind.CLASS);
            boolean nameOk = runnerMatch || matches(method, query) || (type != null && matches(type, query));
            boolean statusOk = status == null || method.status() == status;
            if (nameOk && statusOk) {
                root.add(new DefaultMutableTreeNode(method));
            }
        }
        memberModel.setRoot(root);
        if (root.getChildCount() == 0) {
            memberCards.show(memberCenter, "none");
            return;
        }
        memberCards.show(memberCenter, TREE);
        for (int i = 0; i < memberTree.getRowCount(); i++) {
            memberTree.expandRow(i);
        }
    }

    private TestNode orderRunner(TestNode node) {
        if (node == null || workspace == null || node.kind() != TestKind.METHOD) {
            return null;
        }
        TestNode[] found = new TestNode[1];
        workspace.tree().walk(candidate -> {
            if (found[0] == null && ExecutionOrder.isRunner(candidate) && ExecutionOrder.targets(workspace, candidate).contains(node)) {
                found[0] = candidate;
            }
        });
        return found[0];
    }

    private void restoreSelection(TestNode node) {
        TestNode suite = node.ancestor(TestKind.SUITE);
        if (page == Page.SUITE && suite != null && node.kind() != TestKind.SUITE) {
            selectIdentity(suite);
            showMembers(suite);
            selectMember(node);
            return;
        }
        if (page == Page.ORDER && node.kind() != TestKind.CLASS) {
            TestNode runner = ExecutionOrder.isRunner(node.ancestor(TestKind.CLASS))
                    ? node.ancestor(TestKind.CLASS)
                    : orderRunner(node);
            if (runner != null) {
                selectIdentity(runner);
                showMembers(runner);
                if (orderRunner(node) != null) {
                    selectMember(node);
                }
                return;
            }
        }
        selectIdentity(node);
        if (page == Page.SUITE) {
            showMembers(node.kind() == TestKind.SUITE ? node : null);
        } else if (page == Page.ORDER) {
            showMembers(ExecutionOrder.isRunner(node) ? node : null);
        }
    }

    private void selectMember(TestNode target) {
        Object root = memberModel.getRoot();
        memberSelection = null;
        if (!(root instanceof DefaultMutableTreeNode treeNode)) {
            return;
        }
        TreePath path = findIdentity(treeNode, new TreePath(treeNode), target);
        if (path == null) {
            return;
        }
        memberSelection = target;
        memberTree.setSelectionPath(path);
        memberTree.scrollPathToVisible(path);
    }

    private void showPopup(JTree target, MouseEvent event) {
        TreePath path = target.getPathForLocation(event.getX(), event.getY());
        if (path == null) {
            return;
        }
        target.setSelectionPath(path);
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
        if (node.kind() == TestKind.METHOD || node.kind() == TestKind.CLASS || node.kind() == TestKind.SUITE) {
            item(menu, "セレクタをコピー", () -> actions.copySelector(node));
        }
        item(menu, "mvn test をコピー", () -> actions.copyTestCommand(node));
        item(menu, "フォルダを開く", () -> actions.reveal(node));
        menu.show(target, event.getX(), event.getY());
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
        if (page != Page.SUITE && node.kind() == TestKind.SUITE) {
            return null;
        }
        if (page == Page.TEST && ExecutionOrder.isRunner(node)) {
            return null;
        }
        if (page == Page.SUITE && node.ancestor(TestKind.SUITE) == null && node.kind() != TestKind.ROOT
                && node.kind() != TestKind.MODULE && node.kind() != TestKind.PACKAGE && node.kind() != TestKind.SUITE) {
            return null;
        }
        if (page == Page.ORDER && node.kind() != TestKind.ROOT && node.kind() != TestKind.MODULE
                && node.kind() != TestKind.PACKAGE && !ExecutionOrder.isRunner(node)) {
            return null;
        }
        if (page == Page.SUITE && node.kind() == TestKind.SUITE) {
            if (!includeSuite(node, query, status, ancestorMatch)) {
                return null;
            }
            return new DefaultMutableTreeNode(node);
        }
        if (page == Page.ORDER && ExecutionOrder.isRunner(node)) {
            if (!includeOrder(node, query, status, ancestorMatch)) {
                return null;
            }
            return new DefaultMutableTreeNode(node);
        }
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
        if (treeNode.getChildCount() == 0 && node.kind() != TestKind.ROOT && node.kind() != TestKind.SUITE) {
            if (node.parent() == null || node.parent().kind() != TestKind.SUITE) {
                return null;
            }
        }
        return treeNode;
    }

    private boolean includeSuite(TestNode suite, String query, TestStatus status, boolean ancestorMatch) {
        if (query.isEmpty() && status == null) {
            return true;
        }
        if ((ancestorMatch || matches(suite, query)) && (status == null || suiteHasStatus(suite, status))) {
            return true;
        }
        for (TestNode child : suite.children()) {
            if (child.kind() == TestKind.METHOD) {
                boolean nameOk = query.isEmpty() || matches(child, query);
                boolean statusOk = status == null || child.status() == status;
                if (nameOk && statusOk) {
                    return true;
                }
            } else if (includeSuite(child, query, status, false)) {
                return true;
            }
        }
        return false;
    }

    private boolean includeOrder(TestNode runner, String query, TestStatus status, boolean ancestorMatch) {
        if (query.isEmpty() && status == null) {
            return true;
        }
        if ((ancestorMatch || matches(runner, query)) && (status == null || runner.status() == status)) {
            return true;
        }
        if (workspace == null) {
            return false;
        }
        for (TestNode method : ExecutionOrder.targets(workspace, runner)) {
            TestNode type = method.ancestor(TestKind.CLASS);
            boolean nameOk = query.isEmpty() || matches(method, query) || matches(runner, query) || (type != null && matches(type, query));
            boolean statusOk = status == null || method.status() == status || runner.status() == status;
            if (nameOk && statusOk) {
                return true;
            }
        }
        return false;
    }

    private static boolean suiteHasStatus(TestNode node, TestStatus status) {
        if (node.kind() == TestKind.METHOD) {
            return node.status() == status;
        }
        for (TestNode child : node.children()) {
            if (suiteHasStatus(child, status)) {
                return true;
            }
        }
        return false;
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

    private void selectIdentity(TestNode target) {
        Object root = model.getRoot();
        if (!(root instanceof DefaultMutableTreeNode treeNode)) {
            return;
        }
        TreePath path = findIdentity(treeNode, new TreePath(treeNode), target);
        if (path == null) {
            path = find(treeNode, new TreePath(treeNode), target.key());
        }
        if (path != null) {
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
    }

    private TreePath findIdentity(DefaultMutableTreeNode node, TreePath path, TestNode target) {
        if (nodeOf(node) == target) {
            return path;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            TreePath found = findIdentity(child, path.pathByAddingChild(child), target);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void attachPages() {
        if (center.getParent() != null) {
            center.getParent().remove(center);
        }
        if (page == Page.TEST) {
            testTab.add(center, BorderLayout.CENTER);
        } else {
            JPanel host = page == Page.SUITE ? suiteTab : orderTab;
            suiteSplit.setTopComponent(center);
            if (suiteSplit.getParent() != host) {
                if (suiteSplit.getParent() instanceof JPanel previous) {
                    previous.remove(suiteSplit);
                }
                host.add(suiteSplit, BorderLayout.CENTER);
            }
            if (suiteSplit.getDividerLocation() <= 0) {
                SwingUtilities.invokeLater(() -> suiteSplit.setDividerLocation(0.42));
            }
        }
        tabs.revalidate();
        tabs.repaint();
    }

    private static boolean hasSuite(TestNode node) {
        if (node.kind() == TestKind.SUITE) {
            return true;
        }
        for (TestNode child : node.children()) {
            if (hasSuite(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOrder(TestNode node) {
        if (ExecutionOrder.isRunner(node)) {
            return true;
        }
        for (TestNode child : node.children()) {
            if (hasOrder(child)) {
                return true;
            }
        }
        return false;
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

    private int scrollValue() {
        return treeScroll().getValue();
    }

    private javax.swing.JScrollBar treeScroll() {
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tree);
        return scroll == null ? new javax.swing.JScrollBar() : scroll.getVerticalScrollBar();
    }

    private static TestNode selectedFrom(JTree target) {
        TreePath path = target.getSelectionPath();
        return path == null ? null : nodeOf(path.getLastPathComponent());
    }

    private static TestNode nodeOf(Object value) {
        if (value instanceof DefaultMutableTreeNode treeNode && treeNode.getUserObject() instanceof TestNode node) {
            return node;
        }
        return null;
    }

    private int countMethods(DefaultMutableTreeNode node) {
        TestNode test = nodeOf(node);
        if (page == Page.SUITE && test != null && test.kind() == TestKind.SUITE) {
            return test.methodCount();
        }
        if (page == Page.ORDER && test != null && ExecutionOrder.isRunner(test) && workspace != null) {
            return ExecutionOrder.targets(workspace, test).size();
        }
        int count = test != null && test.kind() == TestKind.METHOD ? 1 : 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            count += countMethods((DefaultMutableTreeNode) node.getChildAt(i));
        }
        return count;
    }

    private final class Renderer extends DefaultTreeCellRenderer {
        private String plain = "";

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            plain = "";
            TestNode node = nodeOf(value);
            if (node == null) {
                return this;
            }
            TestStatus shown = displayedStatus(tree, node);
            setIcon(AppIcon.status(shown, spin));
            String label = treeLabel(tree, node);
            String order = node.hasOrder() ? Integer.toString(node.order()) : "";
            String duration = displayedDuration(tree, node);
            plain = plainLabel(order, label, duration);
            if (selected || (order.isEmpty() && duration.isEmpty())) {
                setText(plain);
            } else {
                StringBuilder html = new StringBuilder("<html>");
                if (!order.isEmpty()) {
                    html.append("<font color='#8B949E'>").append(Ui.escape(order)).append("</font> ");
                }
                html.append(Ui.escape(label));
                if (!duration.isEmpty()) {
                    html.append(" <font color='#8B949E'>").append(Ui.escape(duration)).append("</font>");
                }
                html.append("</html>");
                setText(html.toString());
            }
            setToolTipText(tooltip(node, shown, duration));
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            String shown = getText();
            if (shown == null || !shown.startsWith("<html>") || plain.isEmpty()) {
                return super.getPreferredSize();
            }
            setText(plain);
            Dimension size = super.getPreferredSize();
            setText(shown);
            return size;
        }

        private static String plainLabel(String order, String label, String duration) {
            StringBuilder text = new StringBuilder();
            if (!order.isEmpty()) {
                text.append(order).append("  ");
            }
            text.append(label);
            if (!duration.isEmpty()) {
                text.append("    ").append(duration);
            }
            return text.toString();
        }
    }

    private String treeLabel(JTree source, TestNode node) {
        String label = physicalNames ? node.name() : node.displayName();
        if (source == memberTree && page == Page.ORDER && node.kind() == TestKind.METHOD && node.fqcn() != null) {
            int split = Math.max(node.fqcn().lastIndexOf('.'), node.fqcn().lastIndexOf('$'));
            String type = split >= 0 ? node.fqcn().substring(split + 1) : node.fqcn();
            return type + " · " + label;
        }
        return label;
    }

    private TestStatus displayedStatus(JTree source, TestNode node) {
        if (source == tree && page == Page.SUITE && suiteContainer(node)) {
            return node.statusAmongSuites();
        }
        if (source == tree && page == Page.ORDER && suiteContainer(node)) {
            return statusAmongOrders(node);
        }
        return node.status();
    }

    private String displayedDuration(JTree source, TestNode node) {
        if (source == tree && page == Page.SUITE && suiteContainer(node)) {
            return DurationFormat.of(node.durationAmongSuites());
        }
        if (source == tree && page == Page.ORDER && suiteContainer(node)) {
            return DurationFormat.of(durationAmongOrders(node));
        }
        return node.durationLabel();
    }

    private TestStatus statusAmongOrders(TestNode node) {
        if (ExecutionOrder.isRunner(node) || node.status() == TestStatus.RUNNING) {
            return node.status();
        }
        boolean failed = false;
        boolean notRun = false;
        boolean passed = false;
        boolean skipped = false;
        boolean any = false;
        for (TestNode child : node.children()) {
            if (child.kind() == TestKind.SUITE || child.kind() == TestKind.METHOD) {
                continue;
            }
            if (child.kind() == TestKind.CLASS && !ExecutionOrder.isRunner(child)) {
                continue;
            }
            any = true;
            switch (statusAmongOrders(child)) {
                case FAILED -> failed = true;
                case RUNNING -> {
                }
                case NOT_RUN -> notRun = true;
                case PASSED -> passed = true;
                case SKIPPED -> skipped = true;
            }
        }
        if (!any) {
            return TestStatus.NOT_RUN;
        }
        if (failed) {
            return TestStatus.FAILED;
        }
        if (notRun) {
            return TestStatus.NOT_RUN;
        }
        if (passed) {
            return TestStatus.PASSED;
        }
        if (skipped) {
            return TestStatus.SKIPPED;
        }
        return TestStatus.NOT_RUN;
    }

    private long durationAmongOrders(TestNode node) {
        if (ExecutionOrder.isRunner(node)) {
            return node.durationMillis();
        }
        long duration = 0;
        boolean any = false;
        boolean complete = true;
        for (TestNode child : node.children()) {
            if (child.kind() == TestKind.SUITE || child.kind() == TestKind.METHOD) {
                continue;
            }
            if (child.kind() == TestKind.CLASS && !ExecutionOrder.isRunner(child)) {
                continue;
            }
            long childDuration = durationAmongOrders(child);
            if (childDuration < 0) {
                complete = false;
                continue;
            }
            any = true;
            duration += childDuration;
        }
        return any && complete ? duration : -1;
    }

    private static boolean suiteContainer(TestNode node) {
        return node.kind() == TestKind.ROOT || node.kind() == TestKind.MODULE || node.kind() == TestKind.PACKAGE;
    }

    private String tooltip(TestNode node, TestStatus status, String duration) {
        String kind = switch (node.kind()) {
            case ROOT -> "プロジェクト";
            case MODULE -> "モジュール";
            case PACKAGE -> "パッケージ";
            case SUITE -> "スイート";
            case CLASS -> ExecutionOrder.isRunner(node) ? "順序" : "クラス";
            case METHOD -> "テスト";
        };
        StringBuilder text = new StringBuilder(kind).append('\n').append(physicalNames ? node.name() : node.displayName());
        if (!node.name().equals(node.displayName())) {
            text.append('\n').append(physicalNames ? node.displayName() : node.name());
        }
        if (node.kind() == TestKind.METHOD) {
            text.append('\n').append(node.selector());
        } else if ((node.kind() == TestKind.CLASS || node.kind() == TestKind.SUITE) && node.fqcn() != null) {
            text.append('\n').append(node.fqcn());
        }
        if (node.hasOrder()) {
            text.append('\n').append("@Order ").append(node.order());
        }
        text.append('\n').append(status.label());
        if (!duration.isEmpty()) {
            text.append(" · ").append(duration);
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
