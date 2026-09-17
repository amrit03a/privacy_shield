package com.privacyshield.controller;

import com.privacyshield.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    private final UserRepository userRepository;

    public TestController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/test-db")
    public String testDatabase() {

        long count = userRepository.count();

        return "Database connected. Users in database: " + count;
    }
}