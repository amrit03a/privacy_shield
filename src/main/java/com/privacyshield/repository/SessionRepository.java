package com.privacyshield.repository;

import com.privacyshield.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionRepository
        extends JpaRepository<Session, String> {

    List<Session> findByUserId(Integer userId);

    Optional<Session> findByUserIdAndStatus(
            Integer userId,
            String status
    );

    long countByUserIdAndStatus(
            Integer userId,
            String status
    );

    List<Session> findByStatus(
            String status
    );
}