package com.example.touristpermit.road;

import com.example.touristpermit.area.PermitAreaRepository;
import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditService;
import com.example.touristpermit.common.exception.BusinessRuleException;
import com.example.touristpermit.common.exception.ResourceNotFoundException;
import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.SearchPatterns;
import com.example.touristpermit.road.dto.RoadConditionRequest;
import com.example.touristpermit.road.dto.RoadConditionResponse;
import com.example.touristpermit.security.CurrentUser;
import com.example.touristpermit.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class RoadConditionService {

    /** Upper bound for map payloads; maps never need more than this many active conditions. */
    static final int MAP_LIMIT = 500;
    static final int MAX_LINE_POINTS = 500;

    private final RoadConditionRepository conditions;
    private final PermitAreaRepository areas;
    private final UserRepository users;
    private final AuditService audit;

    public RoadConditionService(RoadConditionRepository conditions, PermitAreaRepository areas, UserRepository users,
                                AuditService audit) {
        this.conditions = conditions;
        this.areas = areas;
        this.users = users;
        this.audit = audit;
    }

    /**
     * Active conditions for maps. With {@code areaId}, returns that area's conditions plus
     * general ones (not tied to any area) when {@code includeGeneral} is true.
     */
    @Transactional(readOnly = true)
    public List<RoadConditionResponse> activeForMap(Long areaId, boolean includeGeneral) {
        Specification<RoadCondition> spec = (root, q, cb) -> cb.isTrue(root.get("active"));
        if (areaId != null) {
            spec = spec.and((root, q, cb) -> includeGeneral
                    ? cb.or(cb.equal(root.get("area").get("id"), areaId), cb.isNull(root.get("area")))
                    : cb.equal(root.get("area").get("id"), areaId));
        }
        return conditions.findAll(spec, PageRequest.of(0, MAP_LIMIT, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .map(RoadConditionResponse::publicView).getContent();
    }

    @Transactional(readOnly = true)
    public PageResponse<RoadConditionResponse> search(String query, RoadStatus status, Long areaId, Boolean active,
                                                      Pageable pageable) {
        Specification<RoadCondition> spec = (root, q, cb) -> cb.conjunction();
        String like = SearchPatterns.contains(query);
        if (like != null) {
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), like, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like, '\\')));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (areaId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("area").get("id"), areaId));
        }
        if (active != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("active"), active));
        }
        return PageResponse.of(conditions.findAll(spec, pageable), RoadConditionResponse::adminView);
    }

    @Transactional(readOnly = true)
    public RoadConditionResponse get(Long id) {
        return RoadConditionResponse.adminView(find(id));
    }

    @Transactional
    public RoadConditionResponse create(RoadConditionRequest request) {
        RoadCondition condition = new RoadCondition(users.getReferenceById(CurrentUser.require().id()));
        apply(condition, request);
        condition = conditions.save(condition);
        audit.record(AuditAction.ROAD_CONDITION_CREATED, "RoadCondition", condition.getId(),
                Map.of("status", condition.getStatus().name(), "title", condition.getTitle()));
        return RoadConditionResponse.adminView(condition);
    }

    @Transactional
    public RoadConditionResponse update(Long id, RoadConditionRequest request) {
        RoadCondition condition = find(id);
        apply(condition, request);
        condition.setUpdatedBy(users.getReferenceById(CurrentUser.require().id()));
        conditions.saveAndFlush(condition);
        audit.record(AuditAction.ROAD_CONDITION_UPDATED, "RoadCondition", id,
                Map.of("status", condition.getStatus().name(), "active", condition.isActive()));
        return RoadConditionResponse.adminView(condition);
    }

    @Transactional
    public void delete(Long id) {
        RoadCondition condition = find(id);
        conditions.delete(condition);
        audit.record(AuditAction.ROAD_CONDITION_DELETED, "RoadCondition", id, Map.of("title", condition.getTitle()));
    }

    private void apply(RoadCondition c, RoadConditionRequest r) {
        validateGeometry(r.geometryType(), r.coordinates());
        c.setTitle(r.title().trim());
        c.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        c.setStatus(r.status());
        c.setGeometryType(r.geometryType());
        c.setCoordinates(r.coordinates());
        c.setActive(r.active());
        c.setArea(r.areaId() == null ? null
                : areas.findById(r.areaId()).orElseThrow(() -> new ResourceNotFoundException("Area not found")));
    }

    static void validateGeometry(GeometryType type, List<Coordinate> coordinates) {
        int n = coordinates.size();
        if (type == GeometryType.POINT && n != 1) {
            throw new BusinessRuleException("A point needs exactly one coordinate");
        }
        if (type == GeometryType.LINE && (n < 2 || n > MAX_LINE_POINTS)) {
            throw new BusinessRuleException("A road segment needs between 2 and " + MAX_LINE_POINTS + " coordinates");
        }
    }

    private RoadCondition find(Long id) {
        return conditions.findWithAreaById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Road condition not found"));
    }
}
