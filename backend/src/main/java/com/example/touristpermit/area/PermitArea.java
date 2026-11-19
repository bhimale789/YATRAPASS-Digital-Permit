package com.example.touristpermit.area;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "permit_areas")
public class PermitArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    private String description;

    private String district;

    private String province;

    @Column(name = "permit_required", nullable = false)
    private boolean permitRequired = true;

    @Column(name = "maximum_days", nullable = false)
    private int maximumDays;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    /** Destination pin (route end point); both null when not set. */
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    /** Relative path of the destination photo in file storage (null = no photo). */
    @Column(name = "photo_path")
    private String photoPath;

    @Column(name = "photo_mime_type")
    private String photoMimeType;

    @Column(name = "photo_updated_at")
    private Instant photoUpdatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PermitArea() {
    }

    public PermitArea(String name, String code, int maximumDays) {
        this.name = name;
        this.code = code;
        this.maximumDays = maximumDays;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public boolean isPermitRequired() {
        return permitRequired;
    }

    public void setPermitRequired(boolean permitRequired) {
        this.permitRequired = permitRequired;
    }

    public int getMaximumDays() {
        return maximumDays;
    }

    public void setMaximumDays(int maximumDays) {
        this.maximumDays = maximumDays;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    public void setLocation(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void setPhoto(String path, String mimeType, Instant updatedAt) {
        this.photoPath = path;
        this.photoMimeType = mimeType;
        this.photoUpdatedAt = updatedAt;
    }

    public void clearPhoto() {
        setPhoto(null, null, null);
    }

    public boolean hasPhoto() {
        return photoPath != null;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public String getPhotoMimeType() {
        return photoMimeType;
    }

    public Instant getPhotoUpdatedAt() {
        return photoUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
