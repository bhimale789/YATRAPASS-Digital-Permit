package com.example.touristpermit.verification;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/** Optional filters for the verification history. Null arguments add no restriction. */
final class VerificationSpecifications {

    private VerificationSpecifications() {
    }

    static Specification<PermitVerification> filter(VerificationResult result, Long permitId, Instant from, Instant to) {
        Specification<PermitVerification> spec = (root, query, cb) -> cb.conjunction();
        if (result != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("result"), result));
        }
        if (permitId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("permit").get("id"), permitId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("verifiedAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("verifiedAt"), to));
        }
        return spec;
    }
}
