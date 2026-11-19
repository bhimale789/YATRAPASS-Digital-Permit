package com.example.touristpermit.document;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermitDocumentRepository extends JpaRepository<PermitDocument, Long> {

    List<PermitDocument> findByApplicationIdOrderByUploadedAtAsc(Long applicationId);

    long countByApplicationId(Long applicationId);

    boolean existsByApplicationIdAndDocumentType(Long applicationId, DocumentType type);

    @EntityGraph(attributePaths = {"application", "application.tourist"})
    Optional<PermitDocument> findWithApplicationById(Long id);
}
