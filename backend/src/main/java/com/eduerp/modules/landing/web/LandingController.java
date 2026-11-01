package com.eduerp.modules.landing.web;

import com.eduerp.modules.landing.LandingConstants;
import com.eduerp.modules.landing.dto.LandingOverviewResponse;
import com.eduerp.modules.landing.dto.SubmitConsultationRequest;
import com.eduerp.modules.landing.usecase.GetLandingOverview;
import com.eduerp.modules.landing.usecase.SubmitConsultation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter công khai cho Landing Page — đọc dữ liệu tổng hợp từ ERP và nhận đăng ký tư vấn.
 */
@RestController
class LandingController {

    private final GetLandingOverview getLandingOverview;
    private final SubmitConsultation submitConsultation;

    LandingController(GetLandingOverview getLandingOverview, SubmitConsultation submitConsultation) {
        this.getLandingOverview = getLandingOverview;
        this.submitConsultation = submitConsultation;
    }

    @GetMapping(LandingConstants.Paths.OVERVIEW)
    LandingOverviewResponse overview() {
        return getLandingOverview.execute();
    }

    @PostMapping(LandingConstants.Paths.PUBLIC_CONSULTATIONS)
    UUID submitConsultation(@Valid @RequestBody SubmitConsultationRequest request) {
        return submitConsultation.execute(request);
    }
}
