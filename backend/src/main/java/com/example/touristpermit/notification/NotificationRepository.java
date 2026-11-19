package com.example.touristpermit.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    List<Notification> findByUserIdAndType(Long userId, NotificationType type);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :at WHERE n.user.id = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("at") Instant at);
}
