package com.example.touristpermit.qr;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    @EntityGraph(attributePaths = {"permit", "permit.area", "permit.tourist"})
    Optional<VerificationToken> findByTokenHash(String tokenHash);

    Optional<VerificationToken> findByPermitIdAndStatus(Long permitId, TokenStatus status);

    List<VerificationToken> findByPermitIdOrderByCreatedAtDesc(Long permitId);

    @Query("""
            SELECT t.permit.id FROM VerificationToken t
            WHERE t.permit.id IN :permitIds AND t.status = com.example.touristpermit.qr.TokenStatus.ACTIVE
            """)
    Set<Long> findPermitIdsWithActiveToken(@Param("permitIds") Collection<Long> permitIds);
}
