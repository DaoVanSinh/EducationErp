package com.eduerp.identity;

import java.time.Instant;

record IssuedToken(String jti, String token, Instant expiresAt) {
}
