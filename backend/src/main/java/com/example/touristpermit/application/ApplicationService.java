package com.example.touristpermit.application;

import com.example.touristpermit.application.dto.ApplicationRequest;
import com.example.touristpermit.application.dto.ApplicationResponse;
import com.example.touristpermit.area.PermitArea;
import com.example.touristpermit.area.PermitAreaRepository;
import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditService;
import com.example.touristpermit.common.exception.BusinessRuleException;
import com.example.touristpermit.common.exception.ConflictException;
import com.example.touristpermit.common.exception.ResourceNotFoundException;
import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.ReferenceNumberGenerator;
import com.example.touristpermit.common.util.SearchPatterns;
import com.example.touristpermit.config.AppProperties;
import com.example.touristpermit.document.DocumentService;
import com.example.touristpermit.notification.event.ApplicationDecidedEvent;
import com.example.touristpermit.payment.PaymentService;
import com.example.touristpermit.payment.dto.PaymentDtos.PaymentSummary;
import com.example.touristpermit.permit.PermitRepository;
import com.example.touristpermit.security.CurrentUser;
import com.example.touristpermit.tourist.TouristProfile;
import com.example.touristpermit.tourist.TouristService;
import com.example.touristpermit.tourist.dto.TouristSummaryResponse;
import com.example.touristpermit.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

/**
 * Permit application use cases. Status changes go exclusively through
 * {@link PermitApplication#transitionTo}, which enforces the lifecycle.
 */
@Service
public class ApplicationService {

    private final PermitApplicationRepository applications;
    private final PermitAreaRepository areas;
    private final TouristService touristService;
    private final DocumentService documentService;
    private final PermitRepository permits;
    private final ReferenceNumberGenerator numbers;
    private final UserRepository users;
    private final AuditService audit;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final boolean allowOverlapping;

    public ApplicationService(PermitApplicationRepository applications, PermitAreaRepository areas,
                              TouristService touristService, DocumentService documentService,
                              PermitRepository permits, ReferenceNumberGenerator numbers, UserRepository users,
                              AuditService audit, PaymentService paymentService, ApplicationEventPublisher events,
                              Clock clock, AppProperties properties) {
        this.applications = applications;
        this.areas = areas;
        this.touristService = touristService;
        this.documentService = documentService;
        this.permits = permits;
        this.numbers = numbers;
        this.users = users;
        this.audit = audit;
        this.paymentService = paymentService;
        this.events = events;
        this.clock = clock;
        this.allowOverlapping = properties.permits().allowOverlapping();
    }

    // ---------------------------------------------------------------- tourist

    @Transactional
    public ApplicationResponse submit(ApplicationRequest request) {
        TouristProfile tourist = touristService.currentProfile();
        if (!tourist.isComplete()) {
            throw new BusinessRuleException("Complete your profile (passport, nationality, date of birth, phone) "
                    + "before applying for a permit");
        }
        PermitArea area = areas.findById(request.areaId())
                .orElseThrow(() -> new ResourceNotFoundException("Area not found"));
        validateRequest(tourist, area, request.startDate(), request.endDate());

        PermitApplication application = applications.save(new PermitApplication(
                numbers.next(ReferenceNumberGenerator.Kind.APPLICATION), tourist, area, request.purpose().trim(),
                request.startDate(), request.endDate(), clock.instant()));
        audit.record(AuditAction.APPLICATION_SUBMITTED, "PermitApplication", application.getId(),
                Map.of("applicationNumber", application.getApplicationNumber(), "areaId", area.getId()));
        return ApplicationResponse.summary(application).withPayment(paymentService.summaryFor(application));
    }

