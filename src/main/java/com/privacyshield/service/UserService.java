package com.privacyshield.service;

import com.privacyshield.model.User;
import com.privacyshield.repository.UserRepository;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    // =========================
    // REGISTER USER
    // =========================

    public User registerUser(String username, String password) {

        // Username and password have already been validated
        // by RegisterRequest using @Valid.

        // Normalize username so usernames are case-insensitive.
        String normalizedUsername =
                normalizeUsername(username);

        // Prevent username from being the same as password.
        if (normalizedUsername.equalsIgnoreCase(password)) {
            throw new IllegalArgumentException(
                    "Username and password cannot be the same"
            );
        }

        // Application-level duplicate check.
        if (userRepository
                .findByUsername(normalizedUsername)
                .isPresent()) {

            throw new IllegalArgumentException(
                    "Username already exists"
            );
        }

        User user = new User();

        // Store only normalized username.
        user.setUsername(normalizedUsername);

        // NEVER store plaintext password.
        user.setPasswordHash(
                passwordEncoder.encode(password)
        );

        // New registrations are normal users.
        user.setRole("USER");

        // New accounts are active.
        user.setStatus("ACTIVE");

        return userRepository.save(user);
    }


    // =========================
    // USER LOGIN
    // =========================

    public User loginUser(
            String username,
            String password) {

        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {

            throw new IllegalArgumentException(
                    "Invalid username or password"
            );
        }

        // Apply the same username normalization used
        // during registration.
        String normalizedUsername =
                normalizeUsername(username);

        User user = userRepository
                .findByUsername(normalizedUsername)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid username or password"
                        )
                );

        // Inactive users cannot login.
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalStateException(
                    "User account is not active"
            );
        }

        // Verify the submitted password against
        // the stored BCrypt hash.
        if (!passwordEncoder.matches(
                password,
                user.getPasswordHash())) {

            throw new IllegalArgumentException(
                    "Invalid username or password"
            );
        }

        return user;
    }


    // =========================
    // ADMIN LOGIN
    // =========================

    public User loginAdmin(
            String username,
            String password) {

        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {

            throw new IllegalArgumentException(
                    "Invalid username or password"
            );
        }

        // Normalize username for case-insensitive login.
        String normalizedUsername =
                normalizeUsername(username);

        User user = userRepository
                .findByUsername(normalizedUsername)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid username or password"
                        )
                );

        // Admin must be active.
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalStateException(
                    "Admin account is not active"
            );
        }

        // Only ADMIN users can use admin login.
        if (!"ADMIN".equals(user.getRole())) {
            throw new SecurityException(
                    "Access denied: Admin privileges required"
            );
        }

        // Verify BCrypt password.
        if (!passwordEncoder.matches(
                password,
                user.getPasswordHash())) {

            throw new IllegalArgumentException(
                    "Invalid username or password"
            );
        }

        return user;
    }


    // =========================
    // RESET PASSWORD
    // =========================

    public User resetPassword(
            Integer userId,
            String newPassword) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "User ID cannot be null"
            );
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException(
                    "Password cannot be empty"
            );
        }

        // Keep the same 8-30 character policy.
        if (newPassword.length() < 8) {
            throw new IllegalArgumentException(
                    "Password must be at least 8 characters"
            );
        }

        if (newPassword.length() > 30) {
            throw new IllegalArgumentException(
                    "Password must not exceed 30 characters"
            );
        }

        // Strong password requirements.
        if (!newPassword.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException(
                    "Password must contain at least one uppercase letter"
            );
        }

        if (!newPassword.matches(".*[a-z].*")) {
            throw new IllegalArgumentException(
                    "Password must contain at least one lowercase letter"
            );
        }

        if (!newPassword.matches(".*\\d.*")) {
            throw new IllegalArgumentException(
                    "Password must contain at least one number"
            );
        }

        if (!newPassword.matches(
                ".*[^A-Za-z0-9\\s].*")) {

            throw new IllegalArgumentException(
                    "Password must contain at least one special character"
            );
        }

        if (newPassword.matches(".*\\s.*")) {
            throw new IllegalArgumentException(
                    "Password cannot contain spaces or whitespace"
            );
        }

        User user = userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found"
                        )
                );

        // Prevent username from becoming the password.
        if (user.getUsername() != null
                && user.getUsername()
                    .equalsIgnoreCase(newPassword)) {

            throw new IllegalArgumentException(
                    "Username and password cannot be the same"
            );
        }

        // Hash new password before storing.
        user.setPasswordHash(
                passwordEncoder.encode(newPassword)
        );

        return userRepository.save(user);
    }


    // =========================
    // ACTIVATE / DEACTIVATE USER
    // =========================

    public User updateUserStatus(
            Integer userId,
            String status) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "User ID cannot be null"
            );
        }

        if (!"ACTIVE".equals(status)
                && !"INACTIVE".equals(status)) {

            throw new IllegalArgumentException(
                    "Invalid status. Use ACTIVE or INACTIVE."
            );
        }

        User user = userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found"
                        )
                );

        user.setStatus(status);

        return userRepository.save(user);
    }


    // =========================
    // USERNAME NORMALIZATION
    // =========================

    private String normalizeUsername(String username) {

        if (username == null) {
            return null;
        }

        return username
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}