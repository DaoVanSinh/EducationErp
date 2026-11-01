package com.eduerp.modules.landing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.landing.LandingConstants;
import com.eduerp.modules.landing.LandingConsultationNotFoundException;
import com.eduerp.modules.landing.dto.SubmitConsultationRequest;
import com.eduerp.modules.landing.dto.UpdateLandingConfigRequest;
import com.eduerp.modules.landing.dto.UpdateLandingConfigRequest.CenterInfoPayload;
import com.eduerp.modules.landing.dto.UpdateLandingConfigRequest.FaqPayload;
import com.eduerp.modules.landing.dto.UpdateLandingConfigRequest.RoadmapStepPayload;
import com.eduerp.modules.landing.dto.UpdateLandingConfigRequest.WhyChooseFeaturePayload;
import com.eduerp.modules.landing.internal.model.LandingCenterConfig;
import com.eduerp.modules.landing.internal.model.LandingConsultation;
import com.eduerp.modules.landing.internal.model.LandingFaq;
import com.eduerp.modules.landing.internal.model.LandingFeature;
import com.eduerp.modules.landing.internal.model.LandingRoadmapStep;
import com.eduerp.modules.landing.internal.repository.LandingCenterConfigRepository;
import com.eduerp.modules.landing.internal.repository.LandingConsultationRepository;
import com.eduerp.modules.landing.internal.repository.LandingFaqRepository;
import com.eduerp.modules.landing.internal.repository.LandingFeatureRepository;
import com.eduerp.modules.landing.internal.repository.LandingRoadmapStepRepository;
import com.eduerp.modules.organization.OrganizationManagement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LandingConfigAndConsultationTest {

    @Mock
    LandingCenterConfigRepository centerConfigs;

    @Mock
    LandingRoadmapStepRepository roadmapSteps;

    @Mock
    LandingFeatureRepository features;

    @Mock
    LandingFaqRepository faqs;

    @Mock
    LandingConsultationRepository consultations;

    @Mock
    CoursesManagement courses;

    @Mock
    OrganizationManagement organization;

    @InjectMocks
    GetLandingContentConfig getContentConfig;

    @Test
    void getContentConfigReadsDatabaseRecordsAndSplitsRoadmapFeatures() {
        var configEntity = new LandingCenterConfig(
                LandingConstants.Defaults.DEFAULT_CONFIG_CODE,
                "Học Viện Đào Tạo Quốc Tế",
                "Học viện Đào tạo",
                "Cam kết chuẩn đầu ra",
                "CHUẨN QUỐC TẾ",
                "Lộ trình chuẩn",
                "Học bổng 100%",
                "Mô tả chi tiết",
                "0869724615",
                "0387 367 162",
                "info@eduerp.vn",
                "08:00 - 20:30",
                "Quận 1, TP.HCM",
                "https://zalo.me/0869724615",
                "https://m.me/eduerp",
                "Footer mô tả");

        when(centerConfigs.findByCode(LandingConstants.Defaults.DEFAULT_CONFIG_CODE))
                .thenReturn(Optional.of(configEntity));
        when(roadmapSteps.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(
                new LandingRoadmapStep("01", "Nền tảng", "Giai đoạn 1", "Bước 1", "8 tuần", "Chuẩn đầu ra", "blue",
                        "Ý 1\nÝ 2", 1)));
        when(features.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(
                new LandingFeature("wallet", "blue", "Minh bạch", "Học phí rõ ràng", "Mô tả", 1)));
        when(faqs.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(
                new LandingFaq("Câu hỏi 1?", "Trả lời 1", 1)));

        var result = getContentConfig.execute();

        assertThat(result.centerInfo().brandName()).isEqualTo("Học Viện Đào Tạo Quốc Tế");
        assertThat(result.roadmapSteps()).hasSize(1);
        assertThat(result.roadmapSteps().getFirst().features()).containsExactly("Ý 1", "Ý 2");
        assertThat(result.whyChooseFeatures()).hasSize(1);
        assertThat(result.faqs()).hasSize(1);
    }

    @Test
    void updateLandingConfigSavesCenterInfoAndReplacesLists() {
        var updater = new UpdateLandingConfig(
                centerConfigs, roadmapSteps, features, faqs, getContentConfig);
        when(centerConfigs.findByCode(LandingConstants.Defaults.DEFAULT_CONFIG_CODE))
                .thenReturn(Optional.empty());
        when(roadmapSteps.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of());
        when(features.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of());
        when(faqs.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of());

        var payload = new UpdateLandingConfigRequest(
                new CenterInfoPayload(
                        "EduERP Mới",
                        "Subtitle",
                        "Tagline",
                        "Badge",
                        "Title",
                        "Highlight",
                        "Hero Desc",
                        "0909000111",
                        "0909 000 111",
                        "contact@eduerp.vn",
                        "8:00 - 20:00",
                        "Hà Nội",
                        "https://zalo.me/0909000111",
                        "https://m.me/eduerp",
                        "Footer"),
                List.of(new RoadmapStepPayload("01", "Badge", "Tag", "Title", "8w", "Target", "blue",
                        List.of("F1", "F2"))),
                List.of(new WhyChooseFeaturePayload("wallet", "blue", "Sub", "Title", "Desc")),
                List.of(new FaqPayload("Q?", "A")));

        updater.execute(payload);

        verify(centerConfigs).save(any(LandingCenterConfig.class));
        verify(roadmapSteps).deleteAllInBatch();
        verify(roadmapSteps).saveAll(any());
        verify(features).deleteAllInBatch();
        verify(features).saveAll(any());
        verify(faqs).deleteAllInBatch();
        verify(faqs).saveAll(any());
    }

    @Test
    void submitAndUpdateConsultationStatusWorksAndThrowsWhenMissing() {
        var submitUseCase = new SubmitConsultation(consultations);
        var updateStatusUseCase = new UpdateConsultationStatus(consultations);
        var saved = new LandingConsultation("Nguyễn Văn A", "0901234567", "a@b.com", null, null, "Ghi chú");
        when(consultations.save(any(LandingConsultation.class))).thenReturn(saved);

        submitUseCase.execute(new SubmitConsultationRequest(
                "  Nguyễn Văn A ", " 0901234567 ", " a@b.com ", null, null, " Ghi chú "));
        verify(consultations).save(any(LandingConsultation.class));

        var missingId = UUID.randomUUID();
        when(consultations.findById(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> updateStatusUseCase.execute(
                missingId, LandingConstants.ConsultationStatus.CONTACTED))
                .isInstanceOf(LandingConsultationNotFoundException.class);
    }
}
