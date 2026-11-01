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
@Table(name = "landing_faqs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LandingFaq {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String question;

    @Column(nullable = false)
    private String answer;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public LandingFaq(String question, String answer, int displayOrder) {
        this.question = question;
        this.answer = answer;
        this.displayOrder = displayOrder;
    }
}
