package com.eduerp.modules.teachers.internal.model;

import com.eduerp.modules.teachers.TeachersConstants;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "teacher_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeacherProfile {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "account_id", nullable = false, unique = true, updatable = false)
    private UUID accountId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "teacher_profile_subjects", joinColumns = @JoinColumn(name = "teacher_profile_id"))
    @Column(name = "subject", nullable = false)
    @BatchSize(size = 50)
    private final List<String> subjects = new ArrayList<>();

    @Setter
    private String phone;

    @Setter
    private String bio;

    @Column(nullable = false)
    private boolean active = true;

    /** Ngày hồ sơ ngừng hoạt động - ngày làm việc cuối cùng khi tính lương. */
    @Column(name = "deactivated_on")
    private LocalDate deactivatedOn;

    /** null = chưa thiết lập lương (hồ sơ sinh tự động khi được cấp vai trò giảng dạy). */
    @Enumerated(EnumType.STRING)
    @Column(name = "pay_type")
    private TeachersConstants.PayType payType;

    @Column(name = "monthly_salary")
    private BigDecimal monthlySalary;

    @Column(name = "hourly_rate")
    private BigDecimal hourlyRate;

    @Column(name = "work_start_date")
    private LocalDate workStartDate;

    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    @Column(name = "dependent_count", nullable = false)
    private int dependentCount;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "teacher_allowances", joinColumns = @JoinColumn(name = "teacher_profile_id"))
    @BatchSize(size = 50)
    private final List<TeacherAllowance> allowances = new ArrayList<>();

    public TeacherProfile(UUID accountId, List<String> subjects, String phone, String bio) {
        this.accountId = accountId;
        this.subjects.addAll(subjects);
        this.phone = phone;
        this.bio = bio;
        this.active = true;
    }

    public List<String> getSubjects() {
        return List.copyOf(subjects);
    }

    public void setSubjects(List<String> subjects) {
        this.subjects.clear();
        this.subjects.addAll(subjects);
    }

    public List<TeacherAllowance> getAllowances() {
        return List.copyOf(allowances);
    }

    /** Lần ngừng đầu tiên chốt ngày làm việc cuối; mở lại thì xoá mốc đó. */
    public void setActive(boolean active) {
        if (active) {
            this.deactivatedOn = null;
        } else if (this.active) {
            this.deactivatedOn = LocalDate.now(ZoneId.of(TeachersConstants.Schedules.CENTER_ZONE_ID));
        }
        this.active = active;
    }

    /** Cho nghỉ: ngày nghỉ là ngày làm việc cuối khi tính lương. */
    public void leave(LocalDate lastWorkDate) {
        this.active = false;
        this.deactivatedOn = lastWorkDate;
    }

    public boolean isPayConfigured() {
        return payType != null;
    }

    /** Thay toàn bộ điều khoản lương. Kiểm tra hợp lệ nằm ở {@code TeacherPayRules}, gọi trước bước này. */
    public void applyPayTerms(TeachersConstants.PayType payType, BigDecimal monthlySalary, BigDecimal hourlyRate,
            LocalDate workStartDate, LocalDate probationEndDate, int dependentCount,
            List<TeacherAllowance> allowances) {
        this.payType = payType;
        this.monthlySalary = monthlySalary;
        this.hourlyRate = hourlyRate;
        this.workStartDate = workStartDate;
        this.probationEndDate = probationEndDate;
        this.dependentCount = dependentCount;
        this.allowances.clear();
        this.allowances.addAll(allowances);
    }
}
