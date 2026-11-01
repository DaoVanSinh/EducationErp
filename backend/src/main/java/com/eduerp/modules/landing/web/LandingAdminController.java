package com.eduerp.modules.landing.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.landing.LandingConstants;
import com.eduerp.modules.landing.dto.ConsultationItemResponse;
import com.eduerp.modules.landing.dto.LandingContentConfigResponse;
import com.eduerp.modules.landing.dto.UpdateLandingConfigRequest;
import com.eduerp.modules.landing.dto.UpdateLandingConfigRequest.UpdateConsultationStatusPayload;
import com.eduerp.modules.landing.usecase.GetLandingContentConfig;
import com.eduerp.modules.landing.usecase.ListConsultations;
import com.eduerp.modules.landing.usecase.UpdateConsultationStatus;
import com.eduerp.modules.landing.usecase.UpdateLandingConfig;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản trị cấu hình thông tin trung tâm, nội dung Landing Page và danh sách đăng ký tư vấn trên ERP.
 */
@RestController
class LandingAdminController {

    private final GetLandingContentConfig getContentConfig;
    private final UpdateLandingConfig updateConfig;
    private final ListConsultations listConsultations;
    private final UpdateConsultationStatus updateConsultationStatus;

    LandingAdminController(
            GetLandingContentConfig getContentConfig,
            UpdateLandingConfig updateConfig,
            ListConsultations listConsultations,
            UpdateConsultationStatus updateConsultationStatus) {
        this.getContentConfig = getContentConfig;
        this.updateConfig = updateConfig;
        this.listConsultations = listConsultations;
        this.updateConsultationStatus = updateConsultationStatus;
    }

    @GetMapping(LandingConstants.Paths.ADMIN_CONFIG)
    @PreAuthorize(AccessConstants.AccessRules.READ_BRANCH)
    LandingContentConfigResponse getConfig() {
        return getContentConfig.execute();
    }

    @PatchMapping(LandingConstants.Paths.ADMIN_CONFIG)
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_BRANCH)
    LandingContentConfigResponse updateConfig(@Valid @RequestBody UpdateLandingConfigRequest request) {
        return updateConfig.execute(request);
    }

    @GetMapping(LandingConstants.Paths.ADMIN_CONSULTATIONS)
    @PreAuthorize(AccessConstants.AccessRules.READ_BRANCH)
    List<ConsultationItemResponse> listConsultations() {
        return listConsultations.execute();
    }

    @PatchMapping(LandingConstants.Paths.ADMIN_CONSULTATIONS + "/{consultationId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_BRANCH)
    void updateConsultationStatus(
            @PathVariable UUID consultationId,
            @Valid @RequestBody UpdateConsultationStatusPayload payload) {
        updateConsultationStatus.execute(consultationId, payload.status());
    }
}
