package fr.medislot.repository;

import java.time.Instant;

public record AuditLine(
    Long id, String action, Long appointmentId, String practitioner, Instant createdAt) {}
