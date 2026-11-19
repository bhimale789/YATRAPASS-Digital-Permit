package com.example.touristpermit.notification.dto;

import com.example.touristpermit.notification.Notification;
import com.example.touristpermit.notification.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        @Schema(description = "In-app path to open, e.g. /tourist/permits") String link,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
                n.getReadAt() != null, n.getCreatedAt());
    }
}
