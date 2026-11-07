package dev.junitmanager.run;

import javax.swing.SwingWorker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class MavenRunner {
    private SwingWorker<RunResult, String> worker;
    private volatile Process process;
    private volatile boolean stopRequested;

    public boolean running() {
        return worker != null && !worker.isDone();
    }

    public void start(
            MavenCommands.Launch launch,
            List<Path> reportModules,
            Consumer<String> output,
            BiConsumer<RunResult, Throwable> finished
    ) {
        stopRequested = false;
        worker = new SwingWorker<>() {
            @Override
            protected RunResult doInBackground() {
                Process proc = null;
                try {
                    Map<Path, Long> before = SurefireReports.snapshot(reportModules);
                    ProcessBuilder builder = new ProcessBuilder(launch.command());
                    builder.directory(launch.directory().toFile());
                    builder.redirectErrorStream(true);
                    configureJvm(builder);
                    proc = builder.start();
                    process = proc;
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("Picked up JAVA_TOOL_OPTIONS:")) {
                                continue;
                            }
                            publish(line);
                            if (stopRequested) {
                                break;
                            }
                        }
                    }
                    if (stopRequested && proc.isAlive()) {
                        destroy(proc);
                    }
                    int code = proc.waitFor();
                    List<ReportedTest> tests = SurefireReports.parseUpdated(reportModules, before);
                    return new RunResult(code, stopRequested, tests);
                } catch (InterruptedException ex) {
                    if (proc != null) {
                        destroy(proc);
                    }
                    Thread.currentThread().interrupt();
                    return new RunResult(-1, true, List.of());
                } catch (Exception ex) {
                    if (proc != null) {
                        destroy(proc);
                    }
                    throw new RuntimeException(ex);
                } finally {
                    process = null;
                }
            }

            @Override
            protected void process(List<String> chunks) {
                output.accept(String.join("\n", chunks) + "\n");
            }

            @Override
            protected void done() {
                try {
                    finished.accept(get(), null);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    if (cause instanceof RuntimeException runtime && runtime.getCause() != null) {
                        cause = runtime.getCause();
                    }
                    finished.accept(null, cause);
                }
            }
        };
        worker.execute();
    }

    public void stop() {
        stopRequested = true;
        Process proc = process;
        if (proc != null) {
            destroy(proc);
        }
    }

    private static void configureJvm(ProcessBuilder builder) {
        String current = builder.environment().get("JAVA_TOOL_OPTIONS");
        String next = current == null ? "" : current.trim();
        if (!next.contains("stderr.encoding")) {
            next = next.isBlank()
                    ? "-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
                    : next + " -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8";
        }
        if (!next.contains("-Dconfirm=")) {
            next = next.isBlank() ? "-Dconfirm=dialog" : next + " -Dconfirm=dialog";
        }
        builder.environment().put("JAVA_TOOL_OPTIONS", next);
    }

    private static void destroy(Process proc) {
        proc.descendants().forEach(handle -> {
            try {
                handle.destroyForcibly();
            } catch (Exception ignored) {
                // すでに終了している子プロセスは無視する
            }
        });
        proc.destroyForcibly();
    }

    public record RunResult(int exitCode, boolean cancelled, List<ReportedTest> tests) {
    }
}
