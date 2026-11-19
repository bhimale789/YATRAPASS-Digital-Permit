package com.example.touristpermit.permit;

import com.example.touristpermit.application.ApplicationStatus;
import com.example.touristpermit.application.PermitApplication;
import com.example.touristpermit.application.PermitApplicationRepository;
import com.example.touristpermit.audit.AuditAction;
import com.example.touristpermit.audit.AuditService;
import com.example.touristpermit.common.exception.BusinessRuleException;
import com.example.touristpermit.common.exception.ConflictException;
import com.example.touristpermit.common.exception.ResourceNotFoundException;
import com.example.touristpermit.common.response.PageResponse;
import com.example.touristpermit.common.util.ReferenceNumberGenerator;
import com.example.touristpermit.common.util.SearchPatterns;
import com.example.touristpermit.document.DocumentService;
import com.example.touristpermit.document.DocumentType;
import com.example.touristpermit.notification.event.ApplicationDecidedEvent;
import com.example.touristpermit.payment.PaymentService;
import com.example.touristpermit.permit.dto.PermitResponse;
import com.example.touristpermit.qr.QrTokenService;
import com.example.touristpermit.security.CurrentUser;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Permit issuance and lifecycle. All status changes go through
 * {@link Permit#transitionTo}, which rejects invalid transitions.
 */
@Service
public class PermitService {

    private final PermitRepository permits;
    private final PermitApplicationRepository applications;
    private final DocumentService documentService;
    private final TouristService touristService;
    private final QrTokenService qrTokens;
    private final ReferenceNumberGenerator numbers;
    private final UserRepository users;
    private final AuditService audit;
    private final PermitPdfRenderer pdfRenderer;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PermitService(PermitRepository permits, PermitApplicationRepository applications,
                         DocumentService documentService, TouristService touristService, QrTokenService qrTokens,
                         ReferenceNumberGenerator numbers, UserRepository users, AuditService audit,
                         PermitPdfRenderer pdfRenderer, PaymentService paymentService,
                         ApplicationEventPublisher events, Clock clock) {
        this.permits = permits;
        this.applications = applications;
        this.documentService = documentService;
        this.touristService = touristService;
        this.qrTokens = qrTokens;
        this.numbers = numbers;
        this.users = users;
        this.audit = audit;
        this.pdfRenderer = pdfRenderer;
        this.paymentService = paymentService;
        this.events = events;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- issuance

    /**
     * Approves an application and issues its permit. A PENDING application is moved
     * through UNDER_REVIEW first so the recorded lifecycle stays complete.
     */
    @Transactional
    public PermitResponse approve(Long applicationId) {
        PermitApplication application = applications.findForUpdate(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        if (application.getStatus() == ApplicationStatus.PENDING) {
            application.transitionTo(ApplicationStatus.UNDER_REVIEW);
        }
        if (!application.getStatus().canTransitionTo(ApplicationStatus.APPROVED)) {
            application.transitionTo(ApplicationStatus.APPROVED); // throws InvalidStateTransitionException
        }
        LocalDate today = LocalDate.now(clock);
        if (!application.getArea().isActive()) {
            throw new BusinessRuleException("The area is no longer active; the application cannot be approved");
        }
        if (application.getRequestedEndDate().isBefore(today)) {
            throw new BusinessRuleException("The requested travel dates have already passed");
        }
        if (!documentService.hasDocumentOfType(applicationId, DocumentType.PASSPORT)) {
            throw new BusinessRuleException("A passport document must be uploaded before approval");
        }
        if (paymentService.isFeeOutstanding(application)) {
            throw new BusinessRuleException("The permit fee has not been paid. Ask the tourist to pay online, "
                    + "or record a counter payment first.");
        }
        if (permits.existsActiveOverlapping(application.getTourist().getId(), application.getArea().getId(),
                application.getRequestedStartDate(), application.getRequestedEndDate())) {
            throw new ConflictException("The tourist already holds an active permit for this area on these dates");
        }

        application.transitionTo(ApplicationStatus.APPROVED);
        var reviewer = users.getReferenceById(CurrentUser.require().id());
        application.markReviewed(reviewer, clock.instant());

        Permit permit = permits.save(new Permit(numbers.next(ReferenceNumberGenerator.Kind.PERMIT), application,
                today, reviewer));
        audit.record(AuditAction.PERMIT_APPROVED, "Permit", permit.getId(), Map.of(
                "permitNumber", permit.getPermitNumber(),
                "applicationNumber", application.getApplicationNumber()));
        qrTokens.issue(permit);
        events.publishEvent(new ApplicationDecidedEvent(application.getId(), true));
        return toResponse(permit, true);
    }

    // ---------------------------------------------------------------- lifecycle

    @Transactional
    public PermitResponse cancel(Long permitId, String reason) {
        Permit permit = lock(permitId);
        permit.transitionTo(PermitStatus.CANCELLED);
        permit.setCancellationReason(reason.trim());
        audit.record(AuditAction.PERMIT_CANCELLED, "Permit", permitId, Map.of("permitNumber", permit.getPermitNumber()));
        return toResponse(find(permitId), true);
    }

    @Transactional
    public PermitResponse complete(Long permitId) {
        Permit permit = lock(permitId);
        permit.transitionTo(PermitStatus.COMPLETED);
        audit.record(AuditAction.PERMIT_COMPLETED, "Permit", permitId, Map.of("permitNumber", permit.getPermitNumber()));
        return toResponse(find(permitId), true);
    }

    /** Revokes the permit's current QR and issues a new one; the old QR stops verifying immediately. */
    @Transactional
    public PermitResponse regenerateQr(Long permitId, String reason) {
        Permit permit = lock(permitId);
        qrTokens.regenerate(permit, reason);
        return toResponse(find(permitId), true);
    }

    /** Revokes the permit's current QR without issuing a replacement. */
    @Transactional
    public PermitResponse revokeQr(Long permitId, String reason) {
        Permit permit = lock(permitId);
        if (!qrTokens.revokeActive(permit, reason)) {
            throw new ConflictException("This permit has no active QR code");
        }
        return toResponse(find(permitId), true);
    }

    /** QR PNG for the tourist's own ACTIVE permit. */
    @Transactional(readOnly = true)
    public byte[] myQrPng(Long permitId) {
        Permit permit = findOwn(permitId);
        if (permit.getStatus() != PermitStatus.ACTIVE) {
            throw new ConflictException("QR codes are only available for active permits");
        }
        return qrTokens.activeQrPng(permit);
    }

    @Transactional(readOnly = true)
    public byte[] qrPngForAdmin(Long permitId) {
        return qrTokens.activeQrPng(find(permitId));
    }

    /** Printable PDF of the tourist's own permit, in any status. */
    @Transactional(readOnly = true)
    public PermitPdf myPermitPdf(Long permitId) {
        return pdf(findOwn(permitId));
    }

    @Transactional(readOnly = true)
    public PermitPdf permitPdfForAdmin(Long permitId) {
        return pdf(find(permitId));
    }

    /** The QR code is printed only while the permit can still be used for travel. */
    private PermitPdf pdf(Permit permit) {
        boolean printQr = permit.getStatus() == PermitStatus.ACTIVE
                && !permit.isPastValidity(LocalDate.now(clock))
                && qrTokens.hasActiveToken(permit.getId());
        byte[] content = pdfRenderer.render(permit, printQr ? qrTokens.activeQrPng(permit) : null);
        return new PermitPdf(permit.getPermitNumber(), content);
    }

    /** Marks ACTIVE permits whose validity has ended as EXPIRED. Returns how many were expired. */
    @Transactional
    public int expireDuePermits() {
        LocalDate today = LocalDate.now(clock);
        int count = 0;
        for (Long id : permits.findIdsToExpire(today)) {
            Permit permit = lock(id);
            if (permit.getStatus() == PermitStatus.ACTIVE && permit.isPastValidity(today)) {
                permit.transitionTo(PermitStatus.EXPIRED);
                Map<String, Object> meta = new HashMap<>();
                meta.put("permitNumber", permit.getPermitNumber());
                meta.put("validUntil", permit.getValidUntil().toString());
                audit.record(null, "SYSTEM", AuditAction.PERMIT_EXPIRED, "Permit", id, meta);
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<PermitResponse> listMine(PermitStatus status, Pageable pageable) {
        Long touristId = touristService.currentProfile().getId();
        return toPage(permits.findByTourist(touristId, status, pageable), false);
    }

    @Transactional(readOnly = true)
    public PermitResponse getMine(Long id) {
        return toResponse(findOwn(id), false);
    }

    /** The current tourist's permit (404 if it belongs to someone else). */
    @Transactional(readOnly = true)
    public Permit findOwn(Long id) {
        Long touristId = touristService.currentProfile().getId();
        return permits.findByIdAndTouristId(id, touristId)
                .orElseThrow(() -> new ResourceNotFoundException("Permit not found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<PermitResponse> search(String query, PermitStatus status, Pageable pageable) {
        return toPage(permits.search(SearchPatterns.contains(query), status, pageable), true);
    }

    @Transactional(readOnly = true)
    public PermitResponse getForAdmin(Long id) {
        return toResponse(find(id), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<PermitResponse> expiringWithin(int days, Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        return toPage(permits.findExpiringBetween(today, today.plusDays(days), pageable), true);
    }

    public PermitResponse toResponse(Permit p, boolean includeTourist) {
        return PermitResponse.of(p, qrTokens.hasActiveToken(p.getId()),
                includeTourist ? TouristSummaryResponse.from(p.getTourist()) : null);
    }

    /** Maps a page, loading QR status for all rows in a single query. */
    private PageResponse<PermitResponse> toPage(Page<Permit> page, boolean includeTourist) {
        Set<Long> withQr = qrTokens.permitIdsWithActiveToken(page.getContent().stream().map(Permit::getId).toList());
        return PageResponse.of(page, p -> PermitResponse.of(p, withQr.contains(p.getId()),
                includeTourist ? TouristSummaryResponse.from(p.getTourist()) : null));
    }

    public Permit find(Long id) {
        return permits.findWithDetailsById(id).orElseThrow(() -> new ResourceNotFoundException("Permit not found"));
    }

    private Permit lock(Long id) {
        return permits.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Permit not found"));
    }
}
