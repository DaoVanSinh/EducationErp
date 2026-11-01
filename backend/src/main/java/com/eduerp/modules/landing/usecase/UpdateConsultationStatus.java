package com.eduerp.modules.landing.usecase;

import com.eduerp.modules.landing.LandingConstants;
import com.eduerp.modules.landing.LandingConsultationNotFoundException;
import com.eduerp.modules.landing.internal.repository.LandingConsultationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateConsultationStatus {

    private final LandingConsultationRepository consultations;

    UpdateConsultationStatus(LandingConsultationRepository consultations) {
        this.consultations = consultations;
    }

    @Transactional
    public void execute(UUID consultationId, LandingConstants.ConsultationStatus status) {
        var entity = consultations
                .findById(consultationId)
                .orElseThrow(() -> new LandingConsultationNotFoundException(consultationId));
        entity.setStatus(status);
    }
}
