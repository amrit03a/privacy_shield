package com.privacyshield;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PrivacyShieldApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                PrivacyShieldApplication.class,
                args
        );
    }
}