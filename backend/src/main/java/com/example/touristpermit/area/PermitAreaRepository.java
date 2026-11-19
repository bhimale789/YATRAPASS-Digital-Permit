package com.example.touristpermit.area;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PermitAreaRepository extends JpaRepository<PermitArea, Long> {

    List<PermitArea> findByActiveTrue(Sort sort);

    Optional<PermitArea> findByIdAndActiveTrue(Long id);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("""
            SELECT a FROM PermitArea a
            WHERE (:active IS NULL OR a.active = :active)
              AND (:q IS NULL OR lower(a.name) LIKE :q ESCAPE '\\'
                   OR lower(a.code) LIKE :q ESCAPE '\\'
                   OR lower(a.district) LIKE :q ESCAPE '\\')
            """)
    Page<PermitArea> search(@Param("q") String likePattern, @Param("active") Boolean active, Pageable pageable);
}
