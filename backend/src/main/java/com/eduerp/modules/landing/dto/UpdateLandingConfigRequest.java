package com.eduerp.modules.landing.dto;

import com.eduerp.modules.landing.LandingConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateLandingConfigRequest(
        @Valid @NotNull CenterInfoPayload centerInfo,
        @Valid @NotNull List<RoadmapStepPayload> roadmapSteps,
        @Valid @NotNull List<WhyChooseFeaturePayload> whyChooseFeatures,
        @Valid @NotNull List<FaqPayload> faqs) {

    public UpdateLandingConfigRequest {
        roadmapSteps = roadmapSteps == null ? List.of() : List.copyOf(roadmapSteps);
        whyChooseFeatures = whyChooseFeatures == null ? List.of() : List.copyOf(whyChooseFeatures);
        faqs = faqs == null ? List.of() : List.copyOf(faqs);
    }

    public record CenterInfoPayload(
            @NotBlank @Size(max = 160) String brandName,
            @NotBlank @Size(max = 255) String brandSubtitle,
            @NotBlank @Size(max = 255) String tagline,
            @NotBlank @Size(max = 255) String heroBadge,
            @NotBlank @Size(max = 255) String heroTitle,
            @NotBlank @Size(max = 255) String heroHighlight,
            @NotBlank @Size(max = 2000) String heroDescription,
            @NotBlank @Size(max = 32) String hotline,
            @NotBlank @Size(max = 64) String hotlineDisplay,
            @NotBlank @Size(max = 160) String email,
            @NotBlank @Size(max = 160) String workTime,
            @NotBlank @Size(max = 255) String defaultAddress,
            @NotBlank @Size(max = 255) String zaloUrl,
            @NotBlank @Size(max = 255) String messengerUrl,
            @NotBlank @Size(max = 2000) String footerDescription) {
    }

    public record RoadmapStepPayload(
            @NotBlank @Size(max = 16) String step,
            @NotBlank @Size(max = 160) String badgeText,
            @NotBlank @Size(max = 160) String subTag,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 160) String duration,
            @NotBlank @Size(max = 255) String target,
            @NotBlank @Size(max = 32) String colorTheme,
            @NotNull List<String> features) {
        public RoadmapStepPayload {
            features = features == null ? List.of() : List.copyOf(features);
        }
    }

    public record WhyChooseFeaturePayload(
            @NotBlank @Size(max = 32) String iconKey,
            @NotBlank @Size(max = 32) String variant,
            @NotBlank @Size(max = 160) String subtitle,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 2000) String description) {
    }

    public record FaqPayload(
            @NotBlank @Size(max = 500) String question,
            @NotBlank @Size(max = 2000) String answer) {
    }

    public record UpdateConsultationStatusPayload(
            @NotNull LandingConstants.ConsultationStatus status) {
    }
}
