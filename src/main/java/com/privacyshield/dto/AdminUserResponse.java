package com.privacyshield.dto;

import com.privacyshield.model.User;

public class AdminUserResponse {

    private Integer userId;
    private String username;
    private String role;
    private String status;

    public AdminUserResponse(User user) {
        this.userId = user.getUserId();
        this.username = user.getUsername();
        this.role = user.getRole();
        this.status = user.getStatus();
    }

    public Integer getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }
}