# Phân hệ 3.1 — Combo khoá học giảm giá Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kế toán gộp các `Enrollment` đang `ACTIVE` của một học viên thành một `Combo` được giảm giá theo số lượng khoá (bậc giảm do admin cấu hình), rồi thu tiền combo đó như MỘT đơn vị thanh toán — tối đa 3 đợt, một hạn đóng chung.

**Architecture:** Không thêm module mới. `Combo`/`ComboEnrollment`/`ComboDiscountTier` là entity mới trong `modules.billing.internal.model`; `Invoice` nhận thêm cột `combo_id` nullable và `enrollment_id`/`course_id` thành nullable, với CHECK constraint buộc một hoá đơn thuộc về ĐÚNG MỘT trong hai (một `Enrollment` — luồng Phase 3 giữ nguyên 100% — hoặc một `Combo`). `modules.enrollment` chỉ nhận một filter `status` thuần bổ sung, facade `EnrollmentManagement` không đổi một dòng. Frontend mở rộng đúng hai slice đã có: `entities/billing` (schema + API + keys + hooks) và `modules/billing` (3 trang mới + dialog), mirror cách `invoices-page`/`invoice-detail-page` đã làm.

**Tech Stack:** Spring Boot 3.4.1, Java 21, Spring Modulith 1.3.1, PostgreSQL 16 + Flyway, Hibernate 6, Lombok, JUnit 5 + AssertJ + Mockito + Testcontainers; React 19 + Vite + TypeScript + TanStack Query v5 + Zod + Tailwind v4.

**Spec:** `docs/superpowers/specs/2026-10-04-combo-khoa-hoc-giam-gia-design.md`

## Global Constraints

