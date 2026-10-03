package com.eduerp.modules.courses.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.dto.CourseResponse;
import com.eduerp.modules.courses.dto.CreateCourseRequest;
import com.eduerp.modules.courses.dto.UpdateCourseRequest;
import com.eduerp.modules.courses.usecase.CreateCourse;
import com.eduerp.modules.courses.usecase.ListCourses;
import com.eduerp.modules.courses.usecase.UpdateCourse;
import com.eduerp.shared.AccountPrincipal;
import com.eduerp.shared.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quản trị danh mục khóa học — không có endpoint xoá, chỉ vô hiệu hoá qua {@code active}. */
@RestController
@RequestMapping("/api/courses/courses")
class CourseAdminController {

    private final ListCourses listCourses;
    private final CreateCourse createCourse;
    private final UpdateCourse updateCourse;

    CourseAdminController(ListCourses listCourses, CreateCourse createCourse, UpdateCourse updateCourse) {
        this.listCourses = listCourses;
        this.createCourse = createCourse;
        this.updateCourse = updateCourse;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_COURSE)
    PageResponse<CourseResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return listCourses.execute(pageable);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_COURSE)
    UUID create(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody CreateCourseRequest request) {
        return createCourse.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @PatchMapping("/{courseId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_COURSE)
    void update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request) {
        updateCourse.execute(courseId, principal.accountId(), principal.homeBranchId(), request);
    }
}
