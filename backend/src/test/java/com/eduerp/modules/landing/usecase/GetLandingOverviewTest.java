package com.eduerp.modules.landing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingManagement;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.landing.LandingConstants;
import com.eduerp.modules.landing.dto.LandingContentConfigResponse;
import com.eduerp.modules.landing.dto.LandingContentConfigResponse.CenterInfoItem;
import com.eduerp.modules.organization.OrganizationManagement;
import com.eduerp.modules.teachers.TeachersManagement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetLandingOverviewTest {

    @Mock
    CoursesManagement courses;

    @Mock
    OrganizationManagement organization;

    @Mock
    TeachersManagement teachers;

    @Mock
    IdentityManagement identity;

    @Mock
    BillingManagement billing;

    @Mock
    GetLandingContentConfig getContentConfig;

    @InjectMocks
    GetLandingOverview useCase;

    @Test
    void aggregatesActiveCoursesClassesTeachersBranchesComboTiersAndConfig() {
        var courseId = UUID.randomUUID();
        var classId = UUID.randomUUID();
        var branchId = UUID.randomUUID();
        var teacherAccountId = UUID.randomUUID();
        var teacherProfileId = UUID.randomUUID();
        var tierId = UUID.randomUUID();
        var columnId = UUID.randomUUID();

        when(courses.listActiveCourses()).thenReturn(List.of(
                new CoursesManagement.ActiveCourseSnapshot(
                        courseId,
                        "ENG-IELTS",
                        "Luyện Thi IELTS Chuyên Sâu",
                        "Khoá đào tạo 4 kỹ năng chuẩn quốc tế cam kết đầu ra",
                        36,
                        new BigDecimal("12500000"),
                        List.of(new GradeColumnResponse(columnId, "Giữa kỳ", new BigDecimal("40"),
                                new BigDecimal("10"), 1)))));

        when(courses.listActiveClasses()).thenReturn(List.of(
                new CoursesManagement.ActiveClassSnapshot(
                        classId,
                        "ENG-IELTS-01",
                        courseId,
                        "Luyện Thi IELTS Chuyên Sâu",
                        branchId,
                        teacherAccountId,
                        25,
                        List.of(new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.MON,
                                LocalTime.of(18, 0), LocalTime.of(20, 0))),
                        CoursesConstants.ClassStatus.ENROLLING, LocalDate.of(2026, 11, 20), 5L)));

        when(organization.listActiveBranches()).thenReturn(List.of(
                new OrganizationManagement.BranchPublicSummary(branchId, "HCM-Q1", "Cơ sở Quận 1",
                        "23B Nguyễn Văn Thủ, Quận 1, TP.HCM")));

        when(teachers.listActiveTeachers()).thenReturn(List.of(
                new TeachersManagement.ActiveTeacherSnapshot(teacherProfileId, teacherAccountId,
                        List.of("IELTS Academic", "Giao tiếp nâng cao"), "Thạc sĩ Ngôn ngữ học ứng dụng")));

        when(identity.summariesOf(anyCollection())).thenReturn(Map.of(
                teacherAccountId,
                new IdentityManagement.AccountBasicInfo(teacherAccountId, "gv@eduerp.vn", "ThS. Nguyễn Minh Anh")));

        when(billing.listActiveComboDiscountTiers()).thenReturn(List.of(
                new BillingManagement.ActiveComboTierSnapshot(tierId, 2, new BigDecimal("10")),
                new BillingManagement.ActiveComboTierSnapshot(UUID.randomUUID(), 3, new BigDecimal("15"))));

        var mockCenterInfo = new CenterInfoItem(
                LandingConstants.Defaults.BRAND_NAME,
                LandingConstants.Defaults.BRAND_SUBTITLE,
                LandingConstants.Defaults.TAGLINE,
                LandingConstants.Defaults.HERO_BADGE,
                LandingConstants.Defaults.HERO_TITLE,
                LandingConstants.Defaults.HERO_HIGHLIGHT,
                LandingConstants.Defaults.HERO_DESCRIPTION,
                LandingConstants.Defaults.HOTLINE,
                LandingConstants.Defaults.HOTLINE_DISPLAY,
                LandingConstants.Defaults.EMAIL,
                LandingConstants.Defaults.WORK_TIME,
                LandingConstants.Defaults.DEFAULT_ADDRESS,
                LandingConstants.Defaults.ZALO_URL,
                LandingConstants.Defaults.MESSENGER_URL,
                LandingConstants.Defaults.FOOTER_DESCRIPTION);
        when(getContentConfig.execute()).thenReturn(new LandingContentConfigResponse(
                mockCenterInfo, List.of(), List.of(), List.of()));

        var result = useCase.execute();

        assertThat(result.stats().activeCourseCount()).isEqualTo(1);
        assertThat(result.stats().activeClassCount()).isEqualTo(1);
        assertThat(result.stats().activeTeacherCount()).isEqualTo(1);
        assertThat(result.stats().activeBranchCount()).isEqualTo(1);
        assertThat(result.stats().maxComboDiscountPercent()).isEqualByComparingTo("15");

        assertThat(result.courses()).hasSize(1);
        assertThat(result.courses().getFirst().openClassCount()).isEqualTo(1);
        assertThat(result.classes().getFirst().branchName()).isEqualTo("Cơ sở Quận 1");
        // 25 chỗ, 5 người đang học: khách thấy còn 20 chỗ và lớp còn đang tuyển.
        assertThat(result.classes().getFirst().seatsLeft()).isEqualTo(20);
        assertThat(result.classes().getFirst().status()).isEqualTo(CoursesConstants.ClassStatus.ENROLLING);
        assertThat(result.classes().getFirst().teacherName()).isEqualTo("ThS. Nguyễn Minh Anh");
        assertThat(result.teachers().getFirst().fullName()).isEqualTo("ThS. Nguyễn Minh Anh");
        assertThat(result.branches().getFirst().code()).isEqualTo("HCM-Q1");
        assertThat(result.comboDiscountTiers()).hasSize(2);
        assertThat(result.config().centerInfo().brandName()).isEqualTo(LandingConstants.Defaults.BRAND_NAME);
    }
}
