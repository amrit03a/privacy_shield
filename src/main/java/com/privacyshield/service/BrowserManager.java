package com.privacyshield.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

@Service
public class BrowserManager {

    private static final Path PROFILES_BASE =
            Path.of("C:/privacy-shield-browser-profiles")
                    .toAbsolutePath()
                    .normalize();

    private static final Path LAUNCHER_BASE =
            Path.of("C:/PrivacyShield-Test")
                    .toAbsolutePath()
                    .normalize();

    private static final String BROWSER_USER =
            "PrivacyShieldUser";

    private static final String BROWSER_USER_FULL =
            "AMRIT_LAPTOP\\PrivacyShieldUser";

    // =========================================================
    // OPEN SECURE BROWSER
    // =========================================================

    public void openSecureBrowser(
            String sessionId,
            String workspacePath) throws IOException {

        validateSessionId(sessionId);

        if (workspacePath == null || workspacePath.isBlank()) {
            throw new IllegalArgumentException(
                    "Workspace path is not available.");
        }

        Path workspace = Path.of(workspacePath)
                .toAbsolutePath()
                .normalize();

        if (!Files.isDirectory(workspace)) {
            throw new IOException(
                    "Secure workspace does not exist: " + workspace);
        }

        Path downloads = workspace.resolve("downloads").normalize();

        if (!downloads.startsWith(workspace)) {
            throw new SecurityException("Invalid downloads path.");
        }

        Files.createDirectories(downloads);
        Files.createDirectories(PROFILES_BASE);
        Files.createDirectories(LAUNCHER_BASE);

        Path profile = getProfileDirectory(sessionId);

        closeSecureBrowser(sessionId);

        Files.createDirectories(profile);

        configureProfilePermissions(profile);
        configureDownloadDirectory(profile, downloads);

        String browserPath = findBrowser();

        Path launcher = LAUNCHER_BASE
                .resolve("launch-" + sessionId + ".bat")
                .normalize();

        if (!launcher.startsWith(LAUNCHER_BASE)) {
            throw new SecurityException("Invalid launcher path.");
        }

        String bat =
                "@echo off\r\n"
                + "start \"\" /B \""
                + browserPath
                + "\" --user-data-dir=\""
                + profile
                + "\" --no-first-run"
                + " --no-default-browser-check"
                + " --disable-sync --new-window\r\n"
                + "exit /b\r\n";

        Files.writeString(
                launcher,
                bat,
                StandardCharsets.UTF_8);

        String command = "cmd.exe /c \"" + launcher + "\"";

        Process process = new ProcessBuilder(
                "runas",
                "/user:" + BROWSER_USER,
                "/savecred",
                command)
                .redirectErrorStream(true)
                .start();

        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();

                throw new IOException(
                        "Timed out launching the secure browser.");
            }

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);

            if (process.exitValue() != 0) {
                throw new IOException(
                        "Browser launch failed. Check the Windows "
                        + "account credentials and permissions. "
                        + output);
            }

        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();

            throw new IOException(
                    "Browser launch was interrupted.", e);
        }
    }

    // =========================================================
    // CLOSE SECURE BROWSER
    // =========================================================

    public void closeSecureBrowser(String sessionId) {

        validateSessionId(sessionId);

        Path profile = getProfileDirectory(sessionId);
        String escapedProfile = profile.toString()
                .replace("'", "''");

        String script =
                "$ErrorActionPreference = 'Stop'; "
                + "$profile = '" + escapedProfile + "'; "
                + "$names = @('chrome.exe','msedge.exe'); "
                + "$all = Get-CimInstance Win32_Process; "
                + "$matches = @($all | Where-Object { "
                + "$_.Name -in $names -and $_.CommandLine -and "
                + "$_.CommandLine.Contains($profile) }); "
                + "foreach ($p in $matches) { "
                + "Stop-Process -Id $p.ProcessId -Force "
                + "-ErrorAction SilentlyContinue }; "
                + "Start-Sleep -Milliseconds 500; "
                + "$left = @(Get-CimInstance Win32_Process | "
                + "Where-Object { $_.Name -in $names -and "
                + "$_.CommandLine -and "
                + "$_.CommandLine.Contains($profile) }); "
                + "if ($left.Count -gt 0) { exit 2 } else { exit 0 }";

        runPowerShell(script, 15, profile.toString());
    }

    private void runPowerShell(
            String script,
            int timeoutSeconds,
            String description) {

        Process process = null;

        try {
            process = new ProcessBuilder(
                    "powershell.exe",
                    "-NoLogo",
                    "-NoProfile",
                    "-NonInteractive",
                    "-Command",
                    script)
                    .redirectErrorStream(true)
                    .start();

            final Process child = process;

            Thread outputReader = new Thread(() -> {
                try {
                    child.getInputStream().transferTo(
                            java.io.OutputStream.nullOutputStream());
                } catch (IOException ignored) {
                    // The process may already have exited.
                }
            });

            outputReader.setDaemon(true);
            outputReader.start();

            boolean finished = process.waitFor(
                    timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();

                throw new IllegalStateException(
                        "Timed out closing browser for profile: "
                        + description);
            }

            outputReader.join(1000);

            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "Browser shutdown command failed for: "
                        + description
                        + ". Exit code: "
                        + process.exitValue());
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to execute browser shutdown command.", e);

        } catch (InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Browser shutdown was interrupted.", e);
        }
    }

    // =========================================================
    // DELETE BROWSER PROFILE
    // =========================================================

    public void deleteBrowserProfile(String sessionId) {

        validateSessionId(sessionId);

        closeSecureBrowser(sessionId);

        Path profileDirectory = getProfileDirectory(sessionId);

        if (!Files.exists(profileDirectory)) {
            return;
        }

        IOException lastError = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                deleteDirectoryTree(profileDirectory);
                return;

            } catch (IOException e) {
                lastError = e;

                try {
                    repairProfilePermissions(profileDirectory);
                } catch (Exception permissionError) {
                    e.addSuppressed(permissionError);
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();

                    throw new IllegalStateException(
                            "Profile deletion was interrupted.",
                            interrupted);
                }
            }
        }

        throw new IllegalStateException(
                "Unable to delete browser profile: "
                + profileDirectory,
                lastError);
    }

    // =========================================================
    // PROFILE PATH VALIDATION
    // =========================================================

    private Path getProfileDirectory(String sessionId) {

        validateSessionId(sessionId);

        Path profile = PROFILES_BASE
                .resolve(sessionId)
                .normalize();

        if (!profile.startsWith(PROFILES_BASE)
                || profile.equals(PROFILES_BASE)) {
            throw new SecurityException(
                    "Invalid browser profile path.");
        }

        return profile;
    }

    private void validateSessionId(String sessionId) {

        if (sessionId == null
                || !sessionId.matches(
                        "^SESSION-[a-fA-F0-9-]+$")) {
            throw new SecurityException(
                    "Invalid session ID.");
        }
    }

    // =========================================================
    // CONFIGURE PROFILE PERMISSIONS
    // =========================================================

    private void configureProfilePermissions(
            Path profile) throws IOException {

        String currentUser = getCurrentWindowsUser();

        runWindowsCommand(
                30,
                "icacls.exe",
                profile.toString(),
                "/grant",
                currentUser + ":(OI)(CI)F",
                BROWSER_USER_FULL + ":(OI)(CI)M",
                "/T",
                "/C");
    }

    // =========================================================
    // REPAIR PROFILE PERMISSIONS
    // =========================================================

    
