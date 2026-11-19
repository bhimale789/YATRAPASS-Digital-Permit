package com.example.touristpermit.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p JOIN FETCH p.application a JOIN FETCH a.tourist WHERE p.transactionUuid = :uuid")
    Optional<Payment> findByTransactionUuidForUpdate(@Param("uuid") String uuid);

    Optional<Payment> findByTransactionUuid(String transactionUuid);

    /** Open (pending / under review) applications with a fee and no completed payment. */
    @Query("""
            SELECT count(a) FROM PermitApplication a
            WHERE a.status IN (com.example.touristpermit.application.ApplicationStatus.PENDING,
                               com.example.touristpermit.application.ApplicationStatus.UNDER_REVIEW)
              AND a.feeAmount > 0
              AND NOT EXISTS (SELECT 1 FROM Payment p WHERE p.application = a
                              AND p.status = com.example.touristpermit.payment.PaymentStatus.COMPLETED)
            """)
    long countUnpaidOpenApplications();

    boolean existsByApplicationIdAndStatus(Long applicationId, PaymentStatus status);

    List<Payment> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    @Query("SELECT p FROM Payment p WHERE p.application.id IN :ids ORDER BY p.createdAt DESC")
    List<Payment> findByApplicationIds(@Param("ids") Collection<Long> applicationIds);

    @Query("SELECT p.transactionUuid FROM Payment p WHERE p.status = com.example.touristpermit.payment.PaymentStatus.PENDING "
            + "AND p.provider = com.example.touristpermit.payment.PaymentProvider.ESEWA AND p.createdAt < :before")
    List<String> findStalePendingEsewa(@Param("before") Instant before);

    @Query("SELECT coalesce(sum(p.amount), 0) FROM Payment p WHERE p.status = com.example.touristpermit.payment.PaymentStatus.COMPLETED")
    BigDecimal totalCollected();

    @Query("SELECT coalesce(sum(p.amount), 0) FROM Payment p WHERE p.status = com.example.touristpermit.payment.PaymentStatus.COMPLETED "
            + "AND p.completedAt >= :since")
    BigDecimal collectedSince(@Param("since") Instant since);

    @Override
    @EntityGraph(attributePaths = {"application", "application.area", "application.tourist"})
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);
}
