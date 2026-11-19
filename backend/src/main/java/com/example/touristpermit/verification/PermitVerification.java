package com.example.touristpermit.verification;

import com.example.touristpermit.permit.Permit;
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

import java.math.BigDecimal;
import java.time.Instant;

/** One QR scan by an administrator/officer, valid or not. */
@Entity
@Table(name = "permit_verifications")
public class PermitVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permit_id", updatable = false)
    private Permit permit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verified_by", nullable = false, updatable = false)
    private User verifiedBy;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private Instant verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private VerificationResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", updatable = false)
    private VerificationReason failureReason;

    @Column(name = "ip_address", updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", updatable = false)
    private String userAgent;

    @Column(updatable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(updatable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    protected PermitVerification() {
    }

    public PermitVerification(Permit permit, User verifiedBy, Instant verifiedAt, VerificationResult result,
                              VerificationReason failureReason, String ipAddress, String userAgent,
                              BigDecimal latitude, BigDecimal longitude) {
        this.permit = permit;
        this.verifiedBy = verifiedBy;
        this.verifiedAt = verifiedAt;
        this.result = result;
        this.failureReason = failureReason;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() {
        return id;
    }

    public Permit getPermit() {
        return permit;
    }

    public User getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public VerificationResult getResult() {
        return result;
    }

    public VerificationReason getFailureReason() {
        return failureReason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }
}
