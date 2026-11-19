package com.example.touristpermit.road;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface RoadConditionRepository extends JpaRepository<RoadCondition, Long>,
        JpaSpecificationExecutor<RoadCondition> {

    @Override
    @EntityGraph(attributePaths = {"area", "updatedBy"})
    Page<RoadCondition> findAll(Specification<RoadCondition> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"area", "updatedBy"})
    Optional<RoadCondition> findWithAreaById(Long id);
}
