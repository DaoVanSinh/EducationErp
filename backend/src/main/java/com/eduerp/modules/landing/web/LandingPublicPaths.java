package com.eduerp.modules.landing.web;

import com.eduerp.modules.landing.LandingConstants;

/**
 * Đường dẫn công khai của module landing — chỉ dùng nội bộ trong package {@code web} để tránh vòng
 * phụ thuộc với {@code modules.identity} (mirror {@code BillingPublicPaths}).
 */
final class LandingPublicPaths {

    private LandingPublicPaths() {
    }

    static final String[] ALL = {
            LandingConstants.Paths.OVERVIEW,
            LandingConstants.Paths.PUBLIC_CONSULTATIONS
    };
}
