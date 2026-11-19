package com.example.touristpermit.verification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

public interface PermitVerificationRepository extends JpaRepository<PermitVerification, Long>,
        JpaSpecificationExecutor<PermitVerification> {

    @Override
    @EntityGraph(attributePaths = {"permit", "permit.area", "verifiedBy"})
    Page<PermitVerification> findAll(Specification<PermitVerification> spec, Pageable pageable);

    long countByVerifiedAtGreaterThanEqual(Instant since);

    long countByResultAndVerifiedAtGreaterThanEqual(VerificationResult result, Instant since);

    long countByPermitId(Long permitId);

    @EntityGraph(attributePaths = {"permit", "permit.area", "verifiedBy"})
    List<PermitVerification> findTop10ByOrderByVerifiedAtDesc();

    // ---- per-officer queries (officer dashboard)

    long countByVerifiedById(Long userId);

    long countByVerifiedByIdAndVerifiedAtGreaterThanEqual(Long userId, Instant since);

    long countByVerifiedByIdAndResultAndVerifiedAtGreaterThanEqual(Long userId, VerificationResult result,
                                                                   Instant since);

    @EntityGraph(attributePaths = {"permit", "permit.area"})
    List<PermitVerification> findTop15ByVerifiedByIdOrderByVerifiedAtDesc(Long userId);
}
