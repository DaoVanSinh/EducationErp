package com.eduerp.identity.internal.token;

import java.time.Instant;

public record IssuedToken(String jti, String token, Instant expiresAt) {
}
