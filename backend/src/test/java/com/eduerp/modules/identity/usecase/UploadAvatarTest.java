package com.eduerp.modules.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.integrations.storage.StorageClient;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.InvalidAvatarException;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class UploadAvatarTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final StorageClient storage = mock(StorageClient.class);
    private final UploadAvatar useCase = new UploadAvatar(accounts, storage);

    @Test
    void normalizesPngImageUploadsToMinioAndDeletesPreviousAvatarKey() throws Exception {
        var accountId = UUID.randomUUID();
        var oldKey = "avatars/" + accountId + "/old-avatar.png";
        var account = new Account("teacher@eduerp.local", "hash", "Giảng Viên", null);
        account.updateProfile("Giảng Viên", IdentityConstants.Avatar.KEY_QUERY_PREFIX + oldKey);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));

        var newKey = "avatars/" + accountId + "/new-avatar.png";
        when(storage.upload(
                eq("avatars/" + accountId),
                eq(IdentityConstants.Avatar.NORMALIZED_FILE_NAME),
                any(byte[].class),
                eq(IdentityConstants.Avatar.CONTENT_TYPE_PNG)))
                .thenReturn(newKey);

        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        var avatarUrl = useCase.execute(accountId, out.toByteArray(), IdentityConstants.Avatar.CONTENT_TYPE_PNG, false);

        assertThat(avatarUrl).isEqualTo(IdentityConstants.Avatar.KEY_QUERY_PREFIX + newKey);
        assertThat(account.getAvatarUrl()).isEqualTo(IdentityConstants.Avatar.KEY_QUERY_PREFIX + newKey);
        verify(storage).delete(oldKey);
    }

    @Test
    void removesAvatarAndDeletesPreviousMinioObject() {
        var accountId = UUID.randomUUID();
        var oldKey = "avatars/" + accountId + "/old-avatar.png";
        var account = new Account("teacher@eduerp.local", "hash", "Giảng Viên", null);
        account.updateProfile("Giảng Viên", IdentityConstants.Avatar.KEY_QUERY_PREFIX + oldKey);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));

        var avatarUrl = useCase.execute(accountId, null, null, true);

        assertThat(avatarUrl).isNull();
        assertThat(account.getAvatarUrl()).isNull();
        verify(storage).delete(oldKey);
    }

    @Test
    void rejectsNonImagePayload() {
        var accountId = UUID.randomUUID();
        var account = new Account("teacher@eduerp.local", "hash", "Giảng Viên", null);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> useCase.execute(
                accountId,
                "not-an-image".getBytes(),
                IdentityConstants.Avatar.CONTENT_TYPE_PNG,
                false)).isInstanceOf(InvalidAvatarException.class);
    }
}
