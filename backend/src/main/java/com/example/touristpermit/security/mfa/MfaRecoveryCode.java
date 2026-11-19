package com.example.touristpermit.security.mfa;

import com.example.touristpermit.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** One-time backup code for two-factor login; only its SHA-256 hash is stored. */
@Entity
@Table(name = "mfa_recovery_codes")
public class MfaRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "code_hash", nullable = false, unique = true, updatable = false, length = 64)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MfaRecoveryCode() {
    }

    public MfaRecoveryCode(User user, String codeHash) {
        this.user = user;
        this.codeHash = codeHash;
    }

    public void markUsed(Instant at) {
        this.usedAt = at;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
