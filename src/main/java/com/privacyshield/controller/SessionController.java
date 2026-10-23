
package com.privacyshield.controller;

import com.privacyshield.model.Session;
import com.privacyshield.repository.SessionRepository;
import com.privacyshield.service.BrowserManager;
import com.privacyshield.service.WorkspaceScanMonitor;
import com.privacyshield.workspace.WorkspaceManager;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private static final Logger logger =
            LoggerFactory.getLogger(SessionController.class);

    private final SessionRepository sessionRepository;
    private final WorkspaceManager workspaceManager;
    private final BrowserManager browserManager;
    private final WorkspaceScanMonitor workspaceScanMonitor;

    public SessionController(
            SessionRepository sessionRepository,
            WorkspaceManager workspaceManager,
            BrowserManager browserManager,
            WorkspaceScanMonitor workspaceScanMonitor) {

        this.sessionRepository = sessionRepository;
        this.workspaceManager = workspaceManager;
        this.browserManager = browserManager;
        this.workspaceScanMonitor = workspaceScanMonitor;
    }

    // =========================================================
    // START SECURE SESSION
    // =========================================================

    @PostMapping("/start")
    public Map<String, Object> startSession(
            HttpSession httpSession) {

        Map<String, Object> response = new HashMap<>();

        Integer userId =
                (Integer) httpSession.getAttribute("userId");

        if (userId == null) {
            response.put("status", "NOT_LOGGED_IN");
            response.put("message", "Please login first.");

            return response;
        }

        String sessionId = null;
        boolean workspaceCreated = false;

        try {
            // Check for an existing active session.
            Optional<Session> activeSession =
                    sessionRepository.findByUserIdAndStatus(
                            userId, "ACTIVE");

            if (activeSession.isPresent()) {

                Session session = activeSession.get();

                if (workspaceManager.workspaceExists(
                        session.getSessionId())) {

                    // Ensure monitoring is active.
                    workspaceScanMonitor.startMonitoring(
                            session.getSessionId(),
                            session.getWorkspacePath());

                    response.put(
                            "status",
                            "SESSION_ALREADY_ACTIVE");

                    response.put(
                            "message",
                            "Secure workspace already active. "
                                    + "Automatic scanning enabled.");

                    response.put(
                            "sessionId",
                            session.getSessionId());

                    response.put(
                            "workspacePath",
                            session.getWorkspacePath());

                    return response;
                }

                // Mark a stale database session as expired.
                LocalDateTime now = LocalDateTime.now();

                session.setEndTime(now);
                session.setLastActivityTime(now);
                session.setStatus("EXPIRED");

                sessionRepository.save(session);

                logger.warn(
                        "Stale active session marked EXPIRED: {}",
                        session.getSessionId());
            }

            // Create the Windows workspace.
            sessionId = workspaceManager.createWorkspace();
            workspaceCreated = true;

            String workspacePath =
                    workspaceManager
                            .getWorkspacePath(sessionId)
                            .toString();

            // Create the database session.
            LocalDateTime now = LocalDateTime.now();

            Session session = new Session();

            session.setSessionId(sessionId);
            session.setUserId(userId);
            session.setContainerName("WINDOWS-WORKSPACE");
            session.setDesktopPort(0);
            session.setWorkspacePath(workspacePath);
            session.setStartTime(now);
            session.setLastActivityTime(now);
            session.setStatus("ACTIVE");

            sessionRepository.save(session);

            // Start automatic monitoring of files and downloads.
            workspaceScanMonitor.startMonitoring(
                    sessionId,
                    workspacePath);

            response.put("status", "SESSION_STARTED");

            response.put(
                    "message",
                    "Secure Windows workspace created. "
                            + "Automatic file scanning enabled.");

            response.put("sessionId", sessionId);
            response.put("workspacePath", workspacePath);

            return response;

        } catch (Exception e) {

            logger.error(
                    "Failed to start session for user: {}",
                    userId,
                    e);

            if (sessionId != null) {
                workspaceScanMonitor.stopMonitoring(sessionId);
            }

            // Roll back a newly created workspace.
            if (workspaceCreated && sessionId != null) {
                try {
                    workspaceManager.deleteWorkspace(sessionId);

                } catch (IOException cleanupException) {
                    logger.error(
                            "Failed to roll back workspace: {}",
                            sessionId,
                            cleanupException);
                }
            }

            response.put("status", "SESSION_FAILED");

            response.put(
                    "message",
                    "Unable to create workspace or start automatic "
                            + "scanning. Check application logs.");

            return response;
        }
    }

    // =========================================================
    // END SECURE SESSION
    // =========================================================

    
