package com.example.touristpermit.audit;

import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.RequestInfo;
import com.example.touristpermit.security.AuthenticatedUser;
import com.example.touristpermit.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Records auditable actions. Callers must pass only non-sensitive metadata
 * (no passwords, tokens or passport numbers).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Records an action by the currently authenticated user (if any). Joins the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, String entityType, Object entityId, Map<String, Object> metadata) {
        AuthenticatedUser actor = CurrentUser.optional().orElse(null);
        record(actor == null ? null : actor.id(), actor == null ? null : actor.role().name(),
                action, entityType, entityId, metadata);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, String actorRole, AuditAction action, String entityType, Object entityId,
                       Map<String, Object> metadata) {
        String id = entityId == null ? null : entityId.toString();
        repository.save(new AuditLog(actorId, actorRole, action, entityType, id,
                RequestInfo.current().ipAddress(), metadata));
        log.info("audit action={} entity={} entityId={} actorId={}", action, entityType, id, actorId);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(AuditAction action, String entityType, String entityId,
                                                 Pageable pageable) {
        Specification<AuditLog> spec = (root, q, cb) -> cb.conjunction();
        if (action != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("action"), action));
        }
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (entityId != null && !entityId.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityId"), entityId));
        }
        return PageResponse.of(repository.findAll(spec, pageable), AuditLogResponse::from);
    }
}
