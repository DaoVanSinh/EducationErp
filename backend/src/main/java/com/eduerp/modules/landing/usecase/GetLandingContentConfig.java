package com.eduerp.modules.landing.usecase;

import com.eduerp.modules.landing.LandingConstants;
import com.eduerp.modules.landing.dto.LandingContentConfigResponse;
import com.eduerp.modules.landing.dto.LandingContentConfigResponse.CenterInfoItem;
import com.eduerp.modules.landing.dto.LandingContentConfigResponse.FaqItem;
import com.eduerp.modules.landing.dto.LandingContentConfigResponse.RoadmapStepItem;
import com.eduerp.modules.landing.dto.LandingContentConfigResponse.WhyChooseFeatureItem;
import com.eduerp.modules.landing.internal.model.LandingCenterConfig;
import com.eduerp.modules.landing.internal.repository.LandingCenterConfigRepository;
import com.eduerp.modules.landing.internal.repository.LandingFaqRepository;
import com.eduerp.modules.landing.internal.repository.LandingFeatureRepository;
import com.eduerp.modules.landing.internal.repository.LandingRoadmapStepRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetLandingContentConfig {

    private final LandingCenterConfigRepository centerConfigs;
    private final LandingRoadmapStepRepository roadmapSteps;
    private final LandingFeatureRepository features;
    private final LandingFaqRepository faqs;

    GetLandingContentConfig(
            LandingCenterConfigRepository centerConfigs,
            LandingRoadmapStepRepository roadmapSteps,
            LandingFeatureRepository features,
            LandingFaqRepository faqs) {
        this.centerConfigs = centerConfigs;
        this.roadmapSteps = roadmapSteps;
        this.features = features;
        this.faqs = faqs;
    }

    @Transactional(readOnly = true)
    public LandingContentConfigResponse execute() {
        var centerInfo = centerConfigs
                .findByCode(LandingConstants.Defaults.DEFAULT_CONFIG_CODE)
                .map(this::toCenterInfoItem)
                .orElseGet(this::defaultCenterInfoItem);

        var roadmapItems = roadmapSteps.findAllByOrderByDisplayOrderAsc().stream()
                .map(step -> new RoadmapStepItem(
                        step.getId(),
                        step.getStepCode(),
                        step.getBadgeText(),
                        step.getSubTag(),
                        step.getTitle(),
                        step.getDuration(),
                        step.getTarget(),
                        step.getColorTheme(),
                        splitFeatures(step.getFeaturesText())))
                .toList();

        var featureItems = features.findAllByOrderByDisplayOrderAsc().stream()
                .map(f -> new WhyChooseFeatureItem(
                        f.getId(),
                        f.getIconKey(),
                        f.getVariant(),
                        f.getSubtitle(),
                        f.getTitle(),
                        f.getDescription()))
                .toList();

        var faqItems = faqs.findAllByOrderByDisplayOrderAsc().stream()
                .map(q -> new FaqItem(q.getId(), q.getQuestion(), q.getAnswer()))
                .toList();

        return new LandingContentConfigResponse(centerInfo, roadmapItems, featureItems, faqItems);
    }

    private CenterInfoItem toCenterInfoItem(LandingCenterConfig entity) {
        return new CenterInfoItem(
                entity.getBrandName(),
                entity.getBrandSubtitle(),
                entity.getTagline(),
                entity.getHeroBadge(),
                entity.getHeroTitle(),
                entity.getHeroHighlight(),
                entity.getHeroDescription(),
                entity.getHotline(),
                entity.getHotlineDisplay(),
                entity.getEmail(),
                entity.getWorkTime(),
                entity.getDefaultAddress(),
                entity.getZaloUrl(),
                entity.getMessengerUrl(),
                entity.getFooterDescription());
    }

    private CenterInfoItem defaultCenterInfoItem() {
        return new CenterInfoItem(
                LandingConstants.Defaults.BRAND_NAME,
                LandingConstants.Defaults.BRAND_SUBTITLE,
                LandingConstants.Defaults.TAGLINE,
                LandingConstants.Defaults.HERO_BADGE,
                LandingConstants.Defaults.HERO_TITLE,
                LandingConstants.Defaults.HERO_HIGHLIGHT,
                LandingConstants.Defaults.HERO_DESCRIPTION,
                LandingConstants.Defaults.HOTLINE,
                LandingConstants.Defaults.HOTLINE_DISPLAY,
                LandingConstants.Defaults.EMAIL,
                LandingConstants.Defaults.WORK_TIME,
                LandingConstants.Defaults.DEFAULT_ADDRESS,
                LandingConstants.Defaults.ZALO_URL,
                LandingConstants.Defaults.MESSENGER_URL,
                LandingConstants.Defaults.FOOTER_DESCRIPTION);
    }

    private static List<String> splitFeatures(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
