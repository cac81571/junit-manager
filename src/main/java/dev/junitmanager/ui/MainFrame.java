package dev.junitmanager.ui;

import dev.junitmanager.discover.TestDiscovery;
import dev.junitmanager.model.TestKind;
import dev.junitmanager.model.TestNode;
import dev.junitmanager.model.TestStatus;
import dev.junitmanager.model.Workspace;
import dev.junitmanager.run.MavenCommands;
import dev.junitmanager.run.MavenRunner;
import dev.junitmanager.run.ResultApplier;
import dev.junitmanager.run.RunPlans;
import dev.junitmanager.run.SourceWatcher;
import dev.junitmanager.settings.UserSettings;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.Desktop;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MainFrame extends JFrame implements UserActions {
    private final UserSettings settings = UserSettings.load();
    private final MavenRunner runner = new MavenRunner();
    private final SourceWatcher watcher = new SourceWatcher();
    private final TestExplorerPanel explorer = new TestExplorerPanel(this);
    private final TestDetailPanel detail = new TestDetailPanel(this);
    private final OutputPanel output = new OutputPanel(this::clearOutput);
    private final JSplitPane vertical;
    private final JSplitPane split;
    private final JLabel status = new JLabel("Maven プロジェクトを開いてください");
    private final JProgressBar progress = new JProgressBar();
    private final JMenu recentMenu = new JMenu("最近使ったプロジェクト");
    private final SourceDialog sourceDialog = new SourceDialog(this);
    private Workspace workspace;
    private RunPlans.Spec lastSpec = RunPlans.Spec.all();
    private int discoveryGeneration;
    private boolean discovering;
    private boolean running;
    private boolean watchPending;
    private boolean selecting;

    public MainFrame() {
        super("JUnit Manager");
        ThemeManager.install(settings.dark());
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(860, 560));
        vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, detail, output);
        vertical.setResizeWeight(0.62);
        vertical.setContinuousLayout(true);
        vertical.setBorder(null);
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, explorer, vertical);
        split.setResizeWeight(0);
        split.setContinuousLayout(true);
        split.setBorder(null);
        progress.setIndeterminate(true);
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(120, 8));
        status.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(Ui.topLine());
        statusBar.add(status, BorderLayout.CENTER);
        statusBar.add(progress, BorderLayout.EAST);
        setJMenuBar(menuBar());
        add(split, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        installDrop();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                shutdown();
            }
        });
        restoreBounds();
        SwingUtilities.invokeLater(() -> {
            split.setDividerLocation(settings.dividerMain());
            vertical.setDividerLocation(settings.dividerVertical());
        });
        explorer.setWatchSelected(settings.watch());
        explorer.setPhysicalNames(settings.physicalNames());
        setIconImages(windowIcons());
        SwingUtilities.updateComponentTreeUI(this);
        explorer.applyTheme();
        sourceDialog.applyTheme();
        output.applyTheme();
        updateActions();
    }

    public void restoreLastProject() {
        Path last = settings.lastProject();
        if (last != null && Files.isDirectory(last)) {
            openProject(last);
        }
    }

    public void openProject(Path path) {
        openProject(path, null);
    }

    public void openProject(Path path, Runnable done) {
        Path project = normalizeProject(path);
        if (project == null) {
            setPlainStatus("pom.xml があるフォルダを選んでください");
            if (done != null) {
                done.run();
            }
            return;
        }
        if (runner.running()) {
            runner.stop();
        }
        watcher.stop();
        settings.remember(project);
        settings.save();
        rebuildRecent();
        int generation = ++discoveryGeneration;
        discovering = true;
        updateActions();
        setPlainStatus("テストを検出しています…");
        progress.setVisible(true);
        new SwingWorker<Workspace, Void>() {
            @Override
            protected Workspace doInBackground() throws Exception {
                return TestDiscovery.discover(project);
            }

            @Override
            protected void done() {
                if (generation != discoveryGeneration) {
                    if (done != null) {
                        done.run();
                    }
                    return;
                }
                discovering = false;
                progress.setVisible(running);
                try {
                    Workspace loaded = get();
                    if (workspace != null && workspace.root().equals(loaded.root())) {
                        loaded.restoreResults(workspace.snapshotResults());
                    }
                    workspace = loaded;
                    explorer.setWorkspace(workspace);
                    detail.show(explorer.selected() == null ? workspace.tree() : explorer.selected(), workspace);
                    for (String warning : loaded.warnings()) {
                        output.append(warning + "\n");
                    }
                    setTitle("JUnit Manager — " + loaded.name());
                    showSummary();
                    if (settings.watch()) {
                        restartWatch();
                    }
                } catch (Exception ex) {
                    setPlainStatus("検出に失敗しました: " + rootMessage(ex));
                }
                updateActions();
                if (done != null) {
                    done.run();
                }
            }
        }.execute();
    }

    public int methodCount() {
        return workspace == null ? 0 : workspace.methodCount();
    }

    @Override
    public void openProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Maven プロジェクトを開く");
        Path last = settings.lastProject();
        if (last != null && last.getParent() != null && Files.isDirectory(last.getParent())) {
            chooser.setCurrentDirectory(last.getParent().toFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openProject(chooser.getSelectedFile().toPath());
        }
    }

    @Override
    public void runAll() {
        run(RunPlans.Spec.all(), false);
    }

    @Override
    public void runSelected() {
        TestNode node = explorer.selected();
        if (node == null) {
            setPlainStatus("テストを選択してください");
            return;
        }
        run(RunPlans.Spec.node(node), false);
    }

    @Override
    public void runFailed() {
        run(RunPlans.Spec.failed(), false);
    }

    @Override
    public void debugSelected() {
        TestNode node = explorer.selected();
        run(node == null ? RunPlans.Spec.all() : RunPlans.Spec.node(node), true);
    }

    @Override
    public void stop() {
        if (runner.running()) {
            runner.stop();
            setPlainStatus("停止しています…");
        }
    }

    @Override
    public void refresh() {
        if (workspace == null || discovering || running) {
            return;
        }
        openProject(workspace.root());
    }

    @Override
    public void watch(boolean enabled) {
        settings.setWatch(enabled);
        settings.save();
        explorer.setWatchSelected(enabled);
        if (enabled) {
            restartWatch();
            setPlainStatus("ソースの変更を監視しています");
        } else {
            watcher.stop();
            watchPending = false;
            showSummary();
        }
    }

    @Override
    public void runNode(TestNode node, boolean debug) {
        if (node == null) {
            return;
        }
        run(RunPlans.Spec.node(node), debug);
    }

    @Override
    public void openSource(TestNode node) {
        if (node == null) {
            return;
        }
        Path file = node.sourceFile();
        if (file == null && node.fqcn() != null) {
            file = Sources.find(workspace, node.fqcn(), null);
        }
        if (file == null || !Files.isRegularFile(file)) {
            setPlainStatus("ソースが見つかりません");
            return;
        }
        showSource(file, Math.max(1, node.line()));
    }

    @Override
    public void openStackLine(String line) {
        Sources.StackFrame frame = Sources.parse(line);
        if (frame == null || workspace == null) {
            return;
        }
        Path file = Sources.find(workspace, frame.className(), frame.fileName());
        if (file == null) {
            setPlainStatus("プロジェクト内にソースがありません: " + frame.fileName() + ":" + frame.line());
            return;
        }
        showSource(file, frame.line());
    }

    private void showSource(Path file, int line) {
        int row = Math.max(1, line);
        if (!settings.externalEditor()) {
            sourceDialog.showFile(file, row);
            return;
        }
        if (settings.editorCommand().isBlank()) {
            setPlainStatus("外部エディタのコマンドが未設定です");
            sourceDialog.showFile(file, row);
            return;
        }
        try {
            ExternalEditor.open(settings.editorCommand(), file, row);
            setPlainStatus("外部エディタで開きました: " + file.getFileName() + ":" + row);
        } catch (IOException ex) {
            setPlainStatus("外部エディタを起動できません: " + ex.getMessage());
            sourceDialog.showFile(file, row);
        }
    }

    @Override
    public void copySelector(TestNode node) {
        if (node == null || (node.kind() != TestKind.METHOD && node.kind() != TestKind.CLASS && node.kind() != TestKind.SUITE)) {
            return;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(node.selector()), null);
            setPlainStatus("コピーしました: " + node.selector());
        } catch (IllegalStateException ex) {
            setPlainStatus("クリップボードを使えませんでした");
        }
    }

    @Override
    public void copyTestCommand(TestNode node) {
        if (workspace == null) {
            return;
        }
        RunPlans.Spec spec = node == null ? RunPlans.Spec.all() : RunPlans.Spec.node(node);
        RunPlans.Plan plan = RunPlans.build(workspace, spec, false);
        if (plan == null) {
            setPlainStatus("コピーできる mvn test コマンドがありません");
            return;
        }
        String command = MavenCommands.clipboardCommand(plan.mavenArgs());
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(command), null);
            setPlainStatus("mvn test コマンドをコピーしました");
        } catch (IllegalStateException ex) {
            setPlainStatus("クリップボードを使えませんでした");
        }
    }

    @Override
    public void reveal(TestNode node) {
        Path path = node == null ? null : node.sourceFile();
        if (path == null && node != null) {
            path = node.moduleDir();
        }
        if (path == null && workspace != null) {
            path = workspace.root();
        }
        if (path == null) {
            return;
        }
        Path folder = Files.isDirectory(path) ? path : path.getParent();
        try {
            Desktop.getDesktop().open(folder.toFile());
        } catch (Exception ex) {
            setPlainStatus("フォルダを開けません: " + folder);
        }
    }

    @Override
    public void selectNode(TestNode node) {
        if (node == null || selecting) {
            return;
        }
        selecting = true;
        try {
            explorer.select(node);
            detail.show(node, workspace);
        } finally {
            selecting = false;
        }
    }

    private TestNode runTarget(RunPlans.Spec spec) {
        if (workspace == null || spec == null) {
            return null;
        }
        return switch (spec.kind()) {
            case NODE -> workspace.findNode(spec.nodeKey());
            case ALL -> workspace.tree();
            case FAILED -> null;
        };
    }

    private void run(RunPlans.Spec spec, boolean debug) {
        if (workspace == null || discovering || running) {
            return;
        }
        RunPlans.Plan plan = RunPlans.build(workspace, spec, debug);
        if (plan == null) {
            setPlainStatus(spec.kind() == RunPlans.Kind.FAILED ? "失敗したテストはありません" : "実行するテストがありません");
            return;
        }
        lastSpec = spec;
        ResultApplier.markRunning(plan.scope(), plan.clearSynthetics(), plan.suite(), runTarget(spec));
        explorer.reload(true);
        detail.refresh();
        running = true;
        updateActions();
        progress.setVisible(true);
        output.append("\n── " + plan.label() + (debug ? "（デバッグ）" : "") + " ──\n");
        if (debug) {
            output.append("デバッガの待ち受け: ポート 5005（suspend=y）。IDE のリモートデバッグで接続してください。\n");
        }
        if (plan.broadened()) {
            output.append("セレクタが長いため、対象を広げて実行します。\n");
        }
        MavenCommands.Launch launch = MavenCommands.launch(workspace.root(), plan.workingDirectory(), plan.mavenArgs());
        output.append(launch.display() + "\n");
        setPlainStatus("実行中 · " + plan.label());
        runner.start(launch, plan.reportModules(), output::append, (result, error) -> finishRun(plan, result, error));
    }

    private void finishRun(RunPlans.Plan plan, MavenRunner.RunResult result, Throwable error) {
        running = false;
        progress.setVisible(discovering);
        if (workspace == null) {
            updateActions();
            return;
        }
        if (error != null) {
            String message = error instanceof IOException io ? MavenCommands.explain(io) : rootMessage(error);
            output.append(message + "\n");
            ResultApplier.apply(workspace, plan.scope(), List.of(), plan.suite());
            explorer.reload(true);
            detail.refresh();
            setPlainStatus(message);
            updateActions();
            return;
        }
        ResultApplier.apply(workspace, plan.scope(), result.tests(), plan.suite());
        explorer.reload(true);
        detail.refresh();
        if (result.cancelled()) {
            output.append("停止しました。\n");
        } else if (result.tests().isEmpty() && result.exitCode() != 0) {
            output.append("ビルドに失敗しました。上のログを確認してください。\n");
        }
        showSummary();
        updateActions();
        if (watchPending && settings.watch()) {
            watchPending = false;
            rerunAfterChange();
        }
    }

    private void rerunAfterChange() {
        if (workspace == null || discovering || running) {
            watchPending = true;
            return;
        }
        RunPlans.Spec spec = lastSpec == null ? RunPlans.Spec.all() : lastSpec;
        openProject(workspace.root(), () -> run(spec, false));
    }

    private void restartWatch() {
        if (workspace == null) {
            return;
        }
        watcher.start(workspace.sourceRoots(), () -> {
            if (!settings.watch()) {
                return;
            }
            if (discovering || running) {
                watchPending = true;
                return;
            }
            rerunAfterChange();
        }, this::setPlainStatus);
    }

    private void showSummary() {
        if (workspace == null) {
            setPlainStatus("Maven プロジェクトを開いてください");
            return;
        }
        int total = workspace.methodCount();
        int passed = workspace.tree().count(TestStatus.PASSED);
        int failed = workspace.tree().count(TestStatus.FAILED);
        int skipped = workspace.tree().count(TestStatus.SKIPPED);
        int notRun = workspace.tree().count(TestStatus.NOT_RUN);
        int showing = explorer.showingCount();
        String visible = showing == total ? "" : " · 表示 " + showing;
        String failedText = failed > 0
                ? " · 失敗 <font color='#F85149'>" + failed + "</font>"
                : " · 失敗 0";
        status.setText("<html>" + Ui.escape(workspace.name())
                + " · テスト " + total
                + " · 成功 " + passed
                + failedText
                + " · スキップ " + skipped
                + " · 未実行 " + notRun
                + visible
                + "</html>");
    }

    private void setPlainStatus(String text) {
        status.setText(text == null ? "" : text);
    }

    private void updateActions() {
        explorer.setBusy(discovering, running);
    }

    @Override
    public void clearResults() {
        if (workspace == null || discovering || running) {
            return;
        }
        workspace.clearResults();
        explorer.reload(true);
        TestNode selected = explorer.selected();
        detail.show(selected == null ? workspace.tree() : selected, workspace);
        showSummary();
    }

    private void clearOutput() {
        output.clear();
    }

    private JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("ファイル");
        file.add(item("プロジェクトを開く", KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcut()), this::openProject));
        file.add(recentMenu);
        file.addSeparator();
        file.add(item("終了", null, this::shutdown));
        JMenu run = new JMenu("実行");
        run.add(item("すべて実行", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), this::runAll));
        run.add(item("選択を実行", KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), this::runSelected));
        run.add(item("失敗を再実行", KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), this::runFailed));
        run.add(item("デバッグ", KeyStroke.getKeyStroke(KeyEvent.VK_F5, shortcut()), this::debugSelected));
        run.add(item("停止", KeyStroke.getKeyStroke(KeyEvent.VK_F5, KeyEvent.SHIFT_DOWN_MASK), this::stop));
        run.addSeparator();
        run.add(item("結果をクリア", null, this::clearResults));
        JMenu view = new JMenu("表示");
        view.add(item("再検出", KeyStroke.getKeyStroke(KeyEvent.VK_R, shortcut() | KeyEvent.SHIFT_DOWN_MASK), this::refresh));
        view.add(item("フィルター", KeyStroke.getKeyStroke(KeyEvent.VK_F, shortcut()), () -> explorer.filterField().requestFocusInWindow()));
        JMenuItem theme = new JMenuItem(settings.dark() ? "ライトテーマ" : "ダークテーマ");
        theme.addActionListener(event -> {
            settings.setDark(!settings.dark());
            settings.save();
            ThemeManager.install(settings.dark());
            SwingUtilities.updateComponentTreeUI(this);
            explorer.applyTheme();
            sourceDialog.applyTheme();
            output.applyTheme();
            split.setBorder(null);
            vertical.setBorder(null);
            theme.setText(settings.dark() ? "ライトテーマ" : "ダークテーマ");
            showSummary();
        });
        view.add(theme);
        view.addSeparator();
        ButtonGroup names = new ButtonGroup();
        JRadioButtonMenuItem displayNames = new JRadioButtonMenuItem("表示名 (@DisplayName)", !settings.physicalNames());
        JRadioButtonMenuItem physicalNames = new JRadioButtonMenuItem("物理名 (クラス名・メソッド名)", settings.physicalNames());
        names.add(displayNames);
        names.add(physicalNames);
        displayNames.addActionListener(event -> applyTreeLabels(false));
        physicalNames.addActionListener(event -> applyTreeLabels(true));
        view.add(displayNames);
        view.add(physicalNames);
        view.addSeparator();
        JCheckBoxMenuItem external = new JCheckBoxMenuItem("外部エディタで開く", settings.externalEditor());
        external.addActionListener(event -> {
            if (external.isSelected() && settings.editorCommand().isBlank() && !editEditorCommand()) {
                external.setSelected(false);
                return;
            }
            if (settings.editorCommand().isBlank()) {
                external.setSelected(false);
            }
            settings.setExternalEditor(external.isSelected());
            settings.save();
        });
        view.add(external);
        view.add(item("外部エディタを設定...", null, () -> {
            if (!editEditorCommand()) {
                return;
            }
            boolean enabled = !settings.editorCommand().isBlank();
            settings.setExternalEditor(enabled);
            external.setSelected(enabled);
            settings.save();
        }));
        view.add(item("出力をクリア", null, this::clearOutput));
        JMenu help = new JMenu("ヘルプ");
        help.add(item("使い方", null, this::showHelp));
        bar.add(file);
        bar.add(run);
        bar.add(view);
        bar.add(help);
        rebuildRecent();
        return bar;
    }

    private void applyTreeLabels(boolean physical) {
        settings.setPhysicalNames(physical);
        settings.save();
        explorer.setPhysicalNames(physical);
    }

    private boolean editEditorCommand() {
        JTextField field = new JTextField(settings.editorCommand(), 42);
        JButton browse = new JButton("参照...");
        browse.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("外部エディタを選択");
            chooser.setFileFilter(new FileNameExtensionFilter("プログラム (*.exe)", "exe"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            String path = chooser.getSelectedFile().getAbsolutePath();
            if (path.indexOf(' ') >= 0) {
                path = "\"" + path + "\"";
            }
            field.setText(path + " \"{file}\" -Y={line}");
        });
        JPanel command = new JPanel(new BorderLayout(8, 0));
        command.add(field, BorderLayout.CENTER);
        command.add(browse, BorderLayout.EAST);
        JLabel hint = new JLabel("<html>{file} にファイル、{line} に行番号が入ります。<br>サクラエディタは -Y={line} でその行を開きます。</html>");
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        hint.setAlignmentX(0);
        command.setAlignmentX(0);
        form.add(hint);
        form.add(Box.createVerticalStrut(8));
        form.add(command);
        int choice = JOptionPane.showConfirmDialog(this, form, "外部エディタ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        settings.setEditorCommand(field.getText());
        settings.save();
        return true;
    }

    private void rebuildRecent() {
        recentMenu.removeAll();
        List<Path> recent = settings.recent();
        if (recent.isEmpty()) {
            JMenuItem empty = new JMenuItem("履歴はありません");
            empty.setEnabled(false);
            recentMenu.add(empty);
            return;
        }
        for (Path path : recent) {
            JMenuItem item = new JMenuItem(path.getFileName() == null ? path.toString() : path.getFileName().toString());
            item.setToolTipText(path.toString());
            item.addActionListener(event -> openProject(path));
            recentMenu.add(item);
        }
    }

    private void showHelp() {
        JOptionPane.showMessageDialog(this, """
                JUnit Manager は Maven プロジェクトの JUnit を、VS Code のテストタブのように一覧して実行します。

                1. プロジェクトを開くで、pom.xml があるフォルダを選びます。フォルダのドロップもできます。
                2. 「テスト」タブに @Test などが並びます。「スイート」タブには @Suite のクラスだけが出て、実行されるクラスとメソッドは下の枠です。「順序」タブには @TestFactory の実行順が出て、order.csv のテストは下の枠です。表示メニューで、@DisplayName とクラス名・メソッド名を切り替えられます。
                3. 実行すると mvn test が走り、失敗のメッセージとスタックトレースを表示します。実行メニューの「結果をクリア」で、成功・失敗の表示を未実行に戻せます。
                4. スタックトレースの行をクリックすると、そのソースを開きます。表示メニューで外部エディタ（サクラエディタなど）に切り替えられます。
                5. 変更を監視すると、ソース保存後に直前のテストを再実行します。
                6. デバッグはポート 5005 で待ち受けます。IDE のリモートデバッグから接続してください。

                検出対象は JUnit 4（@Test @Theory @Ignore）と JUnit 5（@Test @ParameterizedTest @RepeatedTest @TestFactory @TestTemplate @Disabled @DisplayName @Nested）です。
                """.stripIndent(), "使い方", JOptionPane.INFORMATION_MESSAGE);
    }

    private void installDrop() {
        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    Object data = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (data instanceof List<?> files && !files.isEmpty() && files.get(0) instanceof File file) {
                        openProject(file.toPath());
                        return true;
                    }
                } catch (UnsupportedFlavorException | IOException ignored) {
                    return false;
                }
                return false;
            }
        });
    }

    private void restoreBounds() {
        if (settings.width() < 860 || settings.height() < 560) {
            setSize(1180, 760);
            setLocationRelativeTo(null);
            return;
        }
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int x = settings.x();
        int y = settings.y();
        if (x < screen.x - 20 || y < screen.y - 20
                || x > screen.x + screen.width - 80
                || y > screen.y + screen.height - 80) {
            setSize(settings.width(), settings.height());
            setLocationRelativeTo(null);
            return;
        }
        setBounds(x, y, settings.width(), settings.height());
    }

    private void shutdown() {
        runner.stop();
        watcher.stop();
        settings.setWindow(getX(), getY(), getWidth(), getHeight());
        settings.setDividers(split.getDividerLocation(), vertical.getDividerLocation());
        settings.save();
        dispose();
        if (!Boolean.getBoolean("junitmanager.noexit")) {
            System.exit(0);
        }
    }

    private static JMenuItem item(String label, KeyStroke stroke, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        if (stroke != null) {
            item.setAccelerator(stroke);
        }
        item.addActionListener(event -> action.run());
        return item;
    }

    private static int shortcut() {
        return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    private static Path normalizeProject(Path path) {
        if (path == null) {
            return null;
        }
        Path absolute = path.toAbsolutePath().normalize();
        if (Files.isRegularFile(absolute) && "pom.xml".equalsIgnoreCase(name(absolute))) {
            absolute = absolute.getParent();
        }
        if (absolute == null || !Files.isDirectory(absolute) || !Files.isRegularFile(absolute.resolve("pom.xml"))) {
            return null;
        }
        return absolute;
    }

    private static String name(Path path) {
        Path file = path.getFileName();
        return file == null ? "" : file.toString();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static java.util.List<java.awt.Image> windowIcons() {
        java.awt.image.BufferedImage small = raster(16);
        java.awt.image.BufferedImage large = raster(32);
        return java.util.List.of(small, large);
    }

    private static java.awt.image.BufferedImage raster(int size) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(AppIcon.PASSED_COLOR);
        g.fillOval(1, 1, size - 2, size - 2);
        g.setColor(java.awt.Color.WHITE);
        g.setStroke(new java.awt.BasicStroke(Math.max(1.6f, size / 10f), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        int[] xs = {size * 3 / 16, size * 7 / 16, size * 12 / 16};
        int[] ys = {size * 8 / 16, size * 12 / 16, size * 4 / 16};
        g.drawPolyline(xs, ys, 3);
        g.dispose();
        return image;
    }
}
