package com.privacyshield.service;

import com.privacyshield.model.AuditLog;
import com.privacyshield.repository.AuditLogRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(
            AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(
            Integer userId,
            String event,
            String description) {

        AuditLog auditLog = new AuditLog();

        auditLog.setUserId(userId);
        auditLog.setEvent(event);
        auditLog.setDescription(description);
        auditLog.setTimestamp(LocalDateTime.now());

        auditLogRepository.save(auditLog);
    }
}