package com.eduerp.modules.landing.internal.repository;

import com.eduerp.modules.landing.internal.model.LandingRoadmapStep;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandingRoadmapStepRepository extends JpaRepository<LandingRoadmapStep, UUID> {

    List<LandingRoadmapStep> findAllByOrderByDisplayOrderAsc();
}
