package com.eduerp.identity.internal.rules;

import com.eduerp.identity.IdentityConstants;
import org.springframework.stereotype.Component;

/**
 * Các quyết định của module identity, tách khỏi use case để test được không cần Spring/DB.
 * Là {@code @Component} chỉ để inject được; bản thân class vẫn thuần, có thể {@code new} trong test.
 */
@Component
public class IdentityRules {

    /** Tài khoản bị vô hiệu hoá thì không được tạo phiên mới, dù mật khẩu đúng. */
    public boolean canSignIn(IdentityConstants.AccountStatus status) {
        return status == IdentityConstants.AccountStatus.ACTIVE;
    }

    /** Chỉ refresh token được đổi lấy phiên mới — access token trình ra ở đây là tấn công type-confusion. */
    public boolean isRefreshToken(String tokenType) {
        return IdentityConstants.TokenTypes.REFRESH.equals(tokenType);
    }

    /** Cùng một quyền được cấp nhiều nơi thì cấp độ rộng hơn thắng. */
    public boolean isBroaderOrEqual(IdentityConstants.PermissionScope candidate,
            IdentityConstants.PermissionScope current) {
        return candidate.rank() >= current.rank();
    }
}
