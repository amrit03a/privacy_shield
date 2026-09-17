package com.privacyshield.service;

import com.privacyshield.model.Session;
import com.privacyshield.repository.SessionRepository;
import com.privacyshield.workspace.WorkspaceManager;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Service
public class WorkspaceService {

    private final SessionRepository sessionRepository;
    private final WorkspaceManager workspaceManager;

    public WorkspaceService(
            SessionRepository sessionRepository,
            WorkspaceManager workspaceManager) {

        this.sessionRepository = sessionRepository;
        this.workspaceManager = workspaceManager;
    }

    // =========================================================
    // GET ACTIVE WORKSPACE
    // =========================================================

    public Path getActiveWorkspace(Integer userId) {

        if (userId == null) {
            throw new SecurityException(
                    "User is not authenticated."
            );
        }

        Session session =
                sessionRepository
                        .findByUserIdAndStatus(
                                userId,
                                "ACTIVE"
                        )
                        .orElseThrow(() ->
                                new SecurityException(
                                        "No active session found."
                                )
                        );

        String sessionId =
                session.getSessionId();

        if (!workspaceManager.workspaceExists(sessionId)) {
            throw new SecurityException(
                    "Workspace does not exist."
            );
        }

        return workspaceManager
                .getWorkspacePath(sessionId)
                .toAbsolutePath()
                .normalize();
    }

    // =========================================================
    // OPEN ACTIVE WORKSPACE
    // =========================================================

    public void openActiveWorkspace(
            Integer userId) throws IOException {

        if (userId == null) {
            throw new SecurityException(
                    "User is not authenticated."
            );
        }

        Session session =
                sessionRepository
                        .findByUserIdAndStatus(
                                userId,
                                "ACTIVE"
                        )
                        .orElseThrow(() ->
                                new SecurityException(
                                        "No active session found."
                                )
                        );

        String sessionId =
                session.getSessionId();

        if (!workspaceManager.workspaceExists(sessionId)) {
            throw new SecurityException(
                    "Workspace does not exist."
            );
        }

        workspaceManager.openWorkspace(sessionId);
    }

    // =========================================================
    // GET ACTIVE FILES DIRECTORY
    // =========================================================

    public Path getActiveFilesDirectory(
            Integer userId) {

        Path workspace =
                getActiveWorkspace(userId);

        Path filesDirectory =
                workspace
                        .resolve("files")
                        .normalize();

        // Ensure files directory stays
        // inside the user's workspace.
        if (!filesDirectory.startsWith(workspace)) {
            throw new SecurityException(
                    "Invalid files directory."
            );
        }

        return filesDirectory;
    }

    // =========================================================
    // GET SAFE FILE PATH
    // =========================================================

    public Path getSafeFilePath(
            Integer userId,
            String filename) {

        // Basic filename validation
        if (filename == null
                || filename.isBlank()
                || ".".equals(filename)
                || "..".equals(filename)) {

            throw new SecurityException(
                    "Invalid filename."
            );
        }

        Path filesDirectory =
                getActiveFilesDirectory(userId);

        Path requestedPath =
                filesDirectory
                        .resolve(filename)
                        .normalize();

        // =====================================================
        // PATH TRAVERSAL PROTECTION
        // =====================================================

        if (!requestedPath.startsWith(filesDirectory)) {

            throw new SecurityException(
                    "Path traversal attempt blocked."
            );
        }

        return requestedPath;
    }
}