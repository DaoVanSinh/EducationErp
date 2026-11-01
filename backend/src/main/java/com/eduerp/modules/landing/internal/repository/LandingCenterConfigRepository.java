package com.eduerp.modules.landing.internal.repository;

import com.eduerp.modules.landing.internal.model.LandingCenterConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandingCenterConfigRepository extends JpaRepository<LandingCenterConfig, UUID> {

    Optional<LandingCenterConfig> findByCode(String code);
}
