package com.eduerp.modules.identity.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.AccountNotFoundException;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.TokenInvalidException;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.identity.internal.token.JwtTokenService;
import com.eduerp.modules.identity.internal.token.TokenBlacklistService;
import com.eduerp.shared.AccountPrincipal;
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

/** Đọc access token từ cookie, nạp quyền hiệu lực từ Redis (module access) thành authority cho request hiện tại. */
class CookieAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService blacklist;
    private final AccessManagement access;
    private final AccountRepository accounts;

    CookieAuthenticationFilter(JwtTokenService jwtTokenService, TokenBlacklistService blacklist,
            AccessManagement access, AccountRepository accounts) {
        this.jwtTokenService = jwtTokenService;
        this.blacklist = blacklist;
        this.access = access;
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        readCookie(request, IdentityConstants.Cookies.ACCESS_TOKEN).ifPresent(this::authenticate);
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            var decoded = jwtTokenService.verify(token);
            if (!IdentityConstants.TokenTypes.ACCESS.equals(decoded.type())) {
                return;
            }
            if (blacklist.isBlacklisted(decoded.jti())) {
                return;
            }
            var account = accounts.findById(decoded.accountId()).orElse(null);
            if (account == null) {
                return;
            }
            var permissions = access.effectivePermissions(account.getId());
            List<GrantedAuthority> authorities = permissions.stream()
                    .map(p -> new SimpleGrantedAuthority(AccessConstants.Authorities.PERMISSION_AUTHORITY_PREFIX
                            + p.resource() + ":" + p.action() + ":" + p.scope()))
                    .map(GrantedAuthority.class::cast)
                    .toList();
            var principal = new AccountPrincipal(account.getId(), account.getHomeBranchId());
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (TokenInvalidException | AccountNotFoundException e) {
            // Token không hợp lệ/hết hạn/bị thu hồi, hoặc tài khoản đã bị xoá giữa hai lần tra cứu:
            // coi request là chưa xác thực (fail-closed). Lỗi hạ tầng khác (Redis/DB down, lỗi
            // deserialize) KHÔNG bắt ở đây - phải trồi lên thành 500 để cảnh báo vận hành, không
            // được âm thầm biến thành "người dùng bị đăng xuất".
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
