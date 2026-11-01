package com.eduerp.modules.landing.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "landing_center_configs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LandingCenterConfig {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "brand_subtitle", nullable = false)
    private String brandSubtitle;

    @Column(nullable = false)
    private String tagline;

    @Column(name = "hero_badge", nullable = false)
    private String heroBadge;

    @Column(name = "hero_title", nullable = false)
    private String heroTitle;

    @Column(name = "hero_highlight", nullable = false)
    private String heroHighlight;

    @Column(name = "hero_description", nullable = false)
    private String heroDescription;

    @Column(nullable = false)
    private String hotline;

    @Column(name = "hotline_display", nullable = false)
    private String hotlineDisplay;

    @Column(nullable = false)
    private String email;

    @Column(name = "work_time", nullable = false)
    private String workTime;

    @Column(name = "default_address", nullable = false)
    private String defaultAddress;

    @Column(name = "zalo_url", nullable = false)
    private String zaloUrl;

    @Column(name = "messenger_url", nullable = false)
    private String messengerUrl;

    @Column(name = "footer_description", nullable = false)
    private String footerDescription;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public LandingCenterConfig(
            String code,
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
        this.code = code;
        updateInfo(
                brandName,
                brandSubtitle,
                tagline,
                heroBadge,
                heroTitle,
                heroHighlight,
                heroDescription,
                hotline,
                hotlineDisplay,
                email,
                workTime,
                defaultAddress,
                zaloUrl,
                messengerUrl,
                footerDescription);
    }

    public void updateInfo(
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
        this.brandName = brandName;
        this.brandSubtitle = brandSubtitle;
        this.tagline = tagline;
        this.heroBadge = heroBadge;
        this.heroTitle = heroTitle;
        this.heroHighlight = heroHighlight;
        this.heroDescription = heroDescription;
        this.hotline = hotline;
        this.hotlineDisplay = hotlineDisplay;
        this.email = email;
        this.workTime = workTime;
        this.defaultAddress = defaultAddress;
        this.zaloUrl = zaloUrl;
        this.messengerUrl = messengerUrl;
        this.footerDescription = footerDescription;
        this.updatedAt = Instant.now();
    }
}
