package com.eduerp.modules.identity.internal.model;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.util.EmailNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Chỉ còn giữ dữ liệu của riêng identity: thông tin đăng nhập và hồ sơ cơ bản. Role/group (access)
 * và tên chi nhánh (organization) không còn là quan hệ JPA ở đây — {@code homeBranchId} là UUID
 * trần, không {@code @ManyToOne} (rule #3): tên chi nhánh cần hiển thị thì use case tự gọi
 * {@code OrganizationManagement} để ghép, không JOIN chéo module.
 */
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

    @Column(name = "home_branch_id")
    private UUID homeBranchId;

    public Account(String email, String passwordHash, String fullName, UUID homeBranchId) {
        // Chuẩn hoá tại đây để cột email chỉ tồn tại một dạng duy nhất, bất kể ai tạo Account.
        this.email = EmailNormalizer.normalize(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.homeBranchId = homeBranchId;
        this.status = IdentityConstants.AccountStatus.ACTIVE;
    }

    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public void updateProfile(String fullName, String avatarUrl) {
        this.fullName = fullName;
        this.avatarUrl = avatarUrl;
    }

    public void transferToBranch(UUID branchId) {
        this.homeBranchId = branchId;
    }
}
