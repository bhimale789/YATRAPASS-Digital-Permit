package com.example.touristpermit.audit;

import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(
        Long id,
        Instant createdAt,
        AuditAction action,
        Long actorId,
        String actorRole,
        String entityType,
        String entityId,
        String ipAddress,
        Map<String, Object> metadata
) {

    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getCreatedAt(), a.getAction(), a.getActorId(), a.getActorRole(),
                a.getEntityType(), a.getEntityId(), a.getIpAddress(), a.getMetadata());
    }
}
