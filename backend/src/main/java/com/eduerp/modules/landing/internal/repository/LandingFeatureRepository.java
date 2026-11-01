package com.eduerp.modules.landing.internal.repository;

import com.eduerp.modules.landing.internal.model.LandingFeature;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandingFeatureRepository extends JpaRepository<LandingFeature, UUID> {

    List<LandingFeature> findAllByOrderByDisplayOrderAsc();
}
