package com.eduerp.modules.landing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class LandingConsultationNotFoundException extends LandingException {

    public LandingConsultationNotFoundException(UUID consultationId) {
        super(
                LandingConstants.ErrorCodes.CONSULTATION_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "Không tìm thấy đăng ký tư vấn: " + consultationId);
    }
}
