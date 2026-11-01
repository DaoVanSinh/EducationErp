package com.eduerp.modules.landing.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "landing_roadmap_steps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LandingRoadmapStep {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "step_code", nullable = false)
    private String stepCode;

    @Column(name = "badge_text", nullable = false)
    private String badgeText;

    @Column(name = "sub_tag", nullable = false)
    private String subTag;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String duration;

    @Column(nullable = false)
    private String target;

    @Column(name = "color_theme", nullable = false)
    private String colorTheme;

    @Column(name = "features_text", nullable = false)
    private String featuresText;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public LandingRoadmapStep(
            String stepCode,
            String badgeText,
            String subTag,
            String title,
            String duration,
            String target,
            String colorTheme,
            String featuresText,
            int displayOrder) {
        this.stepCode = stepCode;
        this.badgeText = badgeText;
        this.subTag = subTag;
        this.title = title;
        this.duration = duration;
        this.target = target;
        this.colorTheme = colorTheme;
        this.featuresText = featuresText;
        this.displayOrder = displayOrder;
    }
}
