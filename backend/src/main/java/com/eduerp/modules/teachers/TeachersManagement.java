package com.eduerp.modules.teachers;

import com.eduerp.modules.teachers.internal.model.TeacherAllowance;
import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeachersManagement {

    public record ActiveTeacherSnapshot(UUID profileId, UUID accountId, List<String> subjects, String bio) {
    }

    /**
     * Điều khoản lương của một giáo viên cho {@code modules.payroll}. {@code lastWorkDate} null = còn làm việc.
     * Tổng phụ cấp đã cộng sẵn vì công thức lương chỉ cần tổng, không cần từng khoản.
     */
    public record PayTerms(UUID accountId, TeachersConstants.PayType payType, BigDecimal monthlySalary,
            BigDecimal hourlyRate, LocalDate workStartDate, LocalDate probationEndDate, LocalDate lastWorkDate,
            int dependentCount, BigDecimal allowancesTotal) {
    }

    private final TeacherProfileRepository profiles;

    TeachersManagement(TeacherProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public boolean teacherProfileExists(UUID accountId) {
        return profiles.existsByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public List<ActiveTeacherSnapshot> listActiveTeachers() {
        return profiles.findAllByActiveTrue().stream()
                .map(p -> new ActiveTeacherSnapshot(p.getId(), p.getAccountId(), p.getSubjects(), p.getBio()))
                .toList();
    }

    /** Mọi giáo viên đã thiết lập lương, kể cả đã ngừng: họ vẫn có thể còn buổi dạy trong kỳ đang chốt. */
    @Transactional(readOnly = true)
    public List<PayTerms> listPayTerms() {
        return profiles.findAllByPayTypeIsNotNull().stream().map(TeachersManagement::toPayTerms).toList();
    }

    /** Giáo viên đang hoạt động nhưng chưa thiết lập lương - để kỳ lương cảnh báo, tránh bỏ sót người. */
    @Transactional(readOnly = true)
    public List<UUID> listActiveAccountsWithoutPayTerms() {
        return profiles.findAllByActiveTrueAndPayTypeIsNull().stream().map(TeacherProfile::getAccountId).toList();
    }

    private static PayTerms toPayTerms(TeacherProfile profile) {
        var allowancesTotal = profile.getAllowances().stream().map(TeacherAllowance::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PayTerms(profile.getAccountId(), profile.getPayType(), profile.getMonthlySalary(),
                profile.getHourlyRate(), profile.getWorkStartDate(), profile.getProbationEndDate(),
                profile.getDeactivatedOn(), profile.getDependentCount(), allowancesTotal);
    }
}
