package com.privacyshield.workspace;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class WorkspaceManager {

    // =========================================================
    // BASE DIRECTORY
    // =========================================================

    private final Path baseDirectory =
            Paths.get("C:/privacy-shield-workspaces")
                    .toAbsolutePath()
                    .normalize();

    // Dedicated Windows account
    private static final String WINDOWS_WORKSPACE_USER =
            "PrivacyShieldUser";


    // =========================================================
    // CREATE WORKSPACE
    // =========================================================

    public String createWorkspace() throws IOException {

        // Create base directory if it does not exist
        Files.createDirectories(baseDirectory);

        // Generate unique session ID
        String sessionId =
                "SESSION-" + UUID.randomUUID();

        // Create workspace path
        Path workspace =
                baseDirectory
                        .resolve(sessionId)
                        .normalize();

        // Security validation
        validateWorkspacePath(workspace);

        // =====================================================
        // CREATE WORKSPACE DIRECTORIES
        // =====================================================

        Files.createDirectories(
                workspace.resolve("files")
        );

        Files.createDirectories(
                workspace.resolve("downloads")
        );

        Files.createDirectories(
                workspace.resolve("temp")
        );

        // =====================================================
        // GRANT ACCESS TO PRIVACY SHIELD USER
        // =====================================================

        grantPrivacyShieldUserAccess(workspace);

        return sessionId;
    }


    // =========================================================
    // GRANT WINDOWS ACCESS
    // =========================================================

    private void grantPrivacyShieldUserAccess(
            Path workspace) throws IOException {

        /*
         * Give only the dedicated PrivacyShieldUser
         * Modify permission on this session workspace.
         *
         * OI = Object Inherit
         * CI = Container Inherit
         * M  = Modify
         * /T = Apply recursively
         */

        Process process = new ProcessBuilder(
                "icacls",
                workspace.toString(),
                "/grant",
                WINDOWS_WORKSPACE_USER + ":(OI)(CI)M",
                "/T"
        )
                .redirectErrorStream(true)
                .start();

        String output;

        try (var inputStream = process.getInputStream()) {

            output = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        try {

            int exitCode = process.waitFor();

            if (exitCode != 0) {

                throw new IOException(
                        "Failed to grant Windows permission to "
                                + WINDOWS_WORKSPACE_USER
                                + ". ICACLS output: "
                                + output
                );
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IOException(
                    "Permission process was interrupted.",
                    e
            );
        }
    }


    // =========================================================
    // GET WORKSPACE PATH
    // =========================================================

    public Path getWorkspacePath(
            String sessionId) {

        // Validate session ID first
        validateSessionId(sessionId);

        Path workspace =
                baseDirectory
                        .resolve(sessionId)
                        .normalize();

        // Prevent path traversal
        validateWorkspacePath(workspace);

        return workspace;
    }


    // =========================================================
    // CHECK WORKSPACE EXISTS
    // =========================================================

    public boolean workspaceExists(
            String sessionId) {

        Path workspace =
                getWorkspacePath(sessionId);

        return Files.exists(workspace)
                && Files.isDirectory(workspace);
    }


    // =========================================================
    // OPEN WINDOWS WORKSPACE
    // =========================================================

    public void openWorkspace(
            String sessionId)
            throws IOException {

        Path workspace =
                getWorkspacePath(sessionId);

        if (!Files.exists(workspace)
                || !Files.isDirectory(workspace)) {

            throw new IOException(
                    "Workspace does not exist."
            );
        }

        /*
         * Opens the user's temporary workspace
         * in Windows File Explorer.
         */

        new ProcessBuilder(
                "explorer.exe",
                workspace.toString()
        ).start();
    }


    // =========================================================
    // DELETE WORKSPACE
    // =========================================================

    public boolean deleteWorkspace(
            String sessionId)
            throws IOException {

        Path workspace =
                getWorkspacePath(sessionId);

        // Nothing to delete
        if (!Files.exists(workspace)) {
            return true;
        }

        try (Stream<Path> paths =
                     Files.walk(workspace)) {

            /*
             * Delete children before parent.
             */
            paths
                    .sorted(
                            (a, b) ->
                                    b.compareTo(a)
                    )
                    .forEach(path -> {

                        try {

                            Files.deleteIfExists(path);

                        } catch (IOException e) {

                            throw new WorkspaceCleanupException(
                                    "Failed to delete: "
                                            + path,
                                    e
                            );
                        }
                    });

        } catch (WorkspaceCleanupException e) {

            throw new IOException(
                    e.getMessage(),
                    e.getCause()
            );
        }

        return !Files.exists(workspace);
    }


    // =========================================================
    // VALIDATE SESSION ID
    // =========================================================

    private void validateSessionId(
            String sessionId) {

        if (sessionId == null
                || sessionId.isBlank()) {

            throw new IllegalArgumentException(
                    "Session ID cannot be empty."
            );
        }

        /*
         * Only UUID-based session IDs generated
         * by this application are accepted.
         */
        if (!sessionId.matches(
                "^SESSION-[a-fA-F0-9-]+$")) {

            throw new SecurityException(
                    "Invalid session ID."
            );
        }
    }


    // =========================================================
    // VALIDATE WORKSPACE PATH
    // =========================================================

    private void validateWorkspacePath(
            Path workspace) {

        /*
         * Workspace must always remain
         * inside the configured base directory.
         */
        if (!workspace.startsWith(
                baseDirectory)) {

            throw new SecurityException(
                    "Invalid workspace path."
            );
        }
    }


    // =========================================================
    // CLEANUP EXCEPTION
    // =========================================================

    private static class WorkspaceCleanupException
            extends RuntimeException {

        public WorkspaceCleanupException(
                String message,
                Throwable cause) {

            super(message, cause);
        }
    }
}