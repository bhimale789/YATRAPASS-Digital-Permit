package com.example.touristpermit.qr;

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

import java.time.Instant;

/** A QR verification token. The raw token is never stored in plaintext. */
@Entity
@Table(name = "verification_tokens")
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permit_id", nullable = false, updatable = false)
    private Permit permit;

    @Column(name = "token_hash", nullable = false, unique = true, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "token_ciphertext", nullable = false, updatable = false)
    private String tokenCiphertext;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenStatus status = TokenStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by")
    private User revokedBy;

    @Column(name = "revocation_reason")
    private String revocationReason;

    protected VerificationToken() {
    }

    public VerificationToken(Permit permit, String tokenHash, String tokenCiphertext, User createdBy, Instant createdAt) {
        this.permit = permit;
        this.tokenHash = tokenHash;
        this.tokenCiphertext = tokenCiphertext;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public void revoke(User by, Instant at, String reason) {
        if (status == TokenStatus.REVOKED) {
            return;
        }
        status = TokenStatus.REVOKED;
        revokedBy = by;
        revokedAt = at;
        revocationReason = reason;
    }

    public Long getId() {
        return id;
    }

    public Permit getPermit() {
        return permit;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getTokenCiphertext() {
        return tokenCiphertext;
    }

    public TokenStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevocationReason() {
        return revocationReason;
    }
}
