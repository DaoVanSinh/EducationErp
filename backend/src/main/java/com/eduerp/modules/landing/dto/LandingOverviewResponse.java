package com.eduerp.modules.landing.dto;

import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Toàn bộ dữ liệu công khai hiển thị trên Landing Page — lấy trực tiếp từ các bảng nghiệp vụ ERP
 * và bảng cấu hình trung tâm.
 */
public record LandingOverviewResponse(
        LandingStats stats,
        List<LandingCourseItem> courses,
        List<LandingClassItem> classes,
        List<LandingTeacherItem> teachers,
        List<LandingBranchItem> branches,
        List<LandingComboTierItem> comboDiscountTiers,
        LandingContentConfigResponse config) {

    public LandingOverviewResponse {
        courses = List.copyOf(courses);
        classes = List.copyOf(classes);
        teachers = List.copyOf(teachers);
        branches = List.copyOf(branches);
        comboDiscountTiers = List.copyOf(comboDiscountTiers);
    }

    public record LandingStats(
            int activeCourseCount,
            int activeClassCount,
            int activeTeacherCount,
            int activeBranchCount,
            BigDecimal maxComboDiscountPercent) {
    }

    public record LandingCourseItem(
            UUID id,
            String code,
            String name,
            String description,
            Integer standardSessionCount,
            BigDecimal tuitionFee,
            int openClassCount,
            List<GradeColumnResponse> gradeColumns) {
        public LandingCourseItem {
            gradeColumns = List.copyOf(gradeColumns);
        }
    }

    public record LandingClassItem(
            UUID id,
            String code,
            UUID courseId,
            String courseName,
            UUID branchId,
            String branchName,
            String teacherName,
            int maxSeats,
            List<WeeklyScheduleSlot> schedule) {
        public LandingClassItem {
            schedule = List.copyOf(schedule);
        }
    }

    public record LandingTeacherItem(
            UUID id,
            String fullName,
            List<String> subjects,
            String bio) {
        public LandingTeacherItem {
            subjects = List.copyOf(subjects);
        }
    }

    public record LandingBranchItem(
            UUID id,
            String code,
            String name,
            String address) {
    }

    public record LandingComboTierItem(
            UUID id,
            int minCourseCount,
            BigDecimal discountPercent) {
    }
}
