package com.eduerp.modules.academic.internal;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Tên học viên cho bảng điểm và bảng tiến trình. Đi đúng đường mà {@code ListStudentProfiles} đã
 * làm: {@code StudentProfile.accountId} → {@code IdentityManagement.summariesOf} →
 * {@code fullName}. {@code modules.academic} chỉ có {@code studentProfileId} từ
 * {@code EnrollmentManagement}, nên hai bước là tối thiểu.
 *
 * <p>Nằm ở đây thay vì lặp lại trong từng usecase: hai usecase đọc ({@code GetClassGradebook},
 * {@code GetClassProgress}) cần đúng việc này, copy-paste hai lần là hai cơ hội lệch nhau. Cả hai
 * lần gọi facade đều theo LÔ (rule #3) - không bao giờ một câu cho mỗi học viên.
 */
@Component
public class StudentNameResolver {

    private final StudentsManagement students;
    private final IdentityManagement identity;

    StudentNameResolver(StudentsManagement students, IdentityManagement identity) {
        this.students = students;
        this.identity = identity;
    }

    /**
     * Tên đầy đủ theo {@code studentProfileId}. Hồ sơ không tra được chỉ VẮNG MẶT trong map - nơi
     * gọi hiển thị tên trống thay vì làm đổ cả bảng điểm vì một hồ sơ bị xoá.
     */
    public Map<UUID, String> namesOf(Collection<UUID> studentProfileIds) {
        if (studentProfileIds.isEmpty()) {
            return Map.of();
        }
        var profiles = students.summariesOf(studentProfileIds);
        var accounts = identity.summariesOf(profiles.values().stream()
                .map(StudentsManagement.StudentSummaryResponse::accountId).toList());
        return profiles.entrySet().stream()
                .filter(entry -> accounts.get(entry.getValue().accountId()) != null)
                .filter(entry -> accounts.get(entry.getValue().accountId()).fullName() != null)
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> accounts.get(entry.getValue().accountId()).fullName()));
    }
}
