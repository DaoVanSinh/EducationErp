package com.eduerp.modules.landing.internal.repository;

import com.eduerp.modules.landing.internal.model.LandingConsultation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandingConsultationRepository extends JpaRepository<LandingConsultation, UUID> {

    List<LandingConsultation> findAllByOrderByCreatedAtDesc();
}
