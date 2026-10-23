
package com.privacyshield.controller;

import com.privacyshield.repository.SessionRepository;
import com.privacyshield.service.BrowserManager;
import com.privacyshield.service.ClamAVScanner;
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
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final SessionRepository sessionRepository;
    private final BrowserManager browserManager;
    private final ClamAVScanner clamAVScanner;

    public WorkspaceController(
            WorkspaceService workspaceService,
            SessionRepository sessionRepository,
            BrowserManager browserManager,
            ClamAVScanner clamAVScanner) {

        this.workspaceService = workspaceService;
        this.sessionRepository = sessionRepository;
        this.browserManager = browserManager;
        this.clamAVScanner = clamAVScanner;
    }

    // =========================================================
    // UPLOAD FILE - SCAN BEFORE SAVING
    // =========================================================

    @PostMapping("/upload")
    public String uploadFile(
            @RequestParam("file") MultipartFile file,
            HttpSession httpSession) {

        Integer userId = (Integer) httpSession.getAttribute("userId");

        if (userId == null) {
            return "Please login first.";
        }

        if (file == null || file.isEmpty()) {
            return "File cannot be empty.";
        }

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            return "Invalid filename.";
        }

        String filename;

        try {
            filename = Path.of(originalFilename)
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

        Path target;
        Path workspace;
        Path tempDirectory;

        try {
            target = workspaceService.getSafeFilePath(userId, filename);
            workspace = workspaceService.getActiveWorkspace(userId);
            tempDirectory = workspace.resolve("temp").normalize();

            if (!tempDirectory.startsWith(workspace)
                    || !Files.isDirectory(tempDirectory)) {
                return "Upload rejected: temporary directory unavailable.";
            }

            if (Files.exists(target)) {
                return "File already exists: " + filename;
            }

        } catch (SecurityException e) {
            return "Access denied: " + e.getMessage();
        }

        Path stagedFile = null;

        try {
            stagedFile = Files.createTempFile(
                    tempDirectory, ".ps-upload-", ".scan");

            try (var input = file.getInputStream()) {
                Files.copy(
                        input,
                        stagedFile,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            // Fail closed: do not save if infected or scan fails.
            if (!clamAVScanner.isClean(stagedFile)) {
                return "Upload rejected: antivirus detected a threat.";
            }

            Files.createDirectories(target.getParent());

            if (Files.exists(target)) {
                return "File already exists: " + filename;
            }

            Files.move(stagedFile, target);
            stagedFile = null;

            updateLastActivity(userId);

            return "File uploaded and antivirus scan passed: " + filename;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Upload rejected: antivirus scan interrupted.";

        } catch (IOException | SecurityException e) {
            return "Upload rejected: antivirus scan failed or unavailable.";

        } finally {
            if (stagedFile != null) {
                try {
                    Files.deleteIfExists(stagedFile);
                } catch (IOException e) {
                    // Cleanup failure should be logged in production.
                }
            }
        }
    }

    // =========================================================
    // LIST FILES
    // =========================================================

    @GetMapping("/files")
    public List<String> listFiles(HttpSession httpSession)
            throws IOException {

        Integer userId = (Integer) httpSession.getAttribute("userId");

        if (userId == null) {
            throw new SecurityException("Please login first.");
        }

        Path filesDirectory =
                workspaceService.getActiveFilesDirectory(userId);

        if (!Files.exists(filesDirectory)
                || !Files.isDirectory(filesDirectory)) {
            return new ArrayList<>();
        }

        List<String> files;

        try (var paths = Files.list(filesDirectory)) {
            files = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .toList();
        }

        updateLastActivity(userId);
        return files;
    }

    // =========================================================
    // DOWNLOAD FILE FROM APPLICATION WORKSPACE
    // =========================================================

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String filename,
            HttpSession httpSession) throws IOException {

        Integer userId = (Integer) httpSession.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            Path file =
                    workspaceService.getSafeFilePath(userId, filename);

            if (!Files.isRegularFile(file)) {
                return ResponseEntity.notFound().build();
            }

            // Scan before serving the file.
            boolean clean;

            try {
                clean = clamAVScanner.isClean(file);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ResponseEntity.status(503).build();
            } catch (IOException e) {
                return ResponseEntity.status(503).build();
            }

            if (!clean) {
                Files.deleteIfExists(file);
                return ResponseEntity.status(410).build();
            }

            Resource resource = new UrlResource(file.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            updateLastActivity(userId);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" +
                                    file.getFileName() + "\""
                    )
                    .body(resource);

        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // =========================================================
    // DELETE FILE
    // =========================================================

    @DeleteMapping("/file/{filename}")
    public String deleteFile(
            @PathVariable String filename,
            HttpSession httpSession) throws IOException {

        Integer userId = (Integer) httpSession.getAttribute("userId");

        if (userId == null) {
            return "Please login first.";
        }

        try {
            Path file =
                    workspaceService.getSafeFilePath(userId, filename);

            if (!Files.isRegularFile(file)) {
                return "File not found.";
            }

            Files.delete(file);
            updateLastActivity(userId);

            return "File deleted successfully: " + filename;

        } catch (SecurityException e) {
            return "Access denied: " + e.getMessage();
        }
    }

    // =========================================================
    // OPEN SECURE WORKSPACE
    // =========================================================

    @PostMapping("/open")
    public String openWorkspace(HttpSession httpSession)
            throws IOException {

        Integer userId = (Integer) httpSession.getAttribute("userId");

        if (userId == null) {
            return "Please login first.";
        }

        try {
            workspaceService.openActiveWorkspace(userId);
            return "Secure workspace opened.";

        } catch (SecurityException e) {
            return "Access denied: " + e.getMessage();
        }
    }

    // =========================================================
    // OPEN SECURE BROWSER
    // =========================================================

    @PostMapping("/open-browser")
    public String openSecureBrowser(HttpSession httpSession) {

        Integer userId = (Integer) httpSession.getAttribute("userId");

        if (userId == null) {
            return "Please login first.";
        }

        try {
            var activeSession =
                    sessionRepository.findByUserIdAndStatus(userId, "ACTIVE");

            if (activeSession.isEmpty()) {
                return "No active secure session found.";
            }

            var session = activeSession.get();

            String sessionId = session.getSessionId();
            String workspacePath = session.getWorkspacePath();

            if (workspacePath == null || workspacePath.isBlank()) {
                return "Workspace path is not available.";
            }

            browserManager.openSecureBrowser(sessionId, workspacePath);

            session.setLastActivityTime(LocalDateTime.now());
            sessionRepository.save(session);

            return "Secure browser opened successfully. "
                    + "Downloads will be saved inside your secure workspace.";

        } catch (SecurityException e) {
            return "Access denied: " + e.getMessage();

        } catch (IllegalStateException e) {
            return e.getMessage();

        } catch (IOException e) {
            return "Unable to open secure browser: " + e.getMessage();
        }
    }

    // =========================================================
    // UPDATE LAST ACTIVITY
    // =========================================================

    private void updateLastActivity(Integer userId) {

        sessionRepository
                .findByUserIdAndStatus(userId, "ACTIVE")
                .ifPresent(session -> {
                    session.setLastActivityTime(LocalDateTime.now());
                    sessionRepository.save(session);
                });
    }
}
