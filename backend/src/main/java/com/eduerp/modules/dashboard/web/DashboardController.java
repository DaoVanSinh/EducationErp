package com.eduerp.modules.dashboard.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.dashboard.dto.DashboardStatsResponse;
import com.eduerp.modules.dashboard.usecase.GetDashboardStats;
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
    @PreAuthorize(AccessConstants.AccessRules.READ_DASHBOARD)
    DashboardStatsResponse stats() {
        return getDashboardStats.execute();
    }
}
