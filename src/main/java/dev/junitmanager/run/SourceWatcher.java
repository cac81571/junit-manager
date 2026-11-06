package dev.junitmanager.run;

import javax.swing.SwingUtilities;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

public final class SourceWatcher {
    private volatile boolean running;
    private WatchService watchService;
    private Thread thread;

    public void start(List<Path> roots, Runnable onChange, java.util.function.Consumer<String> onError) {
        stop();
        try {
            watchService = FileSystems.getDefault().newWatchService();
            for (Path root : roots) {
                if (Files.isDirectory(root)) {
                    registerTree(root);
                }
            }
        } catch (IOException ex) {
            onError.accept("変更の監視を開始できません: " + ex.getMessage());
            return;
        }
        running = true;
        WatchService service = watchService;
        thread = new Thread(() -> loop(service, onChange), "junit-manager-watch");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        Thread current = thread;
        WatchService service = watchService;
        thread = null;
        watchService = null;
        if (service != null) {
            try {
                service.close();
            } catch (IOException ignored) {
                // 閉じるだけの失敗は監視停止を妨げない
            }
        }
        if (current != null) {
            current.interrupt();
        }
    }

    private void loop(WatchService service, Runnable onChange) {
        boolean dirty = false;
        long lastEvent = 0;
        while (running) {
            WatchKey key;
            try {
                key = service.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ClosedWatchServiceException ex) {
                break;
            }
            if (key != null) {
                Path dir = key.watchable() instanceof Path path ? path : null;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        dirty = true;
                        lastEvent = System.currentTimeMillis();
                        continue;
                    }
                    Object context = event.context();
                    if (!(context instanceof Path name) || dir == null) {
                        continue;
                    }
                    Path child = dir.resolve(name);
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child)) {
                        registerTree(child);
                    }
                    if (name.toString().endsWith(".java")) {
                        dirty = true;
                        lastEvent = System.currentTimeMillis();
                    }
                }
                key.reset();
            }
            if (dirty && System.currentTimeMillis() - lastEvent >= 800) {
                dirty = false;
                SwingUtilities.invokeLater(onChange);
            }
        }
    }

    private void registerTree(Path root) {
        WatchService service = watchService;
        if (service == null || !Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                try {
                    dir.register(
                            service,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE
                    );
                } catch (IOException ignored) {
                    // 一部のディレクトリが監視できなくても、ほかは続ける
                }
            });
        } catch (IOException ignored) {
            // 走査できないソースツリーは監視対象から外す
        }
    }
}
