package com.eduerp.modules.identity.web;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.DashboardStatsResponse;
import com.eduerp.modules.identity.usecase.GetDashboardStats;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
class DashboardController {

    private final GetDashboardStats getDashboardStats;

    DashboardController(GetDashboardStats getDashboardStats) {
        this.getDashboardStats = getDashboardStats;
    }

    @GetMapping("/stats")
    @PreAuthorize(IdentityConstants.AccessRules.READ_DASHBOARD)
    DashboardStatsResponse stats() {
        return getDashboardStats.execute();
    }
}
