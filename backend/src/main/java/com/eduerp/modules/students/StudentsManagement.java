package com.eduerp.modules.students;

import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module students. {@code modules.enrollment} gọi {@link #getProfile(UUID)} để xác nhận
 * học viên tồn tại và còn hoạt động trước khi ghi danh (spec mục 4). {@code modules.academic} gọi
 * {@link #summariesOf(Collection)} để in tên học viên trong bảng điểm/bảng tiến trình của một lớp.
 */
@Service
public class StudentsManagement {

    /** {@code studentProfileId} là khoá của hồ sơ học viên, KHÔNG phải accountId - ghi danh tham
     * chiếu hồ sơ nghiệp vụ, không tham chiếu tài khoản đăng nhập. */
    public record StudentSummaryResponse(UUID studentProfileId, UUID accountId, boolean active) {
    }

    private final StudentProfileRepository profiles;

    StudentsManagement(StudentProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public boolean studentProfileExists(UUID accountId) {
        return profiles.existsByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public Optional<StudentSummaryResponse> getProfile(UUID studentProfileId) {
        return profiles.findById(studentProfileId).map(profile ->
                new StudentSummaryResponse(profile.getId(), profile.getAccountId(), profile.isActive()));
    }

    /**
     * Hồ sơ học viên gắn với một tài khoản đăng nhập - cho cổng học tập
     * ({@code modules.academic}/{@code modules.billing}) đổi {@code principal.accountId()} sang
     * {@code studentProfileId}. Tài khoản chưa có hồ sơ trả {@link Optional#empty()}: cổng hiển thị
     * danh sách rỗng chứ không lỗi.
     */
    @Transactional(readOnly = true)
    public Optional<StudentSummaryResponse> findProfileByAccountId(UUID accountId) {
        return profiles.findByAccountId(accountId).map(profile ->
                new StudentSummaryResponse(profile.getId(), profile.getAccountId(), profile.isActive()));
    }

    /**
     * Hồ sơ của một loạt học viên theo id - tránh N+1 khi {@code modules.academic} in tên cả lớp
     * trong bảng điểm (rule #3), mirror {@code IdentityManagement.summariesOf}. Id không tồn tại chỉ
     * vắng mặt trong map, không ném: một học viên bị xoá hồ sơ không được làm đổ cả bảng điểm.
     */
    @Transactional(readOnly = true)
    public Map<UUID, StudentSummaryResponse> summariesOf(Collection<UUID> studentProfileIds) {
        if (studentProfileIds.isEmpty()) {
            return Map.of();
        }
        return profiles.findAllById(studentProfileIds).stream()
                .collect(Collectors.toMap(StudentProfile::getId, profile -> new StudentSummaryResponse(
                        profile.getId(), profile.getAccountId(), profile.isActive())));
    }
}
