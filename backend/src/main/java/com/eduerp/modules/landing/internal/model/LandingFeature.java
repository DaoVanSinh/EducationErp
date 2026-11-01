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
@Table(name = "landing_features")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LandingFeature {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "icon_key", nullable = false)
    private String iconKey;

    @Column(nullable = false)
    private String variant;

    @Column(nullable = false)
    private String subtitle;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public LandingFeature(
            String iconKey,
            String variant,
            String subtitle,
            String title,
            String description,
            int displayOrder) {
        this.iconKey = iconKey;
        this.variant = variant;
        this.subtitle = subtitle;
        this.title = title;
        this.description = description;
        this.displayOrder = displayOrder;
    }
}
