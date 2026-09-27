package com.eduerp.modules.identity.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stub tối thiểu chỉ để có 1 route thật cho {@code CookieAuthenticationFilterIT}.
 * Task 17 sẽ thay thế bằng bản đầy đủ.
 */
@RestController
@RequestMapping("/api/dashboard")
class DashboardController {

    @GetMapping("/stats")
    @PreAuthorize("hasPermission(null, T(com.eduerp.modules.identity.IdentityConstants.Resources).DASHBOARD, "
            + "T(com.eduerp.modules.identity.IdentityConstants.Actions).READ)")
    String stats() {
        return "{}";
    }
}
