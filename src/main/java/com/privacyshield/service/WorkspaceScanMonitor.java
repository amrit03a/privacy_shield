
package com.privacyshield.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.*;

@Service
public class WorkspaceScanMonitor {

    private static final Logger log =
            LoggerFactory.getLogger(WorkspaceScanMonitor.class);

    private final ClamAVScanner scanner;

    private final ConcurrentMap<String, Monitor> monitors =
            new ConcurrentHashMap<>();

    public WorkspaceScanMonitor(ClamAVScanner scanner) {
        this.scanner = scanner;
    }

    public synchronized void startMonitoring(
            String sessionId, String workspacePath) throws IOException {

        if (sessionId == null ||
                !sessionId.matches("^SESSION-[a-fA-F0-9-]+$")) {
            throw new SecurityException("Invalid session ID.");
        }

        Path root = Path.of(workspacePath)
                .toAbsolutePath().normalize();

        if (!Files.isDirectory(root)) {
            throw new IOException("Workspace does not exist.");
        }

        stopMonitoring(sessionId);

        WatchService watchService =
                FileSystems.getDefault().newWatchService();

        try {
            for (String folder :
                    new String[]{"files", "downloads", "temp"}) {

                Path directory = root.resolve(folder).normalize();

                if (!directory.startsWith(root)
                        || !Files.isDirectory(directory)) {
                    throw new IOException(
                            "Workspace folder unavailable: " + folder);
                }

                directory.register(
                        watchService,
                        ENTRY_CREATE,
                        ENTRY_MODIFY
                );
            }
        } catch (IOException | RuntimeException e) {
            watchService.close();
            throw e;
        }

        Monitor monitor = new Monitor(watchService);
        monitors.put(sessionId, monitor);

        monitor.watcher = new Thread(
                () -> watchLoop(monitor, root),
                "workspace-watch-" + sessionId
        );

        monitor.watcher.setDaemon(true);
        monitor.watcher.start();

        // Scan files already present when monitoring starts.
        for (String folder :
                new String[]{"files", "downloads", "temp"}) {
            Path directory = root.resolve(folder);

            try (var paths = Files.list(directory)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> !isIgnored(path))
                        .forEach(path -> submitScan(monitor, path, root));
            } catch (IOException e) {
                log.error("Unable to list workspace folder {}", directory, e);
            }
        }

        log.info("Automatic scanning started for {}", sessionId);
    }

    public synchronized void stopMonitoring(String sessionId) {
        Monitor monitor = monitors.remove(sessionId);

        if (monitor == null) {
            return;
        }

        monitor.running.set(false);

        try {
            monitor.watchService.close();
        } catch (IOException ignored) {
        }

        monitor.workers.shutdownNow();

        if (monitor.watcher != null) {
            monitor.watcher.interrupt();
        }

        log.info("Automatic scanning stopped for {}", sessionId);
    }

    private void watchLoop(Monitor monitor, Path root) {
        while (monitor.running.get()) {
            WatchKey key;

            try {
                key = monitor.watchService.take();
            } catch (ClosedWatchServiceException e) {
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            Path directory = (Path) key.watchable();

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == OVERFLOW
                        || !(event.context() instanceof Path relative)) {
                    continue;
                }

                Path file = directory.resolve(relative)
                        .toAbsolutePath().normalize();

                if (!file.startsWith(root) || isIgnored(file)) {
                    continue;
                }

                submitScan(monitor, file, root);
            }

            if (!key.reset()) {
                log.warn("A workspace folder watch key became invalid.");
            }
        }
    }

    private boolean isIgnored(Path file) {
        String name = file.getFileName().toString().toLowerCase();

        return name.startsWith(".ps-upload-")
                || name.endsWith(".crdownload")
                || name.endsWith(".part")
                || name.endsWith(".partial");
    }

    private void submitScan(Monitor monitor, Path file, Path root) {
        if (!monitor.running.get()) {
            return;
        }

        try {
            monitor.workers.submit(() -> scanWhenStable(file, root));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void scanWhenStable(Path file, Path root) {
        try {
            Path normalized = file.toAbsolutePath().normalize();

            if (!normalized.startsWith(root)
                    || !Files.isRegularFile(normalized)
                    || isIgnored(normalized)) {
                return;
            }

            long previousSize = -1;
            int stableChecks = 0;

            // Wait up to five minutes for a download/copy to finish.
            for (int i = 0; i < 300; i++) {
                if (!Files.exists(normalized)
                        || !Files.isRegularFile(normalized)) {
                    return;
                }

                long size = Files.size(normalized);

                if (size > 0 && size == previousSize) {
                    stableChecks++;
                } else {
                    stableChecks = 0;
                }

                if (stableChecks >= 3) {
                    boolean clean = scanner.isClean(normalized);

                    if (clean) {
                        log.info("ClamAV CLEAN: {}", normalized);
                    } else {
                        Files.deleteIfExists(normalized);
                        log.error("ClamAV threat detected; deleted {}", normalized);
                    }

                    return;
                }

                previousSize = size;
                Thread.sleep(1000);
            }

            log.error("File did not become stable; deleting {}", normalized);
            Files.deleteIfExists(normalized);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Scan failed; attempting to delete unverified file: {}",
                    file, e);

            try {
                Files.deleteIfExists(file);
            } catch (IOException deleteError) {
                log.error("Unable to delete unverified file: {}",
                        file, deleteError);
            }
        }
    }

    @PreDestroy
    public void stopAll() {
        monitors.keySet().forEach(this::stopMonitoring);
    }

    private static class Monitor {
        private final WatchService watchService;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final ExecutorService workers =
                Executors.newFixedThreadPool(2);
        private Thread watcher;

        private Monitor(WatchService watchService) {
            this.watchService = watchService;
        }
    }
}