- **Tối đa 3 đợt đóng cho CẢ combo** (không phải 3 đợt cho từng khoá), **tổng không vượt tổng tiền combo sau giảm giá** (`Combo.totalDiscountedAmount`). Hằng số: `BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO = 3`.
- **Combo yêu cầu ≥ 2 ghi danh khác nhau**: `enrollmentIds` phải có ≥ 2 phần tử khác nhau, nếu không → `MinimumComboSizeException`. Hằng số: `BillingConstants.Limits.MIN_ENROLLMENTS_PER_COMBO = 2`.
- **Không thêm resource/permission RBAC mới.** Dùng lại `AccessConstants.AccessRules.CREATE_INVOICE` / `READ_INVOICE` / `UPDATE_INVOICE` trên `Resources.INVOICE` cho MỌI endpoint combo, kể cả CRUD bậc giảm giá. **Không có migration seed permission mới.**
- **`EnrollmentManagement` KHÔNG được thêm method ghi nào.** `CreateCombo` chỉ gọi `enrollmentManagement.getEnrollment(UUID)` (read-only, đã có từ Phase 3). Combo không tạo `Enrollment` mới.
- **`CancelCombo` chỉ được phép khi `invoices.countByComboId(comboId) == 0`** — nghĩa là chưa phát hành hoá đơn nào, **bất kể trạng thái, kể cả đã `CANCELLED`**; ngược lại → `ComboHasInvoicesException`. Huỷ là xoá cứng `Combo` + cascade `ComboEnrollment`, không có cột `status` trên `Combo`.
- **Chọn bậc giảm giá:** lấy tier có `minCourseCount` **lớn nhất** thoả `minCourseCount <= số khoá trong combo` VÀ `active = true`. Không có tier nào thoả → `ComboDiscountTierNotConfiguredException` (chặn hẳn, **không mặc định 0%**).
- **`branchId` của combo lấy từ Enrollment ĐẦU TIÊN trong danh sách**, không phải `actorBranchId` của người thao tác — mirror đúng cách `CreateInvoice` lấy `branchId` từ enrollment. Mọi enrollment còn lại phải cùng `branchId` đó.
- **Các usecase/luồng Phase 3 giữ nguyên hành vi, không sửa logic:** `CreateInvoice`, `CancelInvoice`, `InitiateOnlinePayment`, `RecordManualPayment`, `HandlePaymentCallback`, `MarkOverdueInvoices`, `GetPaymentStatus`. `ListInvoices`/`GetInvoiceDetail` chỉ phản ánh field mới (nullable) qua DTO, không đổi logic.
- **Không hardcode string literal.** Backend: hằng gom vào `BillingConstants` dưới static inner class (`Limits`); mọi `@PreAuthorize` lấy từ `AccessConstants.AccessRules`. Frontend: `@/shared/constants/{permissions,api-routes,app-routes}.ts`.
- **Facade là type DUY NHẤT module khác được gọi** (`EnrollmentManagement`, `CoursesManagement`). Không `@ManyToOne` xuyên module — `ComboEnrollment.enrollmentId`/`courseId` là UUID trần; `Combo` ↔ `ComboEnrollment` là quan hệ JPA thật vì cùng module (mirror `Class` ↔ `ClassSchedule`).
- **Một class = một usecase = một method `public execute(...)`**, `@Transactional` phải `public` (proxy AOP của Spring). Constructor của usecase là package-private, mirror `CreateInvoice`.
- **Mọi lỗi nghiệp vụ** là subclass của `BillingException` (sealed — phải thêm vào `permits`), mang `errorCode` prefix `BILLING_*`, trả ra RFC 9457 `ProblemDetail` qua `core.exception.GlobalExceptionHandler`.
- **Tiền VND scale 0** — cột DB `NUMERIC(14,0)`, làm tròn qua `BillingRules.money`. **Phần trăm scale 2** — cột `NUMERIC(5,2)`, làm tròn qua `BillingRules.percent` (mới).
- **Cyclomatic complexity < 15**, component UI < 200 dòng, không nested ternary.
- **Migration theo thứ tự version tăng dần V21 → V22.** V20 là version cao nhất hiện có. V21 tạo 3 bảng combo; V22 `ALTER TABLE invoices` (bảng đã có dữ liệu thật — ALTER, không phải CREATE).
- **`ModularityTests.everyDomainAndIntegrationPackageIsADetectedModule` KHÔNG đổi** — plan này không thêm module nào, danh sách 19 module giữ nguyên. Task 22 chạy lại test này để chứng minh.
- **Không thêm dependency mới** ở cả backend lẫn frontend.
- **Frontend không có test runner** (`package.json` chỉ có `dev`/`build`/`lint`/`typecheck`/`preview`). Vòng TDD của frontend là `npm run typecheck` (đỏ → xanh) + `npm run lint`; mọi quy tắc nghiệp vụ được test thật ở backend. Preview % giảm giá trên dialog là **tham khảo**, nguồn sự thật vẫn là `CreateCombo` ở backend.
- **Không push trực tiếp `main`/`develop`.** Nhánh `feat/combo-course-discount` (đã ở nhánh này). Commit theo Conventional Commits, tiếng Anh, **không có trailer `Co-Authored-By`**.
- **Bốn điểm plan quyết thêm/khác spec, có lý do, ghi rõ tại task sở hữu:**
  1. Spec mục 6 không nêu lỗi cho `UpdateComboDiscountTier` khi `tierId` không tồn tại, và không nêu lỗi khi tạo trùng `minCourseCount` (cột UNIQUE). Plan thêm hai exception `ComboDiscountTierNotFoundException` (404) và `ComboDiscountTierAlreadyExistsException` (409) ở Task 4 — nếu không, hai trường hợp này rơi thành 500. Chúng không mở thêm quyền hay bảng nào.
  2. `InstallmentLimitExceededException` và `InvoiceAmountExceedsTuitionException` được **dùng lại đúng như spec yêu cầu** (cùng type, cùng `errorCode`), nhưng mỗi cái nhận thêm một **static factory `forCombo(...)`** chỉ để đổi câu chữ của message (`"Ghi danh X đã có đủ 3 đợt thu"` → `"Combo X đã có đủ 3 đợt thu"`). Constructor cũ và mọi test cũ không đổi. Xem Task 4.
  3. Route frontend theo convention đã có của repo (`/admin/billing/...`), không theo chữ `/billing/...` ở spec mục 10 — `APP_ROUTE.invoices` hiện là `/admin/billing/invoices`, mọi trang quản trị đều nằm dưới `/admin`. Xem Task 21.
  4. Spec mục 10 nói `combo-detail-page.tsx` "tái dùng `PaymentsTable`/nút thu tiền online/thủ công như `invoice-detail-page.tsx`". Plan **không** nhân bản ba thứ đó sang trang combo: một đợt thu của combo là một `Invoice` bình thường, nên trang chi tiết combo hiện danh sách đợt thu bằng `InvoicesTable` có sẵn, và nút "Xem chi tiết" dẫn sang đúng `invoice-detail-page.tsx` — nơi đã có `PaymentsTable`, thu online và thu thủ công chạy được y nguyên trên hoá đơn combo (đó chính là nội dung Review Focus #9). Nhân bản chúng sẽ tạo hai màn hình thu tiền phải sửa song song mà không thêm một khả năng nào. Xem Task 20.

## Review Focus

1. **Combo lẫn học viên khác nhau** — 2 enrollment của 2 học viên khác nhau phải bị chặn bằng `StudentMismatchInComboException`, không âm thầm lấy học viên của enrollment đầu tiên. Test ở **Task 7**.
2. **Combo chỉ 1 khoá** — phải chặn bằng `MinimumComboSizeException`, không tạo "combo" 1 phần tử (kể cả khi truyền cùng một id hai lần). Test ở **Task 7**.
3. **Race điều kiện enrollment bị gộp hai lần** — UNIQUE `combo_enrollments.enrollment_id` phải chặn một trong hai request đồng thời (test tầng DB ở **Task 2**), và `CreateCombo` phải dịch `DataIntegrityViolationException` sang `EnrollmentAlreadyInComboException`, không rơi thành 500 (test tầng usecase ở **Task 7**).
4. **Bậc giảm giá chưa cấu hình** — tạo combo 2 khoá khi chưa có tier nào thoả `minCourseCount <= 2` → `ComboDiscountTierNotConfiguredException`, không mặc định 0%. Test ở **Task 7**.
5. **Vượt tổng tiền combo sau giảm** — tổng các đợt vượt `totalDiscountedAmount` → `InvoiceAmountExceedsTuitionException`, dù số đợt (3) chưa chạm hạn mức; và ngược lại đợt thứ 4 khi tiền còn dư → `InstallmentLimitExceededException`. Hai lỗi khác nhau, không lẫn vào nhau. Test ở **Task 8**.
6. **Huỷ combo đã có hoá đơn** — combo đã phát hành ít nhất 1 invoice, **kể cả invoice đó đã `CANCELLED`**, thì `CancelCombo` phải chặn bằng `ComboHasInvoicesException`. Test ở **Task 9**.
7. **Rút khỏi một khoá trong combo sau khi combo đã tạo** — `WithdrawEnrollment` không biết gì về combo (không đổi) → combo và hoá đơn combo giữ nguyên, không tự huỷ, không tính lại giảm giá. Test ở **Task 16**.
8. **Dữ liệu Invoice cũ (Phase 3) vẫn hợp lệ sau migration V22** — đọc lại một Invoice đơn-khoá, CHECK constraint không chặn, `comboId` đọc ra `null`; và CHECK phải chặn cả hai biên hỏng (cả hai null / cả hai set). Test ở **Task 3**.
9. **Payment flow dùng chung không rẽ nhánh theo loại Invoice** — `RecordManualPayment`/`CancelInvoice`/`MarkOverdueInvoices`/`InitiateOnlinePayment` chạy đúng trên một invoice combo giống hệt invoice đơn-khoá. Test ở **Task 16**.

---

### Task 1: Migration V21 + `ComboDiscountTier` entity/repository + `BillingRules.percent`

**Files:**
- Create: `backend/src/main/resources/db/migration/V21__create_combo_tables.sql`
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/model/ComboDiscountTier.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/repository/ComboDiscountTierRepository.java`
- Modify: `backend/src/main/java/com/eduerp/modules/billing/internal/rules/BillingRules.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/internal/rules/BillingRulesTest.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/internal/repository/ComboRepositoryIT.java`

**Interfaces:**
- Consumes: `BillingRules.money(BigDecimal)` (đã có).
- Produces:
  - `BillingRules.percent(BigDecimal): BigDecimal` (scale 2, HALF_UP)
  - `BillingRules.discountedTotal(BigDecimal totalOriginalAmount, BigDecimal discountPercent): BigDecimal` (scale 0)
  - `ComboDiscountTier(int minCourseCount, BigDecimal discountPercent)`; getters `getId()`, `getMinCourseCount()`, `getDiscountPercent()`, `isActive()`; `update(BigDecimal discountPercent, boolean active)`
  - `ComboDiscountTierRepository.findAllByOrderByMinCourseCountAsc(): List<ComboDiscountTier>`
  - `ComboDiscountTierRepository.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(int courseCount): Optional<ComboDiscountTier>`
  - Bảng DB `combo_discount_tiers`, `combos`, `combo_enrollments` (Task 2 dùng hai bảng sau).

- [ ] **Step 1: Viết test đỏ cho `BillingRules.percent`/`discountedTotal`**

Thêm vào cuối `backend/src/test/java/com/eduerp/modules/billing/internal/rules/BillingRulesTest.java` (bên trong class, giữ nguyên mọi test đã có):

```java
    /** Cột discount_percent là NUMERIC(5,2) - số trả về client phải khớp số sẽ lưu xuống. */
    @Test
    void percentRoundsToTwoDecimalsHalfUp() {
        assertThat(BillingRules.percent(new BigDecimal("15"))).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(BillingRules.percent(new BigDecimal("12.345"))).isEqualByComparingTo(new BigDecimal("12.35"));
        assertThat(BillingRules.percent(new BigDecimal("15.00")).scale()).isEqualTo(2);
    }

    /** Tổng combo sau giảm là một cột tiền NUMERIC(14,0) - không để lại phần lẻ nào. */
    @Test
    void discountedTotalAppliesThePercentThenRoundsToWholeDong() {
        assertThat(BillingRules.discountedTotal(new BigDecimal("21000000"), new BigDecimal("15.00")))
                .isEqualByComparingTo(new BigDecimal("17850000"));
        assertThat(BillingRules.discountedTotal(new BigDecimal("21000000"), BigDecimal.ZERO))
                .isEqualByComparingTo(new BigDecimal("21000000"));
        assertThat(BillingRules.discountedTotal(new BigDecimal("1000001"), new BigDecimal("12.50")).scale())
                .isZero();
    }
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=BillingRulesTest`
Expected: FAIL — compile error `cannot find symbol: method percent(java.math.BigDecimal)` và `method discountedTotal(...)`.

- [ ] **Step 3: Thêm `percent`/`discountedTotal` vào `BillingRules`**

Trong `backend/src/main/java/com/eduerp/modules/billing/internal/rules/BillingRules.java`, thêm hằng ngay dưới `MONEY_SCALE`:

```java
    /** Cột combo_discount_tiers.discount_percent / combos.discount_percent là NUMERIC(5,2). */
    private static final int PERCENT_SCALE = 2;

    /** Chia 100 trước khi nhân nên cần dư chữ số thập phân, nếu không 15% thành 0 (BigDecimal chia
     * theo scale của số bị chia). Kết quả cuối vẫn được money() kéo về scale 0. */
    private static final int DISCOUNT_MATH_SCALE = 6;

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
```

và hai method ngay dưới `money`:

```java
    public static BigDecimal percent(BigDecimal value) {
        return value.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /** Tổng tiền combo sau giảm giá (spec mục 4): {@code total * (1 - percent/100)}, scale 0. */
    public static BigDecimal discountedTotal(BigDecimal totalOriginalAmount, BigDecimal discountPercent) {
        var multiplier = BigDecimal.ONE.subtract(
                percent(discountPercent).divide(ONE_HUNDRED, DISCOUNT_MATH_SCALE, RoundingMode.HALF_UP));
        return money(totalOriginalAmount.multiply(multiplier));
    }
```

- [ ] **Step 4: Chạy lại để xác nhận xanh**

Run: `cd backend && mvn -q test -Dtest=BillingRulesTest`
Expected: PASS.

- [ ] **Step 5: Viết migration V21**

Tạo `backend/src/main/resources/db/migration/V21__create_combo_tables.sql`:

```sql
CREATE TABLE combo_discount_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- "từ N khoá trở lên". UNIQUE vì nếu tồn tại hai bậc cùng mốc thì việc chọn bậc sẽ phụ thuộc
    -- thứ tự dòng trong bảng thay vì một quy tắc xác định (spec mục 4).
    min_course_count INT NOT NULL UNIQUE,
    discount_percent NUMERIC(5,2) NOT NULL,
    -- Hệ thống này không có Actions.DELETE ở bất kỳ resource nào: "xoá" một bậc là tắt active,
    -- mirror courses.active.
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE combos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- student_profile_id/branch_id là snapshot chốt lúc tạo combo (spec mục 4): FK mức DB để toàn
    -- vẹn dữ liệu, không có @ManyToOne trong Java (rule #3 - module khác).
    student_profile_id UUID NOT NULL REFERENCES student_profiles(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    total_original_amount NUMERIC(14,0) NOT NULL,
    -- Snapshot: bậc giảm giá đổi về sau không được làm đổi combo đã tạo.
    discount_percent NUMERIC(5,2) NOT NULL,
    total_discounted_amount NUMERIC(14,0) NOT NULL,
    due_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_account_id UUID NOT NULL REFERENCES accounts(id)
);

CREATE INDEX idx_combos_student ON combos (student_profile_id);

-- Không có cột status: huỷ combo là xoá cứng (chỉ được phép khi chưa có hoá đơn nào), nên không
-- tồn tại trạng thái CANCELLED song song như invoices (spec mục 4).
CREATE TABLE combo_enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    combo_id UUID NOT NULL REFERENCES combos(id) ON DELETE CASCADE,
    -- UNIQUE ĐẦY ĐỦ, không PARTIAL như V17/V20: huỷ combo xoá cứng dòng này, nên dòng chỉ tồn tại
    -- khi combo đó còn sống - một ghi danh chỉ nằm trong đúng một combo tại một thời điểm. Đây là
    -- lớp chặn cuối cho race hai request cùng gộp một enrollment vào hai combo (spec mục 4).
    enrollment_id UUID NOT NULL UNIQUE REFERENCES enrollments(id),
    course_id UUID NOT NULL REFERENCES courses(id),
    original_tuition_fee NUMERIC(14,0) NOT NULL
);

CREATE INDEX idx_combo_enrollments_combo ON combo_enrollments (combo_id);
```

- [ ] **Step 6: Viết test đỏ cho entity + repository**

Tạo `backend/src/test/java/com/eduerp/modules/billing/internal/repository/ComboRepositoryIT.java`:

```java
package com.eduerp.modules.billing.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class ComboRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    ComboDiscountTierRepository tiers;

    @Test
    void savesATierAsActiveWithPercentAtScaleTwo() {
        var saved = tiers.saveAndFlush(new ComboDiscountTier(2, new BigDecimal("10")));

        var found = tiers.findById(saved.getId()).orElseThrow();
        assertThat(found.getMinCourseCount()).isEqualTo(2);
        assertThat(found.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(found.isActive()).isTrue();
    }

    /** min_course_count UNIQUE: hai bậc cùng mốc sẽ khiến việc chọn bậc phụ thuộc thứ tự dòng. */
    @Test
    void rejectsASecondTierWithTheSameMinCourseCountAtDatabaseLevel() {
        tiers.saveAndFlush(new ComboDiscountTier(3, new BigDecimal("15")));

        assertThatThrownBy(() -> tiers.saveAndFlush(new ComboDiscountTier(3, new BigDecimal("20"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Spec mục 4: bậc áp dụng là minCourseCount LỚN NHẤT còn active mà không vượt số khoá. */
    @Test
    void picksTheHighestActiveTierThatDoesNotExceedTheCourseCount() {
        tiers.saveAndFlush(new ComboDiscountTier(2, new BigDecimal("10")));
        tiers.saveAndFlush(new ComboDiscountTier(3, new BigDecimal("15")));
        tiers.saveAndFlush(new ComboDiscountTier(5, new BigDecimal("25")));

        assertThat(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(4)
                .orElseThrow().getDiscountPercent()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(2)
                .orElseThrow().getDiscountPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(1))
                .isEmpty();
    }

    /** Tắt active là cách duy nhất để "xoá" một bậc - bậc tắt không được chọn nữa. */
    @Test
    void skipsAnInactiveTierAndFallsBackToTheNextOneDown() {
        tiers.saveAndFlush(new ComboDiscountTier(2, new BigDecimal("10")));
        var retired = tiers.saveAndFlush(new ComboDiscountTier(3, new BigDecimal("15")));
        retired.update(new BigDecimal("15"), false);
        tiers.saveAndFlush(retired);

        assertThat(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(3)
                .orElseThrow().getDiscountPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void listsTiersInAscendingMinCourseCountOrder() {
        tiers.saveAndFlush(new ComboDiscountTier(5, new BigDecimal("25")));
        tiers.saveAndFlush(new ComboDiscountTier(2, new BigDecimal("10")));

        assertThat(tiers.findAllByOrderByMinCourseCountAsc())
                .extracting(ComboDiscountTier::getMinCourseCount).containsExactly(2, 5);
    }
}
```

- [ ] **Step 7: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=ComboRepositoryIT`
Expected: FAIL — compile error `package com.eduerp.modules.billing.internal.model does not exist` / `cannot find symbol: class ComboDiscountTier`.

- [ ] **Step 8: Viết `ComboDiscountTier`**

Tạo `backend/src/main/java/com/eduerp/modules/billing/internal/model/ComboDiscountTier.java`:

```java
package com.eduerp.modules.billing.internal.model;

import com.eduerp.modules.billing.internal.rules.BillingRules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Cấu hình admin ở mức module, không thuộc một {@code Combo} cụ thể: "từ N khoá trở lên giảm X%".
 * Không có thao tác xoá - hệ thống này không dùng {@code Actions.DELETE} ở bất kỳ resource nào, nên
 * "xoá" một bậc nghĩa là tắt {@code active} (mirror {@code Course.active}).
 */
@Entity
@Table(name = "combo_discount_tiers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComboDiscountTier {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "min_course_count", nullable = false, unique = true, updatable = false)
    private int minCourseCount;

    @Column(name = "discount_percent", nullable = false)
    private BigDecimal discountPercent;

    @Column(nullable = false)
    private boolean active = true;

    public ComboDiscountTier(int minCourseCount, BigDecimal discountPercent) {
        this.minCourseCount = minCourseCount;
        this.discountPercent = BillingRules.percent(discountPercent);
        this.active = true;
    }

    /** {@code minCourseCount} không sửa được: đổi mốc của một bậc đã dùng là tạo một bậc khác. */
    public void update(BigDecimal discountPercent, boolean active) {
        this.discountPercent = BillingRules.percent(discountPercent);
        this.active = active;
    }
}
```

- [ ] **Step 9: Viết `ComboDiscountTierRepository`**

Tạo `backend/src/main/java/com/eduerp/modules/billing/internal/repository/ComboDiscountTierRepository.java`:

```java
package com.eduerp.modules.billing.internal.repository;

import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComboDiscountTierRepository extends JpaRepository<ComboDiscountTier, UUID> {

    /** Màn hình cấu hình liệt kê theo mốc tăng dần - gồm cả bậc đã tắt, để admin bật lại được. */
    List<ComboDiscountTier> findAllByOrderByMinCourseCountAsc();

    /**
     * Spec mục 4: bậc áp dụng = {@code minCourseCount} lớn nhất còn {@code active} mà không vượt số
     * khoá trong combo. Để DB chọn thay vì lọc/sắp trong Java - một câu query, không N+1, và quy tắc
     * chọn nằm đúng một chỗ.
     */
    Optional<ComboDiscountTier> findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(
            int courseCount);
}
```

- [ ] **Step 10: Chạy lại để xác nhận xanh**

Run: `cd backend && mvn -q test -Dtest=ComboRepositoryIT`
Expected: PASS — 5 test.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/resources/db/migration/V21__create_combo_tables.sql \
  backend/src/main/java/com/eduerp/modules/billing/internal/model/ComboDiscountTier.java \
  backend/src/main/java/com/eduerp/modules/billing/internal/repository/ComboDiscountTierRepository.java \
  backend/src/main/java/com/eduerp/modules/billing/internal/rules/BillingRules.java \
  backend/src/test/java/com/eduerp/modules/billing/internal/rules/BillingRulesTest.java \
  backend/src/test/java/com/eduerp/modules/billing/internal/repository/ComboRepositoryIT.java
git commit -m "feat(billing): add combo tables and discount tier configuration"
```

---

### Task 2: `Combo` + `ComboEnrollment` entities + `ComboRepository`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/model/Combo.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/model/ComboEnrollment.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/repository/ComboRepository.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/internal/repository/ComboRepositoryIT.java`

**Interfaces:**
- Consumes: `BillingRules.money`, `BillingRules.percent`, `BillingRules.discountedTotal` (Task 1); bảng `combos`/`combo_enrollments` (Task 1).
- Produces:
  - `Combo(UUID studentProfileId, UUID branchId, BigDecimal totalOriginalAmount, BigDecimal discountPercent, LocalDate dueDate, UUID createdByAccountId)`
  - getters `getId()`, `getStudentProfileId()`, `getBranchId()`, `getTotalOriginalAmount()`, `getDiscountPercent()`, `getTotalDiscountedAmount()`, `getDueDate()`, `getCreatedAt()`, `getCreatedByAccountId()`, `getEnrollments(): List<ComboEnrollment>` (bản sao bất biến)
  - `Combo.addEnrollment(UUID enrollmentId, UUID courseId, BigDecimal originalTuitionFee)`
  - `ComboEnrollment` getters `getId()`, `getEnrollmentId()`, `getCourseId()`, `getOriginalTuitionFee()`
  - `ComboRepository extends JpaRepository<Combo, UUID>` với `search(UUID studentProfileId, Pageable pageable): Page<Combo>`

- [ ] **Step 1: Viết test đỏ — cascade lưu, UNIQUE enrollment_id (Review Focus #3 tầng DB), search filter**

Thêm vào `backend/src/test/java/com/eduerp/modules/billing/internal/repository/ComboRepositoryIT.java`. Thêm import và field, rồi 4 test. Phần import bổ sung:

```java
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.ComboEnrollment;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.domain.PageRequest;
```

Field + helper + `@BeforeEach` bổ sung vào class (giữ nguyên field `tiers` đã có):

```java
    @Autowired
    ComboRepository combos;

    @Autowired
    EnrollmentRepository enrollments;

    @Autowired
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    @Autowired
    BranchRepository branches;

    @Autowired
    AccountRepository accounts;

    @Autowired
    StudentProfileRepository profiles;

    private UUID branchId;
    private UUID actorId;
    private UUID courseId;
    private UUID classId;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @BeforeEach
    void seedReferences() {
        branchId = branches.save(new Branch("CMB-" + shortId(), "Chi nhánh combo", null)).getId();
        actorId = accounts.save(new Account("cmb-actor-" + shortId() + "@eduerp.local", "hash", "Actor", null))
                .getId();
        var course = courses.save(new Course("CMB-C-" + shortId(), "Khoá combo", null, 24,
                new BigDecimal("12000000")));
        courseId = course.getId();
        var teacherId = accounts.save(new Account("cmb-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        classId = classes.save(new Class(course, "CMB-K-" + shortId(), branchId, teacherId, 20)).getId();
    }

    private UUID newStudentProfileId() {
        var account = accounts.save(new Account("cmb-hv-" + shortId() + "@eduerp.local", "hash", "HV", null));
        return profiles.save(new StudentProfile(account.getId(), null, null, null)).getId();
    }

    private UUID newEnrollmentId(UUID studentProfileId) {
        return enrollments.save(new Enrollment(studentProfileId, classId, courseId, branchId, actorId)).getId();
    }

    private Combo newCombo(UUID studentProfileId) {
        return new Combo(studentProfileId, branchId, new BigDecimal("21000000"), new BigDecimal("15"),
                LocalDate.of(2027, 1, 31), actorId);
    }
```

Bốn test mới:

```java
    @Test
    void savesAComboWithItsEnrollmentsInOneCascade() {
        var studentProfileId = newStudentProfileId();
        var combo = newCombo(studentProfileId);
        combo.addEnrollment(newEnrollmentId(studentProfileId), courseId, new BigDecimal("12000000"));
        combo.addEnrollment(newEnrollmentId(studentProfileId), courseId, new BigDecimal("9000000"));

        var saved = combos.saveAndFlush(combo);

        var found = combos.findById(saved.getId()).orElseThrow();
        assertThat(found.getEnrollments()).hasSize(2);
        assertThat(found.getTotalOriginalAmount()).isEqualByComparingTo(new BigDecimal("21000000"));
        assertThat(found.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(found.getTotalDiscountedAmount()).isEqualByComparingTo(new BigDecimal("17850000"));
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getEnrollments()).extracting(ComboEnrollment::getOriginalTuitionFee)
                .allSatisfy(fee -> assertThat(fee.scale()).isZero());
    }

    /**
     * Review Focus #3, tầng DB: hai request đồng thời cùng chọn một enrollment vào hai combo khác
     * nhau. Cả hai đọc trước khi ai kịp ghi, nên UNIQUE trên combo_enrollments.enrollment_id là thứ
     * duy nhất chặn được một trong hai (usecase dịch lỗi này ở Task 7).
     */
    @Test
    void rejectsTheSameEnrollmentInTwoLiveCombosAtDatabaseLevel() {
        var studentProfileId = newStudentProfileId();
        var sharedEnrollmentId = newEnrollmentId(studentProfileId);
        var first = newCombo(studentProfileId);
        first.addEnrollment(sharedEnrollmentId, courseId, new BigDecimal("12000000"));
        first.addEnrollment(newEnrollmentId(studentProfileId), courseId, new BigDecimal("9000000"));
        combos.saveAndFlush(first);

        var second = newCombo(studentProfileId);
        second.addEnrollment(sharedEnrollmentId, courseId, new BigDecimal("12000000"));
        second.addEnrollment(newEnrollmentId(studentProfileId), courseId, new BigDecimal("9000000"));

        assertThatThrownBy(() -> combos.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Mặt còn lại: UNIQUE đầy đủ chỉ đúng vì huỷ combo XOÁ CỨNG dòng con - sau khi combo cũ bị xoá,
     * chính enrollment đó phải gộp lại được vào một combo mới. */
    @Test
    void allowsReusingAnEnrollmentAfterItsComboIsDeleted() {
        var studentProfileId = newStudentProfileId();
        var sharedEnrollmentId = newEnrollmentId(studentProfileId);
        var first = newCombo(studentProfileId);
        first.addEnrollment(sharedEnrollmentId, courseId, new BigDecimal("12000000"));
        first.addEnrollment(newEnrollmentId(studentProfileId), courseId, new BigDecimal("9000000"));
        var saved = combos.saveAndFlush(first);
        combos.delete(saved);
        combos.flush();

        var second = newCombo(studentProfileId);
        second.addEnrollment(sharedEnrollmentId, courseId, new BigDecimal("12000000"));
        second.addEnrollment(newEnrollmentId(studentProfileId), courseId, new BigDecimal("9000000"));

        assertThat(combos.saveAndFlush(second).getEnrollments()).hasSize(2);
    }

    @Test
    void searchFiltersByOptionalStudentProfileId() {
        var mine = newStudentProfileId();
        var someoneElse = newStudentProfileId();
        var first = newCombo(mine);
        first.addEnrollment(newEnrollmentId(mine), courseId, new BigDecimal("12000000"));
        combos.saveAndFlush(first);
        var second = newCombo(someoneElse);
        second.addEnrollment(newEnrollmentId(someoneElse), courseId, new BigDecimal("12000000"));
        combos.saveAndFlush(second);
        var pageable = PageRequest.of(0, 20);

        assertThat(combos.search(null, pageable).getTotalElements()).isEqualTo(2);
        assertThat(combos.search(mine, pageable).getTotalElements()).isEqualTo(1);
        assertThat(combos.search(UUID.randomUUID(), pageable).getTotalElements()).isZero();
    }
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=ComboRepositoryIT`
Expected: FAIL — compile error `cannot find symbol: class Combo` / `class ComboRepository`.

- [ ] **Step 3: Viết `ComboEnrollment`**

Tạo `backend/src/main/java/com/eduerp/modules/billing/internal/model/ComboEnrollment.java`:

```java
package com.eduerp.modules.billing.internal.model;

import com.eduerp.modules.billing.internal.rules.BillingRules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Một khoá trong combo. {@code combo} là quan hệ JPA thật - {@code Combo} cùng module (rule #3 chỉ
 * cấm xuyên module), mirror {@code ClassSchedule.parentClass}. {@code enrollmentId}/{@code courseId}
 * là UUID trần vì {@code enrollment}/{@code courses} là module khác.
 */
@Entity
@Table(name = "combo_enrollments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComboEnrollment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "combo_id")
    private Combo combo;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    /** Snapshot {@code CourseTuitionResponse.tuitionFee()} lúc tạo combo - đổi giá khoá học về sau
     * không được làm đổi một combo đã chốt (spec mục 4). */
    @Column(name = "original_tuition_fee", nullable = false, updatable = false)
    private BigDecimal originalTuitionFee;

    ComboEnrollment(Combo combo, UUID enrollmentId, UUID courseId, BigDecimal originalTuitionFee) {
        this.combo = combo;
        this.enrollmentId = enrollmentId;
        this.courseId = courseId;
        this.originalTuitionFee = BillingRules.money(originalTuitionFee);
    }
}
```

- [ ] **Step 4: Viết `Combo`**

Tạo `backend/src/main/java/com/eduerp/modules/billing/internal/model/Combo.java`:

```java
package com.eduerp.modules.billing.internal.model;

import com.eduerp.modules.billing.internal.rules.BillingRules;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UuidGenerator;

/**
 * Một gói nhiều khoá của CÙNG một học viên, thu như MỘT đơn vị: một tổng tiền sau giảm, một hạn
 * đóng, tối đa 3 đợt cho cả combo (spec mục 2).
 *
 * <p>Không có cột {@code status}: combo chỉ tồn tại khi chưa bị huỷ, và huỷ chỉ được phép trước khi
 * phát hành hoá đơn đầu tiên - lúc đó xoá cứng cả combo lẫn {@code ComboEnrollment} của nó. Một khi
 * đã có hoá đơn, combo là chứng từ tài chính đã chốt, không xoá được nữa (spec mục 4).
 *
 * <p>Mọi số tiền và % giảm là SNAPSHOT lúc tạo: đổi học phí khoá hay đổi bậc giảm giá về sau không
 * được làm đổi một combo đã chốt.
 */
@Entity
@Table(name = "combos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Combo {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "student_profile_id", nullable = false, updatable = false)
    private UUID studentProfileId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "total_original_amount", nullable = false, updatable = false)
    private BigDecimal totalOriginalAmount;

    @Column(name = "discount_percent", nullable = false, updatable = false)
    private BigDecimal discountPercent;

    @Column(name = "total_discounted_amount", nullable = false, updatable = false)
    private BigDecimal totalDiscountedAmount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private UUID createdByAccountId;

    // Gộp lazy-load của nhiều Combo thành một câu IN duy nhất khi liệt kê một trang - không batch
    // thì mỗi combo trong trang tự bắn một SELECT riêng để đếm số khoá (N+1), xem ListCombos.
    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("originalTuitionFee DESC")
    @BatchSize(size = 50)
    private final List<ComboEnrollment> enrollments = new ArrayList<>();

    public Combo(UUID studentProfileId, UUID branchId, BigDecimal totalOriginalAmount,
            BigDecimal discountPercent, LocalDate dueDate, UUID createdByAccountId) {
        this.studentProfileId = studentProfileId;
        this.branchId = branchId;
        this.totalOriginalAmount = BillingRules.money(totalOriginalAmount);
        this.discountPercent = BillingRules.percent(discountPercent);
        // Tính trong constructor, không nhận từ ngoài: không để ai lưu được một combo mà tổng sau
        // giảm không khớp với tổng gốc và % giảm của chính nó.
        this.totalDiscountedAmount = BillingRules.discountedTotal(totalOriginalAmount, discountPercent);
        this.dueDate = dueDate;
        this.createdByAccountId = createdByAccountId;
        this.createdAt = Instant.now();
    }

    public List<ComboEnrollment> getEnrollments() {
        return List.copyOf(enrollments);
    }

    public void addEnrollment(UUID enrollmentId, UUID courseId, BigDecimal originalTuitionFee) {
        enrollments.add(new ComboEnrollment(this, enrollmentId, courseId, originalTuitionFee));
    }
}
```

- [ ] **Step 5: Viết `ComboRepository`**

Tạo `backend/src/main/java/com/eduerp/modules/billing/internal/repository/ComboRepository.java`:

```java
package com.eduerp.modules.billing.internal.repository;

import com.eduerp.modules.billing.internal.model.Combo;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComboRepository extends JpaRepository<Combo, UUID> {

    /**
     * Filter optional duy nhất (spec mục 6 {@code ListCombos}). Một query với {@code :p IS NULL}
     * thay vì hai nhánh if trong usecase - giữ complexity ở 1, mirror
     * {@code InvoiceRepository.search}. {@code ComboRepositoryIT.searchFiltersByOptional...} phủ cả
     * hai nhánh nên lỗi suy kiểu tham số null (nếu có) đỏ ngay ở test.
     */
    @Query("""
            SELECT c FROM Combo c
            WHERE (:studentProfileId IS NULL OR c.studentProfileId = :studentProfileId)
            """)
    Page<Combo> search(@Param("studentProfileId") UUID studentProfileId, Pageable pageable);
}
```

- [ ] **Step 6: Chạy lại để xác nhận xanh**

Run: `cd backend && mvn -q test -Dtest=ComboRepositoryIT`
Expected: PASS — 9 test.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/internal/model/Combo.java \
  backend/src/main/java/com/eduerp/modules/billing/internal/model/ComboEnrollment.java \
  backend/src/main/java/com/eduerp/modules/billing/internal/repository/ComboRepository.java \
  backend/src/test/java/com/eduerp/modules/billing/internal/repository/ComboRepositoryIT.java
git commit -m "feat(billing): add combo aggregate with cascaded enrollment members"
```

---

### Task 3: Migration V22 + `Invoice.comboId` / `enrollmentId` nullable + query theo combo

**Files:**
- Create: `backend/src/main/resources/db/migration/V22__add_invoice_combo_link.sql`
- Modify: `backend/src/main/java/com/eduerp/modules/billing/internal/model/Invoice.java`
- Modify: `backend/src/main/java/com/eduerp/modules/billing/internal/repository/InvoiceRepository.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/internal/repository/BillingRepositoryIT.java`

**Interfaces:**
- Consumes: bảng `combos` (Task 1); `Combo` entity (Task 2).
- Produces:
  - `Invoice.getComboId(): UUID` (nullable), `Invoice.getEnrollmentId()`/`getCourseId()` nay có thể `null`
  - `Invoice.forCombo(UUID comboId, UUID studentProfileId, UUID branchId, int installmentNumber, BigDecimal amount, LocalDate dueDate, UUID createdByAccountId): Invoice` (static factory)
  - Constructor 8 tham số cũ `Invoice(UUID enrollmentId, UUID studentProfileId, UUID courseId, UUID branchId, int installmentNumber, BigDecimal amount, LocalDate dueDate, UUID createdByAccountId)` **giữ nguyên chữ ký** — mọi call site Phase 3 compile không đổi
  - `InvoiceRepository.countByComboIdAndStatusNot(UUID comboId, BillingConstants.InvoiceStatus status): long`
  - `InvoiceRepository.findAllByComboIdAndStatusNot(UUID comboId, BillingConstants.InvoiceStatus status): List<Invoice>`
  - `InvoiceRepository.findAllByComboId(UUID comboId): List<Invoice>`
  - `InvoiceRepository.countByComboId(UUID comboId): long`

- [ ] **Step 1: Viết migration V22**

Tạo `backend/src/main/resources/db/migration/V22__add_invoice_combo_link.sql`:

```sql
-- Một Invoice thuộc về ĐÚNG MỘT trong hai: một Enrollment (luồng Phase 3, giữ nguyên 100%) hoặc
-- một Combo (luồng mới) - spec mục 5. Mọi Invoice hiện có đều có enrollment_id + course_id và
-- combo_id null, nên thoả CHECK ngay: KHÔNG cần backfill.
ALTER TABLE invoices ALTER COLUMN enrollment_id DROP NOT NULL;
ALTER TABLE invoices ALTER COLUMN course_id DROP NOT NULL;
ALTER TABLE invoices ADD COLUMN combo_id UUID REFERENCES combos(id);
ALTER TABLE invoices ADD CONSTRAINT chk_invoices_enrollment_xor_combo
    CHECK ((enrollment_id IS NOT NULL AND combo_id IS NULL)
        OR (enrollment_id IS NULL AND combo_id IS NOT NULL));

-- GetComboDetail/CancelCombo lọc đúng theo combo_id; không có index này thì mỗi lần mở chi tiết
-- combo là một lần quét toàn bảng hoá đơn.
CREATE INDEX idx_invoices_combo ON invoices (combo_id);

-- Mirror V20: tối đa 3 đợt theo combo, invoice CANCELLED không chiếm chỗ. Vế
-- "combo_id IS NOT NULL" là thừa về mặt logic (Postgres coi hai NULL là khác nhau trong unique
-- index) nhưng giữ lại để index chỉ chứa dòng combo, không phình theo mọi hoá đơn đơn-khoá.
CREATE UNIQUE INDEX uq_invoices_combo_installment
    ON invoices (combo_id, installment_number)
    WHERE status <> 'CANCELLED' AND combo_id IS NOT NULL;

-- uq_invoices_enrollment_installment (V20) không cần sửa: enrollment_id của hoá đơn combo là NULL,
-- mà Postgres không coi hai NULL là trùng nhau, nên index cũ tự động bỏ qua các dòng combo.
```

- [ ] **Step 2: Viết test đỏ — Review Focus #8 + các query theo combo**

Thêm vào `backend/src/test/java/com/eduerp/modules/billing/internal/repository/BillingRepositoryIT.java`. Import bổ sung:

```java
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
```

Field + helper bổ sung (đặt cạnh các `@Autowired` sẵn có và helper `newInvoice`):

```java
    @Autowired
    ComboRepository combos;

    private UUID newComboId() {
        var combo = new Combo(studentProfileId, branchId, new BigDecimal("21000000"),
                new BigDecimal("15"), LocalDate.of(2027, 1, 31), actorId);
        combo.addEnrollment(enrollmentId, courseId, new BigDecimal("12000000"));
        return combos.saveAndFlush(combo).getId();
    }

    private Invoice newComboInvoice(UUID comboId, int installmentNumber, String amount) {
        return Invoice.forCombo(comboId, studentProfileId, branchId, installmentNumber,
                new BigDecimal(amount), LocalDate.of(2027, 1, 31), actorId);
    }
```

Sáu test mới:

```java
    /**
     * Review Focus #8: dữ liệu Invoice của Phase 3 (enrollment_id + course_id set, combo_id null)
     * phải đọc/ghi được y nguyên sau V22 - CHECK constraint không được chặn nhánh cũ, và comboId
     * đọc ra null chứ không phải một giá trị rác nào.
     */
    @Test
    void keepsSingleCourseInvoicesValidAfterTheComboMigration() {
        var saved = invoices.saveAndFlush(newInvoice(1, "6000000"));

        var found = invoices.findById(saved.getId()).orElseThrow();
        assertThat(found.getComboId()).isNull();
        assertThat(found.getEnrollmentId()).isEqualTo(enrollmentId);
        assertThat(found.getCourseId()).isEqualTo(courseId);
        assertThat(found.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.UNPAID);
    }

    @Test
    void savesAComboInvoiceWithoutAnyEnrollmentOrCourse() {
        var comboId = newComboId();

        var saved = invoices.saveAndFlush(newComboInvoice(comboId, 1, "17850000"));

        var found = invoices.findById(saved.getId()).orElseThrow();
        assertThat(found.getComboId()).isEqualTo(comboId);
        assertThat(found.getEnrollmentId()).isNull();
        assertThat(found.getCourseId()).isNull();
        assertThat(found.getStudentProfileId()).isEqualTo(studentProfileId);
        assertThat(found.getBranchId()).isEqualTo(branchId);
        assertThat(found.getInstallmentNumber()).isEqualTo(1);
    }

    /** Review Focus #8, biên hỏng thứ nhất: một hoá đơn không neo vào đâu cả là dữ liệu mồ côi. */
    @Test
    void rejectsAnInvoiceThatBelongsToNeitherAnEnrollmentNorACombo() {
        var orphan = Invoice.forCombo(null, studentProfileId, branchId, 1, new BigDecimal("6000000"),
                LocalDate.of(2027, 1, 31), actorId);

        assertThatThrownBy(() -> invoices.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Review Focus #8, biên hỏng thứ hai: neo vào cả hai thì không trả lời được "ai nợ khoản này". */
    @Test
    void rejectsAnInvoiceThatBelongsToBothAnEnrollmentAndACombo() {
        var comboId = newComboId();
        var invoice = newInvoice(1, "6000000");
        ReflectionTestUtils.setField(invoice, "comboId", comboId);

        assertThatThrownBy(() -> invoices.saveAndFlush(invoice))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void countsAndSumsComboInvoicesIgnoringCancelledOnesAndListsThemAll() {
        var comboId = newComboId();
        invoices.saveAndFlush(newComboInvoice(comboId, 1, "10000000"));
        var cancelled = invoices.saveAndFlush(newComboInvoice(comboId, 2, "7850000"));
        cancelled.cancel();
        invoices.saveAndFlush(cancelled);

        assertThat(invoices.countByComboIdAndStatusNot(comboId, BillingConstants.InvoiceStatus.CANCELLED))
                .isEqualTo(1);
        assertThat(invoices.findAllByComboIdAndStatusNot(comboId, BillingConstants.InvoiceStatus.CANCELLED))
                .singleElement().satisfies(invoice -> assertThat(invoice.getAmount())
                        .isEqualByComparingTo(new BigDecimal("10000000")));
        // countByComboId/findAllByComboId đếm MỌI trạng thái - CancelCombo (Review Focus #6) và màn
        // chi tiết combo đều cần thấy cả hoá đơn đã huỷ.
        assertThat(invoices.countByComboId(comboId)).isEqualTo(2);
        assertThat(invoices.findAllByComboId(comboId)).hasSize(2);
    }

    /** Mirror V20 cho đơn vị neo combo: một đợt đang sống không thể trùng số thứ tự với đợt khác. */
    @Test
    void rejectsASecondLiveComboInvoiceWithTheSameInstallmentNumberAtDatabaseLevel() {
        var comboId = newComboId();
        invoices.saveAndFlush(newComboInvoice(comboId, 2, "5000000"));

        assertThatThrownBy(() -> invoices.saveAndFlush(newComboInvoice(comboId, 2, "5000000")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
```

Import bổ sung cho test dùng `ReflectionTestUtils`:

```java
import org.springframework.test.util.ReflectionTestUtils;
```

- [ ] **Step 3: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=BillingRepositoryIT`
Expected: FAIL — compile error `cannot find symbol: method forCombo(...)` và `method getComboId()`.

- [ ] **Step 4: Sửa `Invoice`**

Trong `backend/src/main/java/com/eduerp/modules/billing/internal/model/Invoice.java`:

4a. Bỏ `nullable = false` khỏi hai cột và thêm cột `combo_id` — thay khối field:

```java
    /** Nullable từ V22: hoá đơn combo không neo vào một ghi danh nào. CHECK constraint
     * {@code chk_invoices_enrollment_xor_combo} buộc đúng một trong hai nhánh có giá trị. */
    @Column(name = "enrollment_id", updatable = false)
    private UUID enrollmentId;

    @Column(name = "student_profile_id", nullable = false, updatable = false)
    private UUID studentProfileId;

    /** Nullable từ V22: hoá đơn combo gộp nhiều khoá nên không có một courseId duy nhất. */
    @Column(name = "course_id", updatable = false)
    private UUID courseId;

    /** Nullable: hoá đơn đơn-khoá (luồng Phase 3) không thuộc combo nào. */
    @Column(name = "combo_id", updatable = false)
    private UUID comboId;
```

4b. Thay constructor hiện tại bằng constructor cũ (giữ nguyên chữ ký) + constructor chung private + static factory:

```java
    public Invoice(UUID enrollmentId, UUID studentProfileId, UUID courseId, UUID branchId, int installmentNumber,
            BigDecimal amount, LocalDate dueDate, UUID createdByAccountId) {
        this(studentProfileId, branchId, installmentNumber, amount, dueDate, createdByAccountId);
        this.enrollmentId = enrollmentId;
        this.courseId = courseId;
    }

    /**
     * Hoá đơn của một combo: {@code enrollmentId}/{@code courseId} để null, {@code studentProfileId}
     * và {@code branchId} lấy từ chính {@code Combo} (spec mục 6). Là static factory chứ không phải
     * constructor thứ hai vì cả hai nhánh đều chỉ nhận toàn UUID - một constructor trùng kiểu là
     * cách gọi nhầm nhánh mà trình biên dịch không bắt được.
     */
    public static Invoice forCombo(UUID comboId, UUID studentProfileId, UUID branchId, int installmentNumber,
            BigDecimal amount, LocalDate dueDate, UUID createdByAccountId) {
        var invoice = new Invoice(studentProfileId, branchId, installmentNumber, amount, dueDate,
                createdByAccountId);
        invoice.comboId = comboId;
        return invoice;
    }

    private Invoice(UUID studentProfileId, UUID branchId, int installmentNumber, BigDecimal amount,
            LocalDate dueDate, UUID createdByAccountId) {
        this.studentProfileId = studentProfileId;
        this.branchId = branchId;
        this.installmentNumber = installmentNumber;
        this.amount = BillingRules.money(amount);
        this.dueDate = dueDate;
        this.createdByAccountId = createdByAccountId;
        this.amountPaid = BillingRules.money(BigDecimal.ZERO);
        this.status = BillingConstants.InvoiceStatus.UNPAID;
        this.issuedAt = Instant.now();
    }
```

Ba method `applyPayment`/`markOverdue`/`cancel` **không đổi một dòng nào** — đó là lý do mọi luồng thanh toán của Phase 3 chạy đúng y nguyên trên hoá đơn combo (Review Focus #9).

- [ ] **Step 5: Thêm 4 query vào `InvoiceRepository`**

Trong `backend/src/main/java/com/eduerp/modules/billing/internal/repository/InvoiceRepository.java`, thêm ngay dưới hai method `...ByEnrollmentIdAndStatusNot`:

```java
    /** Mirror {@code countByEnrollmentIdAndStatusNot} cho đơn vị neo combo: tối đa 3 đợt cho CẢ
     * combo, invoice CANCELLED không chiếm chỗ (spec mục 6). */
    long countByComboIdAndStatusNot(UUID comboId, BillingConstants.InvoiceStatus status);

    /** Cũng loại CANCELLED khi cộng tổng để so với {@code Combo.totalDiscountedAmount}. */
    List<Invoice> findAllByComboIdAndStatusNot(UUID comboId, BillingConstants.InvoiceStatus status);

    /** Chi tiết combo hiển thị MỌI hoá đơn của combo, kể cả đã huỷ (spec mục 6 {@code GetComboDetail}). */
    List<Invoice> findAllByComboId(UUID comboId);

    /** Review Focus #6: {@code CancelCombo} chỉ cho phép khi chưa có hoá đơn nào - đếm MỌI trạng
     * thái, kể cả CANCELLED. Một combo đã từng phát hành chứng từ tài chính thì không xoá cứng nữa. */
    long countByComboId(UUID comboId);
```

- [ ] **Step 6: Chạy lại để xác nhận xanh**

Run: `cd backend && mvn -q test -Dtest=BillingRepositoryIT`
Expected: PASS — 9 test cũ + 6 test mới.

- [ ] **Step 7: Chạy lại toàn bộ test billing để chứng minh Phase 3 không gãy**

Run: `cd backend && mvn -q test -Dtest='com.eduerp.modules.billing.**'`
Expected: PASS — mọi test Phase 3 xanh, không sửa một dòng test cũ nào.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V22__add_invoice_combo_link.sql \
  backend/src/main/java/com/eduerp/modules/billing/internal/model/Invoice.java \
  backend/src/main/java/com/eduerp/modules/billing/internal/repository/InvoiceRepository.java \
  backend/src/test/java/com/eduerp/modules/billing/internal/repository/BillingRepositoryIT.java
git commit -m "feat(billing): let an invoice belong to a combo instead of an enrollment"
```

---

### Task 4: Tám exception mới + mở rộng `BillingException.permits`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/MinimumComboSizeException.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/StudentMismatchInComboException.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/EnrollmentAlreadyInComboException.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/ComboDiscountTierNotConfiguredException.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/ComboDiscountTierNotFoundException.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/ComboDiscountTierAlreadyExistsException.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/ComboNotFoundException.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/ComboHasInvoicesException.java`
- Modify: `backend/src/main/java/com/eduerp/modules/billing/BillingException.java`
- Modify: `backend/src/main/java/com/eduerp/modules/billing/InstallmentLimitExceededException.java`
- Modify: `backend/src/main/java/com/eduerp/modules/billing/InvoiceAmountExceedsTuitionException.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/BillingExceptionTest.java`

**Interfaces:**
- Consumes: `BillingException(String errorCode, HttpStatus status, String message)` (protected, đã có).
- Produces:
  - `MinimumComboSizeException(int minimumEnrollments)` → `BILLING_COMBO_MINIMUM_SIZE`, 400
  - `StudentMismatchInComboException(UUID enrollmentId)` → `BILLING_COMBO_STUDENT_MISMATCH`, 400
  - `EnrollmentAlreadyInComboException(List<UUID> enrollmentIds)` → `BILLING_ENROLLMENT_ALREADY_IN_COMBO`, 409
  - `ComboDiscountTierNotConfiguredException(int courseCount)` → `BILLING_COMBO_DISCOUNT_TIER_NOT_CONFIGURED`, 409
  - `ComboDiscountTierNotFoundException(UUID tierId)` → `BILLING_COMBO_DISCOUNT_TIER_NOT_FOUND`, 404
  - `ComboDiscountTierAlreadyExistsException(int minCourseCount)` → `BILLING_COMBO_DISCOUNT_TIER_ALREADY_EXISTS`, 409
  - `ComboNotFoundException(UUID comboId)` → `BILLING_COMBO_NOT_FOUND`, 404
  - `ComboHasInvoicesException(UUID comboId)` → `BILLING_COMBO_HAS_INVOICES`, 409
  - `InstallmentLimitExceededException.forCombo(UUID comboId, int maxInstallments): InstallmentLimitExceededException`
  - `InvoiceAmountExceedsTuitionException.forCombo(BigDecimal totalAfterThisInvoice, BigDecimal totalDiscountedAmount): InvoiceAmountExceedsTuitionException`

> **Hai exception ngoài spec, có lý do:** spec mục 6 mô tả `UpdateComboDiscountTier`/`CreateComboDiscountTier` là "CRUD đơn giản" mà không nêu lỗi cho `tierId` không tồn tại và cho `minCourseCount` trùng (cột UNIQUE của V21). Không có hai exception này thì cả hai trường hợp rơi thành HTTP 500 thay vì một `ProblemDetail` có `errorCode` — đi ngược Hard Rule #9 của repo. Chúng không mở thêm quyền, bảng hay endpoint nào.

- [ ] **Step 1: Viết test đỏ**

Thêm vào cuối `backend/src/test/java/com/eduerp/modules/billing/BillingExceptionTest.java` (bên trong class, giữ nguyên mọi test đã có). Import bổ sung `java.util.List`:

```java
    @Test
    void minimumComboSizeIsBadRequestAndNamesTheMinimum() {
        var ex = new MinimumComboSizeException(2);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COMBO_MINIMUM_SIZE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("2");
    }

    @Test
    void studentMismatchInComboIsBadRequestAndNamesTheOffendingEnrollment() {
        var enrollmentId = UUID.randomUUID();
        var ex = new StudentMismatchInComboException(enrollmentId);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COMBO_STUDENT_MISMATCH");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains(enrollmentId.toString());
    }

    @Test
    void enrollmentAlreadyInComboIsConflict() {
        var ex = new EnrollmentAlreadyInComboException(List.of(UUID.randomUUID()));
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_ENROLLMENT_ALREADY_IN_COMBO");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** Mirror CourseTuitionNotConfiguredException: giá chưa cấu hình thì chặn, không suy đoán 0%. */
    @Test
    void comboDiscountTierNotConfiguredIsConflictAndNamesTheCourseCount() {
        var ex = new ComboDiscountTierNotConfiguredException(2);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COMBO_DISCOUNT_TIER_NOT_CONFIGURED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("2");
    }

    @Test
    void comboDiscountTierNotFoundIsNotFound() {
        var ex = new ComboDiscountTierNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COMBO_DISCOUNT_TIER_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void comboDiscountTierAlreadyExistsIsConflictAndNamesTheTier() {
        var ex = new ComboDiscountTierAlreadyExistsException(3);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COMBO_DISCOUNT_TIER_ALREADY_EXISTS");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("3");
    }

    @Test
    void comboNotFoundIsNotFound() {
        var ex = new ComboNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COMBO_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Review Focus #6: combo đã có chứng từ tài chính thì không xoá cứng được nữa. */
    @Test
    void comboHasInvoicesIsConflict() {
        var ex = new ComboHasInvoicesException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COMBO_HAS_INVOICES");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** Spec mục 6: combo dùng LẠI đúng hai exception này (cùng type, cùng errorCode) - chỉ câu chữ
     * của message đổi theo đơn vị neo, để kế toán không đọc thấy chữ "Ghi danh" trên màn hình combo. */
    @Test
    void comboVariantsKeepTheSameErrorCodeButNameTheComboInTheMessage() {
        var comboId = UUID.randomUUID();
        var limit = InstallmentLimitExceededException.forCombo(comboId, 3);
        assertThat(limit.getErrorCode()).isEqualTo("BILLING_INSTALLMENT_LIMIT_EXCEEDED");
        assertThat(limit.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(limit.getMessage()).contains("Combo", comboId.toString(), "3").doesNotContain("Ghi danh");

        var exceeds = InvoiceAmountExceedsTuitionException.forCombo(new BigDecimal("18000000"),
                new BigDecimal("17850000"));
        assertThat(exceeds.getErrorCode()).isEqualTo("BILLING_INVOICE_AMOUNT_EXCEEDS_TUITION");
        assertThat(exceeds.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exceeds.getMessage()).contains("18000000", "17850000", "combo");
    }
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=BillingExceptionTest`
Expected: FAIL — compile error `cannot find symbol: class MinimumComboSizeException` (và 7 class còn lại).

- [ ] **Step 3: Viết 8 exception mới**

`backend/src/main/java/com/eduerp/modules/billing/MinimumComboSizeException.java`:

```java
package com.eduerp.modules.billing;

import org.springframework.http.HttpStatus;

/** Một "combo" một phần tử không phải combo - nó chỉ là một ghi danh, và đã có luồng CreateInvoice
 * cho trường hợp đó (spec mục 6 bước 1, Review Focus #2). */
public final class MinimumComboSizeException extends BillingException {
    public MinimumComboSizeException(int minimumEnrollments) {
        super("BILLING_COMBO_MINIMUM_SIZE", HttpStatus.BAD_REQUEST,
                "Combo phải gồm ít nhất " + minimumEnrollments + " ghi danh khác nhau");
    }
}
```

`StudentMismatchInComboException.java`:

```java
package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Review Focus #1: gộp ghi danh của hai học viên (hoặc hai chi nhánh) vào một combo phải bị chặn,
 * KHÔNG được âm thầm lấy học viên của ghi danh đầu tiên rồi thu tiền người khác. */
public final class StudentMismatchInComboException extends BillingException {
    public StudentMismatchInComboException(UUID enrollmentId) {
        super("BILLING_COMBO_STUDENT_MISMATCH", HttpStatus.BAD_REQUEST,
                "Ghi danh " + enrollmentId + " không cùng học viên/chi nhánh với các ghi danh còn lại");
    }
}
```

`EnrollmentAlreadyInComboException.java`:

```java
package com.eduerp.modules.billing;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Review Focus #3: hai request đồng thời cùng chọn một ghi danh vào hai combo khác nhau. UNIQUE
 * {@code combo_enrollments.enrollment_id} chặn một trong hai ở tầng DB; lỗi này là bản dịch nghiệp
 * vụ của nó. Nhận cả danh sách vì tại thời điểm bắt {@code DataIntegrityViolationException} không
 * biết được id nào đã thua - nói "một trong các ghi danh" là sự thật, chỉ đích danh một cái là đoán.
 */
public final class EnrollmentAlreadyInComboException extends BillingException {
    public EnrollmentAlreadyInComboException(List<UUID> enrollmentIds) {
        super("BILLING_ENROLLMENT_ALREADY_IN_COMBO", HttpStatus.CONFLICT,
                "Một trong các ghi danh " + enrollmentIds + " đã nằm trong một combo khác");
    }
}
```

`ComboDiscountTierNotConfiguredException.java`:

```java
package com.eduerp.modules.billing;

import org.springframework.http.HttpStatus;

/** Review Focus #4: chưa cấu hình bậc nào thoả số khoá này thì CHẶN, không mặc định 0% - mirror
 * CourseTuitionNotConfiguredException ("giá chưa cấu hình thì chặn, không suy đoán"). */
public final class ComboDiscountTierNotConfiguredException extends BillingException {
    public ComboDiscountTierNotConfiguredException(int courseCount) {
        super("BILLING_COMBO_DISCOUNT_TIER_NOT_CONFIGURED", HttpStatus.CONFLICT,
                "Chưa cấu hình bậc giảm giá nào cho combo " + courseCount + " khoá");
    }
}
```

`ComboDiscountTierNotFoundException.java`:

```java
package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ComboDiscountTierNotFoundException extends BillingException {
    public ComboDiscountTierNotFoundException(UUID tierId) {
        super("BILLING_COMBO_DISCOUNT_TIER_NOT_FOUND", HttpStatus.NOT_FOUND,
                "Không tìm thấy bậc giảm giá " + tierId);
    }
}
```

`ComboDiscountTierAlreadyExistsException.java`:

```java
package com.eduerp.modules.billing;

import org.springframework.http.HttpStatus;

/** {@code combo_discount_tiers.min_course_count} UNIQUE (V21): hai bậc cùng mốc sẽ khiến việc chọn
 * bậc phụ thuộc thứ tự dòng trong bảng. Sửa bậc đã có thì dùng UpdateComboDiscountTier. */
public final class ComboDiscountTierAlreadyExistsException extends BillingException {
    public ComboDiscountTierAlreadyExistsException(int minCourseCount) {
        super("BILLING_COMBO_DISCOUNT_TIER_ALREADY_EXISTS", HttpStatus.CONFLICT,
                "Đã có bậc giảm giá cho mốc " + minCourseCount + " khoá");
    }
}
```

`ComboNotFoundException.java`:

```java
package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ComboNotFoundException extends BillingException {
    public ComboNotFoundException(UUID comboId) {
        super("BILLING_COMBO_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy combo " + comboId);
    }
}
```

`ComboHasInvoicesException.java`:

```java
package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Review Focus #6: huỷ combo là xoá cứng. Một khi đã phát hành hoá đơn (bất kể trạng thái, kể cả
 * đã huỷ), combo là chứng từ tài chính đã chốt - cùng nguyên tắc "đã chốt thì không xoá" mà
 * CancelInvoice áp dụng cho hoá đơn (spec mục 6). */
public final class ComboHasInvoicesException extends BillingException {
    public ComboHasInvoicesException(UUID comboId) {
        super("BILLING_COMBO_HAS_INVOICES", HttpStatus.CONFLICT,
                "Combo " + comboId + " đã phát hành hoá đơn, không huỷ được");
    }
}
```

- [ ] **Step 4: Thêm `forCombo` vào hai exception dùng lại**

`InstallmentLimitExceededException.java` — giữ nguyên constructor public cũ, thêm:

```java
    private InstallmentLimitExceededException(String message) {
        super("BILLING_INSTALLMENT_LIMIT_EXCEEDED", HttpStatus.CONFLICT, message);
    }

    /** Spec mục 6: combo dùng LẠI lỗi này (cùng errorCode - bản chất giống hệt: hết số đợt được
     * phép), chỉ đổi đơn vị neo trong câu chữ từ "Ghi danh" sang "Combo". */
    public static InstallmentLimitExceededException forCombo(UUID comboId, int maxInstallments) {
        return new InstallmentLimitExceededException(
                "Combo " + comboId + " đã có đủ " + maxInstallments + " đợt thu");
    }
```

`InvoiceAmountExceedsTuitionException.java` — giữ nguyên constructor public cũ, thêm:

```java
    private InvoiceAmountExceedsTuitionException(String message) {
        super("BILLING_INVOICE_AMOUNT_EXCEEDS_TUITION", HttpStatus.CONFLICT, message);
    }

    /** Spec mục 6: tên và errorCode giữ nguyên dù ngữ cảnh là combo, vì bản chất lỗi giống nhau -
     * "vượt tổng tiền phải thu". Chỉ mốc so sánh đổi: tổng combo SAU giảm giá. */
    public static InvoiceAmountExceedsTuitionException forCombo(BigDecimal totalAfterThisInvoice,
            BigDecimal totalDiscountedAmount) {
        return new InvoiceAmountExceedsTuitionException("Tổng các đợt thu "
                + totalAfterThisInvoice.toPlainString() + " vượt tổng tiền combo sau giảm giá "
                + totalDiscountedAmount.toPlainString());
    }
```

`InstallmentLimitExceededException` cần thêm import `java.util.UUID` (đã có) — không cần import mới.

- [ ] **Step 5: Mở rộng `permits` của `BillingException`**

Thay khối `permits` trong `backend/src/main/java/com/eduerp/modules/billing/BillingException.java`:

```java
public sealed class BillingException extends AppException
        permits InvoiceNotFoundException, PaymentNotFoundException, EnrollmentNotFoundException,
        EnrollmentNotActiveForBillingException, CourseNotFoundException, CourseTuitionNotConfiguredException,
        InstallmentLimitExceededException, InvoiceAmountExceedsTuitionException, InvoiceNotPayableException,
        InvalidPaymentAmountException, InvalidCallbackSignatureException, UnknownPaymentGatewayException,
        PaymentGatewayUnavailableException, InvoiceHasPendingPaymentException, MinimumComboSizeException,
        StudentMismatchInComboException, EnrollmentAlreadyInComboException,
        ComboDiscountTierNotConfiguredException, ComboDiscountTierNotFoundException,
        ComboDiscountTierAlreadyExistsException, ComboNotFoundException, ComboHasInvoicesException {
```

- [ ] **Step 6: Chạy lại để xác nhận xanh**

Run: `cd backend && mvn -q test -Dtest=BillingExceptionTest`
Expected: PASS — 12 test cũ + 9 test mới.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/*Exception.java \
  backend/src/test/java/com/eduerp/modules/billing/BillingExceptionTest.java
git commit -m "feat(billing): add combo business exceptions with stable error codes"
```

---

### Task 5: `BillingConstants.Limits` combo + `BillingEvents.ComboCreated`/`ComboCancelled`

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/billing/BillingConstants.java`
- Modify: `backend/src/main/java/com/eduerp/modules/billing/BillingEvents.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/BillingConstantsTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO = 3`
  - `BillingConstants.Limits.MIN_ENROLLMENTS_PER_COMBO = 2`
  - `BillingEvents.ComboCreated(UUID comboId, UUID actorAccountId, UUID actorBranchId)`
  - `BillingEvents.ComboCancelled(UUID comboId, UUID actorAccountId, UUID actorBranchId)`

- [ ] **Step 1: Viết test đỏ**

Thêm vào cuối `backend/src/test/java/com/eduerp/modules/billing/BillingConstantsTest.java` (bên trong class, giữ nguyên mọi test đã có). Import bổ sung `java.util.UUID` nếu chưa có:

```java
    /** Spec mục 1 + 12: 3 đợt là cho CẢ combo, không phải 3 đợt mỗi khoá - hai hạn mức là hai hằng
     * riêng để không ai vô tình dùng chung rồi nhân lên theo số khoá. */
    @Test
    void comboLimitsAreThreeInstallmentsAndTwoEnrollments() {
        assertThat(BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO).isEqualTo(3);
        assertThat(BillingConstants.Limits.MIN_ENROLLMENTS_PER_COMBO).isEqualTo(2);
        assertThat(BillingConstants.Limits.MAX_INSTALLMENTS_PER_ENROLLMENT).isEqualTo(3);
    }

    /** Hai event combo mang đúng hình dạng 4 event billing đã có: (id, actorAccountId, actorBranchId). */
    @Test
    void comboEventsCarryTheComboIdAndTheActor() {
        var comboId = UUID.randomUUID();
        var actorAccountId = UUID.randomUUID();
        var actorBranchId = UUID.randomUUID();

        var created = new BillingEvents.ComboCreated(comboId, actorAccountId, actorBranchId);
        assertThat(created.comboId()).isEqualTo(comboId);
        assertThat(created.actorAccountId()).isEqualTo(actorAccountId);
        assertThat(created.actorBranchId()).isEqualTo(actorBranchId);

        var cancelled = new BillingEvents.ComboCancelled(comboId, actorAccountId, actorBranchId);
        assertThat(cancelled.comboId()).isEqualTo(comboId);
        assertThat(cancelled.actorAccountId()).isEqualTo(actorAccountId);
    }
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=BillingConstantsTest`
Expected: FAIL — compile error `cannot find symbol: variable MAX_INSTALLMENTS_PER_COMBO` và `class ComboCreated`.

- [ ] **Step 3: Thêm hai hằng vào `BillingConstants.Limits`**

Trong `backend/src/main/java/com/eduerp/modules/billing/BillingConstants.java`, thay nội dung `Limits`:

```java
    public static final class Limits {
        private Limits() {
        }

        public static final int MAX_INSTALLMENTS_PER_ENROLLMENT = 3;

        /** Spec mục 1: 3 đợt cho CẢ combo, KHÔNG phải 3 đợt mỗi khoá trong combo. Để riêng khỏi
         * {@link #MAX_INSTALLMENTS_PER_ENROLLMENT} vì đó là hai quyết định nghiệp vụ độc lập: đổi
         * một cái không được kéo theo cái kia. */
        public static final int MAX_INSTALLMENTS_PER_COMBO = 3;

        /** Spec mục 6 bước 1: dưới mốc này thì đó không phải combo, chỉ là một ghi danh. */
        public static final int MIN_ENROLLMENTS_PER_COMBO = 2;
    }
```

- [ ] **Step 4: Thêm hai event vào `BillingEvents`**

Trong `backend/src/main/java/com/eduerp/modules/billing/BillingEvents.java`, thêm sau `InvoiceCancelled`:

```java
    /** Gộp nhiều khoá thành một gói giảm giá là một quyết định về tiền - phải có dấu vết ai đã làm,
     * mirror InvoiceCreated (spec mục 9). */
    public record ComboCreated(UUID comboId, UUID actorAccountId, UUID actorBranchId) {
    }

    /** Huỷ combo là XOÁ CỨNG bản ghi - nếu không ghi lại thì sau đó không còn gì để biết nó từng
     * tồn tại. Phát trước khi xoá, trong cùng transaction (spec mục 6). */
    public record ComboCancelled(UUID comboId, UUID actorAccountId, UUID actorBranchId) {
    }
```

- [ ] **Step 5: Chạy lại để xác nhận xanh**

Run: `cd backend && mvn -q test -Dtest=BillingConstantsTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/BillingConstants.java \
  backend/src/main/java/com/eduerp/modules/billing/BillingEvents.java \
  backend/src/test/java/com/eduerp/modules/billing/BillingConstantsTest.java
git commit -m "feat(billing): add combo limits and combo lifecycle events"
```

---

### Task 6: DTO combo + `InvoiceResponse.comboId`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/CreateComboRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/ComboResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/ComboEnrollmentResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/ComboDetailResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/CreateComboInvoiceRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/ComboDiscountTierResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/CreateComboDiscountTierRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/UpdateComboDiscountTierRequest.java`
- Modify: `backend/src/main/java/com/eduerp/modules/billing/dto/InvoiceResponse.java`
- Modify: `backend/src/main/java/com/eduerp/modules/billing/usecase/CreateInvoice.java` (chỉ `toResponse`)
- Test: `backend/src/test/java/com/eduerp/modules/billing/usecase/CreateInvoiceTest.java`

**Interfaces:**
- Consumes: `BillingConstants.InvoiceStatus`; `Invoice.getComboId()` (Task 3).
- Produces:
  - `InvoiceResponse(UUID id, UUID enrollmentId, UUID comboId, UUID studentProfileId, UUID courseId, UUID branchId, int installmentNumber, BigDecimal amount, BigDecimal amountPaid, BillingConstants.InvoiceStatus status, LocalDate dueDate, Instant issuedAt)` — **`comboId` chèn ngay sau `enrollmentId`**, hai field neo loại trừ nhau nằm cạnh nhau
  - `CreateComboRequest(UUID studentProfileId, List<UUID> enrollmentIds, LocalDate dueDate)`
  - `ComboResponse(UUID id, UUID studentProfileId, UUID branchId, BigDecimal totalOriginalAmount, BigDecimal discountPercent, BigDecimal totalDiscountedAmount, LocalDate dueDate, Instant createdAt, int courseCount)`
  - `ComboEnrollmentResponse(UUID id, UUID enrollmentId, UUID courseId, BigDecimal originalTuitionFee)`
  - `ComboDetailResponse(ComboResponse combo, List<ComboEnrollmentResponse> enrollments, List<InvoiceResponse> invoices)`
  - `CreateComboInvoiceRequest(UUID comboId, BigDecimal amount, LocalDate dueDate)`
  - `ComboDiscountTierResponse(UUID id, int minCourseCount, BigDecimal discountPercent, boolean active)`
  - `CreateComboDiscountTierRequest(Integer minCourseCount, BigDecimal discountPercent)`
  - `UpdateComboDiscountTierRequest(BigDecimal discountPercent, boolean active)`

> **Call site duy nhất của constructor `InvoiceResponse`** là `CreateInvoice.toResponse` (đã kiểm bằng `grep -rn "new InvoiceResponse("` — 1 kết quả). Ba nơi khác (`ListInvoices`, `GetInvoiceDetail`, `RecordManualPayment`) đều đi qua `CreateInvoice.toResponse`, nên đổi thứ tự field chỉ phải sửa đúng một dòng.

- [ ] **Step 1: Viết test đỏ cho `comboId` trên `InvoiceResponse`**

Thêm vào `backend/src/test/java/com/eduerp/modules/billing/usecase/CreateInvoiceTest.java`, trong test `numbersTheFirstInstallmentOneAndPublishesInvoiceCreated`, ngay sau dòng `assertThat(response.enrollmentId()).isEqualTo(enrollmentId);`:

```java
        // Hoá đơn đơn-khoá không thuộc combo nào - hai field neo loại trừ nhau (spec mục 5).
        assertThat(response.comboId()).isNull();
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=CreateInvoiceTest`
Expected: FAIL — compile error `cannot find symbol: method comboId()`.

- [ ] **Step 3: Sửa `InvoiceResponse`**

Thay toàn bộ `backend/src/main/java/com/eduerp/modules/billing/dto/InvoiceResponse.java`:

```java
package com.eduerp.modules.billing.dto;

import com.eduerp.modules.billing.BillingConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tên field là hợp đồng với Zod schema ở frontend (entities/billing) - đổi tên là breaking.
 *
 * <p>Từ V22, {@code enrollmentId}/{@code courseId} nullable và {@code comboId} nullable: một hoá
 * đơn thuộc về ĐÚNG MỘT trong hai nhánh (ghi danh hoặc combo). Hai field neo đó đặt cạnh nhau để
 * người đọc thấy ngay chúng loại trừ nhau. {@code installmentNumber} vẫn có nghĩa ở cả hai nhánh -
 * với combo là "đợt N của combo" (spec mục 5).
 */
public record InvoiceResponse(UUID id, UUID enrollmentId, UUID comboId, UUID studentProfileId, UUID courseId,
        UUID branchId, int installmentNumber, BigDecimal amount, BigDecimal amountPaid,
        BillingConstants.InvoiceStatus status, LocalDate dueDate, Instant issuedAt) {
}
```

- [ ] **Step 4: Sửa call site duy nhất trong `CreateInvoice.toResponse`**

Trong `backend/src/main/java/com/eduerp/modules/billing/usecase/CreateInvoice.java`, thay thân method `toResponse` (javadoc giữ nguyên):

```java
    /** Dùng lại ở mọi usecase billing khác - một chỗ map duy nhất (mirror CreateEnrollment.toResponse). */
    static InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(invoice.getId(), invoice.getEnrollmentId(), invoice.getComboId(),
                invoice.getStudentProfileId(), invoice.getCourseId(), invoice.getBranchId(),
                invoice.getInstallmentNumber(), invoice.getAmount(), invoice.getAmountPaid(),
                invoice.getStatus(), invoice.getDueDate(), invoice.getIssuedAt());
    }
```

- [ ] **Step 5: Chạy lại để xác nhận xanh**

Run: `cd backend && mvn -q test -Dtest='com.eduerp.modules.billing.**'`
Expected: PASS — toàn bộ test billing, kể cả `BillingReadUseCasesTest`/`RecordManualPaymentTest` (chúng gọi qua `toResponse`, không gọi constructor trực tiếp).

- [ ] **Step 6: Viết 8 DTO combo**

`dto/CreateComboRequest.java`:

```java
package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Kế toán chọn sẵn các ghi danh ĐANG ACTIVE của một học viên; hệ thống tự tính % giảm theo số khoá.
 * Hạn đóng nhập tay, KHÔNG suy ra từ lịch học (spec mục 1 + 12).
 *
 * <p>{@code @NotEmpty} chỉ chặn danh sách rỗng - mốc "≥ 2 phần tử KHÁC NHAU" là quy tắc nghiệp vụ
 * (phải loại trùng trước khi đếm), nằm ở {@code CreateCombo} dưới dạng
 * {@code MinimumComboSizeException}, không nhét được vào một annotation.
 */
public record CreateComboRequest(@NotNull UUID studentProfileId,
        @NotEmpty List<@NotNull UUID> enrollmentIds, @NotNull LocalDate dueDate) {
}
```

`dto/ComboResponse.java`:

```java
package com.eduerp.modules.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tên field là hợp đồng với Zod schema ở frontend (entities/billing) - đổi tên là breaking.
 * {@code courseCount} là số khoá trong combo, trả sẵn để danh sách không phải tải cả danh sách con
 * chỉ để đếm.
 */
public record ComboResponse(UUID id, UUID studentProfileId, UUID branchId, BigDecimal totalOriginalAmount,
        BigDecimal discountPercent, BigDecimal totalDiscountedAmount, LocalDate dueDate, Instant createdAt,
        int courseCount) {
}
```

`dto/ComboEnrollmentResponse.java`:

```java
package com.eduerp.modules.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Một khoá trong combo, kèm học phí gốc đã snapshot lúc tạo (spec mục 4). */
public record ComboEnrollmentResponse(UUID id, UUID enrollmentId, UUID courseId,
        BigDecimal originalTuitionFee) {
}
```

`dto/ComboDetailResponse.java`:

```java
package com.eduerp.modules.billing.dto;

import java.util.List;

/** Mirror {@code InvoiceDetailResponse(invoice, payments)}: tổng thể + các danh sách con. */
public record ComboDetailResponse(ComboResponse combo, List<ComboEnrollmentResponse> enrollments,
        List<InvoiceResponse> invoices) {
}
```

`dto/CreateComboInvoiceRequest.java`:

```java
package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Mirror {@code CreateInvoiceRequest} nhưng neo theo {@code comboId} thay vì {@code enrollmentId}:
 * id của đơn vị thu nằm trong body, đúng như {@code POST /api/billing/invoices} đã làm (spec mục 6).
 * Kế toán tự nhập số tiền mỗi đợt, hệ thống KHÔNG tự chia đều.
 */
public record CreateComboInvoiceRequest(@NotNull UUID comboId, @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate dueDate) {
}
```

`dto/ComboDiscountTierResponse.java`:

```java
package com.eduerp.modules.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Tên field là hợp đồng với Zod schema ở frontend (entities/billing) - đổi tên là breaking. */
public record ComboDiscountTierResponse(UUID id, int minCourseCount, BigDecimal discountPercent,
        boolean active) {
}
```

`dto/CreateComboDiscountTierRequest.java`:

```java
package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * {@code minCourseCount} tối thiểu là 2 vì combo tối thiểu 2 khoá (spec mục 6 bước 1) - một bậc cho
 * mốc 1 khoá sẽ không bao giờ được dùng tới. Kiểu bao {@code Integer} để {@code @NotNull} bắt được
 * trường thiếu thay vì mặc định 0.
 */
public record CreateComboDiscountTierRequest(@NotNull @Min(2) Integer minCourseCount,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPercent) {
}
```

`dto/UpdateComboDiscountTierRequest.java`:

```java
package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Không có {@code minCourseCount}: đổi mốc của một bậc đã dùng là tạo một bậc khác. Không có thao
 * tác xoá - đặt {@code active = false} là cách "xoá" một bậc (spec mục 4, mirror {@code Course.active}).
 */
public record UpdateComboDiscountTierRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPercent, boolean active) {
}
```

- [ ] **Step 7: Biên dịch lại toàn bộ**

Run: `cd backend && mvn -q clean compile test-compile`
Expected: BUILD SUCCESS, không warning về record component chưa dùng.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/dto/ \
  backend/src/main/java/com/eduerp/modules/billing/usecase/CreateInvoice.java \
  backend/src/test/java/com/eduerp/modules/billing/usecase/CreateInvoiceTest.java
git commit -m "feat(billing): add combo DTOs and expose comboId on invoice responses"
```

---

### Task 7: `CreateCombo` usecase (Review Focus #1, #2, #3, #4)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/CreateCombo.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/usecase/CreateComboTest.java`

**Interfaces:**
- Consumes: `ComboRepository` (Task 2), `ComboDiscountTierRepository` (Task 1), `Combo`/`ComboEnrollment` (Task 2), 8 exception + 2 factory (Task 4), `BillingConstants.Limits.MIN_ENROLLMENTS_PER_COMBO` + `BillingEvents.ComboCreated` (Task 5), `CreateComboRequest`/`ComboResponse`/`ComboEnrollmentResponse` (Task 6), `EnrollmentManagement.getEnrollment(UUID): Optional<EnrollmentSummaryResponse>` và `CoursesManagement.getCourseTuition(UUID): Optional<CourseTuitionResponse>` (đã có từ Phase 3, **không sửa**).
- Produces:
  - `CreateCombo.execute(UUID actorAccountId, UUID actorBranchId, CreateComboRequest request): ComboResponse`
  - `CreateCombo.toResponse(Combo combo): ComboResponse` (static, package-private — `ListCombos`/`GetComboDetail` dùng lại)
  - `CreateCombo.toEnrollmentResponse(ComboEnrollment member): ComboEnrollmentResponse` (static, package-private)

- [ ] **Step 1: Viết test đỏ cho phần kiểm tra đầu vào (Review Focus #1, #2)**

Tạo `backend/src/test/java/com/eduerp/modules/billing/usecase/CreateComboTest.java`:

```java
package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboDiscountTierNotConfiguredException;
import com.eduerp.modules.billing.CourseNotFoundException;
import com.eduerp.modules.billing.CourseTuitionNotConfiguredException;
import com.eduerp.modules.billing.EnrollmentAlreadyInComboException;
import com.eduerp.modules.billing.EnrollmentNotActiveForBillingException;
import com.eduerp.modules.billing.EnrollmentNotFoundException;
import com.eduerp.modules.billing.MinimumComboSizeException;
import com.eduerp.modules.billing.StudentMismatchInComboException;
import com.eduerp.modules.billing.dto.CreateComboRequest;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

class CreateComboTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2027, 1, 31);
    private static final BigDecimal MATHS_FEE = new BigDecimal("12000000");
    private static final BigDecimal ENGLISH_FEE = new BigDecimal("9000000");

    private final ComboRepository combos = mock(ComboRepository.class);
    private final ComboDiscountTierRepository tiers = mock(ComboDiscountTierRepository.class);
    private final EnrollmentManagement enrollmentManagement = mock(EnrollmentManagement.class);
    private final CoursesManagement coursesManagement = mock(CoursesManagement.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CreateCombo useCase =
            new CreateCombo(combos, tiers, enrollmentManagement, coursesManagement, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID enrollmentBranchId = UUID.randomUUID();
    private final UUID mathsEnrollmentId = UUID.randomUUID();
    private final UUID englishEnrollmentId = UUID.randomUUID();
    private final UUID mathsCourseId = UUID.randomUUID();
    private final UUID englishCourseId = UUID.randomUUID();

    private CreateComboRequest request(UUID... enrollmentIds) {
        return new CreateComboRequest(studentProfileId, List.of(enrollmentIds), DUE_DATE);
    }

    private void stubEnrollment(UUID enrollmentId, UUID courseId, UUID ownerStudentProfileId, UUID branchId,
            EnrollmentConstants.EnrollmentStatus status) {
        when(enrollmentManagement.getEnrollment(enrollmentId)).thenReturn(Optional.of(
                new EnrollmentManagement.EnrollmentSummaryResponse(enrollmentId, ownerStudentProfileId, courseId,
                        branchId, status)));
    }

    private void stubTwoActiveEnrollments() {
        stubEnrollment(mathsEnrollmentId, mathsCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        stubEnrollment(englishEnrollmentId, englishCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
    }

    private void stubTuition(UUID courseId, BigDecimal tuitionFee) {
        when(coursesManagement.getCourseTuition(courseId)).thenReturn(Optional.of(
                new CoursesManagement.CourseTuitionResponse(courseId, tuitionFee, true)));
    }

    private void stubBothTuitions() {
        stubTuition(mathsCourseId, MATHS_FEE);
        stubTuition(englishCourseId, ENGLISH_FEE);
    }

    private void stubTier(int minCourseCount, String discountPercent) {
        when(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(anyInt()))
                .thenReturn(Optional.of(new ComboDiscountTier(minCourseCount, new BigDecimal(discountPercent))));
    }

    private void stubSaveEchoesBack() {
        when(combos.saveAndFlush(any(Combo.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** Review Focus #2: một "combo" một phần tử không phải combo - đã có CreateInvoice cho việc đó. */
    @Test
    void rejectsAComboOfASingleEnrollment() {
        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(mathsEnrollmentId)))
                .isInstanceOf(MinimumComboSizeException.class)
                .hasMessageContaining("2");

        verify(enrollmentManagement, never()).getEnrollment(any());
    }

    /** Review Focus #2, mặt dễ lọt: hai phần tử nhưng là CÙNG MỘT ghi danh vẫn chỉ là một khoá. */
    @Test
    void rejectsAComboBuiltFromTheSameEnrollmentTwice() {
        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, mathsEnrollmentId)))
                .isInstanceOf(MinimumComboSizeException.class);
    }

    /**
     * Review Focus #1: gộp ghi danh của hai học viên khác nhau phải bị CHẶN, không được âm thầm lấy
     * học viên của ghi danh đầu tiên - nếu không, hệ thống phát hành một công nợ cho người này dựa
     * trên khoá học của người kia.
     */
    @Test
    void rejectsAComboMixingTwoDifferentStudents() {
        stubEnrollment(mathsEnrollmentId, mathsCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        stubEnrollment(englishEnrollmentId, englishCourseId, UUID.randomUUID(), enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(StudentMismatchInComboException.class)
                .hasMessageContaining(englishEnrollmentId.toString());

        verify(combos, never()).saveAndFlush(any());
    }

    /** Review Focus #1, biến thể: ghi danh ĐẦU TIÊN mới là người không khớp studentProfileId trong
     * body - không được bỏ qua chỉ vì nó là mốc so sánh branchId. */
    @Test
    void rejectsWhenTheFirstEnrollmentItselfBelongsToAnotherStudent() {
        stubEnrollment(mathsEnrollmentId, mathsCourseId, UUID.randomUUID(), enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        stubEnrollment(englishEnrollmentId, englishCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(StudentMismatchInComboException.class)
                .hasMessageContaining(mathsEnrollmentId.toString());
    }

    /** branchId của combo lấy từ ghi danh ĐẦU TIÊN, không phải actorBranchId - kế toán cấp tổ chức
     * tạo được combo cho chi nhánh khác, nhưng mọi khoá trong combo phải cùng một chi nhánh. */
    @Test
    void rejectsAComboMixingTwoBranchesEvenForTheSameStudent() {
        stubEnrollment(mathsEnrollmentId, mathsCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        stubEnrollment(englishEnrollmentId, englishCourseId, studentProfileId, UUID.randomUUID(),
                EnrollmentConstants.EnrollmentStatus.ACTIVE);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(StudentMismatchInComboException.class);
    }

    @Test
    void rejectsAnUnknownEnrollment() {
        stubEnrollment(mathsEnrollmentId, mathsCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        when(enrollmentManagement.getEnrollment(englishEnrollmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(EnrollmentNotFoundException.class);
    }

    /** Chỉ gộp được ghi danh ĐANG HỌC - một khoá đã rút không còn là thứ để bán gói (spec mục 6). */
    @Test
    void rejectsAWithdrawnEnrollment() {
        stubEnrollment(mathsEnrollmentId, mathsCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        stubEnrollment(englishEnrollmentId, englishCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.WITHDRAWN);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(EnrollmentNotActiveForBillingException.class);
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=CreateComboTest`
Expected: FAIL — compile error `cannot find symbol: class CreateCombo`.

- [ ] **Step 3: Viết `CreateCombo` (đủ cả pricing — các test giá sẽ thêm ở Step 5)**

Tạo `backend/src/main/java/com/eduerp/modules/billing/usecase/CreateCombo.java`:

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboDiscountTierNotConfiguredException;
import com.eduerp.modules.billing.CourseNotFoundException;
import com.eduerp.modules.billing.CourseTuitionNotConfiguredException;
import com.eduerp.modules.billing.EnrollmentAlreadyInComboException;
import com.eduerp.modules.billing.EnrollmentNotActiveForBillingException;
import com.eduerp.modules.billing.EnrollmentNotFoundException;
import com.eduerp.modules.billing.MinimumComboSizeException;
import com.eduerp.modules.billing.StudentMismatchInComboException;
import com.eduerp.modules.billing.dto.ComboEnrollmentResponse;
import com.eduerp.modules.billing.dto.ComboResponse;
import com.eduerp.modules.billing.dto.CreateComboRequest;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.ComboEnrollment;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gộp các ghi danh ĐANG ACTIVE của một học viên thành một gói giảm giá theo số khoá (spec mục 6).
 *
 * <p>KHÔNG tạo ghi danh nào: kế toán ghi danh từng khoá bằng luồng {@code CreateEnrollment} có sẵn
 * trước, rồi mới gộp. Usecase này chỉ ĐỌC {@code EnrollmentManagement.getEnrollment} - đúng mirror
 * cách {@code CreateInvoice} làm, không cần thêm một quyền ghi xuyên module nào (spec mục 3).
 *
 * <p>Thứ tự kiểm tra: số lượng → ghi danh tồn tại/ACTIVE → cùng học viên và cùng chi nhánh → học phí
 * từng khoá → bậc giảm giá → lưu. Dừng ở bước đầu tiên sai, không gom lỗi.
 */
@Service
public class CreateCombo {

    private final ComboRepository combos;
    private final ComboDiscountTierRepository tiers;
    private final EnrollmentManagement enrollmentManagement;
    private final CoursesManagement coursesManagement;
    private final ApplicationEventPublisher events;

    CreateCombo(ComboRepository combos, ComboDiscountTierRepository tiers,
            EnrollmentManagement enrollmentManagement, CoursesManagement coursesManagement,
            ApplicationEventPublisher events) {
        this.combos = combos;
        this.tiers = tiers;
        this.enrollmentManagement = enrollmentManagement;
        this.coursesManagement = coursesManagement;
        this.events = events;
    }

    @Transactional
    public ComboResponse execute(UUID actorAccountId, UUID actorBranchId, CreateComboRequest request) {
        // LinkedHashSet: loại trùng (hai lần cùng một id vẫn chỉ là MỘT khoá - Review Focus #2) mà
        // vẫn giữ thứ tự người dùng gửi lên, vì phần tử đầu tiên quyết định branchId của combo.
        var enrollmentIds = List.copyOf(new LinkedHashSet<>(request.enrollmentIds()));
        if (enrollmentIds.size() < BillingConstants.Limits.MIN_ENROLLMENTS_PER_COMBO) {
            throw new MinimumComboSizeException(BillingConstants.Limits.MIN_ENROLLMENTS_PER_COMBO);
        }

        var members = enrollmentIds.stream().map(this::loadActiveEnrollment).toList();
        // branchId lấy từ ghi danh ĐẦU TIÊN, không phải actorBranchId: kế toán cấp tổ chức tạo được
        // combo cho bất kỳ chi nhánh nào - mirror cách CreateInvoice lấy branchId từ enrollment.
        var comboBranchId = members.get(0).branchId();
        members.forEach(member -> requireSameStudentAndBranch(member, request.studentProfileId(), comboBranchId));

        var priced = members.stream().map(this::price).toList();
        var totalOriginalAmount = priced.stream().map(PricedEnrollment::tuitionFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var tier = tiers
                .findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(priced.size())
                .orElseThrow(() -> new ComboDiscountTierNotConfiguredException(priced.size()));

        var combo = new Combo(request.studentProfileId(), comboBranchId, totalOriginalAmount,
                tier.getDiscountPercent(), request.dueDate(), actorAccountId);
        priced.forEach(item -> combo.addEnrollment(item.enrollmentId(), item.courseId(), item.tuitionFee()));

        var saved = save(combo, enrollmentIds);
        events.publishEvent(new BillingEvents.ComboCreated(saved.getId(), actorAccountId, actorBranchId));
        return toResponse(saved);
    }

    private EnrollmentManagement.EnrollmentSummaryResponse loadActiveEnrollment(UUID enrollmentId) {
        var enrollment = enrollmentManagement.getEnrollment(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        if (enrollment.status() != EnrollmentConstants.EnrollmentStatus.ACTIVE) {
            throw new EnrollmentNotActiveForBillingException(enrollmentId);
        }
        return enrollment;
    }

    /** Review Focus #1: kiểm CẢ ghi danh đầu tiên (nó là mốc so branchId, nhưng studentProfileId của
     * nó vẫn phải khớp đúng cái client gửi lên - không suy ngược từ dữ liệu). */
    private static void requireSameStudentAndBranch(EnrollmentManagement.EnrollmentSummaryResponse enrollment,
            UUID studentProfileId, UUID comboBranchId) {
        if (!enrollment.studentProfileId().equals(studentProfileId)
                || !enrollment.branchId().equals(comboBranchId)) {
            throw new StudentMismatchInComboException(enrollment.enrollmentId());
        }
    }

    private PricedEnrollment price(EnrollmentManagement.EnrollmentSummaryResponse enrollment) {
        var tuition = coursesManagement.getCourseTuition(enrollment.courseId())
                .orElseThrow(() -> new CourseNotFoundException(enrollment.courseId()));
        if (tuition.tuitionFee() == null) {
            throw new CourseTuitionNotConfiguredException(enrollment.courseId());
        }
        return new PricedEnrollment(enrollment.enrollmentId(), enrollment.courseId(), tuition.tuitionFee());
    }

    /**
     * Review Focus #3: hai request đồng thời cùng chọn một ghi danh đều đọc xong trước khi ai kịp
     * ghi, nên mọi kiểm tra ở trên đều qua. UNIQUE {@code combo_enrollments.enrollment_id} (V21) là
     * lớp chặn cuối - {@code saveAndFlush} để INSERT chạy NGAY trong khối try này thay vì trôi tới
     * lúc commit (ngoài tầm với của catch) rồi rơi thành 500. Mirror {@code CreateInvoice.saveInvoice}.
     */
    private Combo save(Combo combo, List<UUID> enrollmentIds) {
        try {
            return combos.saveAndFlush(combo);
        } catch (DataIntegrityViolationException raceLostToAnotherRequest) {
            throw new EnrollmentAlreadyInComboException(enrollmentIds);
        }
    }

    /** Giữ đúng bộ ba (ghi danh, khoá, học phí) đi cùng nhau thay vì ghép lại bằng chỉ số của hai
     * danh sách song song - một chỗ lệch chỉ số là một học viên bị tính sai tiền. */
    private record PricedEnrollment(UUID enrollmentId, UUID courseId, BigDecimal tuitionFee) {
    }

    /** Dùng lại ở {@code ListCombos}/{@code GetComboDetail} - một chỗ map duy nhất cho Combo
     * (mirror {@code CreateInvoice.toResponse}). */
    static ComboResponse toResponse(Combo combo) {
        return new ComboResponse(combo.getId(), combo.getStudentProfileId(), combo.getBranchId(),
                combo.getTotalOriginalAmount(), combo.getDiscountPercent(), combo.getTotalDiscountedAmount(),
                combo.getDueDate(), combo.getCreatedAt(), combo.getEnrollments().size());
    }

    /**
     * Tên khác {@code toResponse} một cách cố ý: hai static method cùng tên, một nhận {@code Combo}
     * một nhận {@code ComboEnrollment}, sẽ biến mọi method reference {@code CreateCombo::toResponse}
     * thành một bài toán suy luận overload - đặt tên riêng thì người đọc và trình biên dịch đều khỏi
     * phải đoán.
     */
    static ComboEnrollmentResponse toEnrollmentResponse(ComboEnrollment member) {
        return new ComboEnrollmentResponse(member.getId(), member.getEnrollmentId(), member.getCourseId(),
                member.getOriginalTuitionFee());
    }
}
```

- [ ] **Step 4: Chạy lại để xác nhận 7 test đầu xanh**

Run: `cd backend && mvn -q test -Dtest=CreateComboTest`
Expected: PASS — 7 test.

- [ ] **Step 5: Viết test đỏ cho phần giá (Review Focus #3 bản dịch lỗi, #4) + happy path**

Thêm 6 test vào cuối class `CreateComboTest`:

```java
    @Test
    void sumsTuitionFeesAppliesTheTierAndPublishesComboCreated() {
        stubTwoActiveEnrollments();
        stubBothTuitions();
        stubTier(2, "15");
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId));

        assertThat(response.studentProfileId()).isEqualTo(studentProfileId);
        assertThat(response.branchId()).isEqualTo(enrollmentBranchId);
        assertThat(response.totalOriginalAmount()).isEqualByComparingTo(new BigDecimal("21000000"));
        assertThat(response.discountPercent()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(response.totalDiscountedAmount()).isEqualByComparingTo(new BigDecimal("17850000"));
        assertThat(response.dueDate()).isEqualTo(DUE_DATE);
        assertThat(response.courseCount()).isEqualTo(2);
        verify(events).publishEvent(any(BillingEvents.ComboCreated.class));
    }

    /** Bậc giảm giá chọn theo SỐ KHOÁ SAU KHI LOẠI TRÙNG, không theo độ dài danh sách gửi lên. */
    @Test
    void picksTheTierByTheDeduplicatedCourseCount() {
        stubTwoActiveEnrollments();
        stubBothTuitions();
        stubTier(2, "15");
        stubSaveEchoesBack();

        useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId, mathsEnrollmentId));

        verify(tiers).findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(2);
    }

    /**
     * Review Focus #4: chưa cấu hình bậc nào thoả số khoá này thì CHẶN HẲN, không mặc định 0% - mirror
     * CourseTuitionNotConfiguredException của Phase 3. Nếu lặng lẽ giảm 0% thì kế toán tưởng đã bán
     * gói giảm giá, còn học viên trả nguyên giá.
     */
    @Test
    void rejectsACombinationWithNoConfiguredDiscountTier() {
        stubTwoActiveEnrollments();
        stubBothTuitions();
        when(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(2))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(ComboDiscountTierNotConfiguredException.class)
                .hasMessageContaining("2");

        verify(combos, never()).saveAndFlush(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void rejectsACourseWithoutATuitionFee() {
        stubTwoActiveEnrollments();
        stubTuition(mathsCourseId, MATHS_FEE);
        stubTuition(englishCourseId, null);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(CourseTuitionNotConfiguredException.class)
                .hasMessageContaining(englishCourseId.toString());
    }

    @Test
    void rejectsAnEnrollmentWhoseCourseIsGone() {
        stubTwoActiveEnrollments();
        stubTuition(mathsCourseId, MATHS_FEE);
        when(coursesManagement.getCourseTuition(englishCourseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(CourseNotFoundException.class);
    }

    /**
     * Review Focus #3: khi race lọt qua mọi kiểm tra ở trên (cả hai request đọc cùng một trạng thái
     * trước khi ai kịp ghi), UNIQUE combo_enrollments.enrollment_id chặn một trong hai ở bước lưu -
     * usecase phải dịch sang lỗi nghiệp vụ, KHÔNG để lọt ra thành 500.
     */
    @Test
    void translatesADatabaseRaceOnASharedEnrollmentIntoEnrollmentAlreadyInCombo() {
        stubTwoActiveEnrollments();
        stubBothTuitions();
        stubTier(2, "15");
        when(combos.saveAndFlush(any(Combo.class)))
                .thenThrow(new DataIntegrityViolationException("combo_enrollments_enrollment_id_key"));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(EnrollmentAlreadyInComboException.class);

        verify(events, never()).publishEvent(any());
    }
```

- [ ] **Step 6: Chạy toàn bộ `CreateComboTest`**

Run: `cd backend && mvn -q test -Dtest=CreateComboTest`
Expected: PASS — 13 test. (`CreateCombo` ở Step 3 đã đủ logic cho cả 6 test này; nếu đỏ thì sai sót nằm ở Step 3, sửa ở đó.)

- [ ] **Step 7: Kiểm tra `EnrollmentManagement` không bị thêm method ghi nào**

Run: `cd /Users/hoangdieu/PycharmProjects/EducationErp && git diff --stat 808528b -- backend/src/main/java/com/eduerp/modules/enrollment/EnrollmentManagement.java`
Expected: không có output. `808528b` là commit spec, tức trạng thái của nhánh trước khi plan này bắt đầu — so với nó thì facade phải y nguyên (Global Constraint).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/usecase/CreateCombo.java \
  backend/src/test/java/com/eduerp/modules/billing/usecase/CreateComboTest.java
git commit -m "feat(billing): group active enrollments into a discounted combo"
```

---

### Task 8: `CreateComboInvoice` usecase (Review Focus #5)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/CreateComboInvoice.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/usecase/CreateComboInvoiceTest.java`

**Interfaces:**
- Consumes: `ComboRepository.findById` (Task 2); `InvoiceRepository.countByComboIdAndStatusNot`/`findAllByComboIdAndStatusNot` + `Invoice.forCombo` (Task 3); `ComboNotFoundException` + `InstallmentLimitExceededException.forCombo` + `InvoiceAmountExceedsTuitionException.forCombo` (Task 4); `BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO` (Task 5); `CreateComboInvoiceRequest`/`InvoiceResponse` (Task 6); `CreateInvoice.toResponse` (Task 6).
- Produces: `CreateComboInvoice.execute(UUID actorAccountId, UUID actorBranchId, CreateComboInvoiceRequest request): InvoiceResponse`

- [ ] **Step 1: Viết test đỏ**

Tạo `backend/src/test/java/com/eduerp/modules/billing/usecase/CreateComboInvoiceTest.java`:

```java
package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.InstallmentLimitExceededException;
import com.eduerp.modules.billing.InvoiceAmountExceedsTuitionException;
import com.eduerp.modules.billing.dto.CreateComboInvoiceRequest;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class CreateComboInvoiceTest {

    /** 12.000.000 + 9.000.000 = 21.000.000, giảm 15% → 17.850.000. */
    private static final BigDecimal DISCOUNTED_TOTAL = new BigDecimal("17850000");
    private static final LocalDate DUE_DATE = LocalDate.of(2027, 1, 31);

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final ComboRepository combos = mock(ComboRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CreateComboInvoice useCase = new CreateComboInvoice(invoices, combos, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID comboBranchId = UUID.randomUUID();
    private final UUID comboId = UUID.randomUUID();

    private CreateComboInvoiceRequest request(String amount) {
        return new CreateComboInvoiceRequest(comboId, new BigDecimal(amount), DUE_DATE);
    }

    /** Combo lưu thật mới có id; trong unit test gán id bằng reflection để không phải dựng DB. */
    private Combo stubbedCombo() {
        var combo = new Combo(studentProfileId, comboBranchId, new BigDecimal("21000000"),
                new BigDecimal("15"), DUE_DATE, actorAccountId);
        ReflectionTestUtils.setField(combo, "id", comboId);
        when(combos.findById(comboId)).thenReturn(Optional.of(combo));
        return combo;
    }

    private Invoice existingComboInvoice(int installmentNumber, String amount) {
        return Invoice.forCombo(comboId, studentProfileId, comboBranchId, installmentNumber,
                new BigDecimal(amount), DUE_DATE, actorAccountId);
    }

    private void stubExistingInvoices(List<Invoice> existing) {
        when(invoices.countByComboIdAndStatusNot(comboId, BillingConstants.InvoiceStatus.CANCELLED))
                .thenReturn((long) existing.size());
        when(invoices.findAllByComboIdAndStatusNot(comboId, BillingConstants.InvoiceStatus.CANCELLED))
                .thenReturn(existing);
    }

    private void stubSaveEchoesBack() {
        when(invoices.saveAndFlush(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsAnUnknownCombo() {
        when(combos.findById(comboId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("5000000")))
                .isInstanceOf(ComboNotFoundException.class);
    }

    /** Hoá đơn combo không neo vào ghi danh/khoá nào; học viên và chi nhánh lấy từ chính Combo. */
    @Test
    void issuesTheFirstInstallmentAgainstTheComboAndPublishesInvoiceCreated() {
        stubbedCombo();
        stubExistingInvoices(List.of());
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId, request("10000000"));

        assertThat(response.installmentNumber()).isEqualTo(1);
        assertThat(response.comboId()).isEqualTo(comboId);
        assertThat(response.enrollmentId()).isNull();
        assertThat(response.courseId()).isNull();
        assertThat(response.studentProfileId()).isEqualTo(studentProfileId);
        assertThat(response.branchId()).isEqualTo(comboBranchId);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("10000000"));
        assertThat(response.status()).isEqualTo(BillingConstants.InvoiceStatus.UNPAID);
        assertThat(response.dueDate()).isEqualTo(DUE_DATE);
        verify(events).publishEvent(any(BillingEvents.InvoiceCreated.class));
    }

    @Test
    void numbersTheSecondInstallmentTwo() {
        stubbedCombo();
        stubExistingInvoices(List.of(existingComboInvoice(1, "10000000")));
        stubSaveEchoesBack();

        assertThat(useCase.execute(actorAccountId, actorBranchId, request("5000000")).installmentNumber())
                .isEqualTo(2);
    }

    /**
     * Review Focus #5: mốc so sánh là {@code totalDiscountedAmount} (17.850.000), KHÔNG phải tổng
     * gốc 21.000.000 - nếu so nhầm với tổng gốc thì học viên bị thu lại đúng phần vừa được giảm.
     * Và lỗi phải là INVOICE_AMOUNT_EXCEEDS_TUITION, không phải INSTALLMENT_LIMIT_EXCEEDED: mới có
     * 2 đợt, hạn mức 3 chưa chạm.
     */
    @Test
    void rejectsAnInstallmentThatPushesTheTotalPastTheDiscountedAmount() {
        stubbedCombo();
        stubExistingInvoices(List.of(existingComboInvoice(1, "10000000"),
                existingComboInvoice(2, "7850000")));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("1")))
                .isInstanceOf(InvoiceAmountExceedsTuitionException.class)
                .hasMessageContaining("17850000");
        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("3000000")))
                .isInstanceOf(InvoiceAmountExceedsTuitionException.class);
    }

    /** Ranh giới: tổng đúng BẰNG tổng sau giảm vẫn phát hành được, chỉ vượt mới bị chặn. */
    @Test
    void acceptsAnInstallmentThatExactlyCompletesTheDiscountedTotal() {
        stubbedCombo();
        stubExistingInvoices(List.of(existingComboInvoice(1, "10000000")));
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId, request("7850000"));

        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("7850000"));
        assertThat(response.installmentNumber()).isEqualTo(2);
    }

    /** Hạn mức 3 đợt là lỗi RIÊNG, chỉ gặp khi tiền còn chỗ mà số đợt đã hết - 3 đợt nhỏ chưa dùng
     * hết tổng combo. Lẫn hai lỗi này là báo sai nguyên nhân cho kế toán (Review Focus #5). */
    @Test
    void rejectsAFourthInstallmentEvenWhenTheComboBudgetRemains() {
        stubbedCombo();
        stubExistingInvoices(List.of(existingComboInvoice(1, "1000000"), existingComboInvoice(2, "1000000"),
                existingComboInvoice(3, "1000000")));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("1000000")))
                .isInstanceOf(InstallmentLimitExceededException.class)
                .hasMessageContaining("Combo")
                .hasMessageContaining("3");
    }

    /** Đợt đã huỷ không chiếm chỗ trong 3 đợt và không tính vào tổng - mirror CreateInvoice. */
    @Test
    void ignoresCancelledComboInvoicesInBothTheCountAndTheSum() {
        stubbedCombo();
        // Repository đã loại CANCELLED, nên usecase chỉ thấy 1 hoá đơn còn sống.
        stubExistingInvoices(List.of(existingComboInvoice(1, "10000000")));
        stubSaveEchoesBack();

        assertThat(useCase.execute(actorAccountId, actorBranchId, request("7850000")).installmentNumber())
                .isEqualTo(2);
    }

    /** Mirror CreateInvoice: race trên (combo_id, installment_number) chặn bởi uq_invoices_combo_
     * installment (V22), phải dịch sang lỗi nghiệp vụ thay vì rơi thành 500. */
    @Test
    void translatesADatabaseRaceOnTheInstallmentNumberIntoAnInstallmentLimitExceededException() {
        stubbedCombo();
        stubExistingInvoices(List.of());
        when(invoices.saveAndFlush(any(Invoice.class)))
                .thenThrow(new DataIntegrityViolationException("uq_invoices_combo_installment"));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("10000000")))
                .isInstanceOf(InstallmentLimitExceededException.class);

        verify(events, never()).publishEvent(any());
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=CreateComboInvoiceTest`
Expected: FAIL — compile error `cannot find symbol: class CreateComboInvoice`.

- [ ] **Step 3: Viết `CreateComboInvoice`**

Tạo `backend/src/main/java/com/eduerp/modules/billing/usecase/CreateComboInvoice.java`:

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.InstallmentLimitExceededException;
import com.eduerp.modules.billing.InvoiceAmountExceedsTuitionException;
import com.eduerp.modules.billing.dto.CreateComboInvoiceRequest;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mirror {@code CreateInvoice} nhưng neo theo {@code comboId}: tối đa 3 đợt cho CẢ combo (không
 * phải 3 đợt mỗi khoá), tổng không vượt {@code Combo.totalDiscountedAmount} - tức tổng SAU giảm giá,
 * không phải tổng gốc (spec mục 6).
 *
 * <p>Hai lỗi "hết đợt" và "vượt tổng tiền" là hai lỗi KHÁC NHAU (Review Focus #5) - kế toán cần biết
 * mình đang chạm giới hạn nào.
 *
 * <p>Hoá đơn tạo ra có {@code enrollmentId = null}, {@code courseId = null}; mọi luồng thu tiền của
 * Phase 3 ({@code InitiateOnlinePayment}/{@code RecordManualPayment}/{@code HandlePaymentCallback}/
 * {@code CancelInvoice}/{@code MarkOverdueInvoices}) chạy đúng trên nó mà không cần một nhánh rẽ
 * nào, vì chúng chỉ thao tác qua {@code id}/{@code amount}/{@code status} (Review Focus #9).
 */
@Service
public class CreateComboInvoice {

    private final InvoiceRepository invoices;
    private final ComboRepository combos;
    private final ApplicationEventPublisher events;

    CreateComboInvoice(InvoiceRepository invoices, ComboRepository combos,
            ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.combos = combos;
        this.events = events;
    }

    @Transactional
    public InvoiceResponse execute(UUID actorAccountId, UUID actorBranchId,
            CreateComboInvoiceRequest request) {
        var combo = combos.findById(request.comboId())
                .orElseThrow(() -> new ComboNotFoundException(request.comboId()));

        var liveInvoiceCount = invoices.countByComboIdAndStatusNot(request.comboId(),
                BillingConstants.InvoiceStatus.CANCELLED);
        if (liveInvoiceCount >= BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO) {
            throw InstallmentLimitExceededException.forCombo(request.comboId(),
                    BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO);
        }

        var alreadyInvoiced = invoices
                .findAllByComboIdAndStatusNot(request.comboId(), BillingConstants.InvoiceStatus.CANCELLED)
                .stream().map(Invoice::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalAfterThisInvoice = alreadyInvoiced.add(request.amount());
        if (totalAfterThisInvoice.compareTo(combo.getTotalDiscountedAmount()) > 0) {
            throw InvoiceAmountExceedsTuitionException.forCombo(totalAfterThisInvoice,
                    combo.getTotalDiscountedAmount());
        }

        var installmentNumber = Math.toIntExact(liveInvoiceCount) + 1;
        var saved = saveInvoice(Invoice.forCombo(combo.getId(), combo.getStudentProfileId(),
                combo.getBranchId(), installmentNumber, request.amount(), request.dueDate(), actorAccountId),
                combo.getId());
        events.publishEvent(new BillingEvents.InvoiceCreated(saved.getId(), actorAccountId, actorBranchId));
        return CreateInvoice.toResponse(saved);
    }

    /** Mirror {@code CreateInvoice.saveInvoice}: {@code uq_invoices_combo_installment} (V22) là lớp
     * chặn cuối cho hai request đồng thời cùng tính ra một số đợt; {@code saveAndFlush} để INSERT
     * chạy ngay trong khối try này thay vì trôi tới lúc commit rồi rơi thành 500. */
    private Invoice saveInvoice(Invoice invoice, UUID comboId) {
        try {
            return invoices.saveAndFlush(invoice);
        } catch (DataIntegrityViolationException raceLostToAnotherRequest) {
            throw InstallmentLimitExceededException.forCombo(comboId,
                    BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO);
        }
    }
}
```

- [ ] **Step 4: Chạy lại để xác nhận xanh**

Run: `cd backend && mvn -q test -Dtest=CreateComboInvoiceTest`
Expected: PASS — 8 test.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/usecase/CreateComboInvoice.java \
  backend/src/test/java/com/eduerp/modules/billing/usecase/CreateComboInvoiceTest.java
git commit -m "feat(billing): issue combo installments capped at the discounted total"
```

---

### Task 9: `CancelCombo` usecase (Review Focus #6)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/CancelCombo.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/usecase/CancelComboTest.java`

**Interfaces:**
- Consumes: `ComboRepository.findById`/`delete` (Task 2); `InvoiceRepository.countByComboId` (Task 3); `ComboNotFoundException`/`ComboHasInvoicesException` (Task 4); `BillingEvents.ComboCancelled` (Task 5).
- Produces: `CancelCombo.execute(UUID comboId, UUID actorAccountId, UUID actorBranchId): void` (chữ ký mirror `CancelInvoice.execute`).

- [ ] **Step 1: Viết test đỏ**

Tạo `backend/src/test/java/com/eduerp/modules/billing/usecase/CancelComboTest.java`:

```java
package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboHasInvoicesException;
import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class CancelComboTest {

    private final ComboRepository combos = mock(ComboRepository.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CancelCombo useCase = new CancelCombo(combos, invoices, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID comboId = UUID.randomUUID();

    private Combo stubbedCombo() {
        var combo = new Combo(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("21000000"),
                new BigDecimal("15"), LocalDate.of(2027, 1, 31), actorAccountId);
        ReflectionTestUtils.setField(combo, "id", comboId);
        when(combos.findById(comboId)).thenReturn(Optional.of(combo));
        when(invoices.countByComboId(comboId)).thenReturn(0L);
        return combo;
    }

    @Test
    void rejectsAnUnknownCombo() {
        when(combos.findById(comboId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(comboId, actorAccountId, actorBranchId))
                .isInstanceOf(ComboNotFoundException.class);
    }

    @Test
    void hardDeletesAComboThatHasNotIssuedAnyInvoice() {
        var combo = stubbedCombo();

        useCase.execute(comboId, actorAccountId, actorBranchId);

        verify(combos).delete(combo);
    }

    /**
     * Review Focus #6: combo đã phát hành ít nhất một hoá đơn là chứng từ tài chính đã chốt - xoá
     * cứng nó sẽ để lại hoá đơn trỏ về một combo không còn tồn tại, và mất luôn căn cứ của số tiền
     * đã thu.
     */
    @Test
    void refusesToCancelAComboThatAlreadyIssuedAnInvoice() {
        stubbedCombo();
        when(invoices.countByComboId(comboId)).thenReturn(1L);

        assertThatThrownBy(() -> useCase.execute(comboId, actorAccountId, actorBranchId))
                .isInstanceOf(ComboHasInvoicesException.class);

        verify(combos, never()).delete(any());
        verify(events, never()).publishEvent(any());
    }

    /**
     * Review Focus #6, mặt dễ lọt: hoá đơn đã HUỶ vẫn tính. {@code countByComboId} cố ý không lọc
     * trạng thái - một combo từng phát hành rồi huỷ hoá đơn vẫn là combo có lịch sử chứng từ, không
     * phải bản nháp. (Khác hẳn hạn mức 3 đợt, nơi CANCELLED không chiếm chỗ.)
     */
    @Test
    void countsCancelledInvoicesTooWhenDecidingWhetherTheComboMayBeDeleted() {
        stubbedCombo();
        when(invoices.countByComboId(comboId)).thenReturn(2L);

        assertThatThrownBy(() -> useCase.execute(comboId, actorAccountId, actorBranchId))
                .isInstanceOf(ComboHasInvoicesException.class);

        // Không được gọi biến thể lọc trạng thái ở đây - đó là câu hỏi của hạn mức đợt, không phải
        // câu hỏi "combo này đã từng có chứng từ chưa".
        verify(invoices, never()).countByComboIdAndStatusNot(any(), any());
    }

    /**
     * Huỷ combo là XOÁ CỨNG: nếu không phát event thì sau đó không còn dấu vết nào cho thấy combo
     * từng tồn tại. Phải phát TRƯỚC khi xoá, trong cùng transaction (spec mục 6) - listener audit
     * chỉ đọc comboId dạng UUID thuần nên không cần bản ghi còn sống.
     */
    @Test
    void publishesComboCancelledBeforeDeletingTheRow() {
        var combo = stubbedCombo();

        useCase.execute(comboId, actorAccountId, actorBranchId);

        var published = ArgumentCaptor.forClass(BillingEvents.ComboCancelled.class);
        verify(events, times(1)).publishEvent(published.capture());
        assertThat(published.getValue().comboId()).isEqualTo(comboId);
        assertThat(published.getValue().actorAccountId()).isEqualTo(actorAccountId);
        assertThat(published.getValue().actorBranchId()).isEqualTo(actorBranchId);

        var order = inOrder(events, combos);
        order.verify(events).publishEvent(any(BillingEvents.ComboCancelled.class));
        order.verify(combos).delete(combo);
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=CancelComboTest`
Expected: FAIL — compile error `cannot find symbol: class CancelCombo`.

- [ ] **Step 3: Viết `CancelCombo`**

Tạo `backend/src/main/java/com/eduerp/modules/billing/usecase/CancelCombo.java`:

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboHasInvoicesException;
import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Huỷ một combo gộp nhầm. CHỈ cho phép khi chưa phát hành hoá đơn nào - {@code countByComboId} đếm
 * MỌI trạng thái, kể cả {@code CANCELLED} (Review Focus #6): một combo từng có chứng từ tài chính
 * gắn vào thì không còn là bản nháp, cùng nguyên tắc mà {@code CancelInvoice} áp dụng cho hoá đơn.
 *
 * <p>Huỷ là XOÁ CỨNG {@code Combo} (cascade xoá {@code ComboEnrollment}), không phải chuyển trạng
 * thái: {@code Combo} cố ý không có cột {@code status}, và việc xoá cứng chính là thứ cho phép
 * UNIQUE {@code combo_enrollments.enrollment_id} là unique đầy đủ thay vì partial (spec mục 4).
 *
 * <p>Dùng quyền {@code UPDATE_INVOICE}, KHÔNG dùng {@code Actions.DELETE} - toàn hệ thống chưa dùng
 * DELETE ở bất kỳ resource nào.
 */
@Service
public class CancelCombo {

    private final ComboRepository combos;
    private final InvoiceRepository invoices;
    private final ApplicationEventPublisher events;

    CancelCombo(ComboRepository combos, InvoiceRepository invoices, ApplicationEventPublisher events) {
        this.combos = combos;
        this.invoices = invoices;
        this.events = events;
    }

    @Transactional
    public void execute(UUID comboId, UUID actorAccountId, UUID actorBranchId) {
        var combo = combos.findById(comboId).orElseThrow(() -> new ComboNotFoundException(comboId));
        if (invoices.countByComboId(comboId) > 0) {
            throw new ComboHasInvoicesException(comboId);
        }
        // Phát TRƯỚC khi xoá: sau lệnh delete không còn gì để đọc, mà đây là dấu vết duy nhất cho
        // thấy combo này từng tồn tại. Cùng transaction nên nếu xoá hỏng thì event cũng không gửi.
        events.publishEvent(new BillingEvents.ComboCancelled(comboId, actorAccountId, actorBranchId));
        combos.delete(combo);
    }
}
```

- [ ] **Step 4: Chạy lại để xác nhận xanh**

Run: `cd backend && mvn -q test -Dtest=CancelComboTest`
Expected: PASS — 5 test.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/usecase/CancelCombo.java \
  backend/src/test/java/com/eduerp/modules/billing/usecase/CancelComboTest.java
git commit -m "feat(billing): allow cancelling a combo only before its first invoice"
```

---

### Task 10: `ListCombos` + `GetComboDetail` usecases

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/ListCombos.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/GetComboDetail.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/usecase/ComboReadUseCasesTest.java`

**Interfaces:**
- Consumes: `ComboRepository.search`/`findById` (Task 2); `InvoiceRepository.findAllByComboId` + `Invoice.forCombo` (Task 3); `ComboNotFoundException` (Task 4); `ComboResponse`/`ComboDetailResponse` (Task 6); `CreateCombo.toResponse(Combo)` và `CreateCombo.toEnrollmentResponse(ComboEnrollment)` (Task 7); `CreateInvoice.toResponse(Invoice)`; `com.eduerp.shared.PageResponse.of(Page)`.
- Produces:
  - `ListCombos.execute(Pageable pageable, UUID studentProfileId): PageResponse<ComboResponse>`
  - `GetComboDetail.execute(UUID comboId): ComboDetailResponse`

- [ ] **Step 1: Viết test đỏ**

Tạo `backend/src/test/java/com/eduerp/modules/billing/usecase/ComboReadUseCasesTest.java`:

```java
package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.dto.ComboEnrollmentResponse;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class ComboReadUseCasesTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2027, 1, 31);

    private final ComboRepository combos = mock(ComboRepository.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final ListCombos listCombos = new ListCombos(combos);
    private final GetComboDetail getComboDetail = new GetComboDetail(combos, invoices);

    private final UUID comboId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID mathsEnrollmentId = UUID.randomUUID();
    private final UUID englishEnrollmentId = UUID.randomUUID();
    private final UUID mathsCourseId = UUID.randomUUID();
    private final UUID englishCourseId = UUID.randomUUID();

    private Combo comboWithTwoCourses() {
        var combo = new Combo(studentProfileId, branchId, new BigDecimal("21000000"), new BigDecimal("15"),
                DUE_DATE, actorId);
        ReflectionTestUtils.setField(combo, "id", comboId);
        combo.addEnrollment(mathsEnrollmentId, mathsCourseId, new BigDecimal("12000000"));
        combo.addEnrollment(englishEnrollmentId, englishCourseId, new BigDecimal("9000000"));
        return combo;
    }

    @Test
    void listPassesTheOptionalStudentFilterStraightToTheRepositoryAndMapsThePage() {
        var pageable = PageRequest.of(0, 20);
        when(combos.search(studentProfileId, pageable))
                .thenReturn(new PageImpl<>(List.of(comboWithTwoCourses()), pageable, 1));

        var page = listCombos.execute(pageable, studentProfileId);

        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(comboId);
            assertThat(item.totalDiscountedAmount()).isEqualByComparingTo(new BigDecimal("17850000"));
            // courseCount trả sẵn để màn hình danh sách không phải tải danh sách con chỉ để đếm.
            assertThat(item.courseCount()).isEqualTo(2);
        });
    }

    @Test
    void listAcceptsANullStudentFilter() {
        var pageable = PageRequest.of(0, 20);
        when(combos.search(null, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(listCombos.execute(pageable, null).items()).isEmpty();
    }

    @Test
    void detailReturnsTheComboItsCoursesAndEveryInvoiceIncludingCancelledOnes() {
        var combo = comboWithTwoCourses();
        var paid = Invoice.forCombo(comboId, studentProfileId, branchId, 1, new BigDecimal("10000000"),
                DUE_DATE, actorId);
        var cancelled = Invoice.forCombo(comboId, studentProfileId, branchId, 2, new BigDecimal("7850000"),
                DUE_DATE, actorId);
        cancelled.cancel();
        when(combos.findById(comboId)).thenReturn(Optional.of(combo));
        when(invoices.findAllByComboId(comboId)).thenReturn(List.of(paid, cancelled));

        var detail = getComboDetail.execute(comboId);

        assertThat(detail.combo().id()).isEqualTo(comboId);
        assertThat(detail.combo().courseCount()).isEqualTo(2);
        // @OrderBy("originalTuitionFee DESC") trên Combo.enrollments quyết định thứ tự - không sort
        // lại ở usecase.
        assertThat(detail.enrollments()).extracting(ComboEnrollmentResponse::enrollmentId)
                .containsExactly(mathsEnrollmentId, englishEnrollmentId);
        assertThat(detail.enrollments()).extracting(ComboEnrollmentResponse::originalTuitionFee)
                .containsExactly(new BigDecimal("12000000"), new BigDecimal("9000000"));
        // Chi tiết combo hiển thị cả hoá đơn đã huỷ: kế toán cần thấy vì sao số đợt lại nhảy số.
        assertThat(detail.invoices()).hasSize(2);
        assertThat(detail.invoices().get(0).comboId()).isEqualTo(comboId);
        assertThat(detail.invoices().get(0).enrollmentId()).isNull();
    }

    @Test
    void detailRejectsAnUnknownCombo() {
        when(combos.findById(comboId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getComboDetail.execute(comboId))
                .isInstanceOf(ComboNotFoundException.class);
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=ComboReadUseCasesTest`
Expected: FAIL — compile error `cannot find symbol: class ListCombos`.

- [ ] **Step 3: Viết `ListCombos`**

Tạo `backend/src/main/java/com/eduerp/modules/billing/usecase/ListCombos.java`:

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.dto.ComboResponse;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.shared.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filter optional dồn vào query ở repository - usecase không rẽ nhánh nào (complexity 1).
 *
 * <p>{@code CreateCombo.toResponse} đọc {@code combo.getEnrollments().size()}, nên mỗi combo trong
 * trang cần danh sách con. {@code @BatchSize(50)} trên {@code Combo.enrollments} gộp chúng thành một
 * câu IN duy nhất thay vì N câu SELECT (mirror cách {@code ListClasses} xử lý lịch học).
 */
@Service
public class ListCombos {

    private final ComboRepository combos;

    ListCombos(ComboRepository combos) {
        this.combos = combos;
    }

    @Transactional(readOnly = true)
    public PageResponse<ComboResponse> execute(Pageable pageable, UUID studentProfileId) {
        return PageResponse.of(combos.search(studentProfileId, pageable).map(CreateCombo::toResponse));
    }
}
```

- [ ] **Step 4: Viết `GetComboDetail`**

Tạo `backend/src/main/java/com/eduerp/modules/billing/usecase/GetComboDetail.java`:

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.dto.ComboDetailResponse;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirror {@code GetInvoiceDetail}: tổng thể + các danh sách con, một query cho mỗi phần, không N+1. */
@Service
public class GetComboDetail {

    private final ComboRepository combos;
    private final InvoiceRepository invoices;

    GetComboDetail(ComboRepository combos, InvoiceRepository invoices) {
        this.combos = combos;
        this.invoices = invoices;
    }

    @Transactional(readOnly = true)
    public ComboDetailResponse execute(UUID comboId) {
        var combo = combos.findById(comboId).orElseThrow(() -> new ComboNotFoundException(comboId));
        // Thứ tự các khoá do @OrderBy trên Combo.enrollments quyết định - không sort lại ở đây.
        var members = combo.getEnrollments().stream().map(CreateCombo::toEnrollmentResponse).toList();
        // findAllByComboId KHÔNG lọc trạng thái: kế toán cần thấy cả đợt đã huỷ để hiểu vì sao số đợt
        // nhảy số (spec mục 6).
        var comboInvoices = invoices.findAllByComboId(comboId).stream()
                .map(CreateInvoice::toResponse).toList();
        return new ComboDetailResponse(CreateCombo.toResponse(combo), members, comboInvoices);
    }
}
```

- [ ] **Step 5: Chạy lại để xác nhận xanh**

Run: `cd backend && mvn -q test -Dtest=ComboReadUseCasesTest`
Expected: PASS — 4 test.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/usecase/ListCombos.java \
  backend/src/main/java/com/eduerp/modules/billing/usecase/GetComboDetail.java \
  backend/src/test/java/com/eduerp/modules/billing/usecase/ComboReadUseCasesTest.java
git commit -m "feat(billing): add combo list and combo detail read use cases"
```

---

### Task 11: CRUD bậc giảm giá — `ListComboDiscountTiers` / `CreateComboDiscountTier` / `UpdateComboDiscountTier`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/ListComboDiscountTiers.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/CreateComboDiscountTier.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/UpdateComboDiscountTier.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/usecase/ComboDiscountTierUseCasesTest.java`

**Interfaces:**
- Consumes: `ComboDiscountTierRepository` + `ComboDiscountTier` (Task 1); `ComboDiscountTierNotFoundException`/`ComboDiscountTierAlreadyExistsException` (Task 4); `ComboDiscountTierResponse`/`CreateComboDiscountTierRequest`/`UpdateComboDiscountTierRequest` (Task 6).
- Produces:
  - `ListComboDiscountTiers.execute(): List<ComboDiscountTierResponse>`
  - `ListComboDiscountTiers.toResponse(ComboDiscountTier tier): ComboDiscountTierResponse` (static, package-private)
  - `CreateComboDiscountTier.execute(CreateComboDiscountTierRequest request): ComboDiscountTierResponse`
  - `UpdateComboDiscountTier.execute(UUID tierId, UpdateComboDiscountTierRequest request): ComboDiscountTierResponse`

> **Không có `DeleteComboDiscountTier`** (spec mục 6): "xoá" một bậc là `active = false`, mirror `Course.active`. Ba usecase này cũng **không publish event audit** — mirror `CreateCourse`/`UpdateCourse`? Không: `CoursesEvents.CourseCreated` có event. Nhưng spec mục 9 chỉ yêu cầu audit cho `ComboCreated`/`ComboCancelled`, nên không suy đoán thêm event cho một bảng cấu hình giá — YAGNI, và thêm event nghĩa là thêm `AuditConstants.Actions` chưa ai yêu cầu.

- [ ] **Step 1: Viết test đỏ**

Tạo `backend/src/test/java/com/eduerp/modules/billing/usecase/ComboDiscountTierUseCasesTest.java`:

```java
package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.ComboDiscountTierAlreadyExistsException;
import com.eduerp.modules.billing.ComboDiscountTierNotFoundException;
import com.eduerp.modules.billing.dto.ComboDiscountTierResponse;
import com.eduerp.modules.billing.dto.CreateComboDiscountTierRequest;
import com.eduerp.modules.billing.dto.UpdateComboDiscountTierRequest;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ComboDiscountTierUseCasesTest {

    private final ComboDiscountTierRepository tiers = mock(ComboDiscountTierRepository.class);
    private final ListComboDiscountTiers listTiers = new ListComboDiscountTiers(tiers);
    private final CreateComboDiscountTier createTier = new CreateComboDiscountTier(tiers);
    private final UpdateComboDiscountTier updateTier = new UpdateComboDiscountTier(tiers);

    /** Màn hình cấu hình liệt kê CẢ bậc đã tắt, để admin bật lại được - không lọc active ở đây. */
    @Test
    void listReturnsEveryTierIncludingInactiveOnesInRepositoryOrder() {
        var twoCourses = new ComboDiscountTier(2, new BigDecimal("10"));
        var fiveCourses = new ComboDiscountTier(5, new BigDecimal("25"));
        fiveCourses.update(new BigDecimal("25"), false);
        when(tiers.findAllByOrderByMinCourseCountAsc()).thenReturn(List.of(twoCourses, fiveCourses));

        var response = listTiers.execute();

        assertThat(response).extracting(ComboDiscountTierResponse::minCourseCount).containsExactly(2, 5);
        assertThat(response).extracting(ComboDiscountTierResponse::active).containsExactly(true, false);
        assertThat(response.get(0).discountPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void createSavesANewTierAsActive() {
        when(tiers.saveAndFlush(any(ComboDiscountTier.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = createTier.execute(new CreateComboDiscountTierRequest(3, new BigDecimal("15")));

        assertThat(response.minCourseCount()).isEqualTo(3);
        assertThat(response.discountPercent()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(response.active()).isTrue();
    }

    /** min_course_count UNIQUE (V21): hai bậc cùng mốc khiến việc chọn bậc phụ thuộc thứ tự dòng -
     * phải là 409 có errorCode, không phải 500. */
    @Test
    void createTranslatesAUniqueViolationIntoComboDiscountTierAlreadyExists() {
        when(tiers.saveAndFlush(any(ComboDiscountTier.class)))
                .thenThrow(new DataIntegrityViolationException("combo_discount_tiers_min_course_count_key"));

        assertThatThrownBy(() -> createTier.execute(
                new CreateComboDiscountTierRequest(3, new BigDecimal("15"))))
                .isInstanceOf(ComboDiscountTierAlreadyExistsException.class)
                .hasMessageContaining("3");
    }

    @Test
    void updateChangesThePercentAndTheActiveFlagButNotTheThreshold() {
        var tier = new ComboDiscountTier(3, new BigDecimal("15"));
        var tierId = UUID.randomUUID();
        when(tiers.findById(tierId)).thenReturn(Optional.of(tier));

        var response = updateTier.execute(tierId,
                new UpdateComboDiscountTierRequest(new BigDecimal("18.5"), false));

        assertThat(response.discountPercent()).isEqualByComparingTo(new BigDecimal("18.50"));
        assertThat(response.active()).isFalse();
        assertThat(response.minCourseCount()).isEqualTo(3);
    }

    @Test
    void updateRejectsAnUnknownTier() {
        var tierId = UUID.randomUUID();
        when(tiers.findById(tierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateTier.execute(tierId,
                new UpdateComboDiscountTierRequest(new BigDecimal("18.5"), true)))
                .isInstanceOf(ComboDiscountTierNotFoundException.class);
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=ComboDiscountTierUseCasesTest`
Expected: FAIL — compile error `cannot find symbol: class ListComboDiscountTiers`.

- [ ] **Step 3: Viết `ListComboDiscountTiers`**

Tạo `backend/src/main/java/com/eduerp/modules/billing/usecase/ListComboDiscountTiers.java`:

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.dto.ComboDiscountTierResponse;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Không phân trang: số bậc giảm giá là một con số nhỏ do admin tự nhập (2 khoá, 3 khoá, 5 khoá...),
 * một trang là đủ và màn hình cấu hình cần thấy hết cùng lúc để so sánh các mốc.
 *
 * <p>Trả CẢ bậc đã tắt {@code active}: đó là cách duy nhất để admin bật lại một bậc đã "xoá".
 */
@Service
public class ListComboDiscountTiers {

    private final ComboDiscountTierRepository tiers;

    ListComboDiscountTiers(ComboDiscountTierRepository tiers) {
        this.tiers = tiers;
    }

    @Transactional(readOnly = true)
    public List<ComboDiscountTierResponse> execute() {
        return tiers.findAllByOrderByMinCourseCountAsc().stream()
                .map(ListComboDiscountTiers::toResponse).toList();
    }

    /** Dùng lại ở {@code CreateComboDiscountTier}/{@code UpdateComboDiscountTier} - một chỗ map duy nhất. */
    static ComboDiscountTierResponse toResponse(ComboDiscountTier tier) {
        return new ComboDiscountTierResponse(tier.getId(), tier.getMinCourseCount(),
                tier.getDiscountPercent(), tier.isActive());
    }
}
```

- [ ] **Step 4: Viết `CreateComboDiscountTier`**

Tạo `backend/src/main/java/com/eduerp/modules/billing/usecase/CreateComboDiscountTier.java`:

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.ComboDiscountTierAlreadyExistsException;
import com.eduerp.modules.billing.dto.ComboDiscountTierResponse;
import com.eduerp.modules.billing.dto.CreateComboDiscountTierRequest;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mốc trùng được chặn bởi UNIQUE {@code min_course_count} (V21) thay vì một lần đọc trước khi ghi -
 * đọc-rồi-ghi vẫn để hở race giữa hai admin, còn UNIQUE thì không. */
@Service
public class CreateComboDiscountTier {

    private final ComboDiscountTierRepository tiers;

    CreateComboDiscountTier(ComboDiscountTierRepository tiers) {
        this.tiers = tiers;
    }

    @Transactional
    public ComboDiscountTierResponse execute(CreateComboDiscountTierRequest request) {
        try {
            return ListComboDiscountTiers.toResponse(tiers.saveAndFlush(
                    new ComboDiscountTier(request.minCourseCount(), request.discountPercent())));
        } catch (DataIntegrityViolationException duplicateThreshold) {
            throw new ComboDiscountTierAlreadyExistsException(request.minCourseCount());
        }
    }
}
```

- [ ] **Step 5: Viết `UpdateComboDiscountTier`**

Tạo `backend/src/main/java/com/eduerp/modules/billing/usecase/UpdateComboDiscountTier.java`:

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.ComboDiscountTierNotFoundException;
import com.eduerp.modules.billing.dto.ComboDiscountTierResponse;
import com.eduerp.modules.billing.dto.UpdateComboDiscountTierRequest;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sửa % giảm và bật/tắt một bậc. Đổi {@code minCourseCount} KHÔNG được hỗ trợ: đó là danh tính của
 * bậc (cột UNIQUE), đổi nó là tạo một bậc khác.
 *
 * <p>Combo đã tạo không bị ảnh hưởng: {@code Combo.discountPercent} là snapshot lúc tạo (spec mục 4).
 */
@Service
public class UpdateComboDiscountTier {

    private final ComboDiscountTierRepository tiers;

    UpdateComboDiscountTier(ComboDiscountTierRepository tiers) {
        this.tiers = tiers;
    }

    @Transactional
    public ComboDiscountTierResponse execute(UUID tierId, UpdateComboDiscountTierRequest request) {
        var tier = tiers.findById(tierId).orElseThrow(() -> new ComboDiscountTierNotFoundException(tierId));
        tier.update(request.discountPercent(), request.active());
        return ListComboDiscountTiers.toResponse(tier);
    }
}
```

- [ ] **Step 6: Chạy lại để xác nhận xanh**

Run: `cd backend && mvn -q test -Dtest=ComboDiscountTierUseCasesTest`
Expected: PASS — 5 test.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/usecase/ListComboDiscountTiers.java \
  backend/src/main/java/com/eduerp/modules/billing/usecase/CreateComboDiscountTier.java \
  backend/src/main/java/com/eduerp/modules/billing/usecase/UpdateComboDiscountTier.java \
  backend/src/test/java/com/eduerp/modules/billing/usecase/ComboDiscountTierUseCasesTest.java
git commit -m "feat(billing): add combo discount tier configuration use cases"
```

---

### Task 12: `ComboAdminController` + IT (RBAC dùng lại quyền `INVOICE`)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/web/ComboAdminController.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/web/ComboAdminControllerIT.java`

**Interfaces:**
- Consumes: `ListCombos`/`GetComboDetail` (Task 10), `CreateCombo` (Task 7), `CreateComboInvoice` (Task 8), `CancelCombo` (Task 9), DTO (Task 6); `AccessConstants.AccessRules.{READ_INVOICE,CREATE_INVOICE,UPDATE_INVOICE}` (đã có, **không thêm hằng mới**); `com.eduerp.shared.AccountPrincipal.accountId()/homeBranchId()`.
- Produces: 5 endpoint HTTP
  - `GET /api/billing/combos?studentProfileId=&page=&size=` → `PageResponse<ComboResponse>` — `READ_INVOICE`
  - `POST /api/billing/combos` body `CreateComboRequest` → `ComboResponse` — `CREATE_INVOICE`
  - `GET /api/billing/combos/{comboId}` → `ComboDetailResponse` — `READ_INVOICE`
  - `POST /api/billing/combos/invoices` body `CreateComboInvoiceRequest` → `InvoiceResponse` — `CREATE_INVOICE`
  - `POST /api/billing/combos/{comboId}/cancel` → `void` — `UPDATE_INVOICE`

- [ ] **Step 1: Viết test đỏ**

Tạo `backend/src/test/java/com/eduerp/modules/billing/web/ComboAdminControllerIT.java`:

```java
package com.eduerp.modules.billing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.billing.dto.CreateComboInvoiceRequest;
import com.eduerp.modules.billing.dto.CreateComboRequest;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.enrollment.dto.CreateEnrollmentRequest;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ComboAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String COMBOS = "/api/billing/combos";
    private static final String COMBO_INVOICES = COMBOS + "/invoices";
    private static final BigDecimal MATHS_FEE = new BigDecimal("12000000");
    private static final BigDecimal ENGLISH_FEE = new BigDecimal("9000000");
    private static final LocalDate DUE_DATE = LocalDate.of(2027, 1, 31);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    BranchRepository branches;

    @Autowired
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    @Autowired
    StudentProfileRepository profiles;

    @Autowired
    ComboDiscountTierRepository tiers;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Cookie signIn(String email, String roleCode) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), roleCode);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    /** Một học viên, một chi nhánh, hai khoá - đúng hình dạng tối thiểu của một combo hợp lệ. Ghi
     * danh đi qua chính HTTP API của enrollment để test cũng chứng minh hai module ghép được. */
    private record ComboFixture(UUID studentProfileId, UUID branchId, String firstEnrollmentId,
            String secondEnrollmentId) {
    }

    private String enrol(Cookie admin, UUID studentProfileId, UUID classId) throws Exception {
        var result = mockMvc.perform(post("/api/enrollment/enrollments").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEnrollmentRequest(studentProfileId, classId))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private UUID newClassId(UUID branchId, BigDecimal tuitionFee) {
        var course = courses.save(new Course("CMBW-C-" + shortId(), "Khoá", null, 24, tuitionFee));
        var teacherId = accounts.save(new Account("cmb-web-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        return classes.save(new Class(course, "CMBW-K-" + shortId(), branchId, teacherId, 20)).getId();
    }

    private ComboFixture newTwoCourseFixture(Cookie admin) throws Exception {
        var branchId = branches.save(new Branch("CMBW-" + shortId(), "Chi nhánh", null)).getId();
        var studentAccountId = accounts
                .save(new Account("cmb-web-hv-" + shortId() + "@eduerp.local", "hash", "HV", null)).getId();
        var studentProfileId = profiles.save(new StudentProfile(studentAccountId, null, null, null)).getId();
        var maths = enrol(admin, studentProfileId, newClassId(branchId, MATHS_FEE));
        var english = enrol(admin, studentProfileId, newClassId(branchId, ENGLISH_FEE));
        return new ComboFixture(studentProfileId, branchId, maths, english);
    }

    /**
     * {@code min_course_count} là UNIQUE và mọi test trong class {@code @SpringBootTest} này dùng
     * CHUNG một database, nên seed lại cùng một mốc ở test thứ hai sẽ vỡ constraint - và nếu chỉ
     * "seed nếu chưa có" thì % giảm mà một test thấy lại phụ thuộc test nào chạy trước. Xoá sạch rồi
     * ghi đúng bộ bậc mà test này cần: mỗi test độc lập, không có thứ tự ngầm. Combo đã tạo ở test
     * trước không bị ảnh hưởng vì {@code Combo.discountPercent} là snapshot, không phải khoá ngoại.
     */
    private void resetTiersTo(int minCourseCount, String discountPercent) {
        tiers.deleteAll();
        tiers.saveAndFlush(new ComboDiscountTier(minCourseCount, new BigDecimal(discountPercent)));
    }

    private String createCombo(Cookie admin, ComboFixture fixture) throws Exception {
        var result = mockMvc.perform(post(COMBOS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboRequest(
                                fixture.studentProfileId(),
                                List.of(UUID.fromString(fixture.firstEnrollmentId()),
                                        UUID.fromString(fixture.secondEnrollmentId())),
                                DUE_DATE))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void createsAComboThenIssuesTwoInstallmentsAndRefusesAnythingBeyondTheDiscountedTotal()
            throws Exception {
        var admin = signIn("cmb-admin-1@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        resetTiersTo(2, "15");
        var fixture = newTwoCourseFixture(admin);

        var comboId = createCombo(admin, fixture);

        var detail = mockMvc.perform(get(COMBOS + "/" + comboId).cookie(admin)).andReturn();
        var comboNode = objectMapper.readTree(detail.getResponse().getContentAsString()).get("combo");
        assertThat(comboNode.get("totalOriginalAmount").asLong()).isEqualTo(21_000_000L);
        assertThat(comboNode.get("totalDiscountedAmount").asLong()).isEqualTo(17_850_000L);
        assertThat(comboNode.get("courseCount").asInt()).isEqualTo(2);

        assertThat(issueComboInvoice(admin, comboId, "10000000").getResponse().getStatus()).isEqualTo(200);
        assertThat(issueComboInvoice(admin, comboId, "7850000").getResponse().getStatus()).isEqualTo(200);

        var tooMuch = issueComboInvoice(admin, comboId, "1");
        assertThat(tooMuch.getResponse().getStatus()).isEqualTo(409);
        assertThat(tooMuch.getResponse().getContentAsString())
                .contains("BILLING_INVOICE_AMOUNT_EXCEEDS_TUITION");
    }

    /** Hoá đơn combo trả về comboId và KHÔNG có enrollmentId/courseId - hợp đồng với Zod ở frontend. */
    @Test
    void comboInvoicesCarryTheComboIdAndNoEnrollmentOrCourse() throws Exception {
        var admin = signIn("cmb-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        resetTiersTo(2, "10");
        var comboId = createCombo(admin, newTwoCourseFixture(admin));

        var body = issueComboInvoice(admin, comboId, "1000000").getResponse().getContentAsString();

        var invoice = objectMapper.readTree(body);
        assertThat(invoice.get("comboId").asText()).isEqualTo(comboId);
        assertThat(invoice.get("enrollmentId").isNull()).isTrue();
        assertThat(invoice.get("courseId").isNull()).isTrue();
        assertThat(invoice.get("installmentNumber").asInt()).isEqualTo(1);
    }

    /** Review Focus #6 qua HTTP: huỷ được khi chưa có hoá đơn, bị chặn 409 sau khi có hoá đơn. */
    @Test
    void cancelsAComboBeforeItsFirstInvoiceAndRefusesAfterwards() throws Exception {
        var admin = signIn("cmb-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        resetTiersTo(2, "10");
        var fixture = newTwoCourseFixture(admin);
        var disposable = createCombo(admin, fixture);

        var cancel = mockMvc.perform(post(COMBOS + "/" + disposable + "/cancel").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(cancel.getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(get(COMBOS + "/" + disposable).cookie(admin)).andReturn()
                .getResponse().getStatus()).isEqualTo(404);

        // Xoá cứng giải phóng enrollment_id, nên gộp lại chính hai ghi danh đó phải được.
        var rebuilt = createCombo(admin, fixture);
        issueComboInvoice(admin, rebuilt, "1000000");

        var refused = mockMvc.perform(post(COMBOS + "/" + rebuilt + "/cancel").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(refused.getResponse().getContentAsString()).contains("BILLING_COMBO_HAS_INVOICES");
    }

    /** Review Focus #1 qua HTTP: hai học viên khác nhau → 400, không phải 500 hay 200. */
    @Test
    void refusesAComboMixingTwoStudents() throws Exception {
        var admin = signIn("cmb-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        resetTiersTo(2, "10");
        var mine = newTwoCourseFixture(admin);
        var someoneElse = newTwoCourseFixture(admin);

        var result = mockMvc.perform(post(COMBOS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboRequest(
                                mine.studentProfileId(),
                                List.of(UUID.fromString(mine.firstEnrollmentId()),
                                        UUID.fromString(someoneElse.firstEnrollmentId())),
                                DUE_DATE))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("BILLING_COMBO_STUDENT_MISMATCH");
    }

    /** Review Focus #4 qua HTTP: chưa cấu hình bậc nào → 409, không âm thầm giảm 0%. */
    @Test
    void refusesAComboWhenNoDiscountTierIsConfigured() throws Exception {
        var admin = signIn("cmb-admin-5@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        // Rỗng hẳn: test này chứng minh "chưa cấu hình bậc nào" bị chặn, nên không được phụ thuộc
        // vào việc test nào chạy trước có seed bậc hay không.
        tiers.deleteAll();
        var fixture = newTwoCourseFixture(admin);

        var result = mockMvc.perform(post(COMBOS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboRequest(
                                fixture.studentProfileId(),
                                List.of(UUID.fromString(fixture.firstEnrollmentId()),
                                        UUID.fromString(fixture.secondEnrollmentId())),
                                DUE_DATE))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString())
                .contains("BILLING_COMBO_DISCOUNT_TIER_NOT_CONFIGURED");
    }

    /** Review Focus #3 qua HTTP: gộp lại một ghi danh đã nằm trong combo khác → 409 có errorCode. */
    @Test
    void refusesToReuseAnEnrollmentThatAlreadyBelongsToAnotherCombo() throws Exception {
        var admin = signIn("cmb-admin-6@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        resetTiersTo(2, "10");
        var fixture = newTwoCourseFixture(admin);
        createCombo(admin, fixture);
        // Khoá thứ ba, cùng học viên cùng chi nhánh - hợp lệ về mọi mặt TRỪ việc ghi danh thứ nhất
        // đã nằm trong combo vừa tạo.
        var thirdEnrollmentId = enrol(admin, fixture.studentProfileId(),
                newClassId(fixture.branchId(), MATHS_FEE));

        var result = mockMvc.perform(post(COMBOS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboRequest(
                                fixture.studentProfileId(),
                                List.of(UUID.fromString(fixture.firstEnrollmentId()),
                                        UUID.fromString(thirdEnrollmentId)),
                                DUE_DATE))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString())
                .contains("BILLING_ENROLLMENT_ALREADY_IN_COMBO");
    }

    @Test
    void filtersTheComboListByStudent() throws Exception {
        var admin = signIn("cmb-admin-7@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        resetTiersTo(2, "10");
        var fixture = newTwoCourseFixture(admin);
        createCombo(admin, fixture);

        var filtered = mockMvc.perform(get(COMBOS).cookie(admin)
                .param("studentProfileId", fixture.studentProfileId().toString())).andReturn();

        assertThat(filtered.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(filtered.getResponse().getContentAsString()).get("totalItems").asInt())
                .isEqualTo(1);
    }

    /** RBAC dùng LẠI quyền INVOICE (spec mục 8): không có quyền đó thì mọi endpoint combo đều 403. */
    @Test
    void refusesEveryComboEndpointWithoutInvoicePermission() throws Exception {
        var admin = signIn("cmb-admin-8@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var outsider = signIn("cmb-outsider-1@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        resetTiersTo(2, "10");
        var comboId = createCombo(admin, newTwoCourseFixture(admin));

        assertThat(mockMvc.perform(get(COMBOS).cookie(outsider)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(get(COMBOS + "/" + comboId).cookie(outsider)).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(post(COMBOS + "/" + comboId + "/cancel").cookie(outsider).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(post(COMBO_INVOICES).cookie(outsider).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboInvoiceRequest(
                                UUID.fromString(comboId), new BigDecimal("1000000"), DUE_DATE))))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void requiresAuthentication() throws Exception {
        assertThat(mockMvc.perform(get(COMBOS)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    private org.springframework.test.web.servlet.MvcResult issueComboInvoice(Cookie admin, String comboId,
            String amount) throws Exception {
        return mockMvc.perform(post(COMBO_INVOICES).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboInvoiceRequest(
                                UUID.fromString(comboId), new BigDecimal(amount), DUE_DATE))))
                .andReturn();
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=ComboAdminControllerIT`
Expected: FAIL — 404 trên mọi endpoint `/api/billing/combos` (controller chưa tồn tại).

- [ ] **Step 3: Viết `ComboAdminController`**

Tạo `backend/src/main/java/com/eduerp/modules/billing/web/ComboAdminController.java`:

```java
package com.eduerp.modules.billing.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.dto.ComboDetailResponse;
import com.eduerp.modules.billing.dto.ComboResponse;
import com.eduerp.modules.billing.dto.CreateComboInvoiceRequest;
import com.eduerp.modules.billing.dto.CreateComboRequest;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.usecase.CancelCombo;
import com.eduerp.modules.billing.usecase.CreateCombo;
import com.eduerp.modules.billing.usecase.CreateComboInvoice;
import com.eduerp.modules.billing.usecase.GetComboDetail;
import com.eduerp.modules.billing.usecase.ListCombos;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản trị combo học phí - mirror {@code InvoiceAdminController} 1:1 về cấu trúc.
 *
 * <p>KHÔNG có resource RBAC riêng (spec mục 8): combo là một hình thức thu học phí, nên dùng lại
 * đúng {@code CREATE_INVOICE} (tạo combo, phát hành đợt thu), {@code READ_INVOICE} (xem) và
 * {@code UPDATE_INVOICE} (huỷ combo - chuyển state, hệ thống chưa dùng {@code Actions.DELETE} ở đâu).
 *
 * <p>Đợt thu của combo nằm ở {@code POST /invoices} với {@code comboId} TRONG BODY, mirror đúng
 * {@code POST /api/billing/invoices} vốn nhận {@code enrollmentId} trong body - id của đơn vị thu là
 * một phần của phiếu thu, không phải một đoạn đường dẫn.
 */
@RestController
@RequestMapping("/api/billing/combos")
class ComboAdminController {

    private final ListCombos listCombos;
    private final CreateCombo createCombo;
    private final GetComboDetail getComboDetail;
    private final CreateComboInvoice createComboInvoice;
    private final CancelCombo cancelCombo;

    ComboAdminController(ListCombos listCombos, CreateCombo createCombo, GetComboDetail getComboDetail,
            CreateComboInvoice createComboInvoice, CancelCombo cancelCombo) {
        this.listCombos = listCombos;
        this.createCombo = createCombo;
        this.getComboDetail = getComboDetail;
        this.createComboInvoice = createComboInvoice;
        this.cancelCombo = cancelCombo;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_INVOICE)
    PageResponse<ComboResponse> list(@PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) UUID studentProfileId) {
        return listCombos.execute(pageable, studentProfileId);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_INVOICE)
    ComboResponse create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreateComboRequest request) {
        return createCombo.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    /** Đặt TRƯỚC {@code /{comboId}} không cần thiết (khác HTTP method), nhưng giữ cạnh nhóm tạo để
     * người đọc thấy đây là endpoint ghi của combo. */
    @PostMapping("/invoices")
    @PreAuthorize(AccessConstants.AccessRules.CREATE_INVOICE)
    InvoiceResponse createInvoice(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreateComboInvoiceRequest request) {
        return createComboInvoice.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @GetMapping("/{comboId}")
    @PreAuthorize(AccessConstants.AccessRules.READ_INVOICE)
    ComboDetailResponse get(@PathVariable UUID comboId) {
        return getComboDetail.execute(comboId);
    }

    @PostMapping("/{comboId}/cancel")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_INVOICE)
    void cancel(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID comboId) {
        cancelCombo.execute(comboId, principal.accountId(), principal.homeBranchId());
    }
}
```

- [ ] **Step 4: Chạy lại để xác nhận xanh**

Run: `cd backend && mvn -q test -Dtest=ComboAdminControllerIT`
Expected: PASS — 9 test.

- [ ] **Step 5: Xác nhận không thêm permission nào vào DB**

Run: `cd backend && git status --short backend/src/main/resources/db/migration/ && grep -rn "INVOICE" src/main/java/com/eduerp/modules/access/AccessConstants.java`
Expected: không có migration seed RBAC mới; `AccessConstants` vẫn chỉ có 3 hằng `CREATE_INVOICE`/`READ_INVOICE`/`UPDATE_INVOICE` như trước, không thêm dòng nào.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/web/ComboAdminController.java \
  backend/src/test/java/com/eduerp/modules/billing/web/ComboAdminControllerIT.java
git commit -m "feat(billing): expose combo admin endpoints reusing invoice permissions"
```

---

### Task 13: `ComboDiscountTierAdminController` + IT

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/web/ComboDiscountTierAdminController.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/web/ComboDiscountTierAdminControllerIT.java`

**Interfaces:**
- Consumes: `ListComboDiscountTiers`/`CreateComboDiscountTier`/`UpdateComboDiscountTier` (Task 11), DTO (Task 6), `AccessConstants.AccessRules` (đã có).
- Produces: 3 endpoint
  - `GET /api/billing/combo-discount-tiers` → `List<ComboDiscountTierResponse>` — `READ_INVOICE`
  - `POST /api/billing/combo-discount-tiers` body `CreateComboDiscountTierRequest` → `ComboDiscountTierResponse` — `CREATE_INVOICE`
  - `PATCH /api/billing/combo-discount-tiers/{tierId}` body `UpdateComboDiscountTierRequest` → `ComboDiscountTierResponse` — `UPDATE_INVOICE`

- [ ] **Step 1: Viết test đỏ**

Tạo `backend/src/test/java/com/eduerp/modules/billing/web/ComboDiscountTierAdminControllerIT.java`:

```java
package com.eduerp.modules.billing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.billing.dto.CreateComboDiscountTierRequest;
import com.eduerp.modules.billing.dto.UpdateComboDiscountTierRequest;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ComboDiscountTierAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String TIERS = "/api/billing/combo-discount-tiers";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie signIn(String email, String roleCode) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), roleCode);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    private String createTier(Cookie admin, int minCourseCount, String discountPercent) throws Exception {
        var result = mockMvc.perform(post(TIERS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboDiscountTierRequest(
                                minCourseCount, new BigDecimal(discountPercent)))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void createsATierThenListsIt() throws Exception {
        var admin = signIn("tier-admin-1@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        createTier(admin, 2, "10");

        var list = mockMvc.perform(get(TIERS).cookie(admin)).andReturn();
        assertThat(list.getResponse().getStatus()).isEqualTo(200);
        var tiers = objectMapper.readTree(list.getResponse().getContentAsString());
        assertThat(tiers.isArray()).isTrue();
        assertThat(tiers).anySatisfy(tier -> {
            assertThat(tier.get("minCourseCount").asInt()).isEqualTo(2);
            assertThat(tier.get("active").asBoolean()).isTrue();
        });
    }

    /** "Xoá" một bậc = PATCH active=false. Không có endpoint DELETE nào trên controller này. */
    @Test
    void retiresATierByTurningItInactiveInsteadOfDeletingIt() throws Exception {
        var admin = signIn("tier-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var tierId = createTier(admin, 4, "20");

        var patched = mockMvc.perform(patch(TIERS + "/" + tierId).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateComboDiscountTierRequest(
                                new BigDecimal("22.50"), false))))
                .andReturn();

        assertThat(patched.getResponse().getStatus()).isEqualTo(200);
        var body = objectMapper.readTree(patched.getResponse().getContentAsString());
        assertThat(body.get("active").asBoolean()).isFalse();
        assertThat(body.get("discountPercent").asDouble()).isEqualTo(22.5);
        assertThat(body.get("minCourseCount").asInt()).isEqualTo(4);
    }

    @Test
    void refusesASecondTierForTheSameThreshold() throws Exception {
        var admin = signIn("tier-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        createTier(admin, 6, "30");

        var duplicate = mockMvc.perform(post(TIERS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboDiscountTierRequest(
                                6, new BigDecimal("35")))))
                .andReturn();

        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(duplicate.getResponse().getContentAsString())
                .contains("BILLING_COMBO_DISCOUNT_TIER_ALREADY_EXISTS");
    }

    @Test
    void refusesAnUnknownTierOnUpdate() throws Exception {
        var admin = signIn("tier-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(patch(TIERS + "/" + UUID.randomUUID()).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateComboDiscountTierRequest(
                                new BigDecimal("10"), true))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString())
                .contains("BILLING_COMBO_DISCOUNT_TIER_NOT_FOUND");
    }

    /** minCourseCount < 2 vô nghĩa: combo tối thiểu 2 khoá, bậc mốc 1 sẽ không bao giờ được dùng. */
    @Test
    void refusesATierThresholdBelowTheMinimumComboSize() throws Exception {
        var admin = signIn("tier-admin-5@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(post(TIERS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboDiscountTierRequest(
                                1, new BigDecimal("5")))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void refusesAPercentAboveOneHundred() throws Exception {
        var admin = signIn("tier-admin-6@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(post(TIERS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboDiscountTierRequest(
                                2, new BigDecimal("100.01")))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    /** Cấu hình giá dùng chung nhóm quyền với người quản lý học phí (spec mục 8). */
    @Test
    void refusesEveryTierEndpointWithoutInvoicePermission() throws Exception {
        var admin = signIn("tier-admin-7@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var outsider = signIn("tier-outsider-1@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var tierId = createTier(admin, 7, "35");

        assertThat(mockMvc.perform(get(TIERS).cookie(outsider)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(patch(TIERS + "/" + tierId).cookie(outsider).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateComboDiscountTierRequest(
                                new BigDecimal("10"), true))))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void requiresAuthentication() throws Exception {
        assertThat(mockMvc.perform(get(TIERS)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=ComboDiscountTierAdminControllerIT`
Expected: FAIL — 404 trên `/api/billing/combo-discount-tiers`.

- [ ] **Step 3: Viết `ComboDiscountTierAdminController`**

Tạo `backend/src/main/java/com/eduerp/modules/billing/web/ComboDiscountTierAdminController.java`:

```java
package com.eduerp.modules.billing.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.dto.ComboDiscountTierResponse;
import com.eduerp.modules.billing.dto.CreateComboDiscountTierRequest;
import com.eduerp.modules.billing.dto.UpdateComboDiscountTierRequest;
import com.eduerp.modules.billing.usecase.CreateComboDiscountTier;
import com.eduerp.modules.billing.usecase.ListComboDiscountTiers;
import com.eduerp.modules.billing.usecase.UpdateComboDiscountTier;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cấu hình bậc giảm giá combo - mirror {@code CourseAdminController} về cấu trúc (PATCH để sửa,
 * không có DELETE: tắt {@code active} là cách "xoá" một bậc).
 *
 * <p>Dùng lại quyền {@code INVOICE} (spec mục 8): đây là cấu hình giá, cùng nhóm quyền với người
 * quản lý học phí - không cần một resource RBAC tách riêng cho một bảng cấu hình nhỏ.
 *
 * <p>Không phân trang và không nhận {@code Pageable}: số bậc là một con số nhỏ do admin tự nhập, màn
 * hình cấu hình cần thấy hết cùng lúc để so sánh các mốc.
 */
@RestController
@RequestMapping("/api/billing/combo-discount-tiers")
class ComboDiscountTierAdminController {

    private final ListComboDiscountTiers listComboDiscountTiers;
    private final CreateComboDiscountTier createComboDiscountTier;
    private final UpdateComboDiscountTier updateComboDiscountTier;

    ComboDiscountTierAdminController(ListComboDiscountTiers listComboDiscountTiers,
            CreateComboDiscountTier createComboDiscountTier,
            UpdateComboDiscountTier updateComboDiscountTier) {
        this.listComboDiscountTiers = listComboDiscountTiers;
        this.createComboDiscountTier = createComboDiscountTier;
        this.updateComboDiscountTier = updateComboDiscountTier;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_INVOICE)
    List<ComboDiscountTierResponse> list() {
        return listComboDiscountTiers.execute();
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_INVOICE)
    ComboDiscountTierResponse create(@Valid @RequestBody CreateComboDiscountTierRequest request) {
        return createComboDiscountTier.execute(request);
    }

    @PatchMapping("/{tierId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_INVOICE)
    ComboDiscountTierResponse update(@PathVariable UUID tierId,
            @Valid @RequestBody UpdateComboDiscountTierRequest request) {
        return updateComboDiscountTier.execute(tierId, request);
    }
}
```

- [ ] **Step 4: Chạy lại để xác nhận xanh**

Run: `cd backend && mvn -q test -Dtest=ComboDiscountTierAdminControllerIT`
Expected: PASS — 8 test.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/web/ComboDiscountTierAdminController.java \
  backend/src/test/java/com/eduerp/modules/billing/web/ComboDiscountTierAdminControllerIT.java
git commit -m "feat(billing): expose combo discount tier configuration endpoints"
```

---

### Task 14: Audit `COMBO_CREATE` / `COMBO_CANCEL`

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/audit/AuditConstants.java`
- Modify: `backend/src/main/java/com/eduerp/modules/audit/internal/listener/AuditEventListeners.java`
- Test: `backend/src/test/java/com/eduerp/modules/audit/internal/listener/AuditEventListenersTest.java`

**Interfaces:**
- Consumes: `BillingEvents.ComboCreated`/`ComboCancelled` (Task 5); `AuditLog(UUID actorAccountId, String action, String entityType, String entityId, UUID branchId)` (đã có).
- Produces: `AuditConstants.Actions.COMBO_CREATE = "COMBO_CREATE"`, `AuditConstants.Actions.COMBO_CANCEL = "COMBO_CANCEL"`, `AuditConstants.EntityTypes.COMBO = "COMBO"`, hai method `AuditEventListeners.on(BillingEvents.ComboCreated)` / `on(BillingEvents.ComboCancelled)`.

> `AuditEventListenersTest` có thể chưa tồn tại trong repo. Nếu `ls backend/src/test/java/com/eduerp/modules/audit/internal/listener/` không có file đó, tạo mới đúng như Step 1; nếu đã có, chỉ thêm 2 test vào class sẵn có (và bỏ phần `class`/import trùng).

- [ ] **Step 1: Viết test đỏ**

Tạo (hoặc bổ sung) `backend/src/test/java/com/eduerp/modules/audit/internal/listener/AuditEventListenersTest.java`:

```java
package com.eduerp.modules.audit.internal.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.internal.model.AuditLog;
import com.eduerp.modules.audit.internal.repository.AuditLogRepository;
import com.eduerp.modules.billing.BillingEvents;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditEventListenersTest {

    private final AuditLogRepository auditLogs = mock(AuditLogRepository.class);
    private final AuditEventListeners listeners = new AuditEventListeners(auditLogs);

    private final UUID comboId = UUID.randomUUID();
    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();

    private AuditLog captureSavedLog() {
        var saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogs).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void logsComboCreationAgainstTheComboEntityType() {
        listeners.on(new BillingEvents.ComboCreated(comboId, actorAccountId, actorBranchId));

        var log = captureSavedLog();
        assertThat(log.getAction()).isEqualTo(AuditConstants.Actions.COMBO_CREATE);
        assertThat(log.getEntityType()).isEqualTo(AuditConstants.EntityTypes.COMBO);
        assertThat(log.getEntityId()).isEqualTo(comboId.toString());
        assertThat(log.getActorAccountId()).isEqualTo(actorAccountId);
        assertThat(log.getBranchId()).isEqualTo(actorBranchId);
    }

    /** Huỷ combo là XOÁ CỨNG - dòng audit này là dấu vết duy nhất còn lại cho thấy combo từng tồn
     * tại, nên nó phải mang đúng comboId đã bị xoá. */
    @Test
    void logsComboCancellationWithTheIdOfTheDeletedCombo() {
        listeners.on(new BillingEvents.ComboCancelled(comboId, actorAccountId, actorBranchId));

        var log = captureSavedLog();
        assertThat(log.getAction()).isEqualTo(AuditConstants.Actions.COMBO_CANCEL);
        assertThat(log.getEntityType()).isEqualTo(AuditConstants.EntityTypes.COMBO);
        assertThat(log.getEntityId()).isEqualTo(comboId.toString());
    }
}
```

> Nếu getter của `AuditLog` có tên khác (`getAction`/`getEntityType`/`getEntityId`/`getActorAccountId`/`getBranchId`), chạy `grep -n "public .* get" backend/src/main/java/com/eduerp/modules/audit/internal/model/AuditLog.java` và dùng đúng tên trả về; `AuditEventListeners` là package-private nên test phải nằm đúng package trên.

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=AuditEventListenersTest`
Expected: FAIL — compile error `cannot find symbol: variable COMBO_CREATE` và `no suitable method found for on(BillingEvents.ComboCreated)`.

- [ ] **Step 3: Thêm hằng vào `AuditConstants`**

Trong `backend/src/main/java/com/eduerp/modules/audit/AuditConstants.java`, thêm vào cuối `Actions` (sau `INVOICE_CANCEL`):

```java
        public static final String COMBO_CREATE = "COMBO_CREATE";
        public static final String COMBO_CANCEL = "COMBO_CANCEL";
```

và vào cuối `EntityTypes` (sau `INVOICE`):

```java
        public static final String COMBO = "COMBO";
```

- [ ] **Step 4: Thêm hai listener**

Trong `backend/src/main/java/com/eduerp/modules/audit/internal/listener/AuditEventListeners.java`, thêm sau method `on(BillingEvents.InvoiceCancelled event)`:

```java
    /** Gộp nhiều khoá thành một gói giảm giá là một quyết định về tiền - mirror 1:1 InvoiceCreated. */
    @ApplicationModuleListener
    void on(BillingEvents.ComboCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.COMBO_CREATE,
                AuditConstants.EntityTypes.COMBO, event.comboId().toString(), event.actorBranchId()));
    }

    /** Huỷ combo là xoá cứng bản ghi, nên dòng log này là dấu vết DUY NHẤT còn lại cho thấy combo
     * từng tồn tại - khác InvoiceCancelled, nơi hoá đơn vẫn còn đó ở trạng thái CANCELLED. */
    @ApplicationModuleListener
    void on(BillingEvents.ComboCancelled event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.COMBO_CANCEL,
                AuditConstants.EntityTypes.COMBO, event.comboId().toString(), event.actorBranchId()));
    }
```

- [ ] **Step 5: Chạy lại để xác nhận xanh**

Run: `cd backend && mvn -q test -Dtest=AuditEventListenersTest`
Expected: PASS.

- [ ] **Step 6: Xác nhận ranh giới module vẫn hợp lệ**

Run: `cd backend && mvn -q test -Dtest=ModularityTests`
Expected: PASS — `audit → billing` là chiều phụ thuộc đã tồn tại (4 event billing khác đã được listen), nên không có vi phạm mới; danh sách 19 module **không đổi**.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/audit/AuditConstants.java \
  backend/src/main/java/com/eduerp/modules/audit/internal/listener/AuditEventListeners.java \
  backend/src/test/java/com/eduerp/modules/audit/internal/listener/AuditEventListenersTest.java
git commit -m "feat(audit): log combo creation and cancellation"
```

---

### Task 15: Filter `status` cho `ListEnrollments` (thay đổi nhỏ ở `modules.enrollment`)

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/enrollment/internal/repository/EnrollmentRepository.java`
- Modify: `backend/src/main/java/com/eduerp/modules/enrollment/usecase/ListEnrollments.java`
- Modify: `backend/src/main/java/com/eduerp/modules/enrollment/web/EnrollmentAdminController.java`
- Test: `backend/src/test/java/com/eduerp/modules/enrollment/usecase/ListEnrollmentsTest.java`
- Test: `backend/src/test/java/com/eduerp/modules/enrollment/internal/repository/EnrollmentRepositoryIT.java`
- Test: `backend/src/test/java/com/eduerp/modules/enrollment/web/EnrollmentAdminControllerIT.java`

**Interfaces:**
- Consumes: `EnrollmentConstants.EnrollmentStatus` (đã có).
- Produces:
  - `EnrollmentRepository.search(UUID studentProfileId, UUID classId, EnrollmentConstants.EnrollmentStatus status, Pageable pageable): Page<Enrollment>` — **thêm tham số thứ 3**
  - `ListEnrollments.execute(Pageable pageable, UUID studentProfileId, UUID classId, EnrollmentConstants.EnrollmentStatus status): PageResponse<EnrollmentResponse>` — **thêm tham số thứ 4**
  - `GET /api/enrollment/enrollments?status=ACTIVE` (param optional)

> **`EnrollmentManagement` không đổi** (Global Constraint) — đây là filter thuần trên đường đọc danh sách, không thêm method facade nào. Hai test cũ trong `ListEnrollmentsTest` gọi `execute` với 3 tham số nên **sẽ compile fail**: đó là bước đỏ có thật của task này, và Step 4 cập nhật chúng. Hành vi khi `status` để trống không đổi.

- [ ] **Step 1: Viết test đỏ ở tầng repository**

Thêm vào `backend/src/test/java/com/eduerp/modules/enrollment/internal/repository/EnrollmentRepositoryIT.java`, ngay sau test `searchFiltersByEveryCombinationOfStudentAndClass`:

```java
    /** Màn hình "Tạo combo" chỉ được chọn ghi danh ĐANG HỌC, nên filter này phải lọc đúng theo
     * status mà không đổi hành vi khi để trống (spec mục 7). */
    @Test
    void searchFiltersByOptionalStatus() {
        var studentProfileId = newStudentProfileId();
        var active = enrollments.saveAndFlush(newEnrollment(studentProfileId));
        var withdrawn = enrollments.saveAndFlush(newEnrollment(newStudentProfileId()));
        withdrawn.withdraw();
        enrollments.saveAndFlush(withdrawn);
        var pageable = PageRequest.of(0, 20);

        assertThat(enrollments.search(null, null, null, pageable).getTotalElements()).isEqualTo(2);
        assertThat(enrollments.search(null, null, EnrollmentConstants.EnrollmentStatus.ACTIVE, pageable)
                .getContent()).extracting(Enrollment::getId).containsExactly(active.getId());
        assertThat(enrollments.search(null, null, EnrollmentConstants.EnrollmentStatus.WITHDRAWN, pageable)
                .getTotalElements()).isEqualTo(1);
        assertThat(enrollments.search(studentProfileId, classId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE, pageable).getTotalElements()).isEqualTo(1);
        assertThat(enrollments.search(studentProfileId, classId,
                EnrollmentConstants.EnrollmentStatus.COMPLETED, pageable).getTotalElements()).isZero();
    }
```

Và sửa 5 lời gọi `enrollments.search(...)` trong test `searchFiltersByEveryCombinationOfStudentAndClass` để thêm `null` làm tham số thứ 3:

```java
        assertThat(enrollments.search(null, null, null, pageable).getTotalElements()).isEqualTo(2);
        assertThat(enrollments.search(studentProfileId, null, null, pageable).getTotalElements()).isEqualTo(1);
        assertThat(enrollments.search(null, classId, null, pageable).getTotalElements()).isEqualTo(2);
        assertThat(enrollments.search(studentProfileId, classId, null, pageable).getTotalElements()).isEqualTo(1);
        assertThat(enrollments.search(studentProfileId, UUID.randomUUID(), null, pageable).getTotalElements())
                .isZero();
```

- [ ] **Step 2: Chạy test để xác nhận đỏ**

Run: `cd backend && mvn -q test -Dtest=EnrollmentRepositoryIT`
Expected: FAIL — compile error `method search cannot be applied to given types` (4 tham số vs 3).

- [ ] **Step 3: Thêm filter vào `EnrollmentRepository.search`**

Trong `backend/src/main/java/com/eduerp/modules/enrollment/internal/repository/EnrollmentRepository.java`, thay javadoc + method `search`:

```java
    /**
     * Ba filter đều optional (spec mục 4 {@code ListEnrollments} + spec 3.1 mục 7). Một query với
     * {@code :p IS NULL} thay vì tám method {@code findAllBy...} - giữ usecase ở complexity 1.
     * {@code EnrollmentRepositoryIT.searchFiltersByEveryCombination...} và
     * {@code ...searchFiltersByOptionalStatus} phủ mọi tổ hợp nên nếu Hibernate/Postgres không suy
     * được kiểu tham số null thì test đỏ ngay, không lọt ra production.
     */
    @Query("""
            SELECT e FROM Enrollment e
            WHERE (:studentProfileId IS NULL OR e.studentProfileId = :studentProfileId)
              AND (:classId IS NULL OR e.classId = :classId)
              AND (:status IS NULL OR e.status = :status)
            """)
    Page<Enrollment> search(@Param("studentProfileId") UUID studentProfileId, @Param("classId") UUID classId,
            @Param("status") EnrollmentConstants.EnrollmentStatus status, Pageable pageable);
```

- [ ] **Step 4: Cập nhật `ListEnrollments` + hai test cũ của nó**

Trong `backend/src/main/java/com/eduerp/modules/enrollment/usecase/ListEnrollments.java`, thay javadoc class + method `execute`:

```java
/** Ba filter optional dồn vào một query ở repository - usecase không rẽ nhánh nào (complexity 1). */
```

```java
    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> execute(Pageable pageable, UUID studentProfileId, UUID classId,
            EnrollmentConstants.EnrollmentStatus status) {
        return PageResponse.of(enrollments.search(studentProfileId, classId, status, pageable)
                .map(CreateEnrollment::toResponse));
    }
```

Thêm import `com.eduerp.modules.enrollment.EnrollmentConstants;`.

Trong `backend/src/test/java/com/eduerp/modules/enrollment/usecase/ListEnrollmentsTest.java`, thêm import `com.eduerp.modules.enrollment.EnrollmentConstants;` rồi cập nhật hai test cũ và thêm một test mới:

```java
    @Test
    void passesAllThreeOptionalFiltersStraightToTheRepositoryAndMapsThePage() {
        var studentProfileId = UUID.randomUUID();
        var classId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 20);
        var enrollment = new Enrollment(studentProfileId, classId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());
        when(enrollments.search(studentProfileId, classId, EnrollmentConstants.EnrollmentStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(enrollment), pageable, 1));

        var page = useCase.execute(pageable, studentProfileId, classId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);

        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.items()).singleElement()
                .satisfies(item -> assertThat(item.studentProfileId()).isEqualTo(studentProfileId));
    }

    @Test
    void acceptsNullFiltersForAnUnfilteredList() {
        var pageable = PageRequest.of(0, 20);
        when(enrollments.search(null, null, null, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(useCase.execute(pageable, null, null, null).items()).isEmpty();
    }

    /** Spec mục 7: thêm filter status KHÔNG đổi hành vi khi để trống - status null vẫn là "mọi
     * trạng thái", đúng như trước khi có tham số này. */
    @Test
    void passesOnlyTheStatusFilterWhenTheOtherTwoAreNull() {
        var pageable = PageRequest.of(0, 20);
        when(enrollments.search(null, null, EnrollmentConstants.EnrollmentStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(useCase.execute(pageable, null, null, EnrollmentConstants.EnrollmentStatus.ACTIVE).items())
                .isEmpty();
    }
```

- [ ] **Step 5: Thêm `@RequestParam status` vào controller**

Trong `backend/src/main/java/com/eduerp/modules/enrollment/web/EnrollmentAdminController.java`, thay method `list`:

```java
    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_ENROLLMENT)
    PageResponse<EnrollmentResponse> list(@PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) UUID studentProfileId,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) EnrollmentConstants.EnrollmentStatus status) {
        return listEnrollments.execute(pageable, studentProfileId, classId, status);
    }
```

Thêm import `com.eduerp.modules.enrollment.EnrollmentConstants;`.

- [ ] **Step 6: Thêm một test HTTP cho filter mới**

Thêm vào `backend/src/test/java/com/eduerp/modules/enrollment/web/EnrollmentAdminControllerIT.java` (dùng đúng các helper `signIn`/tạo ghi danh đã có trong file đó — mở file và dùng lại tên helper thực tế):

```java
    /** Spec mục 7: màn hình "Tạo combo" gọi endpoint này với status=ACTIVE để chỉ hiện ghi danh đang
     * học; để trống thì trả về mọi trạng thái, đúng như trước. */
    @Test
    void filtersTheEnrollmentListByStatus() throws Exception {
        var admin = signIn("enr-admin-status@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newEnrollmentId(admin);
        mockMvc.perform(post("/api/enrollment/enrollments/" + enrollmentId + "/withdraw")
                .cookie(admin).with(csrf()));

        var active = mockMvc.perform(get("/api/enrollment/enrollments").cookie(admin)
                .param("status", EnrollmentConstants.EnrollmentStatus.ACTIVE.name())).andReturn();
        var withdrawn = mockMvc.perform(get("/api/enrollment/enrollments").cookie(admin)
                .param("status", EnrollmentConstants.EnrollmentStatus.WITHDRAWN.name())).andReturn();

        assertThat(active.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(active.getResponse().getContentAsString()).get("items"))
                .noneSatisfy(item -> assertThat(item.get("id").asText()).isEqualTo(enrollmentId));
        assertThat(objectMapper.readTree(withdrawn.getResponse().getContentAsString()).get("items"))
                .anySatisfy(item -> assertThat(item.get("id").asText()).isEqualTo(enrollmentId));
    }
```

> `newEnrollmentId(admin)` là tên giả định cho helper dựng một ghi danh ACTIVE trong file đó. Chạy `grep -n "private String\|private UUID" backend/src/test/java/com/eduerp/modules/enrollment/web/EnrollmentAdminControllerIT.java` và dùng đúng helper có sẵn; nếu không có, dựng ghi danh bằng `POST /api/enrollment/enrollments` giống cách `InvoiceAdminControllerIT.newActiveEnrollmentId` làm.

- [ ] **Step 7: Chạy toàn bộ test enrollment**

Run: `cd backend && mvn -q test -Dtest='com.eduerp.modules.enrollment.**'`
Expected: PASS — mọi test cũ + 3 test mới.

- [ ] **Step 8: Xác nhận facade không đổi**

Run: `cd /Users/hoangdieu/PycharmProjects/EducationErp && git diff --stat 808528b -- backend/src/main/java/com/eduerp/modules/enrollment/EnrollmentManagement.java`
Expected: không có output — facade không đổi dù `ListEnrollments` đã nhận thêm filter.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/enrollment/ \
  backend/src/test/java/com/eduerp/modules/enrollment/
git commit -m "feat(enrollment): add optional status filter to the enrollment list"
```

---

### Task 16: IT xuyên luồng — rút khỏi khoá trong combo (RF #7) + thu tiền hoá đơn combo (RF #9)

**Files:**
- Create: `backend/src/test/java/com/eduerp/modules/billing/web/ComboPaymentFlowIT.java`

**Interfaces:**
- Consumes: mọi endpoint đã có — `POST /api/billing/combos`, `POST /api/billing/combos/invoices`, `POST /api/billing/invoices/{invoiceId}/manual-payment`, `POST /api/billing/invoices/{invoiceId}/cancel`, `GET /api/billing/invoices/{invoiceId}`, `GET /api/billing/combos/{comboId}`, `POST /api/enrollment/enrollments/{enrollmentId}/withdraw`; `MarkOverdueInvoices.execute()`.
- Produces: **không có production code mới.** Task này chứng minh bằng test rằng hai hành vi đã chốt vẫn đúng — nếu một test ở đây đỏ thì lỗi nằm ở task trước, sửa ở đó.

- [ ] **Step 1: Viết test (dự kiến XANH ngay — xem Step 2)**

Tạo `backend/src/test/java/com/eduerp/modules/billing/web/ComboPaymentFlowIT.java`:

```java
package com.eduerp.modules.billing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.dto.CreateComboInvoiceRequest;
import com.eduerp.modules.billing.dto.CreateComboRequest;
import com.eduerp.modules.billing.dto.RecordManualPaymentRequest;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.usecase.MarkOverdueInvoices;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.enrollment.dto.CreateEnrollmentRequest;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Hai bất biến xuyên phân hệ, không có production code riêng:
 * <ul>
 *   <li>Review Focus #7 - {@code WithdrawEnrollment} không biết gì về combo, nên rút khỏi một khoá
 *       KHÔNG huỷ combo và KHÔNG tính lại giảm giá: công nợ đã phát sinh giữ nguyên.</li>
 *   <li>Review Focus #9 - mọi luồng thu tiền của Phase 3 chạy trên hoá đơn combo y như hoá đơn
 *       đơn-khoá, không một nhánh rẽ nào theo loại hoá đơn.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ComboPaymentFlowIT {

    private static final String PASSWORD = "Password123!";
    private static final String COMBOS = "/api/billing/combos";
    private static final String COMBO_INVOICES = COMBOS + "/invoices";
    private static final String INVOICES = "/api/billing/invoices";
    private static final BigDecimal MATHS_FEE = new BigDecimal("12000000");
    private static final BigDecimal ENGLISH_FEE = new BigDecimal("9000000");
    private static final LocalDate DUE_DATE = LocalDate.of(2027, 1, 31);
    private static final LocalDate PAST_DUE_DATE = LocalDate.of(2020, 1, 1);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    BranchRepository branches;

    @Autowired
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    @Autowired
    StudentProfileRepository profiles;

    @Autowired
    ComboDiscountTierRepository tiers;

    @Autowired
    InvoiceRepository invoices;

    @Autowired
    MarkOverdueInvoices markOverdueInvoices;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Cookie signIn(String email) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(account.getId(), AccessConstants.RoleCodes.ADMIN);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    private record Fixture(UUID studentProfileId, String firstEnrollmentId, String secondEnrollmentId,
            String comboId) {
    }

    private UUID newClassId(UUID branchId, BigDecimal tuitionFee) {
        var course = courses.save(new Course("CPF-C-" + shortId(), "Khoá", null, 24, tuitionFee));
        var teacherId = accounts.save(new Account("cpf-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        return classes.save(new Class(course, "CPF-K-" + shortId(), branchId, teacherId, 20)).getId();
    }

    private String enrol(Cookie admin, UUID studentProfileId, UUID classId) throws Exception {
        var result = mockMvc.perform(post("/api/enrollment/enrollments").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEnrollmentRequest(studentProfileId, classId))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    /**
     * Một combo 2 khoá, giảm 10%: 21.000.000 → 18.900.000.
     *
     * <p>{@code deleteAll} trước khi seed: {@code min_course_count} là UNIQUE và mọi test trong class
     * {@code @SpringBootTest} này dùng chung một database, nên ghi lại cùng mốc 2 ở test thứ hai sẽ
     * vỡ constraint. Combo đã tạo ở test trước giữ nguyên % giảm vì đó là snapshot trên chính Combo.
     */
    private Fixture newCombo(Cookie admin) throws Exception {
        tiers.deleteAll();
        tiers.saveAndFlush(new ComboDiscountTier(2, new BigDecimal("10")));
        var branchId = branches.save(new Branch("CPF-" + shortId(), "Chi nhánh", null)).getId();
        var studentAccountId = accounts
                .save(new Account("cpf-hv-" + shortId() + "@eduerp.local", "hash", "HV", null)).getId();
        var studentProfileId = profiles.save(new StudentProfile(studentAccountId, null, null, null)).getId();
        var maths = enrol(admin, studentProfileId, newClassId(branchId, MATHS_FEE));
        var english = enrol(admin, studentProfileId, newClassId(branchId, ENGLISH_FEE));
        var result = mockMvc.perform(post(COMBOS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboRequest(studentProfileId,
                                List.of(UUID.fromString(maths), UUID.fromString(english)), DUE_DATE))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var comboId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        return new Fixture(studentProfileId, maths, english, comboId);
    }

    private String issueComboInvoice(Cookie admin, String comboId, String amount, LocalDate dueDate)
            throws Exception {
        var result = mockMvc.perform(post(COMBO_INVOICES).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateComboInvoiceRequest(
                                UUID.fromString(comboId), new BigDecimal(amount), dueDate))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    /**
     * Review Focus #7: rút khỏi MỘT khoá trong combo. {@code WithdrawEnrollment} không biết gì về
     * combo (và không được dạy), nên combo giữ nguyên tổng tiền, giữ nguyên % giảm, hoá đơn đã phát
     * hành vẫn còn - đúng quyết định "rút khỏi lớp giữ nguyên công nợ" đã chốt ở Phase 3. Hệ thống
     * KHÔNG tự tính lại giảm giá theo số khoá còn lại: đó là việc của kế toán, bằng tay.
     */
    @Test
    void withdrawingFromOneCourseLeavesTheComboAndItsInvoicesUntouched() throws Exception {
        var admin = signIn("cpf-admin-1@eduerp.local");
        var fixture = newCombo(admin);
        var invoiceId = issueComboInvoice(admin, fixture.comboId(), "18900000", DUE_DATE);

        var withdraw = mockMvc.perform(post("/api/enrollment/enrollments/" + fixture.firstEnrollmentId()
                + "/withdraw").cookie(admin).with(csrf())).andReturn();
        assertThat(withdraw.getResponse().getStatus()).isEqualTo(200);

        var detail = mockMvc.perform(get(COMBOS + "/" + fixture.comboId()).cookie(admin)).andReturn();
        assertThat(detail.getResponse().getStatus()).isEqualTo(200);
        var body = objectMapper.readTree(detail.getResponse().getContentAsString());
        assertThat(body.get("combo").get("totalOriginalAmount").asLong()).isEqualTo(21_000_000L);
        assertThat(body.get("combo").get("totalDiscountedAmount").asLong()).isEqualTo(18_900_000L);
        assertThat(body.get("combo").get("discountPercent").asDouble()).isEqualTo(10.0);
        assertThat(body.get("combo").get("courseCount").asInt()).isEqualTo(2);
        assertThat(body.get("enrollments")).hasSize(2);
        assertThat(body.get("invoices")).hasSize(1);
        assertThat(body.get("invoices").get(0).get("id").asText()).isEqualTo(invoiceId);
        assertThat(body.get("invoices").get(0).get("status").asText())
                .isEqualTo(BillingConstants.InvoiceStatus.UNPAID.name());
    }

    /** Review Focus #9: thu tay một phần rồi thu hết - cùng endpoint, cùng hành vi như hoá đơn đơn
     * khoá; trạng thái đi UNPAID → PARTIALLY_PAID → PAID. */
    @Test
    void recordsManualPaymentsOnAComboInvoiceExactlyLikeASingleCourseInvoice() throws Exception {
        var admin = signIn("cpf-admin-2@eduerp.local");
        var fixture = newCombo(admin);
        var invoiceId = issueComboInvoice(admin, fixture.comboId(), "10000000", DUE_DATE);

        var partial = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/manual-payment").cookie(admin)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RecordManualPaymentRequest(new BigDecimal("4000000")))))
                .andReturn();
        assertThat(partial.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(partial.getResponse().getContentAsString()).get("status").asText())
                .isEqualTo(BillingConstants.InvoiceStatus.PARTIALLY_PAID.name());

        var settled = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/manual-payment").cookie(admin)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RecordManualPaymentRequest(new BigDecimal("6000000")))))
                .andReturn();
        var body = objectMapper.readTree(settled.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo(BillingConstants.InvoiceStatus.PAID.name());
        assertThat(body.get("amountPaid").asLong()).isEqualTo(10_000_000L);
        assertThat(body.get("comboId").asText()).isEqualTo(fixture.comboId());

        // Thu quá số còn lại vẫn bị chặn đúng như hoá đơn đơn-khoá.
        var tooMuch = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/manual-payment").cookie(admin)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RecordManualPaymentRequest(new BigDecimal("1")))))
                .andReturn();
        assertThat(tooMuch.getResponse().getStatus()).isEqualTo(409);
        assertThat(tooMuch.getResponse().getContentAsString()).contains("BILLING_INVOICE_NOT_PAYABLE");
    }

    /** Review Focus #9: huỷ hoá đơn combo cũng chạy qua đúng {@code CancelInvoice}, và đợt đã huỷ
     * không chiếm chỗ trong 3 đợt của combo. */
    @Test
    void cancelsAComboInvoiceAndFreesItsInstallmentSlot() throws Exception {
        var admin = signIn("cpf-admin-3@eduerp.local");
        var fixture = newCombo(admin);
        var invoiceId = issueComboInvoice(admin, fixture.comboId(), "18900000", DUE_DATE);

        var cancel = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/cancel").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(cancel.getResponse().getStatus()).isEqualTo(200);

        // Đợt đã huỷ không tính vào tổng và không chiếm số đợt → phát hành lại cả tổng combo được.
        var reissuedId = issueComboInvoice(admin, fixture.comboId(), "18900000", DUE_DATE);
        var reissued = mockMvc.perform(get(INVOICES + "/" + reissuedId).cookie(admin)).andReturn();
        assertThat(objectMapper.readTree(reissued.getResponse().getContentAsString()).get("invoice")
                .get("installmentNumber").asInt()).isEqualTo(1);
    }

    /** Review Focus #9: job quét quá hạn không lọc theo loại hoá đơn, nên hoá đơn combo quá hạn cũng
     * được đánh dấu OVERDUE như mọi hoá đơn khác. */
    @Test
    void marksAnOverdueComboInvoiceJustLikeAnyOtherInvoice() throws Exception {
        var admin = signIn("cpf-admin-4@eduerp.local");
        var fixture = newCombo(admin);
        var invoiceId = issueComboInvoice(admin, fixture.comboId(), "18900000", PAST_DUE_DATE);

        markOverdueInvoices.execute();

        assertThat(invoices.findById(UUID.fromString(invoiceId)).orElseThrow().getStatus())
                .isEqualTo(BillingConstants.InvoiceStatus.OVERDUE);
        // OVERDUE vẫn thu được (nhãn nhắc nợ, không phải khoá sổ) - đúng BillingRules.isPayable.
        assertThat(mockMvc.perform(post(INVOICES + "/" + invoiceId + "/manual-payment").cookie(admin)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RecordManualPaymentRequest(new BigDecimal("1000000")))))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    /** Review Focus #9: danh sách hoá đơn lọc theo học viên trả về cả hoá đơn combo - học viên đó chỉ
     * có một công nợ duy nhất dù nó đến từ combo. */
    @Test
    void listsComboInvoicesAlongsideSingleCourseOnesForTheSameStudent() throws Exception {
        var admin = signIn("cpf-admin-5@eduerp.local");
        var fixture = newCombo(admin);
        issueComboInvoice(admin, fixture.comboId(), "5000000", DUE_DATE);

        var listed = mockMvc.perform(get(INVOICES).cookie(admin)
                .param("studentProfileId", fixture.studentProfileId().toString())).andReturn();

        assertThat(listed.getResponse().getStatus()).isEqualTo(200);
        var items = objectMapper.readTree(listed.getResponse().getContentAsString()).get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("comboId").asText()).isEqualTo(fixture.comboId());
        assertThat(items.get(0).get("enrollmentId").isNull()).isTrue();
    }
}
```

- [ ] **Step 2: Chạy test**

Run: `cd backend && mvn -q test -Dtest=ComboPaymentFlowIT`
Expected: PASS — 5 test, **không cần viết thêm production code nào**. Đó chính là điều cần chứng minh: luồng thu tiền của Phase 3 không có nhánh rẽ theo loại hoá đơn, và `WithdrawEnrollment` không bị dạy gì về combo. Nếu một test ở đây đỏ, đừng vá ở đây — lỗi nằm ở Task 3 (nullable/CHECK), Task 8 (`CreateComboInvoice`) hoặc Task 12 (controller), sửa ở task sở hữu rồi chạy lại.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/eduerp/modules/billing/web/ComboPaymentFlowIT.java
git commit -m "test(billing): pin combo invoices to the shared payment and withdrawal behaviour"
```

---

### Task 17: `invoiceSummarySchema` nullable + `comboId`, và sửa mọi chỗ đọc giả định non-null

**Files:**
- Modify: `frontend/src/entities/billing/model/billing-schema.ts`
- Modify: `frontend/src/modules/billing/ui/invoices-table.tsx`

**Interfaces:**
- Consumes: `InvoiceResponse` của backend (Task 6) — `enrollmentId`/`courseId`/`comboId` đều nullable.
- Produces:
  - `invoiceSummarySchema` với `enrollmentId: z.string().uuid().nullable()`, `courseId: z.string().uuid().nullable()`, `comboId: z.string().uuid().nullable()`
  - `type InvoiceSummary` (suy ra) có ba field nullable → mọi nơi đọc chúng phải null-safe
  - `MIN_COMBO_ENROLLMENTS = 2` (khớp `BillingConstants.Limits.MIN_ENROLLMENTS_PER_COMBO`)

> **Kết quả grep `courseId|enrollmentId` trên toàn bộ `frontend/src`** (đã chạy khi viết plan): chỗ DUY NHẤT đọc hai field này từ một `InvoiceSummary` là `modules/billing/ui/invoices-table.tsx:32` (`invoice.enrollmentId.slice(0, 8)`). `invoice-detail-page.tsx` không đọc field nào trong hai field đó (chỉ `installmentNumber`/`amount`/`amountPaid`/`status`/`dueDate`), nên không cần sửa. `modules/billing/model/billing-forms.ts` và `create-invoice-dialog.tsx` có `enrollmentId` nhưng đó là field của FORM tạo hoá đơn đơn-khoá (luôn bắt buộc) — **không đổi**.

- [ ] **Step 1: Sửa Zod schema (đây là bước "đỏ" — nó làm typecheck vỡ ở đúng chỗ cần sửa)**

Trong `frontend/src/entities/billing/model/billing-schema.ts`, thay `invoiceSummarySchema`:

```ts
/**
 * Khớp từng field với InvoiceResponse ở backend.
 *
 * Một hoá đơn thuộc về ĐÚNG MỘT trong hai: một ghi danh (enrollmentId + courseId) hoặc một combo
 * (comboId) - backend có CHECK constraint chk_invoices_enrollment_xor_combo bảo đảm điều đó. Ba field
 * này nullable nên mọi nơi hiển thị phải xử lý null, không được .slice() thẳng.
 */
export const invoiceSummarySchema = z.object({
  id: z.string().uuid(),
  enrollmentId: z.string().uuid().nullable(),
  comboId: z.string().uuid().nullable(),
  studentProfileId: z.string().uuid(),
  courseId: z.string().uuid().nullable(),
  branchId: z.string().uuid(),
  installmentNumber: z.number().int(),
  amount: z.number(),
  amountPaid: z.number(),
  status: z.enum([
    INVOICE_STATUS.unpaid,
    INVOICE_STATUS.partiallyPaid,
    INVOICE_STATUS.paid,
    INVOICE_STATUS.overdue,
    INVOICE_STATUS.cancelled,
  ]),
  dueDate: z.string(),
  issuedAt: z.string(),
});
```

Và thêm ngay trên đó:

```ts
/** Khớp BillingConstants.Limits.MIN_ENROLLMENTS_PER_COMBO - dưới mốc này backend trả
 * BILLING_COMBO_MINIMUM_SIZE, nên form phải chặn trước để người dùng không bấm rồi mới biết. */
export const MIN_COMBO_ENROLLMENTS = 2;
```

- [ ] **Step 2: Chạy typecheck để xác nhận đỏ ở đúng một chỗ**

Run: `cd frontend && npm run typecheck`
Expected: FAIL với `src/modules/billing/ui/invoices-table.tsx(32,...): error TS18047: 'invoice.enrollmentId' is possibly 'null'.` — và không có lỗi nào khác. Nếu có lỗi ở file khác, sửa luôn ở Step 3 theo cùng cách (null-safe, không dùng `!`).

- [ ] **Step 3: Làm null-safe phần hiển thị trong `invoices-table.tsx`**

Trong `frontend/src/modules/billing/ui/invoices-table.tsx`, thêm helper ngay dưới `toneOf`:

```ts
/**
 * Một hoá đơn neo vào combo hoặc vào ghi danh, không bao giờ cả hai (CHECK constraint ở backend).
 * Nhánh cuối không bao giờ chạy trong dữ liệu hợp lệ, nhưng vẫn phải trả về một chuỗi: hiển thị
 * "Không rõ nguồn" tốt hơn là để trang trắng vì một ô dữ liệu lạ.
 */
function sourceLabelOf(invoice: InvoiceSummary): string {
  if (invoice.comboId !== null) {
    return `Combo ${invoice.comboId.slice(0, 8)}`;
  }
  if (invoice.enrollmentId !== null) {
    return `Ghi danh ${invoice.enrollmentId.slice(0, 8)}`;
  }
  return "Không rõ nguồn";
}
```

và thay dòng hiển thị:

```tsx
            <p className="truncate text-xs text-mist-500">{sourceLabelOf(invoice)}</p>
```

- [ ] **Step 4: Chạy lại typecheck + lint**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: cả hai PASS, 0 lỗi. (`sourceLabelOf` có complexity 3, dưới ngưỡng 15; không dùng `||` trong JSX nên không chạm `no-restricted-syntax`.)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/entities/billing/model/billing-schema.ts \
  frontend/src/modules/billing/ui/invoices-table.tsx
git commit -m "feat(frontend): make invoice course and enrollment links nullable for combos"
```

---

### Task 18: `entities/billing` combo API + keys + hooks, `entities/enrollment` filter `status`, `API_ROUTE`

**Files:**
- Modify: `frontend/src/shared/constants/api-routes.ts`
- Modify: `frontend/src/entities/billing/model/billing-schema.ts`
- Modify: `frontend/src/entities/billing/api/billing-api.ts`
- Modify: `frontend/src/entities/billing/api/billing-keys.ts`
- Create: `frontend/src/entities/billing/api/use-combos.ts`
- Create: `frontend/src/entities/billing/api/use-combo-detail.ts`
- Create: `frontend/src/entities/billing/api/use-combo-discount-tiers.ts`
- Modify: `frontend/src/entities/billing/index.ts`
- Modify: `frontend/src/entities/enrollment/api/enrollment-api.ts`
- Modify: `frontend/src/entities/enrollment/api/enrollment-keys.ts`
- Modify: `frontend/src/entities/enrollment/api/use-enrollments.ts`

**Interfaces:**
- Consumes: endpoint backend từ Task 12 + 13; `apiClient`, `pageResponseSchema` (shared, đã có).
- Produces:
  - `API_ROUTE.billing.{combos, combo(id), comboCancel(id), comboInvoices, comboDiscountTiers, comboDiscountTier(id)}`
  - `comboSchema`, `comboEnrollmentSchema`, `comboDetailSchema`, `comboDiscountTierSchema`; type `Combo`, `ComboEnrollment`, `ComboDetail`, `ComboDiscountTier`
  - payload type `CreateComboPayload`, `CreateComboInvoicePayload`, `CreateComboDiscountTierPayload`, `UpdateComboDiscountTierPayload`
  - `billingApi.{listCombos, getCombo, createCombo, cancelCombo, createComboInvoice, listComboDiscountTiers, createComboDiscountTier, updateComboDiscountTier}`
  - `billingKeys.{comboLists, comboList, comboDetail, discountTiers}`
  - `useCombos(page, size, studentProfileId?)`, `useComboDetail(comboId)`, `useComboDiscountTiers()`
  - `enrollmentApi.listEnrollments(page, size, studentProfileId?, classId?, status?)` — **thêm tham số thứ 5**
  - `useEnrollments(page, size, studentProfileId?, classId?, status?)` — **thêm tham số thứ 5**

- [ ] **Step 1: Thêm đường dẫn API**

Trong `frontend/src/shared/constants/api-routes.ts`, thêm vào trong `billing` (sau `invoiceCancel`, trước `paymentStatus`):

```ts
    combos: "/api/billing/combos",
    combo: (comboId: string) => `/api/billing/combos/${comboId}`,
    comboCancel: (comboId: string) => `/api/billing/combos/${comboId}/cancel`,
    /** comboId nằm trong body, mirror POST /api/billing/invoices vốn nhận enrollmentId trong body. */
    comboInvoices: "/api/billing/combos/invoices",
    comboDiscountTiers: "/api/billing/combo-discount-tiers",
    comboDiscountTier: (tierId: string) => `/api/billing/combo-discount-tiers/${tierId}`,
```

- [ ] **Step 2: Thêm schema + payload combo**

Trong `frontend/src/entities/billing/model/billing-schema.ts`, thêm sau `invoiceDetailSchema`/`payUrlSchema`:

```ts
/** Khớp từng field với ComboResponse ở backend - đổi tên là breaking. */
export const comboSchema = z.object({
  id: z.string().uuid(),
  studentProfileId: z.string().uuid(),
  branchId: z.string().uuid(),
  totalOriginalAmount: z.number(),
  /** % giảm, scale 2 ở backend (NUMERIC(5,2)) - snapshot lúc tạo, không đổi khi bậc giảm giá đổi. */
  discountPercent: z.number(),
  totalDiscountedAmount: z.number(),
  dueDate: z.string(),
  createdAt: z.string(),
  courseCount: z.number().int(),
});

export type Combo = z.infer<typeof comboSchema>;

/** Khớp ComboEnrollmentResponse ở backend. */
export const comboEnrollmentSchema = z.object({
  id: z.string().uuid(),
  enrollmentId: z.string().uuid(),
  courseId: z.string().uuid(),
  originalTuitionFee: z.number(),
});

export type ComboEnrollment = z.infer<typeof comboEnrollmentSchema>;

/** Khớp ComboDetailResponse(combo, enrollments, invoices) ở backend. Mảng invoices gồm CẢ hoá đơn đã
 * huỷ - kế toán cần thấy để hiểu vì sao số đợt nhảy số. */
export const comboDetailSchema = z.object({
  combo: comboSchema,
  enrollments: z.array(comboEnrollmentSchema),
  invoices: z.array(invoiceSummarySchema),
});

export type ComboDetail = z.infer<typeof comboDetailSchema>;

/** Khớp ComboDiscountTierResponse ở backend. */
export const comboDiscountTierSchema = z.object({
  id: z.string().uuid(),
  minCourseCount: z.number().int(),
  discountPercent: z.number(),
  active: z.boolean(),
});

export type ComboDiscountTier = z.infer<typeof comboDiscountTierSchema>;

export interface CreateComboPayload {
  readonly studentProfileId: string;
  readonly enrollmentIds: readonly string[];
  /** ISO date (yyyy-MM-dd) - backend nhận LocalDate. */
  readonly dueDate: string;
}

export interface CreateComboInvoicePayload {
  readonly comboId: string;
  readonly amount: number;
  readonly dueDate: string;
}

export interface CreateComboDiscountTierPayload {
  readonly minCourseCount: number;
  readonly discountPercent: number;
}

export interface UpdateComboDiscountTierPayload {
  readonly discountPercent: number;
  readonly active: boolean;
}
```

- [ ] **Step 3: Thêm 8 hàm API**

Trong `frontend/src/entities/billing/api/billing-api.ts`, thêm vào import từ `billing-schema`:

```ts
  comboDetailSchema,
  comboDiscountTierSchema,
  comboSchema,
  type CreateComboDiscountTierPayload,
  type CreateComboInvoicePayload,
  type CreateComboPayload,
  type UpdateComboDiscountTierPayload,
```

Thêm ngay dưới `const invoicePageSchema`:

```ts
const comboPageSchema = pageResponseSchema(comboSchema);
const comboDiscountTierListSchema = z.array(comboDiscountTierSchema);
```

(thêm `import { z } from "zod";` ở đầu file)

Và thêm 8 method vào object `billingApi`, ngay trước `getPaymentStatus`:

```ts
  async listCombos(page: number, size: number, studentProfileId?: string) {
    return comboPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.billing.combos, { page, size, studentProfileId }),
    );
  },

  async getCombo(comboId: string) {
    return comboDetailSchema.parse(await apiClient.get<unknown>(API_ROUTE.billing.combo(comboId)));
  },

  async createCombo(payload: CreateComboPayload) {
    return comboSchema.parse(await apiClient.post<unknown>(API_ROUTE.billing.combos, payload));
  },

  async cancelCombo(comboId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.billing.comboCancel(comboId));
  },

  /** Trả về một InvoiceSummary như createInvoice - hoá đơn combo và hoá đơn đơn-khoá cùng một kiểu. */
  async createComboInvoice(payload: CreateComboInvoicePayload) {
    return invoiceSummarySchema.parse(
      await apiClient.post<unknown>(API_ROUTE.billing.comboInvoices, payload),
    );
  },

  /** Không phân trang: backend trả thẳng một mảng (số bậc là con số nhỏ do admin tự nhập). */
  async listComboDiscountTiers() {
    return comboDiscountTierListSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.billing.comboDiscountTiers),
    );
  },

  async createComboDiscountTier(payload: CreateComboDiscountTierPayload) {
    return comboDiscountTierSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.billing.comboDiscountTiers, payload),
    );
  },

  async updateComboDiscountTier(tierId: string, payload: UpdateComboDiscountTierPayload) {
    return comboDiscountTierSchema.parse(
      await apiClient.patch<unknown>(API_ROUTE.billing.comboDiscountTier(tierId), payload),
    );
  },
```

- [ ] **Step 4: Thêm query key**

Trong `frontend/src/entities/billing/api/billing-keys.ts`, thêm trước `paymentStatus`:

```ts
  comboLists: () => [...billingKeys.all, "combo", "list"] as const,
  comboList: (page: number, size: number, studentProfileId?: string) =>
    [...billingKeys.comboLists(), { page, size, studentProfileId }] as const,
  comboDetail: (comboId: string) => [...billingKeys.all, "combo", "detail", comboId] as const,
  discountTiers: () => [...billingKeys.all, "combo", "discount-tier", "list"] as const,
```

- [ ] **Step 5: Thêm ba hook đọc**

Tạo `frontend/src/entities/billing/api/use-combos.ts`:

```ts
import { billingApi } from "@/entities/billing/api/billing-api";
import { billingKeys } from "@/entities/billing/api/billing-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useCombos(page: number, size: number, studentProfileId?: string) {
  return useQuery({
    queryKey: billingKeys.comboList(page, size, studentProfileId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => billingApi.listCombos(page, size, studentProfileId),
  });
}
```

Tạo `frontend/src/entities/billing/api/use-combo-detail.ts`:

```ts
import { billingApi } from "@/entities/billing/api/billing-api";
import { billingKeys } from "@/entities/billing/api/billing-keys";
import { QUERY_RETRY_COUNT } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function useComboDetail(comboId: string) {
  return useQuery({
    queryKey: billingKeys.comboDetail(comboId),
    retry: QUERY_RETRY_COUNT,
    enabled: comboId.length > 0,
    queryFn: () => billingApi.getCombo(comboId),
  });
}
```

Tạo `frontend/src/entities/billing/api/use-combo-discount-tiers.ts`:

```ts
import { billingApi } from "@/entities/billing/api/billing-api";
import { billingKeys } from "@/entities/billing/api/billing-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

/** Bậc giảm giá đổi rất ít nên dùng staleTime của danh sách; dialog tạo combo đọc nó để xem trước
 * % giảm, còn con số CHỐT vẫn do backend tính lúc tạo (CreateCombo). */
export function useComboDiscountTiers() {
  return useQuery({
    queryKey: billingKeys.discountTiers(),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => billingApi.listComboDiscountTiers(),
  });
}
```

- [ ] **Step 6: Mở rộng barrel `entities/billing/index.ts`**

Thêm ba dòng export hook (giữ thứ tự alphabet như file hiện có):

```ts
export { useComboDetail } from "@/entities/billing/api/use-combo-detail";
export { useComboDiscountTiers } from "@/entities/billing/api/use-combo-discount-tiers";
export { useCombos } from "@/entities/billing/api/use-combos";
```

và thêm vào khối export từ `billing-schema` (giữ thứ tự alphabet):

```ts
  comboDetailSchema,
  comboDiscountTierSchema,
  comboEnrollmentSchema,
  comboSchema,
  MIN_COMBO_ENROLLMENTS,
  type Combo,
  type ComboDetail,
  type ComboDiscountTier,
  type ComboEnrollment,
  type CreateComboDiscountTierPayload,
  type CreateComboInvoicePayload,
  type CreateComboPayload,
  type UpdateComboDiscountTierPayload,
```

- [ ] **Step 7: Thêm filter `status` cho `entities/enrollment`**

Trong `frontend/src/entities/enrollment/api/enrollment-api.ts`, thay `listEnrollments`:

```ts
  async listEnrollments(
    page: number,
    size: number,
    studentProfileId?: string,
    classId?: string,
    status?: string,
  ) {
    return enrollmentPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.enrollment.enrollments, {
        page,
        size,
        studentProfileId,
        classId,
        status,
      }),
    );
  },
```

Trong `frontend/src/entities/enrollment/api/enrollment-keys.ts`, thay `list`:

```ts
  list: (page: number, size: number, studentProfileId?: string, classId?: string, status?: string) =>
    [...enrollmentKeys.lists(), { page, size, studentProfileId, classId, status }] as const,
```

Trong `frontend/src/entities/enrollment/api/use-enrollments.ts`, thay toàn bộ hook:

```ts
export function useEnrollments(
  page: number,
  size: number,
  studentProfileId?: string,
  classId?: string,
  status?: string,
) {
  return useQuery({
    queryKey: enrollmentKeys.list(page, size, studentProfileId, classId, status),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => enrollmentApi.listEnrollments(page, size, studentProfileId, classId, status),
  });
}
```

Tham số thứ 5 là optional nên hai call site hiện có (`use-invoices-page-controller.ts`, `use-enrollments-page-controller.ts`) **không cần sửa**.

- [ ] **Step 8: Chạy typecheck + lint**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: cả hai PASS, 0 lỗi. Nếu lint báo `boundaries/dependencies`, kiểm lại: `entities/billing` chỉ được import `entities/billing/*` và `shared/*` — ba hook mới tuân đúng luật đó.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/shared/constants/api-routes.ts frontend/src/entities/billing/ \
  frontend/src/entities/enrollment/
git commit -m "feat(frontend): add combo entity schemas, api client and read hooks"
```

---

### Task 19: `modules/billing` — mutation combo, form, trang danh sách combo, dialog tạo combo

**Files:**
- Modify: `frontend/src/modules/billing/api/use-billing-mutations.ts`
- Modify: `frontend/src/modules/billing/model/billing-forms.ts`
- Create: `frontend/src/modules/billing/model/combo-pricing.ts`
- Create: `frontend/src/modules/billing/hooks/use-combos-page-controller.ts`
- Create: `frontend/src/modules/billing/hooks/use-create-combo-controller.ts`
- Create: `frontend/src/modules/billing/ui/combos-table.tsx`
- Create: `frontend/src/modules/billing/ui/create-combo-dialog.tsx`
- Create: `frontend/src/modules/billing/pages/combos-page.tsx`
- Modify: `frontend/src/modules/billing/index.ts`

**Interfaces:**
- Consumes: `useCombos`, `useComboDiscountTiers`, `billingApi`, `billingKeys`, `MIN_COMBO_ENROLLMENTS`, type `Combo`/`ComboDiscountTier`/`CreateComboPayload` (Task 18); `useEnrollments` với tham số `status` (Task 18); `useStudents` (`@/entities/student`), `useCourses` (`@/entities/course`); `ENROLLMENT_STATUS` (`@/entities/enrollment`); `CheckableRow` (`@/shared/ui/checkable-row`); `ACCESS_RULE` (đã có). `APP_ROUTE.combos`/`comboDetail`/`comboDiscountTiers` và `buildComboDetailPath` được **tạo ngay trong task này** ở Step 5 (Task 21 chỉ dùng lại, không định nghĩa lại).
- Produces:
  - `useCreateCombo()`, `useCancelCombo(comboId)`, `useCreateComboInvoice()` — mutation hook
  - `createComboFormSchema` — Zod form schema
  - `resolveDiscountPercent(tiers, courseCount): number | null`, `previewDiscountedTotal(totalOriginalAmount, discountPercent): number`
  - `useCombosPageController()`, `useCreateComboController(onCreated)`
  - `<CombosTable rows={...} />`, `<CreateComboDialog open onClose />`, `<CombosPage />`
  - `APP_ROUTE.combos`/`comboDetail` + `buildComboDetailPath(comboId)`

- [ ] **Step 1: Thêm ba mutation hook**

Trong `frontend/src/modules/billing/api/use-billing-mutations.ts`, mở rộng import từ `@/entities/billing`:

```ts
  type CreateComboInvoicePayload,
  type CreateComboPayload,
```

và thêm ba hook vào cuối file:

```ts
export function useCreateCombo() {
  const invalidate = useInvoicesInvalidation();
  return useMutation({
    mutationFn: (payload: CreateComboPayload) => billingApi.createCombo(payload),
    onSuccess: invalidate,
  });
}

/** Huỷ combo xoá cứng bản ghi, nên phải dọn CẢ cache danh sách lẫn cache chi tiết - invalidate
 * billingKeys.all làm cả hai trong một lần. */
export function useCancelCombo(comboId: string) {
  const invalidate = useInvoicesInvalidation();
  return useMutation({
    mutationFn: () => billingApi.cancelCombo(comboId),
    onSuccess: invalidate,
  });
}

export function useCreateComboInvoice() {
  const invalidate = useInvoicesInvalidation();
  return useMutation({
    mutationFn: (payload: CreateComboInvoicePayload) => billingApi.createComboInvoice(payload),
    onSuccess: invalidate,
  });
}
```

- [ ] **Step 2: Thêm form schema**

Trong `frontend/src/modules/billing/model/billing-forms.ts`, thêm import `MIN_COMBO_ENROLLMENTS` từ `@/entities/billing` và thêm hai schema:

```ts
export const createComboFormSchema = z.object({
  studentProfileId: z.string().uuid("Chọn học viên"),
  enrollmentIds: z
    .array(z.string().uuid())
    .min(MIN_COMBO_ENROLLMENTS, `Chọn ít nhất ${MIN_COMBO_ENROLLMENTS} khoá đang học`),
  dueDate: z.string().min(1, "Chọn hạn đóng của combo"),
});

export const comboDiscountTierFormSchema = z.object({
  minCourseCount: z
    .string()
    .min(1, "Nhập số khoá tối thiểu")
    .transform((value) => Number(value))
    .refine(
      (value) => Number.isInteger(value) && value >= MIN_COMBO_ENROLLMENTS,
      `Số khoá tối thiểu phải từ ${MIN_COMBO_ENROLLMENTS}`,
    ),
  discountPercent: z
    .string()
    .min(1, "Nhập % giảm")
    .transform((value) => Number(value))
    .refine((value) => Number.isFinite(value) && value >= 0 && value <= 100, "% giảm phải trong 0-100"),
});
```

- [ ] **Step 3: Viết helper tính trước giá (thuần, không I/O)**

Tạo `frontend/src/modules/billing/model/combo-pricing.ts`:

```ts
import type { ComboDiscountTier } from "@/entities/billing";

/**
 * Xem trước % giảm theo đúng quy tắc của backend (spec mục 4): bậc có minCourseCount LỚN NHẤT còn
 * active mà không vượt số khoá đã chọn. Trả về null khi chưa cấu hình bậc nào thoả - lúc đó UI phải
 * nói rõ "chưa cấu hình", KHÔNG được hiển thị 0% như thể đó là một mức giảm hợp lệ.
 *
 * Đây chỉ là xem trước. Con số chốt do CreateCombo ở backend tính và snapshot vào Combo; nếu admin
 * vừa đổi bậc thì backend là nguồn sự thật, không phải màn hình này.
 */
export function resolveDiscountPercent(
  tiers: readonly ComboDiscountTier[],
  courseCount: number,
): number | null {
  const applicable = tiers.filter((tier) => tier.active && tier.minCourseCount <= courseCount);
  if (applicable.length === 0) {
    return null;
  }
  return applicable.reduce((best, tier) => (tier.minCourseCount > best.minCourseCount ? tier : best))
    .discountPercent;
}

/** Khớp BillingRules.discountedTotal: nhân rồi làm tròn về đồng nguyên (scale 0). */
export function previewDiscountedTotal(totalOriginalAmount: number, discountPercent: number): number {
  return Math.round(totalOriginalAmount * (1 - discountPercent / 100));
}
```

- [ ] **Step 4: Viết controller của dialog tạo combo**

Tạo `frontend/src/modules/billing/hooks/use-create-combo-controller.ts`:

```ts
import { useComboDiscountTiers, type CreateComboPayload } from "@/entities/billing";
import { useCourses } from "@/entities/course";
import { ENROLLMENT_STATUS, useEnrollments } from "@/entities/enrollment";
import { useStudents } from "@/entities/student";
import { useCreateCombo } from "@/modules/billing/api/use-billing-mutations";
import { createComboFormSchema } from "@/modules/billing/model/billing-forms";
import {
  previewDiscountedTotal,
  resolveDiscountPercent,
} from "@/modules/billing/model/combo-pricing";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { useCallback, useMemo } from "react";

/** Đủ cho một trung tâm; hai dropdown tham chiếu không cần phân trang (mirror các controller khác). */
const REFERENCE_PAGE_SIZE = 200;

/** Một khoá đã chọn, kèm nhãn và học phí để dialog chỉ việc render (Mandate #2). */
export interface ComboCandidate {
  readonly enrollmentId: string;
  readonly label: string;
  readonly tuitionFee: number | null;
}

/**
 * Toàn bộ state/query/mutation của dialog tạo combo. Trình tự: chọn học viên → nạp ghi danh ACTIVE
 * của chính học viên đó (filter server-side, spec mục 7) → tick ≥ 2 khoá → xem trước % giảm và tổng
 * sau giảm → nhập hạn đóng → tạo.
 */
export function useCreateComboController(onCreated: () => void) {
  const createCombo = useCreateCombo();
  const students = useStudents(0, REFERENCE_PAGE_SIZE);
  const courses = useCourses(0, REFERENCE_PAGE_SIZE);
  const tiers = useComboDiscountTiers();

  const form = useZodForm({
    schema: createComboFormSchema,
    initialValues: { studentProfileId: "", enrollmentIds: [], dueDate: "" },
    onSubmit: async (values) => {
      const payload: CreateComboPayload = {
        studentProfileId: values.studentProfileId,
        enrollmentIds: values.enrollmentIds,
        dueDate: values.dueDate,
      };
      await createCombo.mutateAsync(payload);
      form.reset();
      onCreated();
    },
  });

  const selectedStudentId = form.values.studentProfileId;
  // Chỉ nạp khi đã chọn học viên: danh sách ghi danh của MỘT học viên, đã lọc ACTIVE ở server.
  const enrollments = useEnrollments(
    0,
    REFERENCE_PAGE_SIZE,
    selectedStudentId.length === 0 ? undefined : selectedStudentId,
    undefined,
    ENROLLMENT_STATUS.active,
  );

  const tuitionByCourseId = useMemo(() => {
    const map = new Map<string, number | null>();
    (courses.data?.items ?? []).forEach((course) => map.set(course.id, course.tuitionFee));
    return map;
  }, [courses.data]);

  const candidates: readonly ComboCandidate[] = useMemo(
    () =>
      (enrollments.data?.items ?? []).map((enrollment) => ({
        enrollmentId: enrollment.id,
        label: `Lớp ${enrollment.classId.slice(0, 8)} — khoá ${enrollment.courseId.slice(0, 8)}`,
        tuitionFee: tuitionByCourseId.get(enrollment.courseId) ?? null,
      })),
    [enrollments.data, tuitionByCourseId],
  );

  const selectedIds = form.values.enrollmentIds;

  const totalOriginalAmount = useMemo(
    () =>
      candidates
        .filter((candidate) => selectedIds.includes(candidate.enrollmentId))
        .reduce((sum, candidate) => sum + (candidate.tuitionFee ?? 0), 0),
    [candidates, selectedIds],
  );

  const discountPercent = resolveDiscountPercent(tiers.data ?? [], selectedIds.length);
  const discountedTotal =
    discountPercent === null ? null : previewDiscountedTotal(totalOriginalAmount, discountPercent);

  const onSelectStudent = useCallback(
    (studentProfileId: string) => {
      form.setValue("studentProfileId", studentProfileId);
      // Đổi học viên thì các khoá đã tick không còn thuộc về ai - xoá, không giữ lại.
      form.setValue("enrollmentIds", []);
    },
    [form],
  );

  const onToggleEnrollment = useCallback(
    (enrollmentId: string) => {
      const next = selectedIds.includes(enrollmentId)
        ? selectedIds.filter((id) => id !== enrollmentId)
        : [...selectedIds, enrollmentId];
      form.setValue("enrollmentIds", next);
    },
    [form, selectedIds],
  );

  return {
    form,
    students,
    candidates,
    selectedIds,
    isLoadingCandidates: selectedStudentId.length > 0 && enrollments.isPending,
    totalOriginalAmount,
    discountPercent,
    discountedTotal,
    tiersError: tiers.error,
    onSelectStudent,
    onToggleEnrollment,
  };
}
```

- [ ] **Step 5: Thêm route constant + builder (dùng ở `CombosTable` và Task 21)**

Trong `frontend/src/shared/constants/app-routes.ts`, thêm vào `APP_ROUTE` sau `invoiceDetail`:

```ts
  combos: "/admin/billing/combos",
  comboDetail: "/admin/billing/combos/:comboId",
  comboDiscountTiers: "/admin/billing/combo-discount-tiers",
```

và thêm builder sau `buildInvoiceDetailPath`:

```ts
/** Link thật tới trang chi tiết một combo - APP_ROUTE.comboDetail chỉ là route template. */
export function buildComboDetailPath(comboId: string): string {
  return `/admin/billing/combos/${comboId}`;
}
```

- [ ] **Step 6: Viết `CombosTable`**

Tạo `frontend/src/modules/billing/ui/combos-table.tsx`:

```tsx
import type { Combo } from "@/entities/billing";
import { buildComboDetailPath } from "@/shared/constants/app-routes";
import { formatter } from "@/shared/lib/format";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";

export interface CombosTableProps {
  readonly rows: readonly Combo[];
}

export function CombosTable({ rows }: CombosTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((combo, index) => (
        <m.li
          key={combo.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto_auto]"
        >
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">{combo.courseCount} khoá trong combo</p>
            <p className="truncate text-xs text-mist-500">
              HV {combo.studentProfileId.slice(0, 8)} · hạn {combo.dueDate}
            </p>
          </div>
          <p className="text-xs text-mist-500 line-through">
            {formatter.count(combo.totalOriginalAmount)} đ
          </p>
          <Badge tone="accent">-{combo.discountPercent}%</Badge>
          <p className="text-sm text-mist-100">{formatter.count(combo.totalDiscountedAmount)} đ</p>
          <Link to={buildComboDetailPath(combo.id)}>
            <GlassButton variant="secondary" size="sm" icon={<ChevronRight size={14} aria-hidden />}>
              Xem chi tiết
            </GlassButton>
          </Link>
        </m.li>
      ))}
    </ul>
  );
}
```

- [ ] **Step 7: Viết `CreateComboDialog`**

Tạo `frontend/src/modules/billing/ui/create-combo-dialog.tsx`:

```tsx
import { MIN_COMBO_ENROLLMENTS } from "@/entities/billing";
import { useCreateComboController } from "@/modules/billing/hooks/use-create-combo-controller";
import { formatter } from "@/shared/lib/format";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";
import { Skeleton } from "@/shared/ui/skeleton";
import { CheckableRow } from "@/shared/ui/checkable-row";

export interface CreateComboDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateComboDialog({ open, onClose }: CreateComboDialogProps) {
  const controller = useCreateComboController(onClose);
  const { form } = controller;

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Tạo combo khoá học"
      description={`Chọn từ ${MIN_COMBO_ENROLLMENTS} khoá đang học của cùng một học viên. Combo thu tối đa 3 đợt, một hạn đóng chung.`}
    >
      <form id="create-combo-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Học viên" htmlFor="combo-student" error={form.fieldErrors.studentProfileId}>
          <GlassSelect
            id="combo-student"
            className="w-full"
            containerClassName="w-full"
            value={form.values.studentProfileId}
            invalid={form.fieldErrors.studentProfileId !== undefined}
            onChange={(event) => controller.onSelectStudent(event.target.value)}
          >
            <option value="">Chọn học viên</option>
            {(controller.students.data?.items ?? [])
              .filter((student) => student.active)
              .map((student) => (
                <option key={student.id} value={student.id}>
                  {student.fullName ?? student.email ?? student.id}
                </option>
              ))}
          </GlassSelect>
        </FormField>

        <FormField
          label="Khoá đang học"
          htmlFor="combo-enrollments"
          hint="Chỉ hiện ghi danh đang học của học viên đã chọn."
          error={form.fieldErrors.enrollmentIds}
        >
          <ComboCandidateList controller={controller} />
        </FormField>

        <ComboPricePreview controller={controller} />

        <FormField label="Hạn đóng của combo" htmlFor="combo-due-date" error={form.fieldErrors.dueDate}>
          <GlassInput
            id="combo-due-date"
            type="date"
            value={form.values.dueDate}
            invalid={form.fieldErrors.dueDate !== undefined}
            onChange={(event) => form.setValue("dueDate", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-combo-form" loading={form.isSubmitting}>
          Tạo combo
        </GlassButton>
      </footer>
    </GlassModal>
  );
}

/** Tách ra để dialog không rẽ nhánh trong JSX (Mandate #3) và giữ mỗi component dưới 200 dòng. */
function ComboCandidateList({
  controller,
}: {
  readonly controller: ReturnType<typeof useCreateComboController>;
}) {
  if (controller.form.values.studentProfileId.length === 0) {
    return <p className="text-xs text-mist-500">Chọn học viên trước để xem các khoá đang học.</p>;
  }
  if (controller.isLoadingCandidates) {
    return <Skeleton className="h-20" />;
  }
  if (controller.candidates.length === 0) {
    return (
      <p className="text-xs text-mist-500">
        Học viên này chưa có ghi danh đang học nào. Ghi danh các khoá trước, rồi quay lại gộp combo.
      </p>
    );
  }
  return (
    <div id="combo-enrollments" className="flex max-h-56 flex-col gap-2 overflow-y-auto">
      {controller.candidates.map((candidate) => (
        <CheckableRow
          key={candidate.enrollmentId}
          checked={controller.selectedIds.includes(candidate.enrollmentId)}
          onToggle={() => controller.onToggleEnrollment(candidate.enrollmentId)}
          label={candidate.label}
          description={
            candidate.tuitionFee === null
              ? "Khoá chưa gắn học phí — không gộp được"
              : `${formatter.count(candidate.tuitionFee)} đ`
          }
        />
      ))}
    </div>
  );
}

/** Xem trước; con số chốt do backend tính lúc tạo (CreateCombo snapshot vào Combo). */
function ComboPricePreview({
  controller,
}: {
  readonly controller: ReturnType<typeof useCreateComboController>;
}) {
  if (controller.selectedIds.length === 0) {
    return null;
  }
  if (controller.discountPercent === null) {
    return (
      <p className="text-xs text-amber-600">
        Chưa cấu hình bậc giảm giá cho {controller.selectedIds.length} khoá — tạo combo sẽ bị từ chối.
        Thêm bậc ở trang Bậc giảm giá combo trước.
      </p>
    );
  }
  return (
    <dl className="glass grid grid-cols-1 gap-2 rounded-2xl p-3 sm:grid-cols-3">
      <div>
        <dt className="text-xs text-mist-500">Tổng gốc</dt>
        <dd className="text-sm text-mist-100">{formatter.count(controller.totalOriginalAmount)} đ</dd>
      </div>
      <div>
        <dt className="text-xs text-mist-500">Giảm</dt>
        <dd className="text-sm text-mist-100">{controller.discountPercent}%</dd>
      </div>
      <div>
        <dt className="text-xs text-mist-500">Phải thu</dt>
        <dd className="text-sm text-mist-100">{formatter.count(controller.discountedTotal ?? 0)} đ</dd>
      </div>
    </dl>
  );
}
```

- [ ] **Step 8: Viết controller + trang danh sách combo**

Tạo `frontend/src/modules/billing/hooks/use-combos-page-controller.ts`:

```ts
import { useCombos } from "@/entities/billing";
import { useStudents } from "@/entities/student";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useCallback, useState } from "react";

const REFERENCE_PAGE_SIZE = 200;

/** Toàn bộ state/query của trang danh sách combo - ui/* chỉ render (Mandate #2). */
export function useCombosPageController() {
  const [page, setPage] = useState(0);
  const [studentFilter, setStudentFilter] = useState("");
  const [createDialogOpen, setCreateDialogOpen] = useState(false);

  const combos = useCombos(
    page,
    DEFAULT_PAGE_SIZE,
    studentFilter.length === 0 ? undefined : studentFilter,
  );
  const students = useStudents(0, REFERENCE_PAGE_SIZE);

  const openCreateDialog = useCallback(() => setCreateDialogOpen(true), []);
  const closeCreateDialog = useCallback(() => setCreateDialogOpen(false), []);

  return {
    page,
    setPage,
    combos,
    students,
    studentFilter,
    setStudentFilter,
    createDialogOpen,
    openCreateDialog,
    closeCreateDialog,
  };
}
```

Tạo `frontend/src/modules/billing/pages/combos-page.tsx`:

```tsx
import { Can, RequirePermission } from "@/entities/permission";
import { useCombosPageController } from "@/modules/billing/hooks/use-combos-page-controller";
import { CombosTable } from "@/modules/billing/ui/combos-table";
import { CreateComboDialog } from "@/modules/billing/ui/create-combo-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { GlassSelect } from "@/shared/ui/glass-select";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Package, Plus } from "lucide-react";

export function CombosPage() {
  const controller = useCombosPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readInvoice}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Combo khoá học"
          description="Gộp các khoá đang học của một học viên để giảm giá theo số khoá. Combo thu tối đa 3 đợt, một hạn đóng chung."
          actions={
            <Can {...ACCESS_RULE.createInvoice}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Tạo combo
              </GlassButton>
            </Can>
          }
        />

        {controller.combos.isError ? <ErrorNotice error={controller.combos.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          <div className="flex flex-wrap gap-2">
            <GlassSelect
              aria-label="Lọc theo học viên"
              value={controller.studentFilter}
              onChange={(event) => controller.setStudentFilter(event.target.value)}
            >
              <option value="">Mọi học viên</option>
              {(controller.students.data?.items ?? []).map((student) => (
                <option key={student.id} value={student.id}>
                  {student.fullName ?? student.email ?? student.id}
                </option>
              ))}
            </GlassSelect>
          </div>

          {controller.combos.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {controller.combos.data ? <CombosListSection controller={controller} /> : null}
        </GlassPanel>

        <CreateComboDialog open={controller.createDialogOpen} onClose={controller.closeCreateDialog} />
      </div>
    </RequirePermission>
  );
}

/** Tách nhánh rỗng/có dữ liệu ra component riêng - tránh nested ternary trong JSX (Mandate #3). */
function CombosListSection({
  controller,
}: {
  readonly controller: ReturnType<typeof useCombosPageController>;
}) {
  const page = controller.combos.data;
  if (!page || page.items.length === 0) {
    return (
      <EmptyState
        icon={<Package size={28} aria-hidden />}
        title="Chưa có combo nào"
        description="Ghi danh học viên vào nhiều khoá trước, rồi gộp chúng thành một combo giảm giá."
      />
    );
  }
  return (
    <>
      <CombosTable rows={page.items} />
      <Pagination
        page={page.page}
        totalPages={page.totalPages}
        totalItems={page.totalItems}
        onPageChange={controller.setPage}
        itemLabel="combo"
      />
    </>
  );
}
```

- [ ] **Step 9: Mở rộng barrel `modules/billing/index.ts`**

```ts
export { CombosPage } from "@/modules/billing/pages/combos-page";
```

(giữ thứ tự alphabet với các export sẵn có)

- [ ] **Step 10: Chạy typecheck + lint**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: cả hai PASS, 0 lỗi. Kiểm thêm bằng mắt: `create-combo-dialog.tsx` < 200 dòng (đã tách 2 sub-component), `combos-page.tsx` < 200 dòng, không file nào có nested ternary.

- [ ] **Step 11: Commit**

```bash
git add frontend/src/shared/constants/app-routes.ts frontend/src/modules/billing/
git commit -m "feat(frontend): add combo list page and combo creation dialog"
```

---

### Task 20: Trang chi tiết combo + phát hành đợt thu của combo

**Files:**
- Create: `frontend/src/modules/billing/hooks/use-combo-detail-controller.ts`
- Create: `frontend/src/modules/billing/ui/combo-courses-table.tsx`
- Create: `frontend/src/modules/billing/ui/combo-invoice-dialog.tsx`
- Create: `frontend/src/modules/billing/pages/combo-detail-page.tsx`
- Modify: `frontend/src/modules/billing/model/billing-forms.ts`
- Modify: `frontend/src/modules/billing/index.ts`

**Interfaces:**
- Consumes: `useComboDetail` (Task 18); `useCreateComboInvoice`/`useCancelCombo` (Task 19); `InvoicesTable` (đã có, đã null-safe từ Task 17); `buildComboDetailPath`/`APP_ROUTE.comboDetail` (Task 19 Step 5); `ACCESS_RULE`.
- Produces:
  - `comboInvoiceFormSchema` — Zod form cho một đợt thu combo
  - `useComboDetailController(comboId)`
  - `<ComboCoursesTable rows={...} />`, `<ComboInvoiceDialog comboId open onClose remaining />`, `<ComboDetailPage />`

> Lịch sử thanh toán của từng đợt vẫn xem ở trang chi tiết hoá đơn có sẵn (`InvoicesTable` đã có nút "Xem chi tiết" dẫn sang đó) — không nhân bản `PaymentsTable` ở đây, vì một đợt thu của combo là một `Invoice` bình thường và trang chi tiết hoá đơn đã làm đúng việc đó (Review Focus #9).

- [ ] **Step 1: Thêm form schema cho đợt thu combo**

Trong `frontend/src/modules/billing/model/billing-forms.ts`, thêm:

```ts
export const comboInvoiceFormSchema = z.object({
  amount: z
    .string()
    .min(1, "Nhập số tiền")
    .transform((value) => Number(value))
    .refine((value) => Number.isFinite(value) && value > 0, "Số tiền phải lớn hơn 0"),
  dueDate: z.string().min(1, "Chọn hạn thanh toán của đợt này"),
});
```

- [ ] **Step 2: Viết controller**

Tạo `frontend/src/modules/billing/hooks/use-combo-detail-controller.ts`:

```ts
import { INVOICE_STATUS, useComboDetail } from "@/entities/billing";
import { useCancelCombo } from "@/modules/billing/api/use-billing-mutations";
import { useCallback, useMemo, useState } from "react";

/** Khớp BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO - 3 đợt cho CẢ combo. */
const MAX_INSTALLMENTS_PER_COMBO = 3;

/** Toàn bộ state/query/mutation của trang chi tiết combo - ui/* chỉ render (Mandate #2). */
export function useComboDetailController(comboId: string) {
  const [invoiceDialogOpen, setInvoiceDialogOpen] = useState(false);
  const detail = useComboDetail(comboId);
  const cancelCombo = useCancelCombo(comboId);

  const invoices = detail.data?.invoices ?? [];

  // Đợt đã huỷ không chiếm chỗ và không tính vào tổng - đúng như backend tính (CreateComboInvoice).
  const liveInvoices = useMemo(
    () => invoices.filter((invoice) => invoice.status !== INVOICE_STATUS.cancelled),
    [invoices],
  );
  const invoicedAmount = liveInvoices.reduce((sum, invoice) => sum + invoice.amount, 0);
  const discountedTotal = detail.data?.combo.totalDiscountedAmount ?? 0;
  const remainingToInvoice = Math.max(discountedTotal - invoicedAmount, 0);

  const canIssueInstallment =
    detail.data !== undefined
    && liveInvoices.length < MAX_INSTALLMENTS_PER_COMBO
    && remainingToInvoice > 0;
  // Backend chỉ cho huỷ khi combo chưa có hoá đơn nào, KỂ CẢ hoá đơn đã huỷ (Review Focus #6).
  const canCancel = detail.data !== undefined && invoices.length === 0;

  const openInvoiceDialog = useCallback(() => setInvoiceDialogOpen(true), []);
  const closeInvoiceDialog = useCallback(() => setInvoiceDialogOpen(false), []);

  const onCancelCombo = useCallback(() => {
    void cancelCombo.mutateAsync();
  }, [cancelCombo]);

  return {
    detail,
    liveInstallmentCount: liveInvoices.length,
    maxInstallments: MAX_INSTALLMENTS_PER_COMBO,
    invoicedAmount,
    remainingToInvoice,
    canIssueInstallment,
    canCancel,
    invoiceDialogOpen,
    openInvoiceDialog,
    closeInvoiceDialog,
    onCancelCombo,
    isCancelling: cancelCombo.isPending,
    cancelError: cancelCombo.error,
  };
}
```

- [ ] **Step 3: Viết `ComboCoursesTable`**

Tạo `frontend/src/modules/billing/ui/combo-courses-table.tsx`:

```tsx
import type { ComboEnrollment } from "@/entities/billing";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";

export interface ComboCoursesTableProps {
  readonly rows: readonly ComboEnrollment[];
}

/** Học phí gốc ở đây là SNAPSHOT lúc tạo combo - đổi giá khoá học về sau không làm đổi con số này. */
export function ComboCoursesTable({ rows }: ComboCoursesTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((member) => (
        <li
          key={member.id}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_auto_auto]"
        >
          <p className="truncate text-sm text-mist-100">Khoá {member.courseId.slice(0, 8)}</p>
          <Badge tone="neutral">Ghi danh {member.enrollmentId.slice(0, 8)}</Badge>
          <p className="text-sm text-mist-100">{formatter.count(member.originalTuitionFee)} đ</p>
        </li>
      ))}
    </ul>
  );
}
```

- [ ] **Step 4: Viết `ComboInvoiceDialog`**

Tạo `frontend/src/modules/billing/ui/combo-invoice-dialog.tsx`:

```tsx
import { useCreateComboInvoice } from "@/modules/billing/api/use-billing-mutations";
import { comboInvoiceFormSchema } from "@/modules/billing/model/billing-forms";
import { formatter } from "@/shared/lib/format";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface ComboInvoiceDialogProps {
  readonly comboId: string;
  readonly open: boolean;
  readonly onClose: () => void;
  readonly remainingToInvoice: number;
  readonly installmentNumber: number;
  readonly maxInstallments: number;
}

export function ComboInvoiceDialog({
  comboId,
  open,
  onClose,
  remainingToInvoice,
  installmentNumber,
  maxInstallments,
}: ComboInvoiceDialogProps) {
  const createComboInvoice = useCreateComboInvoice();

  const form = useZodForm({
    schema: comboInvoiceFormSchema,
    initialValues: { amount: "", dueDate: "" },
    onSubmit: async (values) => {
      await createComboInvoice.mutateAsync({ comboId, amount: values.amount, dueDate: values.dueDate });
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title={`Phát hành đợt ${installmentNumber}/${maxInstallments}`}
      description="Tổng các đợt của combo không vượt số tiền sau giảm giá."
    >
      <form id="combo-invoice-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField
          label="Số tiền đợt này (VND)"
          htmlFor="combo-invoice-amount"
          hint={`Còn có thể phát hành ${formatter.count(remainingToInvoice)} đ.`}
          error={form.fieldErrors.amount}
        >
          <GlassInput
            id="combo-invoice-amount"
            type="number"
            min={1}
            max={remainingToInvoice}
            step={1000}
            autoFocus
            value={form.values.amount}
            invalid={form.fieldErrors.amount !== undefined}
            onChange={(event) => form.setValue("amount", event.target.value)}
          />
        </FormField>

        <FormField label="Hạn thanh toán" htmlFor="combo-invoice-due-date" error={form.fieldErrors.dueDate}>
          <GlassInput
            id="combo-invoice-due-date"
            type="date"
            value={form.values.dueDate}
            invalid={form.fieldErrors.dueDate !== undefined}
            onChange={(event) => form.setValue("dueDate", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="combo-invoice-form" loading={form.isSubmitting}>
          Phát hành
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

- [ ] **Step 5: Viết `ComboDetailPage`**

Tạo `frontend/src/modules/billing/pages/combo-detail-page.tsx`:

```tsx
import { Can, RequirePermission } from "@/entities/permission";
import { useComboDetailController } from "@/modules/billing/hooks/use-combo-detail-controller";
import { ComboCoursesTable } from "@/modules/billing/ui/combo-courses-table";
import { ComboInvoiceDialog } from "@/modules/billing/ui/combo-invoice-dialog";
import { InvoicesTable } from "@/modules/billing/ui/invoices-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { Ban, Plus, Receipt } from "lucide-react";
import { useParams } from "react-router-dom";

export function ComboDetailPage() {
  const { comboId = "" } = useParams<{ comboId: string }>();
  const controller = useComboDetailController(comboId);

  return (
    <RequirePermission {...ACCESS_RULE.readInvoice}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Chi tiết combo"
          description="Các khoá trong combo, số tiền sau giảm giá và các đợt thu đã phát hành."
        />

        {controller.detail.isError ? <ErrorNotice error={controller.detail.error} /> : null}
        {controller.detail.isPending ? <Skeleton className="h-40" /> : null}

        {controller.detail.data ? (
          <>
            <GlassPanel className="flex flex-col gap-4">
              <div className="flex flex-wrap items-center gap-3">
                <p className="text-sm text-mist-100">
                  {controller.detail.data.combo.courseCount} khoá
                </p>
                <Badge tone="accent">-{controller.detail.data.combo.discountPercent}%</Badge>
                <p className="text-xs text-mist-500">Hạn {controller.detail.data.combo.dueDate}</p>
                <p className="text-xs text-mist-500">
                  Đợt {controller.liveInstallmentCount}/{controller.maxInstallments}
                </p>
              </div>
              <dl className="grid grid-cols-1 gap-3 sm:grid-cols-4">
                <AmountCell label="Tổng gốc" value={controller.detail.data.combo.totalOriginalAmount} />
                <AmountCell
                  label="Phải thu sau giảm"
                  value={controller.detail.data.combo.totalDiscountedAmount}
                />
                <AmountCell label="Đã phát hành" value={controller.invoicedAmount} />
                <AmountCell label="Chưa phát hành" value={controller.remainingToInvoice} />
              </dl>
              <Can {...ACCESS_RULE.createInvoice}>
                <ComboActions controller={controller} />
              </Can>
            </GlassPanel>

            <GlassPanel className="flex flex-col gap-4">
              <h2 className="text-sm font-semibold text-mist-100">Khoá trong combo</h2>
              <ComboCoursesTable rows={controller.detail.data.enrollments} />
            </GlassPanel>

            <GlassPanel className="flex flex-col gap-4">
              <h2 className="text-sm font-semibold text-mist-100">Các đợt thu</h2>
              <ComboInvoicesSection controller={controller} />
            </GlassPanel>

            <ComboInvoiceDialog
              comboId={comboId}
              open={controller.invoiceDialogOpen}
              onClose={controller.closeInvoiceDialog}
              remainingToInvoice={controller.remainingToInvoice}
              installmentNumber={controller.liveInstallmentCount + 1}
              maxInstallments={controller.maxInstallments}
            />
          </>
        ) : null}
      </div>
    </RequirePermission>
  );
}

function AmountCell({ label, value }: { readonly label: string; readonly value: number }) {
  return (
    <div className="glass rounded-2xl p-3">
      <dt className="text-xs text-mist-500">{label}</dt>
      <dd className="text-sm text-mist-100">{formatter.count(value)} đ</dd>
    </div>
  );
}

/** Tách ra để trang không rẽ nhánh trong JSX (Mandate #3). */
function ComboActions({
  controller,
}: {
  readonly controller: ReturnType<typeof useComboDetailController>;
}) {
  return (
    <div className="flex flex-col gap-2">
      {controller.cancelError ? <ErrorNotice error={controller.cancelError} /> : null}
      <div className="flex flex-wrap gap-2">
        <GlassButton
          size="sm"
          disabled={!controller.canIssueInstallment}
          onClick={controller.openInvoiceDialog}
          icon={<Plus size={14} aria-hidden />}
        >
          Phát hành đợt thu
        </GlassButton>
        <GlassButton
          variant="ghost"
          size="sm"
          disabled={!controller.canCancel || controller.isCancelling}
          onClick={controller.onCancelCombo}
          icon={<Ban size={14} aria-hidden />}
        >
          Huỷ combo
        </GlassButton>
      </div>
      {controller.canCancel ? null : (
        <p className="text-xs text-mist-500">
          Combo đã phát hành hoá đơn nên không huỷ được nữa.
        </p>
      )}
    </div>
  );
}

/** Tái dùng InvoicesTable: một đợt thu của combo là một Invoice bình thường, bấm "Xem chi tiết" là
 * sang đúng trang chi tiết hoá đơn có sẵn để thu online/thủ công (Review Focus #9). */
function ComboInvoicesSection({
  controller,
}: {
  readonly controller: ReturnType<typeof useComboDetailController>;
}) {
  const invoices = controller.detail.data?.invoices ?? [];
  if (invoices.length === 0) {
    return (
      <EmptyState
        icon={<Receipt size={28} aria-hidden />}
        title="Chưa phát hành đợt thu nào"
        description="Phát hành đợt đầu tiên cho combo này; tối đa 3 đợt, tổng không vượt số tiền sau giảm."
      />
    );
  }
  return <InvoicesTable rows={invoices} />;
}
```

- [ ] **Step 6: Mở rộng barrel `modules/billing/index.ts`**

```ts
export { ComboDetailPage } from "@/modules/billing/pages/combo-detail-page";
```

- [ ] **Step 7: Chạy typecheck + lint**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: cả hai PASS, 0 lỗi. Kiểm bằng `wc -l src/modules/billing/pages/combo-detail-page.tsx`: phải dưới 200 dòng (đã tách 3 sub-component `AmountCell`/`ComboActions`/`ComboInvoicesSection` để đạt được điều đó); `useComboDetailController` complexity < 15.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/modules/billing/
git commit -m "feat(frontend): add combo detail page with installment issuing"
```

---

### Task 21: Trang cấu hình bậc giảm giá + đăng ký route và menu

**Files:**
- Create: `frontend/src/modules/billing/api/use-combo-discount-tier-mutations.ts`
- Create: `frontend/src/modules/billing/hooks/use-combo-discount-tiers-page-controller.ts`
- Create: `frontend/src/modules/billing/ui/combo-discount-tiers-table.tsx`
- Create: `frontend/src/modules/billing/ui/combo-discount-tier-dialog.tsx`
- Create: `frontend/src/modules/billing/pages/combo-discount-tiers-page.tsx`
- Modify: `frontend/src/modules/billing/index.ts`
- Modify: `frontend/src/app/router/app-router.tsx`
- Modify: `frontend/src/app/layouts/nav-items.ts`

**Interfaces:**
- Consumes: `useComboDiscountTiers`, `billingApi.createComboDiscountTier`/`updateComboDiscountTier`, `billingKeys.discountTiers`, type `ComboDiscountTier`/`CreateComboDiscountTierPayload`/`UpdateComboDiscountTierPayload` (Task 18); `comboDiscountTierFormSchema` (Task 19 Step 2); `APP_ROUTE.{combos,comboDetail,comboDiscountTiers}` (Task 19 Step 5); `CombosPage`/`ComboDetailPage` (Task 19/20).
- Produces:
  - `useCreateComboDiscountTier()`, `useUpdateComboDiscountTier()` (mutate nhận `{ tierId, payload }`)
  - `useComboDiscountTiersPageController()`
  - `<ComboDiscountTiersTable rows onToggleActive isMutating />`, `<ComboDiscountTierDialog open onClose />`, `<ComboDiscountTiersPage />`
  - 3 route: `/admin/billing/combos`, `/admin/billing/combos/:comboId`, `/admin/billing/combo-discount-tiers`
  - 2 mục menu: "Combo khoá học", "Bậc giảm giá combo"

- [ ] **Step 1: Viết hai mutation hook**

Tạo `frontend/src/modules/billing/api/use-combo-discount-tier-mutations.ts`:

```ts
import {
  billingApi,
  billingKeys,
  type CreateComboDiscountTierPayload,
  type UpdateComboDiscountTierPayload,
} from "@/entities/billing";
import { useMutation, useQueryClient } from "@tanstack/react-query";

/** Đổi bậc giảm giá không ảnh hưởng combo đã tạo (Combo.discountPercent là snapshot), nên chỉ cần
 * dọn đúng cache danh sách bậc - không invalidate cả billingKeys.all như các mutation hoá đơn. */
function useDiscountTiersInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: billingKeys.discountTiers() });
  };
}

export function useCreateComboDiscountTier() {
  const invalidate = useDiscountTiersInvalidation();
  return useMutation({
    mutationFn: (payload: CreateComboDiscountTierPayload) => billingApi.createComboDiscountTier(payload),
    onSuccess: invalidate,
  });
}

export function useUpdateComboDiscountTier() {
  const invalidate = useDiscountTiersInvalidation();
  return useMutation({
    mutationFn: ({ tierId, payload }: { tierId: string; payload: UpdateComboDiscountTierPayload }) =>
      billingApi.updateComboDiscountTier(tierId, payload),
    onSuccess: invalidate,
  });
}
```

- [ ] **Step 2: Viết controller**

Tạo `frontend/src/modules/billing/hooks/use-combo-discount-tiers-page-controller.ts`:

```ts
import { useComboDiscountTiers, type ComboDiscountTier } from "@/entities/billing";
import { useUpdateComboDiscountTier } from "@/modules/billing/api/use-combo-discount-tier-mutations";
import { useCallback, useState } from "react";

/** Toàn bộ state/query/mutation của trang bậc giảm giá - ui/* chỉ render (Mandate #2). */
export function useComboDiscountTiersPageController() {
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const tiers = useComboDiscountTiers();
  const updateTier = useUpdateComboDiscountTier();

  const openCreateDialog = useCallback(() => setCreateDialogOpen(true), []);
  const closeCreateDialog = useCallback(() => setCreateDialogOpen(false), []);

  /** "Xoá" một bậc là tắt active - hệ thống không có endpoint DELETE nào cho bảng này. */
  const onToggleActive = useCallback(
    (tier: ComboDiscountTier) => {
      void updateTier.mutateAsync({
        tierId: tier.id,
        payload: { discountPercent: tier.discountPercent, active: !tier.active },
      });
    },
    [updateTier],
  );

  return {
    tiers,
    createDialogOpen,
    openCreateDialog,
    closeCreateDialog,
    onToggleActive,
    isMutating: updateTier.isPending,
    mutationError: updateTier.error,
  };
}
```

- [ ] **Step 3: Viết `ComboDiscountTiersTable`**

Tạo `frontend/src/modules/billing/ui/combo-discount-tiers-table.tsx`:

```tsx
import type { ComboDiscountTier } from "@/entities/billing";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { Power } from "lucide-react";

export interface ComboDiscountTiersTableProps {
  readonly rows: readonly ComboDiscountTier[];
  readonly onToggleActive: (tier: ComboDiscountTier) => void;
  readonly isMutating: boolean;
}

/** Không có nút xoá: tắt active là cách "xoá" một bậc (mirror Course.active). */
export function ComboDiscountTiersTable({ rows, onToggleActive, isMutating }: ComboDiscountTiersTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((tier) => (
        <li
          key={tier.id}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto]"
        >
          <p className="truncate text-sm text-mist-100">Từ {tier.minCourseCount} khoá trở lên</p>
          <p className="text-sm text-mist-100">giảm {tier.discountPercent}%</p>
          <Badge tone={tier.active ? "positive" : "neutral"}>
            {tier.active ? "Đang áp dụng" : "Đã tắt"}
          </Badge>
          <Can {...ACCESS_RULE.updateInvoice}>
            <GlassButton
              variant="ghost"
              size="sm"
              disabled={isMutating}
              onClick={() => onToggleActive(tier)}
              icon={<Power size={14} aria-hidden />}
            >
              {tier.active ? "Tắt bậc này" : "Bật lại"}
            </GlassButton>
          </Can>
        </li>
      ))}
    </ul>
  );
}
```

- [ ] **Step 4: Viết `ComboDiscountTierDialog`**

Tạo `frontend/src/modules/billing/ui/combo-discount-tier-dialog.tsx`:

```tsx
import { MIN_COMBO_ENROLLMENTS } from "@/entities/billing";
import { useCreateComboDiscountTier } from "@/modules/billing/api/use-combo-discount-tier-mutations";
import { comboDiscountTierFormSchema } from "@/modules/billing/model/billing-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface ComboDiscountTierDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function ComboDiscountTierDialog({ open, onClose }: ComboDiscountTierDialogProps) {
  const createTier = useCreateComboDiscountTier();

  const form = useZodForm({
    schema: comboDiscountTierFormSchema,
    initialValues: { minCourseCount: "", discountPercent: "" },
    onSubmit: async (values) => {
      await createTier.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Thêm bậc giảm giá"
      description="Combo được áp bậc có mốc số khoá cao nhất mà nó đạt được. Mỗi mốc chỉ có một bậc."
    >
      <form id="combo-tier-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField
          label="Số khoá tối thiểu"
          htmlFor="tier-min-course-count"
          hint={`Combo tối thiểu ${MIN_COMBO_ENROLLMENTS} khoá, nên mốc nhỏ nhất có nghĩa là ${MIN_COMBO_ENROLLMENTS}.`}
          error={form.fieldErrors.minCourseCount}
        >
          <GlassInput
            id="tier-min-course-count"
            type="number"
            min={MIN_COMBO_ENROLLMENTS}
            step={1}
            autoFocus
            value={form.values.minCourseCount}
            invalid={form.fieldErrors.minCourseCount !== undefined}
            onChange={(event) => form.setValue("minCourseCount", event.target.value)}
          />
        </FormField>

        <FormField label="% giảm" htmlFor="tier-discount-percent" error={form.fieldErrors.discountPercent}>
          <GlassInput
            id="tier-discount-percent"
            type="number"
            min={0}
            max={100}
            step={0.5}
            value={form.values.discountPercent}
            invalid={form.fieldErrors.discountPercent !== undefined}
            onChange={(event) => form.setValue("discountPercent", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="combo-tier-form" loading={form.isSubmitting}>
          Thêm bậc
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

- [ ] **Step 5: Viết `ComboDiscountTiersPage`**

Tạo `frontend/src/modules/billing/pages/combo-discount-tiers-page.tsx`:

```tsx
import { Can, RequirePermission } from "@/entities/permission";
import { useComboDiscountTiersPageController } from "@/modules/billing/hooks/use-combo-discount-tiers-page-controller";
import { ComboDiscountTierDialog } from "@/modules/billing/ui/combo-discount-tier-dialog";
import { ComboDiscountTiersTable } from "@/modules/billing/ui/combo-discount-tiers-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { Percent, Plus } from "lucide-react";

export function ComboDiscountTiersPage() {
  const controller = useComboDiscountTiersPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readInvoice}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Bậc giảm giá combo"
          description="Số khoá tối thiểu → % giảm. Chưa có bậc nào phù hợp thì hệ thống từ chối tạo combo, không tự giảm 0%."
          actions={
            <Can {...ACCESS_RULE.createInvoice}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Thêm bậc
              </GlassButton>
            </Can>
          }
        />

        {controller.tiers.isError ? <ErrorNotice error={controller.tiers.error} /> : null}
        {controller.mutationError ? <ErrorNotice error={controller.mutationError} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {controller.tiers.isPending ? <Skeleton className="h-16" /> : null}
          {controller.tiers.data ? <TiersListSection controller={controller} /> : null}
        </GlassPanel>

        <ComboDiscountTierDialog
          open={controller.createDialogOpen}
          onClose={controller.closeCreateDialog}
        />
      </div>
    </RequirePermission>
  );
}

/** Tách nhánh rỗng/có dữ liệu ra component riêng - tránh nested ternary trong JSX (Mandate #3). */
function TiersListSection({
  controller,
}: {
  readonly controller: ReturnType<typeof useComboDiscountTiersPageController>;
}) {
  const tiers = controller.tiers.data ?? [];
  if (tiers.length === 0) {
    return (
      <EmptyState
        icon={<Percent size={28} aria-hidden />}
        title="Chưa cấu hình bậc giảm giá nào"
        description="Thêm bậc đầu tiên (ví dụ: từ 2 khoá giảm 10%) để kế toán tạo được combo."
      />
    );
  }
  return (
    <ComboDiscountTiersTable
      rows={tiers}
      onToggleActive={controller.onToggleActive}
      isMutating={controller.isMutating}
    />
  );
}
```

- [ ] **Step 6: Mở rộng barrel + đăng ký route + menu**

`frontend/src/modules/billing/index.ts` — thêm:

```ts
export { ComboDiscountTiersPage } from "@/modules/billing/pages/combo-discount-tiers-page";
```

`frontend/src/app/router/app-router.tsx` — thêm ba `lazy` sau `InvoiceDetailPage`:

```tsx
const CombosPage = lazy(() => import("@/modules/billing").then((module) => ({ default: module.CombosPage })));
const ComboDetailPage = lazy(() =>
  import("@/modules/billing").then((module) => ({ default: module.ComboDetailPage })),
);
const ComboDiscountTiersPage = lazy(() =>
  import("@/modules/billing").then((module) => ({ default: module.ComboDiscountTiersPage })),
);
```

và ba `<Route>` trong khối `<RequireAuth />`, ngay sau `APP_ROUTE.invoiceDetail`:

```tsx
          <Route path={APP_ROUTE.combos} element={<CombosPage />} />
          <Route path={APP_ROUTE.comboDetail} element={<ComboDetailPage />} />
          <Route path={APP_ROUTE.comboDiscountTiers} element={<ComboDiscountTiersPage />} />
```

`frontend/src/app/layouts/nav-items.ts` — thêm hai mục vào section "Điều hành & Nghiệp vụ", ngay sau mục `APP_ROUTE.invoices`:

```ts
      { path: APP_ROUTE.combos, label: "Combo khoá học", requirement: ACCESS_RULE.readInvoice },
      { path: APP_ROUTE.comboDiscountTiers, label: "Bậc giảm giá combo", requirement: ACCESS_RULE.readInvoice },
```

(Không thêm mục cho `comboDetail` — đó là trang con, vào từ bảng danh sách, mirror cách `invoiceDetail`/`payrollRunDetail` đã làm.)

- [ ] **Step 7: Chạy typecheck + lint + build**

Run: `cd frontend && npm run typecheck && npm run lint && npm run build`
Expected: cả ba PASS, 0 lỗi, build sinh ra chunk riêng cho ba trang mới (lazy import).

- [ ] **Step 8: Commit**

```bash
git add frontend/src/modules/billing/ frontend/src/app/
git commit -m "feat(frontend): add combo discount tier admin page and register combo routes"
```

---

### Task 22: Kiểm tra toàn bộ — backend suite, ranh giới module, frontend, rà soát Mandate

**Files:**
- Không tạo/sửa file nào. Task này chỉ chạy và đọc kết quả. Nếu một bước đỏ, sửa ở **task sở hữu** phần code đó rồi chạy lại cả Task 22 từ đầu.

**Interfaces:**
- Consumes: toàn bộ 21 task trước.
- Produces: bằng chứng để tuyên bố xong — không có production code mới.

- [ ] **Step 1: Biên dịch sạch cả main và test**

Run: `cd backend && mvn -q clean compile test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Chạy kiểm tra ranh giới Spring Modulith**

Run: `cd backend && mvn test -Dtest=ModularityTests`
Expected: PASS, 2 test. Đặc biệt `everyDomainAndIntegrationPackageIsADetectedModule` phải xanh **mà không sửa danh sách module** — plan này không thêm module nào, chỉ mở rộng `modules.billing` và thêm một filter cho `modules.enrollment`.

- [ ] **Step 3: Chạy toàn bộ test backend**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS, 0 failure, 0 error. Trong đó phải thấy các class mới: `ComboRepositoryIT`, `CreateComboTest`, `CreateComboInvoiceTest`, `CancelComboTest`, `ComboReadUseCasesTest`, `ComboDiscountTierUseCasesTest`, `ComboAdminControllerIT`, `ComboDiscountTierAdminControllerIT`, `ComboPaymentFlowIT`, `AuditEventListenersTest` — và **mọi test Phase 3 vẫn xanh** (`CreateInvoiceTest`, `CancelInvoiceTest`, `HandlePaymentCallbackTest`, `InitiateOnlinePaymentTest`, `RecordManualPaymentTest`, `MarkOverdueInvoicesTest`, `BillingReadUseCasesTest`, `BillingRepositoryIT`, `InvoiceAdminControllerIT`, `PaymentCallbackControllerIT`, `EnrollmentRepositoryIT`, `EnrollmentAdminControllerIT`, `ListEnrollmentsTest`).

- [ ] **Step 4: Chạy toàn bộ kiểm tra frontend**

Run: `cd frontend && npm run typecheck && npm run lint && npm run build`
Expected: cả ba PASS, 0 lỗi, 0 warning về `boundaries/dependencies` hay `complexity`.

- [ ] **Step 5: Rà soát Mandate #1 — không hardcode string**

Run:
```bash
cd /Users/hoangdieu/PycharmProjects/EducationErp
grep -rn '"INVOICE"\|"CREATE"\|"READ"\|"UPDATE"' backend/src/main/java/com/eduerp/modules/billing/web/ || echo "OK: khong co chuoi quyen tho trong web/"
grep -rn '"/api/billing' frontend/src/modules frontend/src/entities || echo "OK: moi path di qua API_ROUTE"
grep -rn 'resource: "' frontend/src/modules/billing || echo "OK: moi barrier dung ACCESS_RULE"
```
Expected: ba dòng `OK: ...`. Nếu có kết quả grep, chuyển chuỗi đó về `AccessConstants`/`API_ROUTE`/`ACCESS_RULE` tại task sở hữu.

- [ ] **Step 6: Rà soát Mandate #2 + #3 — tách logic khỏi render, giới hạn dòng**

Run:
```bash
cd /Users/hoangdieu/PycharmProjects/EducationErp/frontend
wc -l src/modules/billing/pages/*.tsx src/modules/billing/ui/*.tsx | sort -rn | head -8
grep -rn 'onSubmit={async\|onClick={async' src/modules/billing || echo "OK: khong co handler async inline"
grep -rn 'useQuery(\|useMutation(' src/modules/billing/ui src/modules/billing/pages || echo "OK: query/mutation chi nam trong hooks//api/"
```
Expected: mọi file dưới 200 dòng (trừ dòng `total`); hai dòng `OK: ...`.

- [ ] **Step 7: Rà soát các Global Constraint không diễn đạt được bằng test**

Run:
```bash
cd /Users/hoangdieu/PycharmProjects/EducationErp
# 808528b = commit spec, trang thai nhanh truoc khi plan nay bat dau.
git diff --stat 808528b -- backend/src/main/java/com/eduerp/modules/enrollment/EnrollmentManagement.java
git diff --stat 808528b -- backend/src/main/java/com/eduerp/modules/access/AccessConstants.java
git diff --stat 808528b -- backend/src/test/java/com/eduerp/ModularityTests.java
git diff --name-only 808528b -- backend/src/main/resources/db/migration/
```
Expected:
- `EnrollmentManagement.java`: **không có output** (facade không thêm method ghi nào).
- `AccessConstants.java`: **không có output** (không thêm resource/permission RBAC nào).
- `ModularityTests.java`: **không có output** (danh sách 19 module không đổi).
- danh sách migration mới **đúng hai file**: `V21__create_combo_tables.sql` và `V22__add_invoice_combo_link.sql`.

- [ ] **Step 8: Đối chiếu Review Focus — 9 dòng, 9 test có thật**

Run:
```bash
cd /Users/hoangdieu/PycharmProjects/EducationErp/backend
grep -rn "rejectsAComboMixingTwoDifferentStudents\|rejectsWhenTheFirstEnrollmentItselfBelongsToAnotherStudent" src/test/java | head -2   # RF 1
grep -rn "rejectsAComboOfASingleEnrollment\|rejectsAComboBuiltFromTheSameEnrollmentTwice" src/test/java | head -2                       # RF 2
grep -rn "rejectsTheSameEnrollmentInTwoLiveCombosAtDatabaseLevel\|translatesADatabaseRaceOnASharedEnrollment" src/test/java | head -2    # RF 3
grep -rn "rejectsACombinationWithNoConfiguredDiscountTier" src/test/java | head -1                                                      # RF 4
grep -rn "rejectsAnInstallmentThatPushesTheTotalPastTheDiscountedAmount" src/test/java | head -1                                         # RF 5
grep -rn "countsCancelledInvoicesTooWhenDecidingWhetherTheComboMayBeDeleted" src/test/java | head -1                                     # RF 6
grep -rn "withdrawingFromOneCourseLeavesTheComboAndItsInvoicesUntouched" src/test/java | head -1                                         # RF 7
grep -rn "keepsSingleCourseInvoicesValidAfterTheComboMigration" src/test/java | head -1                                                  # RF 8
grep -rn "recordsManualPaymentsOnAComboInvoiceExactlyLikeASingleCourseInvoice\|marksAnOverdueComboInvoiceJustLikeAnyOtherInvoice" src/test/java | head -2  # RF 9
```
Expected: cả 9 nhóm đều có kết quả. Nếu một nhóm rỗng, test đó chưa được viết — quay lại task sở hữu (ghi trong mục Review Focus ở đầu plan) và viết nó.

- [ ] **Step 9: Chạy ứng dụng một lần để chắc Flyway migrate được trên DB thật**

Run: `cd backend && ./mvnw spring-boot:run` (dừng bằng Ctrl+C sau khi thấy log khởi động xong)
Expected: log Flyway có `Migrating schema "public" to version "21 - create combo tables"` và `"22 - add invoice combo link"`, rồi `Started EduErpApplication`. Nếu Flyway báo `out of order` hoặc checksum mismatch, nghĩa là có file migration đánh version sai — sửa ở Task 1/Task 3.

- [ ] **Step 10: Commit (chỉ nếu Step 1-9 làm lộ ra thay đổi cần sửa)**

```bash
git status --short
# Nếu sạch thì không commit gì - mọi task trước đã commit xong.
```

---

## Ghi chú thực thi

- **Thứ tự task là thứ tự phụ thuộc thật**, không phải gợi ý: Task 3 cần bảng `combos` của Task 1; Task 7 cần entity của Task 2 + exception của Task 4 + DTO của Task 6; Task 17 phải xong trước Task 20 (vì `ComboDetailPage` tái dùng `InvoicesTable` đã null-safe).
- **Mỗi task commit một lần** theo Conventional Commits tiếng Anh, **không có trailer `Co-Authored-By`**.
- **Nếu một test ở Task 16 hoặc Task 22 đỏ**, đừng vá tại chỗ: tìm task sở hữu đoạn code đó, sửa ở đó, chạy lại test của task đó rồi chạy lại task kiểm tra.
