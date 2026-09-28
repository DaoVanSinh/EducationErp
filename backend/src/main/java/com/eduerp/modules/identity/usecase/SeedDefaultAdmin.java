package com.eduerp.modules.identity.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityProperties;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bảo đảm hệ thống luôn có ít nhất một tài khoản giữ Role ADMIN. Điều kiện kiểm tra là "có account
 * nào giữ role ADMIN" chứ không phải "đã tồn tại email mặc định" — nên khi Admin thật đã được tạo,
 * tài khoản mặc định không bị dựng lại, còn khi mất hết Admin thì nó quay lại.
 */
@Service
public class SeedDefaultAdmin {

    private final AccountRepository accounts;
    private final AccessManagement access;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;

    SeedDefaultAdmin(AccountRepository accounts, AccessManagement access, PasswordEncoder passwordEncoder,
            IdentityProperties properties) {
        this.accounts = accounts;
        this.access = access;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public void execute() {
        if (access.hasAnyAccountWithRole(AccessConstants.RoleCodes.ADMIN)) {
            return;
        }
        var admin = accounts.save(new Account(properties.defaultAdminEmail(),
                passwordEncoder.encode(properties.defaultAdminPassword()),
                IdentityConstants.Defaults.ADMIN_FULL_NAME, null));
        access.assignRole(admin.getId(), AccessConstants.RoleCodes.ADMIN);
    }
}
