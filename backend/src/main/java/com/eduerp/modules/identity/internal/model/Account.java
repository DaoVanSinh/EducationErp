package com.eduerp.modules.identity.internal.model;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.util.EmailNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdentityConstants.AccountStatus status;

    @ManyToOne
    @JoinColumn(name = "home_branch_id")
    private Branch homeBranch;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToMany
    @JoinTable(name = "account_groups",
            joinColumns = @JoinColumn(name = "account_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    private final Set<Group> groups = new HashSet<>();

    public Account(String email, String passwordHash, String fullName, Role role, Branch homeBranch) {
        // Chuẩn hoá tại đây để cột email chỉ tồn tại một dạng duy nhất, bất kể ai tạo Account.
        this.email = EmailNormalizer.normalize(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.homeBranch = homeBranch;
        this.status = IdentityConstants.AccountStatus.ACTIVE;
    }

    public Set<Group> getGroups() {
        return Collections.unmodifiableSet(groups);
    }

    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public void updateProfile(String fullName, String avatarUrl) {
        this.fullName = fullName;
        this.avatarUrl = avatarUrl;
    }

    public void transferToBranch(Branch branch) {
        this.homeBranch = branch;
    }

    public void joinGroup(Group group) {
        groups.add(group);
    }

    public void changeRole(Role role) {
        this.role = role;
    }
}
