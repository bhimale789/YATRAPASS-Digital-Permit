package com.example.touristpermit.permit;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PermitRepository extends JpaRepository<Permit, Long> {

    Optional<Permit> findByApplicationId(Long applicationId);

    @EntityGraph(attributePaths = {"area", "tourist", "tourist.user", "application"})
    Optional<Permit> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"area", "tourist", "application"})
    Optional<Permit> findByIdAndTouristId(Long id, Long touristId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Permit p WHERE p.id = :id")
    Optional<Permit> findForUpdate(@Param("id") Long id);

    @Query("""
            SELECT count(p) > 0 FROM Permit p
            WHERE p.tourist.id = :touristId AND p.area.id = :areaId AND p.status = com.example.touristpermit.permit.PermitStatus.ACTIVE
              AND p.validFrom <= :end AND p.validUntil >= :start
            """)
    boolean existsActiveOverlapping(@Param("touristId") Long touristId, @Param("areaId") Long areaId,
                                    @Param("start") LocalDate start, @Param("end") LocalDate end);

    @EntityGraph(attributePaths = {"area", "application"})
    @Query("""
            SELECT p FROM Permit p
            WHERE p.tourist.id = :touristId AND (:status IS NULL OR p.status = :status)
            """)
    Page<Permit> findByTourist(@Param("touristId") Long touristId, @Param("status") PermitStatus status,
                               Pageable pageable);

    @EntityGraph(attributePaths = {"area", "tourist", "tourist.user", "application"})
    @Query("""
            SELECT p FROM Permit p JOIN p.tourist t JOIN p.area ar JOIN p.application app
            WHERE (:status IS NULL OR p.status = :status)
              AND (:q IS NULL
                   OR lower(p.permitNumber) LIKE :q ESCAPE '\\'
                   OR lower(app.applicationNumber) LIKE :q ESCAPE '\\'
                   OR lower(concat(t.firstName, ' ', t.lastName)) LIKE :q ESCAPE '\\'
                   OR lower(t.passportNumber) LIKE :q ESCAPE '\\'
                   OR lower(ar.name) LIKE :q ESCAPE '\\'
                   OR lower(ar.code) LIKE :q ESCAPE '\\')
            """)
    Page<Permit> search(@Param("q") String likePattern, @Param("status") PermitStatus status, Pageable pageable);

    /** ACTIVE permits whose validity ended before the given date (candidates for expiry). */
    @Query("SELECT p.id FROM Permit p WHERE p.status = com.example.touristpermit.permit.PermitStatus.ACTIVE AND p.validUntil < :today")
    List<Long> findIdsToExpire(@Param("today") LocalDate today);

    @EntityGraph(attributePaths = {"area", "tourist", "tourist.user", "application"})
    @Query("""
            SELECT p FROM Permit p
            WHERE p.status = com.example.touristpermit.permit.PermitStatus.ACTIVE AND p.validUntil BETWEEN :from AND :to
            ORDER BY p.validUntil ASC
            """)
    Page<Permit> findExpiringBetween(@Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    @Query("""
            SELECT new com.example.touristpermit.permit.dto.AreaPermitStats(a.id, a.name, a.code, count(p),
                   sum(CASE WHEN p.status = com.example.touristpermit.permit.PermitStatus.ACTIVE THEN 1 ELSE 0 END))
            FROM Permit p JOIN p.area a
            GROUP BY a.id, a.name, a.code
            ORDER BY count(p) DESC
            """)
    List<com.example.touristpermit.permit.dto.AreaPermitStats> statsByArea(Pageable pageable);

    long countByStatus(PermitStatus status);

    /** ACTIVE permits whose validity window includes the given date. */
    @Query("""
            SELECT count(p) FROM Permit p
            WHERE p.status = com.example.touristpermit.permit.PermitStatus.ACTIVE
              AND p.validFrom <= :date AND p.validUntil >= :date
            """)
    long countValidOn(@Param("date") LocalDate date);

    long countByTouristId(Long touristId);
}
