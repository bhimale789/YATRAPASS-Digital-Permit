package com.example.touristpermit.tourist;

import com.example.touristpermit.application.PermitApplicationRepository;
import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditService;
import com.example.touristpermit.auth.RefreshTokenRepository;
import com.example.touristpermit.common.exception.ConflictException;
import com.example.touristpermit.common.exception.ResourceNotFoundException;
import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.SearchPatterns;
import com.example.touristpermit.permit.PermitRepository;
import com.example.touristpermit.security.CurrentUser;
import com.example.touristpermit.tourist.dto.AdminTouristDetailResponse;
import com.example.touristpermit.tourist.dto.TouristProfileResponse;
import com.example.touristpermit.tourist.dto.TouristSummaryResponse;
import com.example.touristpermit.tourist.dto.UpdateProfileRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;

@Service
public class TouristService {

    private final TouristProfileRepository profiles;
    private final PermitApplicationRepository applications;
    private final PermitRepository permits;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;
    private final Clock clock;

    public TouristService(TouristProfileRepository profiles, PermitApplicationRepository applications,
                          PermitRepository permits, RefreshTokenRepository refreshTokens, AuditService audit,
                          Clock clock) {
        this.profiles = profiles;
        this.applications = applications;
        this.permits = permits;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.clock = clock;
    }

    /** Profile of the authenticated tourist. */
    @Transactional(readOnly = true)
    public TouristProfile currentProfile() {
        Long userId = CurrentUser.require().id();
        return profiles.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Tourist profile not found"));
    }

    @Transactional(readOnly = true)
    public TouristProfileResponse getMyProfile() {
        return TouristProfileResponse.from(currentProfile());
    }

    @Transactional
    public TouristProfileResponse updateMyProfile(UpdateProfileRequest r) {
        TouristProfile p = currentProfile();
        String passport = r.passportNumber().trim().toUpperCase(Locale.ROOT);
        if (profiles.existsByPassportNumberAndIdNot(passport, p.getId())) {
            throw new ConflictException("This passport number is already registered to another account");
        }
        p.setFirstName(r.firstName().trim());
        p.setLastName(r.lastName().trim());
        p.setPassportNumber(passport);
        p.setNationality(r.nationality().trim());
        p.setDateOfBirth(r.dateOfBirth());
        p.setGender(r.gender());
        p.setPhone(r.phone().trim());
        p.setAddress(r.address());
        p.setEmergencyContact(r.emergencyContact());
        profiles.save(p);
        audit.record(AuditAction.TOURIST_UPDATED, "TouristProfile", p.getId(), Map.of("by", "self"));
        return TouristProfileResponse.from(p);
    }

    @Transactional(readOnly = true)
    public PageResponse<TouristSummaryResponse> search(String query, Pageable pageable) {
        return PageResponse.of(profiles.search(SearchPatterns.contains(query), pageable), TouristSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminTouristDetailResponse getForAdmin(Long id) {
        TouristProfile p = find(id);
        return AdminTouristDetailResponse.from(p, applications.countByTouristId(id),
                permits.countByTouristId(id));
    }

    /** Enables/disables the tourist's login. Administrators cannot edit the tourist's identity data. */
    @Transactional
    public AdminTouristDetailResponse setAccountEnabled(Long id, boolean enabled) {
        TouristProfile p = find(id);
        p.getUser().setEnabled(enabled);
        if (!enabled) {
            refreshTokens.revokeAllForUser(p.getUser().getId(), clock.instant());
        }
        audit.record(AuditAction.TOURIST_UPDATED, "TouristProfile", id, Map.of("enabled", enabled));
        return getForAdmin(id);
    }

    private TouristProfile find(Long id) {
        return profiles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tourist not found"));
    }
}
