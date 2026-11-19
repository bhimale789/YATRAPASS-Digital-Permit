package com.example.touristpermit.area;

import com.example.touristpermit.area.dto.AreaRequest;
import com.example.touristpermit.area.dto.AreaResponse;
import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditService;
import com.example.touristpermit.common.exception.ConflictException;
import com.example.touristpermit.common.exception.ResourceNotFoundException;
import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.SearchPatterns;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AreaService {

    private final PermitAreaRepository areas;
    private final AuditService audit;

    public AreaService(PermitAreaRepository areas, AuditService audit) {
        this.areas = areas;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AreaResponse> listActive() {
        return areas.findByActiveTrue(Sort.by("name")).stream().map(AreaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AreaResponse getActive(Long id) {
        return areas.findByIdAndActiveTrue(id).map(AreaResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Area not found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<AreaResponse> search(String query, Boolean active, Pageable pageable) {
        return PageResponse.of(areas.search(SearchPatterns.contains(query), active, pageable), AreaResponse::from);
    }

    @Transactional(readOnly = true)
    public AreaResponse get(Long id) {
        return AreaResponse.from(find(id));
    }

    @Transactional
    public AreaResponse create(AreaRequest request) {
        String code = normalizeCode(request.code());
        if (areas.existsByCode(code)) {
            throw new ConflictException("An area with this code already exists");
        }
        PermitArea area = new PermitArea(request.name().trim(), code, request.maximumDays());
        apply(area, request);
        area = areas.save(area);
        audit.record(AuditAction.AREA_CREATED, "PermitArea", area.getId(), Map.of("code", code));
        return AreaResponse.from(area);
    }

    @Transactional
    public AreaResponse update(Long id, AreaRequest request) {
        PermitArea area = find(id);
        String code = normalizeCode(request.code());
        if (areas.existsByCodeAndIdNot(code, id)) {
            throw new ConflictException("An area with this code already exists");
        }
        area.setCode(code);
        apply(area, request);
        audit.record(AuditAction.AREA_UPDATED, "PermitArea", id, Map.of("code", code, "active", area.isActive()));
        return AreaResponse.from(area);
    }

    @Transactional
    public AreaResponse setActive(Long id, boolean active) {
        PermitArea area = find(id);
        area.setActive(active);
        audit.record(AuditAction.AREA_UPDATED, "PermitArea", id, Map.of("active", active));
        return AreaResponse.from(area);
    }

    private void apply(PermitArea area, AreaRequest r) {
        area.setName(r.name().trim());
        area.setDescription(r.description());
        area.setDistrict(r.district());
        area.setProvince(r.province());
        area.setPermitRequired(r.permitRequired());
        area.setMaximumDays(r.maximumDays());
        area.setFee(r.fee());
        area.setActive(r.active());
        area.setLocation(r.latitude(), r.longitude());
    }

    private PermitArea find(Long id) {
        return areas.findById(id).orElseThrow(() -> new ResourceNotFoundException("Area not found"));
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
