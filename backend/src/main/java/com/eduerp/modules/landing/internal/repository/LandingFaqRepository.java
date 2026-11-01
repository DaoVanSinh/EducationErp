package com.eduerp.modules.landing.internal.repository;

import com.eduerp.modules.landing.internal.model.LandingFaq;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LandingFaqRepository extends JpaRepository<LandingFaq, UUID> {

    List<LandingFaq> findAllByOrderByDisplayOrderAsc();
}
