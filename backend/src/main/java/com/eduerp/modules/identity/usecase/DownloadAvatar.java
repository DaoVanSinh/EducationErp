package com.eduerp.modules.identity.usecase;

import com.eduerp.integrations.storage.StorageClient;
import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.AvatarNotFoundException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đọc ảnh đại diện từ bucket MinIO thông qua phiên đăng nhập — theo khuôn {@code ReadMcpLogo} của
 * mcp-hub, không phơi bày đường dẫn nội bộ hay khoá S3 ra trình duyệt.
 */
@Service
public class DownloadAvatar {

    private final AccountRepository accounts;
    private final StorageClient storage;

    DownloadAvatar(AccountRepository accounts, StorageClient storage) {
        this.accounts = accounts;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public AvatarFileResult execute(UUID accountId) {
        var account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        var avatarUrl = account.getAvatarUrl();
        if (avatarUrl == null || !avatarUrl.startsWith(IdentityConstants.Avatar.KEY_QUERY_PREFIX)) {
            throw new AvatarNotFoundException(accountId);
        }
        var key = avatarUrl.substring(IdentityConstants.Avatar.KEY_QUERY_PREFIX.length());
        if (key.isBlank()) {
            throw new AvatarNotFoundException(accountId);
        }
        var contentType = key.endsWith(".webp")
                ? IdentityConstants.Avatar.CONTENT_TYPE_WEBP
                : IdentityConstants.Avatar.CONTENT_TYPE_PNG;
        return new AvatarFileResult(storage.download(key), contentType);
    }

    public record AvatarFileResult(byte[] content, String contentType) {
    }
}
