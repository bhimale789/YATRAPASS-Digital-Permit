package com.example.touristpermit.notification;

import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.notification.dto.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasRole('TOURIST')")
@Tag(name = "Tourist: Notifications", description = "Approval, rejection, payment and expiry notices")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "My notifications, newest first")
    public PageResponse<NotificationResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return notificationService.mine(pageable);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount());
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", notificationService.markAllRead());
    }
}
