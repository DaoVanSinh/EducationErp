package com.eduerp.identity;

import java.time.Instant;
import java.util.UUID;

record DecodedToken(UUID accountId, String jti, Instant expiresAt) {
}
