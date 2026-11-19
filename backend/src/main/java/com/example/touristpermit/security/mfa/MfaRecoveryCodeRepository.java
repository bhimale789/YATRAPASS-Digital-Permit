package com.example.touristpermit.security.mfa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {

    /** Locked so one code can never be redeemed twice concurrently. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM MfaRecoveryCode c WHERE c.user.id = :userId AND c.codeHash = :hash AND c.usedAt IS NULL")
    Optional<MfaRecoveryCode> findUnusedForUpdate(@Param("userId") Long userId, @Param("hash") String hash);

    @Query("SELECT count(c) FROM MfaRecoveryCode c WHERE c.user.id = :userId AND c.usedAt IS NULL")
    long countUnused(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM MfaRecoveryCode c WHERE c.user.id = :userId")
    int deleteAllForUser(@Param("userId") Long userId);
}
