package com.example.touristpermit.notification;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    @Query(value = """
            SELECT id FROM notification_deliveries
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, id LIMIT :limit
            """, nativeQuery = true)
    List<Long> findDueIds(@Param("now") Instant now, @Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM NotificationDelivery d JOIN FETCH d.notification n JOIN FETCH n.user WHERE d.id = :id")
    Optional<NotificationDelivery> findForUpdate(@Param("id") Long id);

    List<NotificationDelivery> findByNotificationId(Long notificationId);
}