@PostMapping("/end")
public Map<String, Object> endSession(
        @RequestParam String sessionId,
        HttpSession httpSession) {

    Map<String, Object> response = new HashMap<>();

    Integer userId =
            (Integer) httpSession.getAttribute("userId");

    if (userId == null) {
        response.put("status", "NOT_LOGGED_IN");
        response.put("message", "Please login first.");
        return response;
    }

    if (sessionId == null
            || sessionId.isBlank()
            || !sessionId.matches("^SESSION-[a-fA-F0-9-]+$")) {

        response.put("status", "INVALID_SESSION_ID");
        response.put("message", "Invalid session ID.");
        return response;
    }

    try {
        Optional<Session> optionalSession =
                sessionRepository.findById(sessionId);

        if (optionalSession.isEmpty()) {
            response.put("status", "SESSION_NOT_FOUND");
            response.put("message", "Session not found.");
            return response;
        }

        Session session = optionalSession.get();

        if (session.getUserId() == null
                || !session.getUserId().equals(userId)) {

            response.put("status", "ACCESS_DENIED");
            response.put("message", "You cannot access this session.");
            return response;
        }

        if (!"ACTIVE".equals(session.getStatus())) {
            response.put("status", "SESSION_ALREADY_ENDED");
            response.put("message", "Session is already ended.");
            return response;
        }

        workspaceScanMonitor.stopMonitoring(sessionId);

        boolean profileDeleted = false;
        boolean workspaceDeleted = false;

        // Attempt browser-profile cleanup.
        try {
            browserManager.deleteBrowserProfile(sessionId);
            profileDeleted = true;

        } catch (Exception e) {
            logger.error(
                    "Browser profile cleanup failed for session: {}",
                    sessionId,
                    e);
        }

        // Always attempt workspace cleanup.
        try {
            workspaceDeleted =
                    workspaceManager.deleteWorkspace(sessionId);

        } catch (Exception e) {
            logger.error(
                    "Workspace cleanup failed for session: {}",
                    sessionId,
                    e);
        }

        // A workspace deletion failure must not be hidden.
        if (!workspaceDeleted) {
            response.put("status", "CLEANUP_FAILED");
            response.put(
                    "message",
                    "Workspace cleanup failed. Please check application logs.");
            response.put("sessionId", sessionId);
            return response;
        }

        // Workspace is deleted. Record session completion even if
        // browser-profile cleanup failed, but log that failure above.
        LocalDateTime now = LocalDateTime.now();

        session.setEndTime(now);
        session.setLastActivityTime(now);
        session.setStatus("COMPLETED");

        sessionRepository.save(session);

        response.put("status", "SESSION_ENDED");
        response.put(
                "message",
                "Secure session ended and workspace cleanup completed.");
        response.put("sessionId", sessionId);
        response.put("profileDeleted", profileDeleted);

        return response;

    } catch (Exception e) {
        logger.error("Failed to end session: {}", sessionId, e);

        response.put("status", "SESSION_END_FAILED");
        response.put(
                "message",
                "Unable to end session. Check application logs.");

        return response;
    }
}


    // =========================================================
    // GET MY SESSIONS
    // =========================================================

    @GetMapping("/my-sessions")
    public Object getMySessions(HttpSession httpSession) {

        Integer userId =
                (Integer) httpSession.getAttribute("userId");

        if (userId == null) {
            return Map.of(
                    "status", "NOT_LOGGED_IN",
                    "message", "Please login first.");
        }

        return sessionRepository.findByUserId(userId);
    }
}
