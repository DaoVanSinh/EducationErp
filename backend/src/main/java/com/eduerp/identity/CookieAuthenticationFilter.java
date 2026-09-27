package com.eduerp.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

class CookieAuthenticationFilter extends OncePerRequestFilter {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService blacklist;
    private final PermissionCacheService permissionCache;
    private final AccountRepository accounts;

    CookieAuthenticationFilter(JwtTokenService jwtTokenService, TokenBlacklistService blacklist,
            PermissionCacheService permissionCache, AccountRepository accounts) {
        this.jwtTokenService = jwtTokenService;
        this.blacklist = blacklist;
        this.permissionCache = permissionCache;
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        readCookie(request, ACCESS_TOKEN_COOKIE).ifPresent(this::authenticate);
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            var decoded = jwtTokenService.verify(token);
            if (blacklist.isBlacklisted(decoded.jti())) {
                return;
            }
            var account = accounts.findById(decoded.accountId()).orElse(null);
            if (account == null) {
                return;
            }
            var permissions = permissionCache.getEffectivePermissions(account.getId());
            List<GrantedAuthority> authorities = permissions.stream()
                    .map(p -> new SimpleGrantedAuthority(IdentityConstants.Authorities.PERMISSION_AUTHORITY_PREFIX
                            + p.resource() + ":" + p.action() + ":" + p.scope()))
                    .map(GrantedAuthority.class::cast)
                    .toList();
            var principal = new AccountPrincipal(account.getId(),
                    account.getHomeBranch() == null ? null : account.getHomeBranch().getId());
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException e) {
            // Token không hợp lệ/hết hạn hoặc tài khoản không còn tồn tại: coi request là chưa xác thực
            // thay vì để lỗi rò rỉ thành 500 - filter phải fail-closed về mặt bảo mật.
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return List.of(request.getCookies()).stream()
                .filter(c -> c.getName().equals(name))
                .map(Cookie::getValue)
                .findFirst();
    }
}
