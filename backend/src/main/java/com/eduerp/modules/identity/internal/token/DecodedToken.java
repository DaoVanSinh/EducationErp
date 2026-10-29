package com.eduerp.modules.identity.internal.token;

import java.time.Instant;
import java.util.UUID;

/**
 * @param issuedAt thời điểm phát hành, chính xác tới mili-giây (claim {@code ims}); {@code null} với token
 *        phát hành trước khi hệ thống ghi claim này - coi như phát hành từ đầu, bị mọi mốc buộc đăng xuất thu hồi
 */
public record DecodedToken(UUID accountId, String jti, Instant expiresAt, String type, Instant issuedAt) {
}
