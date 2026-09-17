package com.privacyshield;

import com.privacyshield.model.User;
import com.privacyshield.repository.UserRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class AdminPasswordReset implements CommandLineRunner {

    private final UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public AdminPasswordReset(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {

        Integer adminId = 16;

        User admin = userRepository
                .findById(adminId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Admin user not found"
                        )
                );

        String newPassword = "admin123";

        admin.setPasswordHash(
                passwordEncoder.encode(newPassword)
        );

        admin.setRole("ADMIN");
        admin.setStatus("ACTIVE");

        userRepository.save(admin);

        System.out.println(
                "===================================="
        );
        System.out.println(
                "ADMIN PASSWORD RESET SUCCESSFULLY"
        );
        System.out.println(
                "Username : " + admin.getUsername()
        );
        System.out.println(
                "Password : " + newPassword
        );
        System.out.println(
                "===================================="
        );
    }
}