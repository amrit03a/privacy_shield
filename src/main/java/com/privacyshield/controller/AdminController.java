package com.privacyshield.controller;

import com.privacyshield.dto.AdminUserResponse;
import com.privacyshield.dto.LoginRequest;
import com.privacyshield.model.Session;
import com.privacyshield.model.User;
import com.privacyshield.repository.SessionRepository;
import com.privacyshield.repository.UserRepository;
import com.privacyshield.service.AuditLogService;
import com.privacyshield.service.UserService;
import com.privacyshield.workspace.WorkspaceManager;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger logger =
            LoggerFactory.getLogger(AdminController.class);

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final WorkspaceManager workspaceManager;

    public AdminController(
            UserService userService,
            AuditLogService auditLogService,
            UserRepository userRepository,
            SessionRepository sessionRepository,
            WorkspaceManager workspaceManager) {

        this.userService = userService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.workspaceManager = workspaceManager;
    }


    // =========================================================
    // ADMIN LOGIN
    // =========================================================

    @PostMapping("/login")
    public Map<String, Object> adminLogin(
            @Valid @RequestBody LoginRequest request,
            HttpSession httpSession) {

        User user = userService.loginAdmin(
                request.getUsername(),
                request.getPassword()
        );

        httpSession.setAttribute(
                "userId",
                user.getUserId()
        );

        httpSession.setAttribute(
                "username",
                user.getUsername()
        );

        httpSession.setAttribute(
                "role",
                user.getRole()
        );

        auditLogService.log(
                user.getUserId(),
                "ADMIN_LOGIN",
                "Admin logged in successfully"
        );

        Map<String, Object> response =
                new HashMap<>();

        response.put(
                "message",
                "Admin login successful"
        );

        response.put(
                "userId",
                user.getUserId()
        );

        response.put(
                "username",
                user.getUsername()
        );

        response.put(
                "role",
                user.getRole()
        );

        response.put(
                "status",
                user.getStatus()
        );

        return response;
    }


    // =========================================================
    // ADMIN LOGOUT
    // =========================================================

    @PostMapping("/logout")
    public Map<String, String> adminLogout(
            HttpSession httpSession) {

        Map<String, String> response =
                new HashMap<>();

        Integer adminId =
                (Integer) httpSession.getAttribute(
                        "userId"
                );

        String role =
                (String) httpSession.getAttribute(
                        "role"
                );

        // -----------------------------------------------------
        // NOT LOGGED IN
        // -----------------------------------------------------

        if (adminId == null) {

            response.put(
                    "status",
                    "NOT_LOGGED_IN"
            );

            response.put(
                    "message",
                    "Admin is not logged in."
            );

            return response;
        }

        // -----------------------------------------------------
        // VERIFY ADMIN ROLE
        // -----------------------------------------------------

        if (!"ADMIN".equals(role)) {

            response.put(
                    "status",
                    "ACCESS_DENIED"
            );

            response.put(
                    "message",
                    "Admin privileges required."
            );

            return response;
        }

        // -----------------------------------------------------
        // AUDIT LOG
        // -----------------------------------------------------

        auditLogService.log(
                adminId,
                "ADMIN_LOGOUT",
                "Admin logged out successfully"
        );

        // -----------------------------------------------------
        // INVALIDATE HTTP SESSION
        // -----------------------------------------------------

        httpSession.invalidate();

        response.put(
                "status",
                "LOGOUT_SUCCESSFUL"
        );

        response.put(
                "message",
                "Admin logged out successfully."
        );

        return response;
    }


    // =========================================================
    // GET ALL USERS
    // =========================================================

    @GetMapping("/users")
    public List<AdminUserResponse> getAllUsers(
            HttpSession httpSession) {

        requireAdmin(httpSession);

        return userRepository.findAll()
                .stream()
                .map(AdminUserResponse::new)
                .toList();
    }


    // =========================================================
    // ACTIVATE / DEACTIVATE USER
    // =========================================================

    @PutMapping("/users/{userId}/status")
    public AdminUserResponse changeUserStatus(
            @PathVariable Integer userId,
            @RequestParam String status,
            HttpSession httpSession) {

        requireAdmin(httpSession);

        if (userId == null) {

            throw new IllegalArgumentException(
                    "User ID cannot be null"
            );
        }

        if (status == null
                || (!"ACTIVE".equals(status)
                && !"INACTIVE".equals(status))) {

            throw new IllegalArgumentException(
                    "Invalid status. Use ACTIVE or INACTIVE."
            );
        }

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "User not found"
                                )
                        );

        Integer adminId =
                (Integer) httpSession.getAttribute(
                        "userId"
                );

        // -----------------------------------------------------
        // PREVENT SELF-DEACTIVATION
        // -----------------------------------------------------

        if (userId.equals(adminId)
                && "INACTIVE".equals(status)) {

            throw new IllegalStateException(
                    "Admin cannot deactivate themselves"
            );
        }


        // =====================================================
        // DEACTIVATE USER
        // =====================================================

        if ("INACTIVE".equals(status)) {

            Optional<Session> activeSession =
                    sessionRepository.findByUserIdAndStatus(
                            userId,
                            "ACTIVE"
                    );

            if (activeSession.isPresent()) {

                Session session =
                        activeSession.get();

                try {

                    boolean cleaned =
                            workspaceManager.deleteWorkspace(
                                    session.getSessionId()
                            );

                    if (!cleaned) {

                        throw new IllegalStateException(
                                "Workspace cleanup failed. "
                                        + "User was not deactivated."
                        );
                    }

                } catch (IOException e) {

                    logger.error(
                            "Workspace cleanup failed while "
                                    + "deactivating user {}",
                            userId,
                            e
                    );

                    throw new IllegalStateException(
                            "Workspace cleanup failed. "
                                    + "User was not deactivated.",
                            e
                    );
                }

                LocalDateTime now =
                        LocalDateTime.now();

                session.setEndTime(now);

                session.setLastActivityTime(now);

                session.setStatus(
                        "COMPLETED"
                );

                sessionRepository.save(
                        session
                );
            }

            user.setStatus(
                    "INACTIVE"
            );

            userRepository.save(
                    user
            );

            auditLogService.log(
                    userId,
                    "USER_DEACTIVATED",
                    "Admin deactivated user account "
                            + user.getUsername()
            );
        }


        // =====================================================
        // ACTIVATE USER
        // =====================================================

        else {

            user.setStatus(
                    "ACTIVE"
            );

            userRepository.save(
                    user
            );

            auditLogService.log(
                    userId,
                    "USER_ACTIVATED",
                    "Admin activated user account "
                            + user.getUsername()
            );
        }

        return new AdminUserResponse(user);
    }


    // =========================================================
    // GET ALL SESSIONS
    // =========================================================

    @GetMapping("/sessions")
    public List<Session> getAllSessions(
            HttpSession httpSession) {

        requireAdmin(httpSession);

        return sessionRepository.findAll();
    }


    // =========================================================
    // FORCE CLEANUP SESSION
    // =========================================================

    @PutMapping("/sessions/{sessionId}/force-cleanup")
    public Map<String, String> forceCleanup(
            @PathVariable String sessionId,
            HttpSession httpSession) {

        requireAdmin(httpSession);

        // -----------------------------------------------------
        // VALIDATE SESSION ID
        // -----------------------------------------------------

        if (sessionId == null
                || sessionId.isBlank()) {

            throw new IllegalArgumentException(
                    "Session ID cannot be empty"
            );
        }

        if (!sessionId.matches(
                "^SESSION-[a-fA-F0-9-]+$")) {

            throw new SecurityException(
                    "Invalid session ID."
            );
        }


        // -----------------------------------------------------
        // FIND SESSION
        // -----------------------------------------------------

        Session session =
                sessionRepository.findById(sessionId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Session not found"
                                )
                        );


        // -----------------------------------------------------
        // CHECK SESSION STATUS
        // -----------------------------------------------------

        if (!"ACTIVE".equals(
                session.getStatus())) {

            Map<String, String> response =
                    new HashMap<>();

            response.put(
                    "status",
                    "ALREADY_COMPLETED"
            );

            response.put(
                    "message",
                    "Session is not active"
            );

            return response;
        }


        // -----------------------------------------------------
        // DELETE WORKSPACE
        // -----------------------------------------------------

        try {

            boolean cleaned =
                    workspaceManager.deleteWorkspace(
                            session.getSessionId()
                    );

            if (!cleaned) {

                throw new IllegalStateException(
                        "Workspace cleanup failed"
                );
            }

        } catch (IOException e) {

            logger.error(
                    "Force cleanup failed for session {}",
                    sessionId,
                    e
            );

            throw new IllegalStateException(
                    "Workspace cleanup failed",
                    e
            );
        }


        // -----------------------------------------------------
        // COMPLETE SESSION
        // -----------------------------------------------------

        LocalDateTime now =
                LocalDateTime.now();

        session.setEndTime(now);

        session.setLastActivityTime(now);

        session.setStatus(
                "COMPLETED"
        );

        sessionRepository.save(
                session
        );


        // -----------------------------------------------------
        // AUDIT LOG
        // -----------------------------------------------------

        Integer userId =
                session.getUserId();

        auditLogService.log(
                userId,
                "ADMIN_FORCE_CLEANUP",
                "Admin forcefully cleaned session "
                        + session.getSessionId()
        );


        // -----------------------------------------------------
        // RESPONSE
        // -----------------------------------------------------

        Map<String, String> response =
                new HashMap<>();

        response.put(
                "status",
                "CLEANUP_SUCCESSFUL"
        );

        response.put(
                "sessionId",
                session.getSessionId()
        );

        response.put(
                "message",
                "Session terminated and workspace "
                        + "cleaned successfully."
        );

        return response;
    }


    // =========================================================
    // ADMIN AUTHORIZATION
    // =========================================================

    private void requireAdmin(
            HttpSession httpSession) {

        if (httpSession == null) {

            throw new SecurityException(
                    "Authentication required"
            );
        }

        Integer userId =
                (Integer) httpSession.getAttribute(
                        "userId"
                );

        String role =
                (String) httpSession.getAttribute(
                        "role"
                );

        if (userId == null) {

            throw new SecurityException(
                    "Authentication required"
            );
        }

        if (!"ADMIN".equals(role)) {

            throw new SecurityException(
                    "Access denied: "
                            + "Admin privileges required"
            );
        }
    }
}