private void repairProfilePermissions(
        Path profileDirectory) throws IOException {

    if (!Files.exists(profileDirectory)) {
        return;
    }

    Path profile = profileDirectory.toAbsolutePath().normalize();

    // Step 1: Take ownership of this session profile.
    runWindowsCommand(
            60,
            "takeown.exe",
            "/F",
            profile.toString(),
            "/R",
            "/D",
            "Y"
    );

    // Step 2: Grant the application account full control.
    String currentUser = getCurrentWindowsUser();

    runWindowsCommand(
            60,
            "icacls.exe",
            profile.toString(),
            "/grant",
            currentUser + ":(OI)(CI)F",
            "/T",
            "/C"
    );

    // Step 3: Restore access for the dedicated browser account.
    runWindowsCommand(
            60,
            "icacls.exe",
            profile.toString(),
            "/grant",
            BROWSER_USER_FULL + ":(OI)(CI)F",
            "/T",
            "/C"
    );
}


    private String getCurrentWindowsUser() throws IOException {

        String domain = System.getenv("USERDOMAIN");
        String username = System.getenv("USERNAME");

        if (username == null || username.isBlank()) {
            throw new IOException(
                    "Cannot determine the current Windows user.");
        }

        return domain == null || domain.isBlank()
                ? username
                : domain + "\\" + username;
    }

    // =========================================================
    // RUN WINDOWS COMMAND
    // =========================================================

    private void runWindowsCommand(
            int timeoutSeconds,
            String... command) throws IOException {

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        Thread outputReader = new Thread(() -> {
            try {
                process.getInputStream().transferTo(
                        java.io.OutputStream.nullOutputStream());
            } catch (IOException ignored) {
                // The process may already have exited.
            }
        });

        outputReader.setDaemon(true);
        outputReader.start();

        try {
            if (!process.waitFor(
                    timeoutSeconds, TimeUnit.SECONDS)) {

                process.destroyForcibly();

                throw new IOException(
                        "Timed out running: " + command[0]);
            }

            outputReader.join(1000);

            if (process.exitValue() != 0) {
                throw new IOException(
                        command[0]
                        + " failed with exit code "
                        + process.exitValue());
            }

        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();

            throw new IOException(
                    "Windows command was interrupted.", e);
        }
    }

    // =========================================================
    // CONFIGURE DOWNLOAD DIRECTORY
    // =========================================================

    private void configureDownloadDirectory(
            Path profile,
            Path downloads) throws IOException {

        Path defaultProfile = profile
                .resolve("Default")
                .normalize();

        if (!defaultProfile.startsWith(profile)) {
            throw new SecurityException(
                    "Invalid default profile path.");
        }

        Files.createDirectories(defaultProfile);

        Path preferences = defaultProfile
                .resolve("Preferences")
                .normalize();

        if (!preferences.startsWith(defaultProfile)) {
            throw new SecurityException(
                    "Invalid preferences path.");
        }

        String downloadPath = downloads.toString()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");

        String json = """
                {
                  "download": {
                    "default_directory": "%s",
                    "directory_upgrade": true,
                    "prompt_for_download": false
                  }
                }
                """.formatted(downloadPath);

        Files.writeString(
                preferences,
                json,
                StandardCharsets.UTF_8);
    }

    // =========================================================
    // FIND BROWSER
    // =========================================================

    private String findBrowser() {

        String[] candidates = {
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
        };

        for (String candidate : candidates) {
            if (Files.exists(Path.of(candidate))) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "No supported Chrome or Edge installation found.");
    }

    // =========================================================
    // DELETE DIRECTORY TREE
    // =========================================================

    private void deleteDirectoryTree(Path directory)
            throws IOException {

        Files.walkFileTree(
                directory,
                new SimpleFileVisitor<>() {

                    @Override
                    public FileVisitResult visitFile(
                            Path file,
                            BasicFileAttributes attributes)
                            throws IOException {

                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(
                            Path file,
                            IOException exception)
                            throws IOException {

                        throw new IOException(
                                "Cannot access profile item: " + file,
                                exception);
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(
                            Path dir,
                            IOException exception)
                            throws IOException {

                        if (exception != null) {
                            throw exception;
                        }

                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }
}