package com.eduerp.modules.academic.web;

import com.eduerp.modules.academic.dto.MyLearningClassResponse;
import com.eduerp.modules.academic.usecase.GetMyLearning;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.shared.AccountPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cổng học tập của học viên: lớp, lịch học, điểm và tiến trình của CHÍNH người gọi. Dữ liệu luôn
 * lọc theo {@code principal.accountId()} - không nhận id từ client.
 */
@RestController
@RequestMapping("/api/academic/me")
class LearningSelfServiceController {

    private final GetMyLearning getMyLearning;

    LearningSelfServiceController(GetMyLearning getMyLearning) {
        this.getMyLearning = getMyLearning;
    }

    @GetMapping("/classes")
    @PreAuthorize(AccessConstants.AccessRules.READ_LEARNING)
    List<MyLearningClassResponse> myClasses(@AuthenticationPrincipal AccountPrincipal principal) {
        return getMyLearning.execute(principal.accountId());
    }
}
