package com.eduerp.identity.internal.token;

import java.time.Instant;
import java.util.UUID;

public record DecodedToken(UUID accountId, String jti, Instant expiresAt, String type) {
}
