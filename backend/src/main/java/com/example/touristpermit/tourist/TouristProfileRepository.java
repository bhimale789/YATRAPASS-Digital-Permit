package com.example.touristpermit.tourist;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TouristProfileRepository extends JpaRepository<TouristProfile, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<TouristProfile> findByUserId(Long userId);

    boolean existsByPassportNumberAndIdNot(String passportNumber, Long id);

    @EntityGraph(attributePaths = "user")
    @Query("""
            SELECT t FROM TouristProfile t JOIN t.user u
            WHERE :q IS NULL
               OR lower(concat(t.firstName, ' ', t.lastName)) LIKE :q ESCAPE '\\'
               OR lower(t.passportNumber) LIKE :q ESCAPE '\\'
               OR lower(u.email) LIKE :q ESCAPE '\\'
            """)
    Page<TouristProfile> search(@Param("q") String likePattern, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<TouristProfile> findById(Long id);
}
