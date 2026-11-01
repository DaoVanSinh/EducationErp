package com.eduerp.modules.identity.usecase;

import com.eduerp.integrations.storage.StorageClient;
import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.util.AvatarImageNormalizer;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chuẩn hoá và đẩy ảnh đại diện lên MinIO (hoặc xoá ảnh đại diện để về chữ cái đầu) — theo khuôn
 * {@code SaveMcpLogo} của mcp-hub.
 */
@Service
public class UploadAvatar {

    private static final Logger log = LoggerFactory.getLogger(UploadAvatar.class);

    private final AccountRepository accounts;
    private final StorageClient storage;

    UploadAvatar(AccountRepository accounts, StorageClient storage) {
        this.accounts = accounts;
        this.storage = storage;
    }

    @Transactional
    public String execute(UUID accountId, byte[] rawBytes, String contentType, boolean remove) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        String previousKey = extractMinioKey(account.getAvatarUrl());

        if (remove) {
            account.updateProfile(account.getFullName(), null);
            deletePreviousObjectQuietly(previousKey);
            return null;
        }

        var normalized = AvatarImageNormalizer.normalize(rawBytes, contentType);
        String keyPrefix = IdentityConstants.Avatar.KEY_PREFIX + "/" + accountId;
        String key = storage.upload(
                keyPrefix,
                normalized.fileName(),
                normalized.content(),
                normalized.contentType());
        String newAvatarUrl = IdentityConstants.Avatar.KEY_QUERY_PREFIX + key;
        account.updateProfile(account.getFullName(), newAvatarUrl);
        deletePreviousObjectQuietly(previousKey);
        return newAvatarUrl;
    }

    private static String extractMinioKey(String avatarUrl) {
        if (avatarUrl != null && avatarUrl.startsWith(IdentityConstants.Avatar.KEY_QUERY_PREFIX)) {
            String key = avatarUrl.substring(IdentityConstants.Avatar.KEY_QUERY_PREFIX.length());
            return key.isBlank() ? null : key;
        }
        return null;
    }

    private void deletePreviousObjectQuietly(String previousKey) {
        if (previousKey == null) {
            return;
        }
        try {
            storage.delete(previousKey);
        } catch (RuntimeException ex) {
            log.warn("Không xoá được ảnh đại diện cũ trên MinIO (key={}): {}", previousKey, ex.getMessage());
        }
    }
}
