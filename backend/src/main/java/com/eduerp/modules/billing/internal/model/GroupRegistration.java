package com.eduerp.modules.billing.internal.model;

import com.eduerp.modules.billing.internal.rules.BillingRules;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

/**
 * Đăng ký nhóm: nhiều học viên học CÙNG một khoá ở cùng chi nhánh, mỗi người được giảm theo số người trong
 * nhóm (cùng bảng bậc giảm giá với combo - "từ N suất học").
 *
 * <p>Khác {@link Combo}: không có tổng tiền chung hay hạn đóng chung. Mỗi thành viên vẫn tự đóng học phí của
 * mình qua hoá đơn theo ghi danh; nhóm chỉ hạ trần học phí của từng ghi danh xuống mức đã giảm. Lý do: nhóm
 * là nhiều người trả tiền khác nhau, gom về một hoá đơn thì không biết ai còn nợ.
 *
 * <p>Không có cột {@code status}: huỷ là xoá cứng, chỉ khi chưa thành viên nào có hoá đơn sống.
 */
@Entity
@Table(name = "group_registrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupRegistration {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "discount_percent", nullable = false, updatable = false)
    private BigDecimal discountPercent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private UUID createdByAccountId;

    // BatchSize: liệt kê một trang nhóm không bắn N câu SELECT thành viên (mirror Combo.enrollments).
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private final List<GroupRegistrationMember> members = new ArrayList<>();

    public GroupRegistration(UUID courseId, UUID branchId, BigDecimal discountPercent, UUID createdByAccountId) {
        this.courseId = courseId;
        this.branchId = branchId;
        this.discountPercent = BillingRules.percent(discountPercent);
        this.createdByAccountId = createdByAccountId;
        this.createdAt = Instant.now();
    }

    public List<GroupRegistrationMember> getMembers() {
        return List.copyOf(members);
    }

    /** Học phí sau giảm tính ở đây, không nhận từ ngoài - không ai lưu được một mức giảm lệch % của nhóm. */
    public void addMember(UUID enrollmentId, UUID studentProfileId, BigDecimal originalTuitionFee) {
        members.add(new GroupRegistrationMember(this, enrollmentId, studentProfileId, originalTuitionFee,
                BillingRules.discountedTotal(originalTuitionFee, discountPercent)));
    }
}
