package com.privacyshield.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
public class Session {

    // =========================================================
    // SESSION ID
    // =========================================================

    @Id
    @Column(name = "session_id")
    private String sessionId;


    // =========================================================
    // USER ID
    // =========================================================

    @Column(name = "user_id")
    private Integer userId;


    // =========================================================
    // DOCKER CONTAINER NAME
    // =========================================================

    @Column(name = "container_name")
    private String containerName;


    // =========================================================
    // DESKTOP PORT
    // =========================================================

    @Column(name = "desktop_port")
    private Integer desktopPort;


    // =========================================================
    // SESSION TIMES
    // =========================================================

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "last_activity_time")
    private LocalDateTime lastActivityTime;


    // =========================================================
    // STATUS
    // =========================================================

    @Column(name = "status")
    private String status;

    @Column(name = "workspace_path")
    private String workspacePath;

    // =========================================================
    // GETTERS AND SETTERS
    // =========================================================

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }


    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }


    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }


    public Integer getDesktopPort() {
        return desktopPort;
    }

    public void setDesktopPort(Integer desktopPort) {
        this.desktopPort = desktopPort;
    }


    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }


    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }


    public LocalDateTime getLastActivityTime() {
        return lastActivityTime;
    }

    public void setLastActivityTime(LocalDateTime lastActivityTime) {
        this.lastActivityTime = lastActivityTime;
    }


    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public String getWorkspacePath() {
        return workspacePath;
    }

    public void setWorkspacePath(String workspacePath) {
        this.workspacePath = workspacePath;
    }
}