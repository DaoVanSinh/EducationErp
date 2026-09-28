package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityProperties;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.repository.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bảo đảm hệ thống luôn có ít nhất một tài khoản giữ Role ADMIN. Điều kiện kiểm tra là "có Account
 * nào mang Role ADMIN" chứ không phải "đã tồn tại email mặc định" — nên khi Admin thật đã được tạo,
 * tài khoản mặc định không bị dựng lại, còn khi mất hết Admin thì nó quay lại.
 */
@Service
public class SeedDefaultAdmin {

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;

    SeedDefaultAdmin(AccountRepository accounts, RoleRepository roles, PasswordEncoder passwordEncoder,
            IdentityProperties properties) {
        this.accounts = accounts;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public void execute() {
        if (accounts.countByRole_Code(IdentityConstants.RoleCodes.ADMIN) > 0) {
            return;
        }
        var adminRole = roles.findByCode(IdentityConstants.RoleCodes.ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "Role ADMIN chưa được seed — kiểm tra V5__seed_default_rbac.sql"));
        accounts.save(new Account(properties.defaultAdminEmail(),
                passwordEncoder.encode(properties.defaultAdminPassword()),
                IdentityConstants.Defaults.ADMIN_FULL_NAME, adminRole, null));
    }
}
