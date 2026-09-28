package com.eduerp.modules.identity.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "branches")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Branch {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    @Setter
    private String name;

    @Setter
    private String address;

    @Column(nullable = false)
    @Setter
    private boolean active = true;

    public Branch(String code, String name, String address) {
        this.code = code;
        this.name = name;
        this.address = address;
        this.active = true;
    }
}
