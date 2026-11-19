package com.example.touristpermit.permit;

import com.example.touristpermit.application.PermitApplication;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "permits")
public class Permit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permit_number", nullable = false, unique = true, updatable = false)
    private String permitNumber;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true, updatable = false)
    private PermitApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tourist_id", nullable = false, updatable = false)
    private TouristProfile tourist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false, updatable = false)
    private PermitArea area;

    @Column(name = "issue_date", nullable = false, updatable = false)
    private LocalDate issueDate;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PermitStatus status = PermitStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issued_by", nullable = false, updatable = false)
    private User issuedBy;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Permit() {
    }

    public Permit(String permitNumber, PermitApplication application, LocalDate issueDate, User issuedBy) {
        this.permitNumber = permitNumber;
        this.application = application;
        this.tourist = application.getTourist();
        this.area = application.getArea();
        this.issueDate = issueDate;
        this.validFrom = application.getRequestedStartDate();
        this.validUntil = application.getRequestedEndDate();
        this.issuedBy = issuedBy;
    }

    public void transitionTo(PermitStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Permit", status, target);
        }
        status = target;
    }

    /** True when the permit's validity window has passed on the given date. */
    public boolean isPastValidity(LocalDate today) {
        return today.isAfter(validUntil);
    }

    public boolean isWithinValidity(LocalDate today) {
        return !today.isBefore(validFrom) && !today.isAfter(validUntil);
    }

    public Long getId() {
        return id;
    }

    public String getPermitNumber() {
        return permitNumber;
    }

    public PermitApplication getApplication() {
        return application;
    }

    public TouristProfile getTourist() {
        return tourist;
    }

    public PermitArea getArea() {
        return area;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public PermitStatus getStatus() {
        return status;
    }

    public User getIssuedBy() {
        return issuedBy;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
