package com.eduerp.modules.landing.dto;

import java.util.List;
import java.util.UUID;

public record LandingContentConfigResponse(
        CenterInfoItem centerInfo,
        List<RoadmapStepItem> roadmapSteps,
        List<WhyChooseFeatureItem> whyChooseFeatures,
        List<FaqItem> faqs) {

    public LandingContentConfigResponse {
        roadmapSteps = List.copyOf(roadmapSteps);
        whyChooseFeatures = List.copyOf(whyChooseFeatures);
        faqs = List.copyOf(faqs);
    }

    public record CenterInfoItem(
            String brandName,
            String brandSubtitle,
            String tagline,
            String heroBadge,
            String heroTitle,
            String heroHighlight,
            String heroDescription,
            String hotline,
            String hotlineDisplay,
            String email,
            String workTime,
            String defaultAddress,
            String zaloUrl,
            String messengerUrl,
            String footerDescription) {
    }

    public record RoadmapStepItem(
            UUID id,
            String step,
            String badgeText,
            String subTag,
            String title,
            String duration,
            String target,
            String colorTheme,
            List<String> features) {
        public RoadmapStepItem {
            features = List.copyOf(features);
        }
    }

    public record WhyChooseFeatureItem(
            UUID id,
            String iconKey,
            String variant,
            String subtitle,
            String title,
            String description) {
    }

    public record FaqItem(
            UUID id,
            String question,
            String answer) {
    }
}
