package com.eduerp.modules.landing.usecase;

import com.eduerp.modules.landing.LandingConstants;
import com.eduerp.modules.landing.dto.LandingContentConfigResponse;
import com.eduerp.modules.landing.dto.UpdateLandingConfigRequest;
import com.eduerp.modules.landing.internal.model.LandingCenterConfig;
import com.eduerp.modules.landing.internal.model.LandingFaq;
import com.eduerp.modules.landing.internal.model.LandingFeature;
import com.eduerp.modules.landing.internal.model.LandingRoadmapStep;
import com.eduerp.modules.landing.internal.repository.LandingCenterConfigRepository;
import com.eduerp.modules.landing.internal.repository.LandingFaqRepository;
import com.eduerp.modules.landing.internal.repository.LandingFeatureRepository;
import com.eduerp.modules.landing.internal.repository.LandingRoadmapStepRepository;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateLandingConfig {

    private final LandingCenterConfigRepository centerConfigs;
    private final LandingRoadmapStepRepository roadmapSteps;
    private final LandingFeatureRepository features;
    private final LandingFaqRepository faqs;
    private final GetLandingContentConfig getContentConfig;

    UpdateLandingConfig(
            LandingCenterConfigRepository centerConfigs,
            LandingRoadmapStepRepository roadmapSteps,
            LandingFeatureRepository features,
            LandingFaqRepository faqs,
            GetLandingContentConfig getContentConfig) {
        this.centerConfigs = centerConfigs;
        this.roadmapSteps = roadmapSteps;
        this.features = features;
        this.faqs = faqs;
        this.getContentConfig = getContentConfig;
    }

    @Transactional
    public LandingContentConfigResponse execute(UpdateLandingConfigRequest request) {
        upsertCenterInfo(request.centerInfo());
        replaceRoadmapSteps(request);
        replaceFeatures(request);
        replaceFaqs(request);
        return getContentConfig.execute();
    }

    private void upsertCenterInfo(UpdateLandingConfigRequest.CenterInfoPayload info) {
        var existing = centerConfigs.findByCode(LandingConstants.Defaults.DEFAULT_CONFIG_CODE);
        if (existing.isPresent()) {
            existing.get().updateInfo(
                    info.brandName().trim(),
                    info.brandSubtitle().trim(),
                    info.tagline().trim(),
                    info.heroBadge().trim(),
                    info.heroTitle().trim(),
                    info.heroHighlight().trim(),
                    info.heroDescription().trim(),
                    info.hotline().trim(),
                    info.hotlineDisplay().trim(),
                    info.email().trim(),
                    info.workTime().trim(),
                    info.defaultAddress().trim(),
                    info.zaloUrl().trim(),
                    info.messengerUrl().trim(),
                    info.footerDescription().trim());
            return;
        }
        centerConfigs.save(new LandingCenterConfig(
                LandingConstants.Defaults.DEFAULT_CONFIG_CODE,
                info.brandName().trim(),
                info.brandSubtitle().trim(),
                info.tagline().trim(),
                info.heroBadge().trim(),
                info.heroTitle().trim(),
                info.heroHighlight().trim(),
                info.heroDescription().trim(),
                info.hotline().trim(),
                info.hotlineDisplay().trim(),
                info.email().trim(),
                info.workTime().trim(),
                info.defaultAddress().trim(),
                info.zaloUrl().trim(),
                info.messengerUrl().trim(),
                info.footerDescription().trim()));
    }

    private void replaceRoadmapSteps(UpdateLandingConfigRequest request) {
        roadmapSteps.deleteAllInBatch();
        var entities = new ArrayList<LandingRoadmapStep>();
        var items = request.roadmapSteps();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            var joinedFeatures = String.join("\n", item.features());
            entities.add(new LandingRoadmapStep(
                    item.step().trim(),
                    item.badgeText().trim(),
                    item.subTag().trim(),
                    item.title().trim(),
                    item.duration().trim(),
                    item.target().trim(),
                    item.colorTheme().trim(),
                    joinedFeatures,
                    i + 1));
        }
        roadmapSteps.saveAll(entities);
    }

    private void replaceFeatures(UpdateLandingConfigRequest request) {
        features.deleteAllInBatch();
        var entities = new ArrayList<LandingFeature>();
        var items = request.whyChooseFeatures();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            entities.add(new LandingFeature(
                    item.iconKey().trim(),
                    item.variant().trim(),
                    item.subtitle().trim(),
                    item.title().trim(),
                    item.description().trim(),
                    i + 1));
        }
        features.saveAll(entities);
    }

    private void replaceFaqs(UpdateLandingConfigRequest request) {
        faqs.deleteAllInBatch();
        var entities = new ArrayList<LandingFaq>();
        var items = request.faqs();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            entities.add(new LandingFaq(item.question().trim(), item.answer().trim(), i + 1));
        }
        faqs.saveAll(entities);
    }
}
