package com.eduerp.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "permissions", uniqueConstraints = @UniqueConstraint(columnNames = {"resource", "action"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Permission {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String resource;

    @Column(nullable = false)
    private String action;

    Permission(String resource, String action) {
        this.resource = resource;
        this.action = action;
    }
}
