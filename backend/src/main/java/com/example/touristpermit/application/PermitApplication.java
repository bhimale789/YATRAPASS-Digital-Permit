package com.example.touristpermit.application;

import com.example.touristpermit.area.PermitArea;
import com.example.touristpermit.common.exception.InvalidStateTransitionException;
import com.example.touristpermit.tourist.TouristProfile;
import com.example.touristpermit.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "permit_applications")
public class PermitApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_number", nullable = false, unique = true, updatable = false)
    private String applicationNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tourist_id", nullable = false, updatable = false)
    private TouristProfile tourist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false, updatable = false)
    private PermitArea area;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "requested_start_date", nullable = false)
    private LocalDate requestedStartDate;

    @Column(name = "requested_end_date", nullable = false)
    private LocalDate requestedEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    /** Permit fee at submission time (0 = free). */
    @Column(name = "fee_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PermitApplication() {
    }

    public PermitApplication(String applicationNumber, TouristProfile tourist, PermitArea area, String purpose,
                             LocalDate requestedStartDate, LocalDate requestedEndDate, Instant submittedAt) {
        this.applicationNumber = applicationNumber;
        this.tourist = tourist;
        this.area = area;
        this.purpose = purpose;
        this.requestedStartDate = requestedStartDate;
        this.requestedEndDate = requestedEndDate;
        this.submittedAt = submittedAt;
        this.feeAmount = area.getFee() == null ? BigDecimal.ZERO : area.getFee();
    }

    /** Applies a lifecycle transition, rejecting any not allowed by {@link ApplicationStatus}. */
    public void transitionTo(ApplicationStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Application", status, target);
        }
        status = target;
    }

    public void markReviewed(User reviewer, Instant at) {
        this.reviewedBy = reviewer;
        this.reviewedAt = at;
    }

    /** Inclusive number of calendar days requested. */
    public long requestedDays() {
        return ChronoUnit.DAYS.between(requestedStartDate, requestedEndDate) + 1;
    }

    public Long getId() {
        return id;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public TouristProfile getTourist() {
        return tourist;
    }

    public PermitArea getArea() {
        return area;
    }

    public String getPurpose() {
        return purpose;
    }

    public LocalDate getRequestedStartDate() {
        return requestedStartDate;
    }

    public LocalDate getRequestedEndDate() {
        return requestedEndDate;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public boolean requiresPayment() {
        return feeAmount != null && feeAmount.signum() > 0;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
