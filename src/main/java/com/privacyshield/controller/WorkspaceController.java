package com.privacyshield.controller;

import com.privacyshield.repository.SessionRepository;
import com.privacyshield.service.BrowserManager;
import com.privacyshield.service.WorkspaceService;
import jakarta.servlet.http.HttpSession;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final SessionRepository sessionRepository;
    private final BrowserManager browserManager;

    public WorkspaceController(
            WorkspaceService workspaceService,
            SessionRepository sessionRepository,
            BrowserManager browserManager) {

        this.workspaceService = workspaceService;
        this.sessionRepository = sessionRepository;
        this.browserManager = browserManager;
    }


    // =========================================================
    // UPLOAD FILE
    // =========================================================

    @PostMapping("/upload")
    public String uploadFile(
            @RequestParam("file") MultipartFile file,
            HttpSession httpSession)
            throws IOException {

        Integer userId =
                (Integer) httpSession.getAttribute("userId");

        if (userId == null) {
            return "Please login first.";
        }

        if (file == null || file.isEmpty()) {
            return "File cannot be empty.";
        }

        String originalFilename =
                file.getOriginalFilename();

        if (originalFilename == null
                || originalFilename.isBlank()) {

            return "Invalid filename.";
        }

        // =====================================================
        // SAFELY EXTRACT FILENAME
        // =====================================================

        String filename;

        try {

            filename =
                    Path.of(originalFilename)
                            .getFileName()
                            .toString();

        } catch (InvalidPathException e) {

            return "Invalid filename.";
        }

        if (filename.isBlank()
                || filename.equals(".")
                || filename.equals("..")) {

            return "Invalid filename.";
        }

        // =====================================================
        // GET SAFE TARGET PATH
        // =====================================================

        Path target;

        try {

            target =
                    workspaceService.getSafeFilePath(
                            userId,
                            filename);

        } catch (SecurityException e) {

            return "Access denied: "
                    + e.getMessage();
        }

        // =====================================================
        // PREVENT OVERWRITE
        // =====================================================

        if (Files.exists(target)) {

            return "File already exists: "
                    + filename;
        }

        // =====================================================
        // CREATE DIRECTORY IF REQUIRED
        // =====================================================

        Files.createDirectories(
                target.getParent());

        // =====================================================
        // SAVE FILE
        // =====================================================

        Files.copy(
                file.getInputStream(),
                target);

        // =====================================================
        // UPDATE ACTIVITY
        // =====================================================

        updateLastActivity(userId);

        return "File uploaded successfully: "
                + filename;
    }


    // =========================================================
    // LIST FILES
    // =========================================================

    @GetMapping("/files")
    public List<String> listFiles(
            HttpSession httpSession)
            throws IOException {

        Integer userId =
                (Integer) httpSession.getAttribute(
                        "userId");

        if (userId == null) {

            throw new SecurityException(
                    "Please login first.");
        }

        Path filesDirectory =
                workspaceService
                        .getActiveFilesDirectory(
                                userId);

        if (!Files.exists(filesDirectory)
                || !Files.isDirectory(filesDirectory)) {

            return new ArrayList<>();
        }

        List<String> files;

        try (var paths =
                     Files.list(filesDirectory)) {

            files =
                    paths
                            .filter(
                                    Files::isRegularFile)
                            .map(
                                    path ->
                                            path.getFileName()
                                                    .toString())
                            .toList();
        }

        updateLastActivity(userId);

        return files;
    }


    // =========================================================
    // DOWNLOAD FILE
    // =========================================================

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String filename,
            HttpSession httpSession)
            throws IOException {

        Integer userId =
                (Integer) httpSession.getAttribute(
                        "userId");

        if (userId == null) {

            return ResponseEntity
                    .status(401)
                    .build();
        }

        try {

            Path file =
                    workspaceService.getSafeFilePath(
                            userId,
                            filename);

            // =================================================
            // VERIFY FILE
            // =================================================

            if (!Files.exists(file)
                    || !Files.isRegularFile(file)) {

                return ResponseEntity
                        .notFound()
                        .build();
            }

            Resource resource =
                    new UrlResource(
                            file.toUri());

            if (!resource.exists()
                    || !resource.isReadable()) {

                return ResponseEntity
                        .notFound()
                        .build();
            }

            // =================================================
            // UPDATE ACTIVITY
            // =================================================

            updateLastActivity(userId);

            // =================================================
            // RETURN FILE
            // =================================================

            return ResponseEntity
                    .ok()
                    .contentType(
                            MediaType.APPLICATION_OCTET_STREAM)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\""
                                    + file.getFileName()
                                    + "\"")
                    .body(resource);

        } catch (SecurityException e) {

            return ResponseEntity
                    .status(403)
                    .build();
        }
    }


    // =========================================================
    // DELETE FILE
    // =========================================================

    @DeleteMapping("/file/{filename}")
    public String deleteFile(
            @PathVariable String filename,
            HttpSession httpSession)
            throws IOException {

        Integer userId =
                (Integer) httpSession.getAttribute(
                        "userId");

        if (userId == null) {

            return "Please login first.";
        }

        try {

            Path file =
                    workspaceService.getSafeFilePath(
                            userId,
                            filename);

            // =================================================
            // VERIFY FILE
            // =================================================

            if (!Files.exists(file)
                    || !Files.isRegularFile(file)) {

                return "File not found.";
            }

            // =================================================
            // DELETE
            // =================================================

            Files.delete(file);

            // =================================================
            // UPDATE ACTIVITY
            // =================================================

            updateLastActivity(userId);

            return "File deleted successfully: "
                    + filename;

        } catch (SecurityException e) {

            return "Access denied: "
                    + e.getMessage();
        }
    }


    // =========================================================
    // OPEN SECURE WORKSPACE
    // =========================================================

    @PostMapping("/open")
    public String openWorkspace(
            HttpSession httpSession)
            throws IOException {

        Integer userId =
                (Integer) httpSession.getAttribute(
                        "userId");

        if (userId == null) {

            return "Please login first.";
        }

        try {

            workspaceService.openActiveWorkspace(
                    userId);

            return "Secure workspace opened.";

        } catch (SecurityException e) {

            return "Access denied: "
                    + e.getMessage();
        }
    }


    // =========================================================
    // OPEN SECURE BROWSER
    // =========================================================

    @PostMapping("/open-browser")
    public String openSecureBrowser(
            HttpSession httpSession) {

        Integer userId =
                (Integer) httpSession.getAttribute(
                        "userId");

        if (userId == null) {
            return "Please login first.";
        }

        try {

            /*
             * Find the currently active session
             * belonging to the logged-in user.
             */
            var activeSession =
                    sessionRepository
                            .findByUserIdAndStatus(
                                    userId,
                                    "ACTIVE");

            if (activeSession.isEmpty()) {
                return "No active secure session found.";
            }

            var session =
                    activeSession.get();

            String sessionId =
                    session.getSessionId();

            String workspacePath =
                    session.getWorkspacePath();

            if (workspacePath == null
                    || workspacePath.isBlank()) {

                return "Workspace path is not available.";
            }

            /*
             * Launch browser using:
             *
             * Session-specific browser profile
             * +
             * Session-specific downloads folder
             */
            browserManager.openSecureBrowser(
                    sessionId,
                    workspacePath);

            // Update session activity
            session.setLastActivityTime(
                    LocalDateTime.now());

            sessionRepository.save(session);

            return "Secure browser opened successfully. "
                    + "Downloads will be saved inside your secure workspace.";

        } catch (SecurityException e) {

            return "Access denied: "
                    + e.getMessage();

        } catch (IllegalStateException e) {

            return e.getMessage();

        } catch (IOException e) {

            return "Unable to open secure browser: "
                    + e.getMessage();
        }
    }


    // =========================================================
    // UPDATE LAST ACTIVITY
    // =========================================================

    private void updateLastActivity(
            Integer userId) {

        sessionRepository
                .findByUserIdAndStatus(
                        userId,
                        "ACTIVE")
                .ifPresent(session -> {

                    session.setLastActivityTime(
                            LocalDateTime.now());

                    sessionRepository.save(
                            session);
                });
    }
}