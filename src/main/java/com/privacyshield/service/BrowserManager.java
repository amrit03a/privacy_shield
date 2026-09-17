package com.privacyshield.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BrowserManager {

    private static final String BROWSER_PROFILES_FOLDER =
            "C:/privacy-shield-browser-profiles";

    private static final String WINDOWS_BROWSER_USER =
            "PrivacyShieldUser";

    private static final String WINDOWS_BROWSER_USER_FULL =
            "AMRIT_LAPTOP\\PrivacyShieldUser";

    private final Map<String, String> browserTypes =
            new ConcurrentHashMap<>();


    // =========================================================
    // OPEN SECURE BROWSER
    // =========================================================

    public void openSecureBrowser(
            String sessionId,
            String workspacePath) throws IOException {

        // -----------------------------------------------------
        // 1. Validate session ID
        // -----------------------------------------------------

        validateSessionId(sessionId);


        // -----------------------------------------------------
        // 2. Validate workspace
        // -----------------------------------------------------

        if (workspacePath == null
                || workspacePath.isBlank()) {

            throw new IllegalArgumentException(
                    "Workspace path is not available."
            );
        }


        Path workspace =
                Path.of(workspacePath)
                        .toAbsolutePath()
                        .normalize();


        if (!Files.exists(workspace)
                || !Files.isDirectory(workspace)) {

            throw new IOException(
                    "Secure workspace does not exist."
            );
        }


        // -----------------------------------------------------
        // 3. Create downloads directory
        // -----------------------------------------------------

        Path downloadsDirectory =
                workspace
                        .resolve("downloads")
                        .normalize();


        if (!downloadsDirectory.startsWith(workspace)) {

            throw new SecurityException(
                    "Invalid downloads path."
            );
        }


        Files.createDirectories(
                downloadsDirectory
        );


        // -----------------------------------------------------
        // 4. Browser profile base directory
        // -----------------------------------------------------

        Path browserProfilesBase =
                Path.of(BROWSER_PROFILES_FOLDER)
                        .toAbsolutePath()
                        .normalize();


        Files.createDirectories(
                browserProfilesBase
        );


        // -----------------------------------------------------
        // 5. Session-specific browser profile
        // -----------------------------------------------------

        Path profileDirectory =
                browserProfilesBase
                        .resolve(sessionId)
                        .normalize();


        if (!profileDirectory.startsWith(
                browserProfilesBase)) {

            throw new SecurityException(
                    "Invalid browser profile path."
            );
        }


        Files.createDirectories(
                profileDirectory
        );


        // -----------------------------------------------------
        // 6. Configure downloads
        // -----------------------------------------------------

        configureDownloadDirectory(
                profileDirectory,
                downloadsDirectory
        );


        // -----------------------------------------------------
        // 7. Find installed browser
        // -----------------------------------------------------

        String browserPath =
                findBrowser();


        String browserName =
                getBrowserName(browserPath);


        // -----------------------------------------------------
        // 8. Close previous secure browser
        // -----------------------------------------------------

        closeSecureBrowser(sessionId);


        // -----------------------------------------------------
        // 9. Create temporary launcher directory
        // -----------------------------------------------------

        Path launcherDirectory =
                Path.of("C:/PrivacyShield-Test")
                        .toAbsolutePath()
                        .normalize();


        Files.createDirectories(
                launcherDirectory
        );


        // -----------------------------------------------------
        // 10. Create session-specific BAT file
        // -----------------------------------------------------

        Path launcherFile =
                launcherDirectory
                        .resolve(
                                "launch-"
                                        + sessionId
                                        + ".bat"
                        )
                        .normalize();


        if (!launcherFile.startsWith(
                launcherDirectory)) {

            throw new SecurityException(
                    "Invalid launcher path."
            );
        }


        // -----------------------------------------------------
        // 11. Build browser command
        // -----------------------------------------------------

        String browserCommand =
                "\"" + browserPath + "\" "
                        + "--user-data-dir=\""
                        + profileDirectory
                        + "\" "
                        + "--no-first-run "
                        + "--no-default-browser-check "
                        + "--disable-sync "
                        + "--new-window";


        // -----------------------------------------------------
        // 12. Write BAT file
        // -----------------------------------------------------

        String batContent =
                "@echo off\r\n"
                        + browserCommand
                        + "\r\n";


        Files.writeString(
                launcherFile,
                batContent,
                StandardCharsets.UTF_8
        );


        // -----------------------------------------------------
        // 13. IMPORTANT:
        //     Entire CMD command must be ONE runas argument
        // -----------------------------------------------------

        String commandForRunas =
                "cmd.exe /c \""
                        + launcherFile
                        + "\"";


        // -----------------------------------------------------
        // 14. Launch as PrivacyShieldUser
        // -----------------------------------------------------

        ProcessBuilder processBuilder =
                new ProcessBuilder(
                        "runas",
                        "/user:" + WINDOWS_BROWSER_USER,
                        "/savecred",
                        commandForRunas
                );


        processBuilder.redirectErrorStream(true);


        Process process =
                processBuilder.start();


        // -----------------------------------------------------
        // 15. Read runas output
        // -----------------------------------------------------

        String output;

        try {

            output =
                    new String(
                            process.getInputStream()
                                    .readAllBytes(),
                            StandardCharsets.UTF_8
                    );


            int exitCode =
                    process.waitFor();


            if (exitCode != 0) {

                throw new IOException(
                        "Unable to launch secure browser as "
                                + WINDOWS_BROWSER_USER
                                + ". runas output: "
                                + output
                );
            }


        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IOException(
                    "Browser launch was interrupted.",
                    e
            );
        }
        // -----------------------------------------------------
        // 16. Remember browser type
        // -----------------------------------------------------

        browserTypes.put(
                sessionId,
                browserName
        );

            }
    // =========================================================
    // CLOSE SECURE BROWSER
    // =========================================================

    public void closeSecureBrowser(
            String sessionId) {

        validateSessionId(sessionId);


        String browserName =
                browserTypes.remove(
                        sessionId
                );


        if ("chrome".equals(browserName)) {

            closeBrowserProcess(
                    "chrome.exe"
            );

        } else if ("edge".equals(browserName)) {

            closeBrowserProcess(
                    "msedge.exe"
            );

        } else {

            closeBrowserProcess(
                    "chrome.exe"
            );

            closeBrowserProcess(
                    "msedge.exe"
            );
        }
    }


    // =========================================================
    // CLOSE BROWSER PROCESS
    // =========================================================

    private void closeBrowserProcess(
            String processName) {

        try {

            ProcessBuilder processBuilder =
                    new ProcessBuilder(
                            "taskkill",
                            "/F",
                            "/T",
                            "/FI",
                            "USERNAME eq "
                                    + WINDOWS_BROWSER_USER_FULL,
                            "/IM",
                            processName
                    );


            Process process =
                    processBuilder.start();


            process.waitFor();


        } catch (IOException e) {

            System.err.println(
                    "Unable to close "
                            + processName
                            + ": "
                            + e.getMessage()
            );


        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            System.err.println(
                    "Browser close interrupted."
            );
        }
    }


    // =========================================================
    // CONFIGURE DOWNLOAD DIRECTORY
    // =========================================================

    private void configureDownloadDirectory(
            Path profileDirectory,
            Path downloadsDirectory)
            throws IOException {

        Path defaultProfile =
                profileDirectory
                        .resolve("Default")
                        .normalize();


        if (!defaultProfile.startsWith(
                profileDirectory)) {

            throw new SecurityException(
                    "Invalid browser profile path."
            );
        }


        Files.createDirectories(
                defaultProfile
        );


        Path preferencesFile =
                defaultProfile
                        .resolve("Preferences")
                        .normalize();


        if (!preferencesFile.startsWith(
                defaultProfile)) {

            throw new SecurityException(
                    "Invalid preferences path."
            );
        }


        String downloadPath =
                downloadsDirectory
                        .toString()
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"");


        String preferencesJson =
                """
                {
                  "download": {
                    "default_directory": "%s",
                    "directory_upgrade": true,
                    "prompt_for_download": false
                  }
                }
                """.formatted(downloadPath);


        Files.writeString(
                preferencesFile,
                preferencesJson,
                StandardCharsets.UTF_8
        );
    }


    // =========================================================
    // FIND INSTALLED BROWSER
    // =========================================================

    private String findBrowser() {

        String[] possibleBrowsers = {

                // Chrome
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",

                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",

                // Edge
                "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",

                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
        };


        for (String browser :
                possibleBrowsers) {

            if (Files.exists(
                    Path.of(browser))) {

                return browser;
            }
        }


        throw new IllegalStateException(
                "No supported browser found. "
                        + "Please install Google Chrome "
                        + "or Microsoft Edge."
        );
    }


    // =========================================================
    // GET BROWSER NAME
    // =========================================================

    private String getBrowserName(
            String browserPath) {

        if (browserPath
                .toLowerCase()
                .contains("chrome")) {

            return "chrome";
        }


        if (browserPath
                .toLowerCase()
                .contains("msedge")) {

            return "edge";
        }


        return "unknown";
    }


    // =========================================================
    // DELETE BROWSER PROFILE
    // =========================================================

    public void deleteBrowserProfile(
            String sessionId) {

        validateSessionId(sessionId);


        // Close browser first
        closeSecureBrowser(sessionId);


        Path baseDirectory =
                Path.of(BROWSER_PROFILES_FOLDER)
                        .toAbsolutePath()
                        .normalize();


        Path profileDirectory =
                baseDirectory
                        .resolve(sessionId)
                        .normalize();


        if (!profileDirectory.startsWith(
                baseDirectory)) {

            throw new SecurityException(
                    "Invalid browser profile path."
            );
        }


        if (!Files.exists(
                profileDirectory)) {

            return;
        }


        try {

            Files.walk(profileDirectory)
                    .sorted(
                            (a, b) ->
                                    b.compareTo(a)
                    )
                    .forEach(path -> {

                        try {

                            Files.deleteIfExists(
                                    path
                            );

                        } catch (IOException e) {

                            throw new RuntimeException(
                                    "Unable to delete browser profile: "
                                            + e.getMessage(),
                                    e
                            );
                        }
                    });


        } catch (IOException e) {

            throw new RuntimeException(
                    "Unable to clean browser profile: "
                            + e.getMessage(),
                    e
            );
        }
    }


    // =========================================================
    // VALIDATE SESSION ID
    // =========================================================

    private void validateSessionId(
            String sessionId) {

        if (sessionId == null
                || sessionId.isBlank()) {

            throw new IllegalArgumentException(
                    "Session ID is required."
            );
        }


        if (!sessionId.matches(
                "^SESSION-[a-fA-F0-9-]+$")) {

            throw new SecurityException(
                    "Invalid session ID."
            );
        }
    }
}