package com.privacyshield.controller;

import com.privacyshield.dto.LoginRequest;
import com.privacyshield.dto.RegisterRequest;
import com.privacyshield.model.Session;
import com.privacyshield.model.User;
import com.privacyshield.repository.SessionRepository;
import com.privacyshield.service.AuditLogService;
import com.privacyshield.service.UserService;
import com.privacyshield.workspace.WorkspaceManager;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger =
            LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final SessionRepository sessionRepository;
    private final WorkspaceManager workspaceManager;
    private final AuditLogService auditLogService;


    public UserController(
            UserService userService,
            SessionRepository sessionRepository,
            WorkspaceManager workspaceManager,
            AuditLogService auditLogService) {

        this.userService = userService;
        this.sessionRepository = sessionRepository;
        this.workspaceManager = workspaceManager;
        this.auditLogService = auditLogService;
    }


    // =========================================================
    // VALIDATION ERROR HANDLER
    // =========================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationException(
            MethodArgumentNotValidException e) {

        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Invalid input.");

        return ResponseEntity
                .badRequest()
                .body(message);
    }


    // =========================================================
    // REGISTER
    // =========================================================

    @PostMapping("/register")
    public ResponseEntity<String> register(
            @Valid @RequestBody RegisterRequest request) {

        try {

            User user = userService.registerUser(
                    request.getUsername(),
                    request.getPassword()
            );

            return ResponseEntity.ok(
                    "User registered successfully: "
                            + user.getUsername()
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(e.getMessage());
        }
    }


    // =========================================================
    // LOGIN
    // =========================================================

    @PostMapping("/login")
    public Map<String, Object> login(
            @Valid @RequestBody LoginRequest request,
            HttpSession httpSession) {

        User user = userService.loginUser(
                request.getUsername(),
                request.getPassword()
        );

        // Store authenticated user information
        // in the HTTP session.
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

        // Audit successful login
        auditLogService.log(
                user.getUserId(),
                "USER_LOGIN",
                "User logged in successfully"
        );

        Map<String, Object> response =
                new HashMap<>();

        response.put(
                "message",
                "Login successful"
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
    // PASSWORD RESET
    // =========================================================

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(
            @RequestParam String newPassword,
            HttpSession httpSession) {

        Map<String, String> response =
                new HashMap<>();

        Integer userId =
                (Integer) httpSession.getAttribute(
                        "userId"
                );


        // =====================================================
        // USER NOT LOGGED IN
        // =====================================================

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


        try {

            userService.resetPassword(
                    userId,
                    newPassword
            );

            // Audit password change
            auditLogService.log(
                    userId,
                    "PASSWORD_RESET",
                    "User password reset successfully"
            );

            response.put(
                    "status",
                    "PASSWORD_RESET_SUCCESSFUL"
            );

            response.put(
                    "message",
                    "Password reset successfully."
            );

            return response;

        } catch (IllegalArgumentException e) {

            response.put(
                    "status",
                    "PASSWORD_RESET_FAILED"
            );

            response.put(
                    "message",
                    e.getMessage()
            );

            return response;
        }
    }


    // =========================================================
    // LOGOUT
    // =========================================================

    @PostMapping("/logout")
    public Map<String, String> logout(
            HttpSession httpSession) {

        Map<String, String> response =
                new HashMap<>();

        Integer userId =
                (Integer) httpSession.getAttribute(
                        "userId"
                );


        // =====================================================
        // USER NOT LOGGED IN
        // =====================================================

        if (userId == null) {

            response.put(
                    "status",
                    "NOT_LOGGED_IN"
            );

            response.put(
                    "message",
                    "User is not logged in."
            );

            return response;
        }


        // =====================================================
        // FIND ACTIVE SESSION
        // =====================================================

        Optional<Session> optionalSession =
                sessionRepository.findByUserIdAndStatus(
                        userId,
                        "ACTIVE"
                );


        // =====================================================
        // NO ACTIVE WORKSPACE
        // =====================================================

        if (optionalSession.isEmpty()) {

            auditLogService.log(
                    userId,
                    "USER_LOGOUT",
                    "User logged out successfully; "
                            + "no active workspace found"
            );

            httpSession.invalidate();

            response.put(
                    "status",
                    "LOGOUT_SUCCESSFUL"
            );

            response.put(
                    "message",
                    "Logged out successfully. "
                            + "No active session found."
            );

            return response;
        }


        Session session =
                optionalSession.get();


        // =====================================================
        // VALIDATE SESSION ID
        // =====================================================

        String sessionId =
                session.getSessionId();

        if (sessionId == null
                || sessionId.isBlank()) {

            logger.error(
                    "Active session has invalid session ID. "
                            + "User ID: {}",
                    userId
            );

            response.put(
                    "status",
                    "LOGOUT_FAILED"
            );

            response.put(
                    "message",
                    "Invalid active session. "
                            + "Logout cancelled."
            );

            return response;
        }


        // =====================================================
        // DELETE TEMPORARY WORKSPACE
        // =====================================================

        try {

            boolean cleaned =
                    workspaceManager.deleteWorkspace(
                            sessionId
                    );

            if (!cleaned) {

                logger.error(
                        "Workspace cleanup failed during logout. "
                                + "User ID: {}, Session ID: {}",
                        userId,
                        sessionId
                );

                response.put(
                        "status",
                        "LOGOUT_FAILED"
                );

                response.put(
                        "message",
                        "Workspace cleanup failed. "
                                + "Logout cancelled."
                );

                return response;
            }

        } catch (IOException e) {

            logger.error(
                    "Workspace cleanup error during logout. "
                            + "User ID: {}, Session ID: {}",
                    userId,
                    sessionId,
                    e
            );

            response.put(
                    "status",
                    "LOGOUT_FAILED"
            );

            response.put(
                    "message",
                    "Workspace cleanup failed. "
                            + "Logout cancelled."
            );

            return response;
        }


        // =====================================================
        // COMPLETE DATABASE SESSION
        // =====================================================

        LocalDateTime now =
                LocalDateTime.now();

        session.setEndTime(now);

        session.setLastActivityTime(now);

        session.setStatus(
                "COMPLETED"
        );

        sessionRepository.save(session);


        // =====================================================
        // AUDIT LOG
        // =====================================================

        auditLogService.log(
                userId,
                "USER_LOGOUT",
                "User logged out successfully"
        );


        // =====================================================
        // INVALIDATE HTTP SESSION
        // =====================================================

        httpSession.invalidate();


        // =====================================================
        // RESPONSE
        // =====================================================

        response.put(
                "status",
                "LOGOUT_SUCCESSFUL"
        );

        response.put(
                "sessionId",
                sessionId
        );

        response.put(
                "message",
                "Logged out and temporary workspace "
                        + "cleaned successfully."
        );

        return response;
    }
}