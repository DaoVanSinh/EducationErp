package com.eduerp.modules.identity.dto;

public record LoginResult(SessionTokens tokens, boolean requiresPasswordChange) {

    public static LoginResult needsPasswordChange() {
        return new LoginResult(null, true);
    }

    public static LoginResult of(SessionTokens tokens) {
        return new LoginResult(tokens, false);
    }
}
