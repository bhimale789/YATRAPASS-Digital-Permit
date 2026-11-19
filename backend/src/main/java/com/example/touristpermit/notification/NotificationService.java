package com.example.touristpermit.notification;

import com.example.touristpermit.common.exception.ResourceNotFoundException;
import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.notification.dto.NotificationResponse;
import com.example.touristpermit.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Creates notifications (in-app + queued email/SMS) and serves the current user's inbox.
 * Creation joins the caller's transaction, so a notification exists if and only if the
 * event that caused it was committed.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final JdbcTemplate jdbc;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, JdbcTemplate jdbc,
                               NotificationProperties properties, Clock clock) {
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Idempotent: a second call with the same {@code dedupKey} does nothing.
     *
     * @return true if a new notification was created
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean notify(Long userId, NotificationType type, String title, String body, String link,
                          String dedupKey) {
        List<Long> created = jdbc.query("""
                        INSERT INTO notifications (user_id, type, title, body, link, dedup_key)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (dedup_key) DO NOTHING
                        RETURNING id
                        """, (rs, i) -> rs.getLong(1),
                userId, type.name(), truncate(title, 200), truncate(body, 2000), link, truncate(dedupKey, 150));
        if (created.isEmpty()) {
            return false;
        }
        Long id = created.getFirst();
        if (properties.emailEnabled()) {
            queue(id, DeliveryChannel.EMAIL);
        }
        if (properties.smsEnabled()) {
            queue(id, DeliveryChannel.SMS);
        }
        return true;
    }

    private void queue(Long notificationId, DeliveryChannel channel) {
        jdbc.update("INSERT INTO notification_deliveries (notification_id, channel) VALUES (?, ?)",
                notificationId, channel.name());
    }

    // ---------------------------------------------------------------- inbox

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> mine(Pageable pageable) {
        return PageResponse.of(notifications.findByUserIdOrderByCreatedAtDescIdDesc(CurrentUser.require().id(),
                pageable), NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notifications.countByUserIdAndReadAtIsNull(CurrentUser.require().id());
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        Notification n = notifications.findByIdAndUserId(id, CurrentUser.require().id())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        n.markRead(clock.instant());
        return NotificationResponse.from(n);
    }

    @Transactional
    public int markAllRead() {
        return notifications.markAllRead(CurrentUser.require().id(), clock.instant());
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
