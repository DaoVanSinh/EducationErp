package com.eduerp.modules.identity.dto;

/** Cặp token của một phiên. Việc gói vào cookie là chuyện của tầng web, không phải của use case. */
public record SessionTokens(String accessToken, String refreshToken) {
}
