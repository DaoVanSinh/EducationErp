package com.eduerp.modules.access.internal.rules;

import com.eduerp.modules.access.AccessConstants;
import org.springframework.stereotype.Component;

/**
 * Các quyết định của module access, tách khỏi use case để test được không cần Spring.
 * Là {@code @Component} chỉ để inject được; bản thân class vẫn thuần, có thể {@code new} trong test.
 */
@Component
public class AccessRules {

    /** Cùng một quyền được cấp nhiều nơi thì cấp độ rộng hơn thắng. */
    public boolean isBroaderOrEqual(AccessConstants.PermissionScope candidate,
            AccessConstants.PermissionScope current) {
        return candidate.rank() >= current.rank();
    }
}
