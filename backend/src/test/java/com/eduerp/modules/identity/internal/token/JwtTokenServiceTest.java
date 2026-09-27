package com.eduerp.modules.identity.internal.token;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.IdentityProperties;
import com.eduerp.modules.identity.TokenInvalidException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private final IdentityProperties properties = new IdentityProperties(
            "test-secret-key-must-be-at-least-32-bytes-long",
            Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofMinutes(30),
            "no-reply@eduerp.local", "http://localhost:5173/reset-password");

    private final JwtTokenService service = new JwtTokenService(properties);

    @Test
    void issuesAndVerifiesAccessToken() {
        var accountId = UUID.randomUUID();
        var issued = service.issueAccessToken(accountId);

        var decoded = service.verify(issued.token());

        assertThat(decoded.accountId()).isEqualTo(accountId);
        assertThat(decoded.jti()).isEqualTo(issued.jti());
    }

    @Test
    void rejectsTamperedToken() {
        var issued = service.issueAccessToken(UUID.randomUUID());
        var tampered = issued.token().substring(0, issued.token().length() - 2) + "xx";

        assertThatThrownBy(() -> service.verify(tampered)).isInstanceOf(TokenInvalidException.class);
    }

    @Test
    void issuedTokensCarryTheCorrectType() {
        var accountId = UUID.randomUUID();

        var access = service.verify(service.issueAccessToken(accountId).token());
        var refresh = service.verify(service.issueRefreshToken(accountId).token());

        assertThat(access.type()).isEqualTo(IdentityConstants.TokenTypes.ACCESS);
        assertThat(refresh.type()).isEqualTo(IdentityConstants.TokenTypes.REFRESH);
    }
}
