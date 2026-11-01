package com.eduerp.modules.landing.usecase;

import com.eduerp.modules.landing.dto.SubmitConsultationRequest;
import com.eduerp.modules.landing.internal.model.LandingConsultation;
import com.eduerp.modules.landing.internal.repository.LandingConsultationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmitConsultation {

    private final LandingConsultationRepository consultations;

    SubmitConsultation(LandingConsultationRepository consultations) {
        this.consultations = consultations;
    }

    @Transactional
    public UUID execute(SubmitConsultationRequest request) {
        var entity = new LandingConsultation(
                request.fullName().trim(),
                request.phone().trim(),
                trimToNull(request.email()),
                request.targetCourseId(),
                request.targetBranchId(),
                trimToNull(request.notes()));
        return consultations.save(entity).getId();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
