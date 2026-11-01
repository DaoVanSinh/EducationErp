package com.eduerp.modules.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.integrations.storage.StorageClient;
import com.eduerp.modules.identity.AvatarNotFoundException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DownloadAvatarTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final StorageClient storage = mock(StorageClient.class);
    private final DownloadAvatar useCase = new DownloadAvatar(accounts, storage);

    @Test
    void downloadsAvatarBytesFromMinioForCurrentAccount() {
        var accountId = UUID.randomUUID();
        var key = "avatars/" + accountId + "/avatar.png";
        var account = new Account("student@eduerp.local", "hash", "Học Viên", null);
        account.updateProfile("Học Viên", IdentityConstants.Avatar.KEY_QUERY_PREFIX + key);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(storage.download(key)).thenReturn(new byte[]{1, 2, 3});

        var result = useCase.execute(accountId);

        assertThat(result.content()).containsExactly(1, 2, 3);
        assertThat(result.contentType()).isEqualTo(IdentityConstants.Avatar.CONTENT_TYPE_PNG);
    }

    @Test
    void throwsWhenAccountHasNoAvatarInMinio() {
        var accountId = UUID.randomUUID();
        var account = new Account("student@eduerp.local", "hash", "Học Viên", null);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> useCase.execute(accountId))
                .isInstanceOf(AvatarNotFoundException.class);
    }
}
