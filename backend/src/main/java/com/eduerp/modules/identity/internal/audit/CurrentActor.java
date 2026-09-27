package com.eduerp.modules.identity.internal.audit;

import com.eduerp.modules.identity.AccountPrincipal;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Ai đang thực hiện request hiện tại. Rỗng với việc chạy nền hoặc runner lúc khởi động. */
@Component
class CurrentActor {

    Optional<AccountPrincipal> principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }
}
