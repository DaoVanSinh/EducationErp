package com.eduerp.modules.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class IdentityExceptionTest {

    @Test
    void emailAlreadyExistsIsConflict() {
        var ex = new EmailAlreadyExistsException("a@b.com");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("IDENTITY_EMAIL_ALREADY_EXISTS");
    }

    @Test
    void accountInviteExpiredIsUnauthorized() {
        var ex = new AccountInviteExpiredException();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getErrorCode()).isEqualTo("IDENTITY_INVITE_EXPIRED");
    }

    @Test
    void accountAlreadyActivatedIsConflict() {
        var id = java.util.UUID.randomUUID();
        var ex = new AccountAlreadyActivatedException(id);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("IDENTITY_ACCOUNT_ALREADY_ACTIVATED");
    }

    @Test
    void invalidAvatarIsBadRequest() {
        var ex = new InvalidAvatarException();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo(IdentityConstants.ErrorCodes.INVALID_AVATAR);
    }

    @Test
    void avatarNotFoundIsNotFound() {
        var id = java.util.UUID.randomUUID();
        var ex = new AvatarNotFoundException(id);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo(IdentityConstants.ErrorCodes.AVATAR_NOT_FOUND);
    }
}
