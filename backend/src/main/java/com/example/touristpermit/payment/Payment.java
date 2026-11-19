package com.example.touristpermit.payment;

import com.example.touristpermit.application.PermitApplication;
import com.example.touristpermit.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private PermitApplication application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentProvider provider;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private String currency = "NPR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "transaction_uuid", nullable = false, unique = true, updatable = false)
    private String transactionUuid;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "failure_reason")
    private String failureReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by", updatable = false)
    private User recordedBy;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Payment() {
    }

    public Payment(PermitApplication application, PaymentProvider provider, BigDecimal amount, String transactionUuid,
                   User recordedBy) {
        this.application = application;
        this.provider = provider;
        this.amount = amount;
        this.transactionUuid = transactionUuid;
        this.recordedBy = recordedBy;
    }

    public void complete(String providerRef, Instant at) {
        this.status = PaymentStatus.COMPLETED;
        this.providerRef = providerRef;
        this.failureReason = null;
        this.completedAt = at;
    }

    public void close(PaymentStatus finalStatus, String reason) {
        if (status == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("A completed payment cannot be closed");
        }
        this.status = finalStatus;
        this.failureReason = reason;
    }

    public Long getId() {
        return id;
    }

    public PermitApplication getApplication() {
        return application;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getTransactionUuid() {
        return transactionUuid;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
