package com.privacyshield.controller;

import com.privacyshield.model.Session;
import com.privacyshield.repository.SessionRepository;
import com.privacyshield.workspace.WorkspaceManager;

import jakarta.servlet.http.HttpSession;
import com.privacyshield.service.BrowserManager;
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

    public SessionController(
        SessionRepository sessionRepository,
        WorkspaceManager workspaceManager,
        BrowserManager browserManager) {

                this.sessionRepository = sessionRepository;
                this.workspaceManager = workspaceManager;
                        this.browserManager = browserManager;
        }


    // =========================================================
    // START SECURE SESSION
    // =========================================================

    @PostMapping("/start")
    public Map<String, Object> startSession(
            HttpSession httpSession) {

        Map<String, Object> response =
                new HashMap<>();

        Integer userId =
                (Integer) httpSession.getAttribute(
                        "userId"
                );

        if (userId == null) {

            response.put(
                    "status",
                    "NOT_LOGGED_IN"
            );

            response.put(
                    "message",
                    "Please login first."
            );

            return response;
        }

        String sessionId = null;

        try {

            // =================================================
            // CHECK EXISTING ACTIVE SESSION
            // =================================================

            Optional<Session> activeSession =
                    sessionRepository
                            .findByUserIdAndStatus(
                                    userId,
                                    "ACTIVE"
                            );

            if (activeSession.isPresent()) {

                Session session =
                        activeSession.get();

                // Verify physical workspace still exists
                if (workspaceManager.workspaceExists(
                        session.getSessionId())) {

                    response.put(
                            "status",
                            "SESSION_ALREADY_ACTIVE"
                    );

                    response.put(
                            "message",
                            "Secure workspace already active."
                    );

                    response.put(
                            "sessionId",
                            session.getSessionId()
                    );

                    response.put(
                            "workspacePath",
                            session.getWorkspacePath()
                    );

                    return response;
                }

                // =================================================
                // STALE DATABASE SESSION
                // =================================================

                LocalDateTime now =
                        LocalDateTime.now();

                session.setEndTime(now);

                session.setLastActivityTime(now);

                session.setStatus(
                        "EXPIRED"
                );

                sessionRepository.save(
                        session
                );

                logger.warn(
                        "Stale active session marked EXPIRED: {}",
                        session.getSessionId()
                );
            }


            // =================================================
            // CREATE WINDOWS WORKSPACE
            // =================================================

            sessionId =
                    workspaceManager.createWorkspace();

            String workspacePath =
                    workspaceManager
                            .getWorkspacePath(
                                    sessionId
                            )
                            .toString();


            // =================================================
            // CREATE DATABASE SESSION
            // =================================================

            LocalDateTime now =
                    LocalDateTime.now();

            Session session =
                    new Session();

            session.setSessionId(
                    sessionId
            );

            session.setUserId(
                    userId
            );

            // Managed Windows workspace
            // instead of Docker/VM.
            session.setContainerName(
                    "WINDOWS-WORKSPACE"
            );

            session.setDesktopPort(
                    0
            );

            session.setWorkspacePath(
                    workspacePath
            );

            session.setStartTime(
                    now
            );

            session.setLastActivityTime(
                    now
            );

            session.setStatus(
                    "ACTIVE"
            );


            // =================================================
            // SAVE SESSION
            // =================================================

            sessionRepository.save(
                    session
            );


            // =================================================
            // SUCCESS
            // =================================================

            response.put(
                    "status",
                    "SESSION_STARTED"
            );

            response.put(
                    "message",
                    "Secure Windows workspace created."
            );

            response.put(
                    "sessionId",
                    sessionId
            );

            response.put(
                    "workspacePath",
                    workspacePath
            );

            return response;


        } catch (Exception e) {

            // =================================================
            // ROLLBACK WORKSPACE
            // =================================================

            if (sessionId != null) {

                try {

                    workspaceManager.deleteWorkspace(
                            sessionId
                    );

                } catch (IOException cleanupException) {

                    logger.error(
                            "Failed to rollback workspace: {}",
                            sessionId,
                            cleanupException
                    );
                }
            }

            logger.error(
                    "Failed to start session for user: {}",
                    userId,
                    e
            );

            response.put(
                    "status",
                    "SESSION_FAILED"
            );

            response.put(
                    "message",
                    "Unable to create secure workspace."
            );

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

        Map<String, Object> response =
                new HashMap<>();

        Integer userId =
                (Integer) httpSession.getAttribute(
                        "userId"
                );

        if (userId == null) {

            response.put(
                    "status",
                    "NOT_LOGGED_IN"
            );

            response.put(
                    "message",
                    "Please login first."
            );

            return response;
        }


        // =================================================
        // VALIDATE SESSION ID
        // =================================================

        if (sessionId == null
                || sessionId.isBlank()) {

            response.put(
                    "status",
                    "INVALID_SESSION_ID"
            );

            response.put(
                    "message",
                    "Session ID cannot be empty."
            );

            return response;
        }

        if (!sessionId.matches(
                "^SESSION-[a-fA-F0-9-]+$")) {

            response.put(
                    "status",
                    "INVALID_SESSION_ID"
            );

            response.put(
                    "message",
                    "Invalid session ID."
            );

            return response;
        }


        try {

            // =================================================
            // FIND SESSION
            // =================================================

            Optional<Session> optionalSession =
                    sessionRepository.findById(
                            sessionId
                    );

            if (optionalSession.isEmpty()) {

                response.put(
                        "status",
                        "SESSION_NOT_FOUND"
                );

                response.put(
                        "message",
                        "Session not found."
                );

                return response;
            }

            Session session =
                    optionalSession.get();


            // =================================================
            // OWNERSHIP CHECK
            // =================================================

            if (session.getUserId() == null
                    || !session.getUserId()
                    .equals(userId)) {

                response.put(
                        "status",
                        "ACCESS_DENIED"
                );

                response.put(
                        "message",
                        "You cannot access this session."
                );

                return response;
            }


            // =================================================
            // CHECK ACTIVE
            // =================================================

            if (!"ACTIVE".equals(
                    session.getStatus())) {

                response.put(
                        "status",
                        "SESSION_ALREADY_ENDED"
                );

                response.put(
                        "message",
                        "Session is already ended."
                );

                return response;
            }


            // =================================================
// CLOSE SECURE BROWSER
// =================================================

browserManager.closeSecureBrowser(sessionId);


// =================================================
// DELETE BROWSER PROFILE
// =================================================

browserManager.deleteBrowserProfile(sessionId);


// =================================================
// DELETE WINDOWS WORKSPACE
// =================================================

boolean deleted =
        workspaceManager.deleteWorkspace(
                sessionId
        );

            if (!deleted) {

                response.put(
                        "status",
                        "CLEANUP_FAILED"
                );

                response.put(
                        "message",
                        "Workspace cleanup failed."
                );

                return response;
            }


            // =================================================
            // UPDATE DATABASE
            // =================================================

            LocalDateTime now =
                    LocalDateTime.now();

            session.setEndTime(
                    now
            );

            session.setLastActivityTime(
                    now
            );

            session.setStatus(
                    "COMPLETED"
            );

            sessionRepository.save(
                    session
            );


            // =================================================
            // SUCCESS
            // =================================================

            response.put(
                    "status",
                    "SESSION_ENDED"
            );

            response.put(
                    "message",
                    "Secure workspace deleted successfully."
            );

            response.put(
                    "sessionId",
                    sessionId
            );

            return response;


        } catch (Exception e) {

            logger.error(
                    "Failed to end session: {}",
                    sessionId,
                    e
            );

            response.put(
                    "status",
                    "SESSION_END_FAILED"
            );

            response.put(
                    "message",
                    "Unable to end session."
            );

            return response;
        }
    }


    // =========================================================
    // GET MY SESSIONS
    // =========================================================

    @GetMapping("/my-sessions")
    public Object getMySessions(
            HttpSession httpSession) {

        Integer userId =
                (Integer) httpSession.getAttribute(
                        "userId"
                );

        if (userId == null) {

            return Map.of(
                    "status",
                    "NOT_LOGGED_IN",
                    "message",
                    "Please login first."
            );
        }

        return sessionRepository.findByUserId(
                userId
        );
    }
}