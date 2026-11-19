package com.example.touristpermit.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PermitApplicationRepository extends JpaRepository<PermitApplication, Long> {

    @EntityGraph(attributePaths = {"area", "tourist", "tourist.user", "reviewedBy"})
    Optional<PermitApplication> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"area"})
    Optional<PermitApplication> findByIdAndTouristId(Long id, Long touristId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PermitApplication a WHERE a.id = :id")
    Optional<PermitApplication> findForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"area"})
    @Query("""
            SELECT a FROM PermitApplication a
            WHERE a.tourist.id = :touristId AND (:status IS NULL OR a.status = :status)
            """)
    Page<PermitApplication> findByTourist(@Param("touristId") Long touristId,
                                          @Param("status") ApplicationStatus status,
                                          Pageable pageable);

    @EntityGraph(attributePaths = {"area", "tourist", "tourist.user"})
    @Query("""
            SELECT a FROM PermitApplication a JOIN a.tourist t JOIN a.area ar
            WHERE (:status IS NULL OR a.status = :status)
              AND (:q IS NULL
                   OR lower(a.applicationNumber) LIKE :q ESCAPE '\\'
                   OR lower(concat(t.firstName, ' ', t.lastName)) LIKE :q ESCAPE '\\'
                   OR lower(t.passportNumber) LIKE :q ESCAPE '\\'
                   OR lower(ar.name) LIKE :q ESCAPE '\\'
                   OR lower(ar.code) LIKE :q ESCAPE '\\')
            """)
    Page<PermitApplication> search(@Param("q") String likePattern,
                                   @Param("status") ApplicationStatus status,
                                   Pageable pageable);

    /** Open applications by the same tourist for the same area whose date range overlaps. */
    @Query("""
            SELECT count(a) > 0 FROM PermitApplication a
            WHERE a.tourist.id = :touristId AND a.area.id = :areaId
              AND a.status IN :statuses
              AND a.requestedStartDate <= :end AND a.requestedEndDate >= :start
            """)
    boolean existsOverlapping(@Param("touristId") Long touristId, @Param("areaId") Long areaId,
                              @Param("start") LocalDate start, @Param("end") LocalDate end,
                              @Param("statuses") Collection<ApplicationStatus> statuses);

    long countByStatus(ApplicationStatus status);

    long countByTouristId(Long touristId);

    @EntityGraph(attributePaths = {"area", "tourist", "tourist.user"})
    List<PermitApplication> findTop10ByOrderBySubmittedAtDesc();
}