    /** Business rules 1-4 (+ profile completeness) for a new application. */
    void validateRequest(TouristProfile tourist, PermitArea area, LocalDate start, LocalDate end) {
        if (!area.isActive()) {
            throw new BusinessRuleException("This area is not currently accepting permit applications");
        }
        if (!area.isPermitRequired()) {
            throw new BusinessRuleException("This area does not require a permit");
        }
        if (end.isBefore(start)) {
            throw new BusinessRuleException("End date cannot be before start date");
        }
        if (start.isBefore(LocalDate.now(clock))) {
            throw new BusinessRuleException("Start date cannot be in the past");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (days > area.getMaximumDays()) {
            throw new BusinessRuleException("Requested duration of %d days exceeds this area's maximum of %d days"
                    .formatted(days, area.getMaximumDays()));
        }
        if (!allowOverlapping) {
            boolean overlapsApplication = applications.existsOverlapping(tourist.getId(), area.getId(), start, end,
                    ApplicationStatus.OPEN);
            if (overlapsApplication || permits.existsActiveOverlapping(tourist.getId(), area.getId(), start, end)) {
                throw new ConflictException("You already have an application or active permit for this area "
                        + "covering some of these dates");
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> listMine(ApplicationStatus status, Pageable pageable) {
        Long touristId = touristService.currentProfile().getId();
        Page<PermitApplication> page = applications.findByTourist(touristId, status, pageable);
        Map<Long, PaymentSummary> fees = paymentService.summariesFor(page.getContent());
        return PageResponse.of(page, a -> withPermitRef(a).withPayment(fees.get(a.getId())));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getMine(Long id) {
        PermitApplication a = own(id);
        return ApplicationResponse.of(a, documentService.listForApplication(id), null, permitRef(a))
                .withPayment(paymentService.summaryFor(a));
    }

    @Transactional
    public ApplicationResponse cancelMine(Long id) {
        PermitApplication a = applications.findForUpdate(own(id).getId()).orElseThrow();
        a.transitionTo(ApplicationStatus.CANCELLED);
        audit.record(AuditAction.APPLICATION_CANCELLED, "PermitApplication", id, Map.of());
        return ApplicationResponse.summary(a);
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> search(String query, ApplicationStatus status, Pageable pageable) {
        Page<PermitApplication> page = applications.search(SearchPatterns.contains(query), status, pageable);
        Map<Long, PaymentSummary> fees = paymentService.summariesFor(page.getContent());
        return PageResponse.of(page, a -> ApplicationResponse.of(a, null, TouristSummaryResponse.from(a.getTourist()),
                permitRef(a)).withPayment(fees.get(a.getId())));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getForAdmin(Long id) {
        PermitApplication a = applications.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        return ApplicationResponse.of(a, documentService.listForApplication(id),
                TouristSummaryResponse.from(a.getTourist()), permitRef(a)).withPayment(paymentService.summaryFor(a));
    }

    @Transactional
    public ApplicationResponse startReview(Long id) {
        PermitApplication a = lockForReview(id);
        a.transitionTo(ApplicationStatus.UNDER_REVIEW);
        audit.record(AuditAction.APPLICATION_REVIEW_STARTED, "PermitApplication", id, Map.of());
        return getForAdmin(id);
    }

    @Transactional
    public ApplicationResponse reject(Long id, String reason) {
        PermitApplication a = lockForReview(id);
        a.transitionTo(ApplicationStatus.REJECTED);
        a.setRejectionReason(reason.trim());
        a.markReviewed(users.getReferenceById(CurrentUser.require().id()), clock.instant());
        audit.record(AuditAction.PERMIT_REJECTED, "PermitApplication", id,
                Map.of("applicationNumber", a.getApplicationNumber()));
        events.publishEvent(new ApplicationDecidedEvent(a.getId(), false));
        return getForAdmin(id);
    }

    /** Locks the application row so concurrent reviewers cannot both act on it. */
    PermitApplication lockForReview(Long id) {
        return applications.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
    }

    private ApplicationResponse.IssuedPermitRef permitRef(PermitApplication a) {
        if (a.getStatus() != ApplicationStatus.APPROVED) {
            return null;
        }
        return permits.findByApplicationId(a.getId())
                .map(p -> new ApplicationResponse.IssuedPermitRef(p.getId(), p.getPermitNumber()))
                .orElse(null);
    }

    private ApplicationResponse withPermitRef(PermitApplication a) {
        return ApplicationResponse.of(a, null, null, permitRef(a));
    }

    private PermitApplication own(Long id) {
        Long touristId = touristService.currentProfile().getId();
        return applications.findByIdAndTouristId(id, touristId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
    }
}
