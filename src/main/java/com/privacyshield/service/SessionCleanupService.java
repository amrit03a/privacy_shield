package com.privacyshield.service;

import com.privacyshield.model.Session;
import com.privacyshield.repository.SessionRepository;
import com.privacyshield.workspace.WorkspaceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SessionCleanupService {

    private static final Logger logger =
            LoggerFactory.getLogger(SessionCleanupService.class);

    private final SessionRepository sessionRepository;
    private final WorkspaceManager workspaceManager;

    // =========================================================
    // SESSION TIMEOUT
    // =========================================================

    private static final long TIMEOUT_MINUTES = 30;

    public SessionCleanupService(
            SessionRepository sessionRepository,
            WorkspaceManager workspaceManager) {

        this.sessionRepository = sessionRepository;
        this.workspaceManager = workspaceManager;
    }

    // =========================================================
    // AUTOMATIC CLEANUP
    // Runs every 1 minute
    // =========================================================

    @Scheduled(fixedRate = 60000)
    public void cleanupInactiveSessions() {

        LocalDateTime now =
                LocalDateTime.now();

        // Get all ACTIVE sessions
        List<Session> activeSessions =
                sessionRepository.findByStatus("ACTIVE");

        for (Session session : activeSessions) {

            LocalDateTime lastActivity =
                    session.getLastActivityTime();

            // -------------------------------------------------
            // No activity timestamp
            // -------------------------------------------------

            if (lastActivity == null) {

                logger.warn(
                        "Skipping session {} because "
                                + "last activity time is missing.",
                        session.getSessionId()
                );

                continue;
            }

            long inactiveMinutes =
                    Duration.between(
                            lastActivity,
                            now
                    ).toMinutes();

            // =================================================
            // SESSION EXPIRED
            // =================================================

            if (inactiveMinutes >= TIMEOUT_MINUTES) {

                try {

                    boolean cleaned =
                            workspaceManager.deleteWorkspace(
                                    session.getSessionId()
                            );

                    if (!cleaned) {

                        logger.error(
                                "Workspace cleanup failed for "
                                        + "expired session: {}",
                                session.getSessionId()
                        );

                        continue;
                    }

                    // -------------------------------------------------
                    // Update session state
                    // -------------------------------------------------

                    session.setEndTime(now);

                    session.setLastActivityTime(now);

                    session.setStatus("EXPIRED");

                    sessionRepository.save(session);

                    logger.info(
                            "Expired inactive session: {} "
                                    + "(inactive for {} minutes)",
                            session.getSessionId(),
                            inactiveMinutes
                    );

                } catch (IOException e) {

                    logger.error(
                            "Failed to cleanup workspace for "
                                    + "session: {}",
                            session.getSessionId(),
                            e
                    );
                }
            }
        }
    }
}