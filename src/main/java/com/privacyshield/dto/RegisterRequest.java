package com.privacyshield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "Username cannot be empty")
    @Size(
            min = 3,
            max = 30,
            message = "Username must be between 3 and 30 characters"
    )
    @Pattern(
            regexp = "^[A-Za-z0-9_-]+$",
            message = "Username may contain only letters, numbers, underscore, and hyphen"
    )
    private String username;


    @NotBlank(message = "Password cannot be empty")
    @Size(
            min = 8,
            max = 30,
            message = "Password must be between 8 and 30 characters"
    )
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).+$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character"
    )
    private String password;


    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }


    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}