# Phân hệ 2.6 — Nhân sự & Tiền lương Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thêm module `modules.payroll` (hợp đồng lao động + chốt lương theo kỳ, có luồng duyệt 3 trạng thái) và module tích hợp `integrations.storage` (lưu file hợp đồng PDF qua S3-compatible storage), đầy đủ RBAC, audit, và giao diện quản trị.

**Architecture:** Backend mirror 1:1 khuôn `modules.courses`/`modules.teachers` (facade tối thiểu, `usecase/` một class một nghiệp vụ, `web/` controller mỏng, `internal/model+repository+rules` che bởi Spring Modulith). `integrations.storage` mirror `integrations.mail` (wrapper mỏng quanh AWS SDK v2, cấu hình qua `@ConfigurationProperties`). Frontend mirror 1:1 `entities/course` + `modules/courses`, với toàn bộ state/mutation tách vào `hooks/use-*-controller.ts` theo Mandate #2.

**Tech Stack:** Spring Boot 3.4.1, Spring Modulith 1.3.1, PostgreSQL 16, AWS SDK v2 (`software.amazon.awssdk:s3`), Testcontainers (Postgres, Redis, MinIO), React 19, TanStack Query v5, Zod, react-router-dom v7.

**Spec:** docs/superpowers/specs/2026-10-03-phan-he-2-6-nhan-su-luong-giao-vien-design.md

## Global Constraints

- Hợp đồng gắn với `Account` (UUID trần, không `@ManyToOne` xuyên module) — tên/email tra qua `IdentityManagement.summariesOf(Collection<UUID>)`.
- Hai loại hợp đồng: `OFFICIAL` (lương tháng cố định, có BHXH) và `COLLABORATOR` (theo giờ dạy thực tế, không BHXH).
- BHXH/BHYT/BHTN là hằng số Java cố định: `StatutoryRates.EMPLOYER_SHARE = 0.215`, `EMPLOYEE_SHARE = 0.105` — không phải bảng DB, không đổi theo cấu hình.
- Thuế TNCN nhập tay (`incomeTaxWithheld`) — hệ thống không tự động tính biểu thuế lũy tiến.
- Lương thử việc = 85% lương chính thức, tính theo trạng thái tại **ngày đầu kỳ lương** (`year-month-01`), không chia theo ngày trong tháng.
- `COLLABORATOR` không có khái niệm thử việc — `CreateContract` từ chối nhận `probationStartDate`/`probationEndDate` khi `contractType = COLLABORATOR`.
- Một `accountId` chỉ có một `EmploymentContract` ở trạng thái `ACTIVE` tại một thời điểm.
- `PayrollRun` duy nhất theo `(year, month)` — trùng kỳ bị chặn bằng lỗi nghiệp vụ rõ ràng, không lộ constraint-violation thô.
- `Account.status = DISABLED` **không** tự động `TERMINATED` hợp đồng — hai khái niệm độc lập; `CreatePayrollRun` vẫn tính lương cho hợp đồng còn `ACTIVE` bất kể trạng thái tài khoản.
- `SubmitPayrollRunForApproval` từ chối nếu còn `Payslip` của hợp đồng `COLLABORATOR` với `hoursWorked == 0`.
- Không mở lại `PayrollRun` đã `APPROVED` — sửa sai phải tạo kỳ lương mới.
- RBAC: mọi `AccessRule` mới dùng `MINIMUM_SCOPE = ORGANIZATION` (đúng khuôn 100% rule hiện có); không thêm `RoleCodes` cứng mới; migration seed `PAYROLL:*:ORGANIZATION` chỉ cho `RoleCodes.ADMIN`.
- Không tách `modules.hr` riêng — `EmploymentContract` và `PayrollRun`/`Payslip` cùng một module `modules.payroll`.
- Frontend: không có test runner (xác nhận qua `frontend/package.json` — chỉ có `typecheck`/`lint`, không có vitest/jest). Mọi task frontend dùng `cd frontend && npm run typecheck` (và `npm run lint` ở bước cuối) làm cổng RED/GREEN thay cho unit test — đây là cổng thật duy nhất của repo này, không phải placeholder.

## Review Focus

1. Thử việc đổi giữa chừng kỳ lương: `grossPay` tính nguyên tháng theo trạng thái thử việc **tại ngày đầu kỳ** (`PayrollRules.isInProbation(contract, payPeriodStart)`), không chia theo ngày trong tháng.
2. Hợp đồng `COLLABORATOR` không được nhận `probationStartDate`/`probationEndDate` — `CreateContract` ném `InvalidContractTermsException`.
3. Hai hợp đồng `ACTIVE` cùng lúc cho một `accountId` bị chặn — `CreateContract` kiểm tra trước khi lưu, ném `ContractAlreadyActiveException`.
4. Tạo `PayrollRun` trùng `(year, month)` bị chặn ở cả DB (`UNIQUE` constraint) và usecase (`PayrollRunAlreadyExistsException`, không lộ `DataIntegrityViolationException` thô).
5. `Account.status = DISABLED` không tự động `TERMINATED` hợp đồng; `CreatePayrollRun` chỉ lọc theo `EmploymentContract.status = ACTIVE`, không truy vấn `Account.status`.

---

### Task 1: RBAC — `AccessConstants.Resources.PAYROLL` + 4 `AccessRules` + seed migration

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/access/AccessConstants.java`
- Modify: `backend/src/test/java/com/eduerp/modules/access/AccessConstantsTest.java`
- Create: `backend/src/main/resources/db/migration/V14__seed_payroll_rbac.sql`

**Interfaces:**
- Consumes: nothing (foundational).
- Produces: `AccessConstants.Resources.PAYROLL: String`, `AccessConstants.AccessRules.CREATE_PAYROLL/READ_PAYROLL/UPDATE_PAYROLL/APPROVE_PAYROLL: String` — dùng trong mọi `@PreAuthorize` của Task 10/15.

- [ ] **Step 1: Write the failing test**
Thêm method vào file test đã có (không tạo file mới, mirror đúng `courseAndClassRulesRequireOrganizationScope`):
```java
    @Test
    void payrollRulesRequireOrganizationScope() {
        assertThat(AccessConstants.AccessRules.CREATE_PAYROLL)
                .contains("PAYROLL", "CREATE", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.READ_PAYROLL)
                .contains("PAYROLL", "READ", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.UPDATE_PAYROLL)
                .contains("PAYROLL", "UPDATE", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.APPROVE_PAYROLL)
                .contains("PAYROLL", "APPROVE", "@ORGANIZATION");
    }
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=AccessConstantsTest`
Expected: FAIL — compile error: cannot find symbol `AccessConstants.AccessRules.CREATE_PAYROLL` (chưa tồn tại).
- [ ] **Step 3: Write minimal implementation**
Trong `AccessConstants.Resources`, thêm sau `STUDENT`:
```java
        public static final String PAYROLL = "PAYROLL";
```
Trong `AccessConstants.AccessRules`, thêm sau `UPDATE_STUDENT`:
```java
        public static final String CREATE_PAYROLL = CHECK_PREFIX + Resources.PAYROLL + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_PAYROLL = CHECK_PREFIX + Resources.PAYROLL + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String UPDATE_PAYROLL = CHECK_PREFIX + Resources.PAYROLL + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String APPROVE_PAYROLL = CHECK_PREFIX + Resources.PAYROLL + CHECK_SEPARATOR
                + Actions.APPROVE + MINIMUM_SCOPE + CHECK_SUFFIX;
```
Tạo `V14__seed_payroll_rbac.sql` (mirror chính xác `V13__seed_teachers_students_rbac.sql`):
```sql
INSERT INTO permissions (id, resource, action) VALUES
    (gen_random_uuid(), 'PAYROLL', 'CREATE'),
    (gen_random_uuid(), 'PAYROLL', 'READ'),
    (gen_random_uuid(), 'PAYROLL', 'UPDATE'),
    (gen_random_uuid(), 'PAYROLL', 'APPROVE');

-- V5 đã seed "Toàn quyền hệ thống" xong và đã chạy rồi - không sửa lại được, nên permission mới
-- phải tự thêm dòng gán vào đúng group đó, giống cách V10/V13 đã làm.
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000001', p.id, 'ORGANIZATION'
FROM permissions p WHERE p.resource = 'PAYROLL';
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=AccessConstantsTest`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/eduerp/modules/access/AccessConstants.java \
        backend/src/test/java/com/eduerp/modules/access/AccessConstantsTest.java \
        backend/src/main/resources/db/migration/V14__seed_payroll_rbac.sql
git commit -m "feat(payroll): add PAYROLL resource and access rules"
```

---

### Task 2: `PayrollConstants` + `PayrollException` sealed hierarchy (9 subclasses)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/payroll/PayrollConstants.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/PayrollException.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/ContractNotFoundException.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/PayrollRunNotFoundException.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/PayslipNotFoundException.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/ContractAlreadyTerminatedException.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/ContractAlreadyActiveException.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/PayrollRunNotEditableException.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/PayrollRunNotPendingApprovalException.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/InvalidContractTermsException.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/PayrollRunAlreadyExistsException.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/ContractFileNotFoundException.java`
- Test: `backend/src/test/java/com/eduerp/modules/payroll/PayrollConstantsTest.java`
- Test: `backend/src/test/java/com/eduerp/modules/payroll/PayrollExceptionTest.java`

**Interfaces:**
- Consumes: `com.eduerp.core.exception.AppException(String errorCode, HttpStatus status, String message)`.
- Produces: `PayrollConstants.ContractType{OFFICIAL,COLLABORATOR}`, `PayrollConstants.ContractStatus{ACTIVE,TERMINATED}`, `PayrollConstants.PayrollRunStatus{DRAFT,PENDING_APPROVAL,APPROVED}`, `PayrollConstants.StatutoryRates.{EMPLOYER_SHARE,EMPLOYEE_SHARE,PROBATION_RATE}: BigDecimal`, `PayrollConstants.Limits.{MAX_ALLOWANCE_NAME_LENGTH,MAX_ALLOWANCES_PER_CONTRACT}: int` — dùng trong mọi task sau. 10 exception classes dùng trong mọi usecase từ Task 9 trở đi (`ContractFileNotFoundException` dùng từ Task 10 - tạo sẵn ở đây vì `sealed...permits` của `PayrollException` phải liệt kê đủ mọi subclass tồn tại ngay khi compile).

> **Lưu ý (tự thêm so với spec mục 3.2):** Spec liệt kê 7 exception nhưng mục 6 (Review Focus #3, #4) yêu cầu lỗi rõ ràng cho "hai hợp đồng ACTIVE cùng lúc" và "trùng kỳ lương" — hai tình huống **409 CONFLICT** không nên dùng chung mã lỗi 400 `InvalidContractTermsException`. Thêm `ContractAlreadyActiveException` và `PayrollRunAlreadyExistsException` (sealed `permits` mở sẵn chỗ cho subclass thứ 10 ở Task 10 — `ContractFileNotFoundException`).

- [ ] **Step 1: Write the failing test**
```java
// backend/src/test/java/com/eduerp/modules/payroll/PayrollConstantsTest.java
package com.eduerp.modules.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PayrollConstantsTest {

    @Test
    void statutoryRatesSumToThirtyTwoPercent() {
        var total = PayrollConstants.StatutoryRates.EMPLOYER_SHARE.add(PayrollConstants.StatutoryRates.EMPLOYEE_SHARE);
        assertThat(total).isEqualTo(new BigDecimal("0.320"));
    }

    @Test
    void probationRateIsEightyFivePercent() {
        assertThat(PayrollConstants.StatutoryRates.PROBATION_RATE).isEqualTo(new BigDecimal("0.85"));
    }

    @Test
    void contractTypesHaveExactlyTwoValues() {
        assertThat(PayrollConstants.ContractType.values())
                .containsExactly(PayrollConstants.ContractType.OFFICIAL, PayrollConstants.ContractType.COLLABORATOR);
    }

    @Test
    void payrollRunStatusTransitionsAreDefinedInOrder() {
        assertThat(PayrollConstants.PayrollRunStatus.values()).containsExactly(
                PayrollConstants.PayrollRunStatus.DRAFT, PayrollConstants.PayrollRunStatus.PENDING_APPROVAL,
                PayrollConstants.PayrollRunStatus.APPROVED);
    }
}
```
```java
// backend/src/test/java/com/eduerp/modules/payroll/PayrollExceptionTest.java
package com.eduerp.modules.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PayrollExceptionTest {

    @Test
    void contractNotFoundIsNotFound() {
        var ex = new ContractNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_CONTRACT_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void payrollRunNotFoundIsNotFound() {
        var ex = new PayrollRunNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_RUN_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void payslipNotFoundIsNotFound() {
        var ex = new PayslipNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_PAYSLIP_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void contractAlreadyTerminatedIsConflict() {
        var ex = new ContractAlreadyTerminatedException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_CONTRACT_ALREADY_TERMINATED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void contractAlreadyActiveIsConflict() {
        var ex = new ContractAlreadyActiveException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_CONTRACT_ALREADY_ACTIVE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void payrollRunNotEditableIsConflict() {
        var ex = new PayrollRunNotEditableException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_RUN_NOT_EDITABLE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void payrollRunNotPendingApprovalIsConflict() {
        var ex = new PayrollRunNotPendingApprovalException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_RUN_NOT_PENDING_APPROVAL");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidContractTermsIsBadRequest() {
        var ex = new InvalidContractTermsException("Hợp đồng chính thức phải có baseSalary");
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_INVALID_CONTRACT_TERMS");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void payrollRunAlreadyExistsIsConflict() {
        var ex = new PayrollRunAlreadyExistsException(2026, 1);
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_RUN_ALREADY_EXISTS");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void contractFileNotFoundIsNotFound() {
        var ex = new ContractFileNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("PAYROLL_CONTRACT_FILE_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=PayrollConstantsTest,PayrollExceptionTest`
Expected: FAIL — compile error: cannot find symbol `PayrollConstants` / exception classes (package `com.eduerp.modules.payroll` chưa tồn tại).
- [ ] **Step 3: Write minimal implementation**
```java
// backend/src/main/java/com/eduerp/modules/payroll/PayrollConstants.java
package com.eduerp.modules.payroll;

import java.math.BigDecimal;

/**
 * Hằng số dùng chung của module payroll, nằm ở base package (không phải {@code internal}) vì
 * {@code dto} và {@code internal.model} cùng tier, cả hai cần tham chiếu - mirror
 * {@code CoursesConstants}.
 */
public final class PayrollConstants {

    private PayrollConstants() {
    }

    public enum ContractType {
        OFFICIAL, COLLABORATOR
    }

    public enum ContractStatus {
        ACTIVE, TERMINATED
    }

    public enum PayrollRunStatus {
        DRAFT, PENDING_APPROVAL, APPROVED
    }

    public static final class Limits {
        private Limits() {
        }

        public static final int MAX_ALLOWANCE_NAME_LENGTH = 100;
        public static final int MAX_ALLOWANCES_PER_CONTRACT = 20;
    }

    /**
     * BHXH/BHYT/BHTN (spec mục 2): tổng 32% lương tham gia BHXH, chỉ áp dụng hợp đồng OFFICIAL.
     * Hằng số Java, không phải bảng DB - tỷ lệ bắt buộc hiếm khi đổi, mỗi lần đổi là thay đổi luật
     * lớn cần deploy lại toàn hệ thống dù sao.
     */
    public static final class StatutoryRates {
        private StatutoryRates() {
        }

        public static final BigDecimal EMPLOYER_SHARE = new BigDecimal("0.215");
        public static final BigDecimal EMPLOYEE_SHARE = new BigDecimal("0.105");
        public static final BigDecimal PROBATION_RATE = new BigDecimal("0.85");
    }

    /** Không cần cache namespace nào ở V1 (không có read nóng cần cache) - để trống theo khuôn tier 0. */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/PayrollException.java
package com.eduerp.modules.payroll;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module payroll phát ra. */
public sealed class PayrollException extends AppException
        permits ContractNotFoundException, PayrollRunNotFoundException, PayslipNotFoundException,
        ContractAlreadyTerminatedException, ContractAlreadyActiveException, PayrollRunNotEditableException,
        PayrollRunNotPendingApprovalException, InvalidContractTermsException, PayrollRunAlreadyExistsException,
        ContractFileNotFoundException {

    protected PayrollException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
```
> `ContractFileNotFoundException` được `permits` trước (chưa tạo file ở task này) để tránh phải sửa lại danh sách `permits` — nhưng Java sealed class bắt buộc mọi subclass liệt kê trong `permits` phải tồn tại khi compile. Vì vậy tạo luôn file rỗng tối thiểu ở đây, nội dung đầy đủ dùng từ Task 10:
```java
// backend/src/main/java/com/eduerp/modules/payroll/ContractFileNotFoundException.java
package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ContractFileNotFoundException extends PayrollException {
    public ContractFileNotFoundException(UUID contractId) {
        super("PAYROLL_CONTRACT_FILE_NOT_FOUND", HttpStatus.NOT_FOUND,
                "Hợp đồng " + contractId + " chưa có file đính kèm");
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/ContractNotFoundException.java
package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ContractNotFoundException extends PayrollException {
    public ContractNotFoundException(UUID contractId) {
        super("PAYROLL_CONTRACT_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng " + contractId);
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/PayrollRunNotFoundException.java
package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class PayrollRunNotFoundException extends PayrollException {
    public PayrollRunNotFoundException(UUID payrollRunId) {
        super("PAYROLL_RUN_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy kỳ lương " + payrollRunId);
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/PayslipNotFoundException.java
package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class PayslipNotFoundException extends PayrollException {
    public PayslipNotFoundException(UUID payslipId) {
        super("PAYROLL_PAYSLIP_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy phiếu lương " + payslipId);
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/ContractAlreadyTerminatedException.java
package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ContractAlreadyTerminatedException extends PayrollException {
    public ContractAlreadyTerminatedException(UUID contractId) {
        super("PAYROLL_CONTRACT_ALREADY_TERMINATED", HttpStatus.CONFLICT, "Hợp đồng " + contractId + " đã kết thúc");
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/ContractAlreadyActiveException.java
package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Review Focus #3: chặn hai hợp đồng ACTIVE cùng lúc cho một account. */
public final class ContractAlreadyActiveException extends PayrollException {
    public ContractAlreadyActiveException(UUID accountId) {
        super("PAYROLL_CONTRACT_ALREADY_ACTIVE", HttpStatus.CONFLICT,
                "Tài khoản " + accountId + " đã có hợp đồng đang hiệu lực");
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/PayrollRunNotEditableException.java
package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class PayrollRunNotEditableException extends PayrollException {
    public PayrollRunNotEditableException(UUID payrollRunId) {
        super("PAYROLL_RUN_NOT_EDITABLE", HttpStatus.CONFLICT,
                "Kỳ lương " + payrollRunId + " không ở trạng thái DRAFT");
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/PayrollRunNotPendingApprovalException.java
package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class PayrollRunNotPendingApprovalException extends PayrollException {
    public PayrollRunNotPendingApprovalException(UUID payrollRunId) {
        super("PAYROLL_RUN_NOT_PENDING_APPROVAL", HttpStatus.CONFLICT,
                "Kỳ lương " + payrollRunId + " không ở trạng thái PENDING_APPROVAL");
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/InvalidContractTermsException.java
package com.eduerp.modules.payroll;

import org.springframework.http.HttpStatus;

public final class InvalidContractTermsException extends PayrollException {
    public InvalidContractTermsException(String message) {
        super("PAYROLL_INVALID_CONTRACT_TERMS", HttpStatus.BAD_REQUEST, message);
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/PayrollRunAlreadyExistsException.java
package com.eduerp.modules.payroll;

import org.springframework.http.HttpStatus;

/** Review Focus #4: chặn tạo PayrollRun trùng (year, month) bằng lỗi rõ ràng. */
public final class PayrollRunAlreadyExistsException extends PayrollException {
    public PayrollRunAlreadyExistsException(int year, int month) {
        super("PAYROLL_RUN_ALREADY_EXISTS", HttpStatus.CONFLICT, "Kỳ lương " + year + "-" + month + " đã tồn tại");
    }
}
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=PayrollConstantsTest,PayrollExceptionTest`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/eduerp/modules/payroll/ backend/src/test/java/com/eduerp/modules/payroll/
git commit -m "feat(payroll): add PayrollConstants and PayrollException hierarchy"
```

---

### Task 3: `dto/` package (10 records) + `PayrollEvents` + `package-info`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/payroll/dto/AllowanceRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/dto/AllowanceResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/dto/CreateContractRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/dto/UpdateContractRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/dto/ContractResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/dto/CreatePayrollRunRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/dto/UpdatePayslipRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/dto/PayrollRunResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/dto/PayslipResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/dto/PayrollRunDetailResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/dto/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/PayrollEvents.java`
- Test: `backend/src/test/java/com/eduerp/modules/payroll/dto/PayrollDtoTest.java`

**Interfaces:**
- Consumes: `PayrollConstants.ContractType/ContractStatus/PayrollRunStatus` (Task 2).
- Produces: all 10 DTO record signatures exactly as below — every later task (usecases Task 9-15, web Task 10/15, frontend Task 17) constructs/consumes these EXACT signatures, no deviation. `PayrollEvents.ContractCreated(UUID contractId, UUID actorAccountId, UUID actorBranchId)`, `PayrollEvents.ContractTerminated(UUID contractId, UUID actorAccountId, UUID actorBranchId)`, `PayrollEvents.PayrollRunApproved(UUID payrollRunId, UUID actorAccountId, UUID actorBranchId)` — consumed by Task 4 (audit) and Task 9/10/14.

- [ ] **Step 1: Write the failing test**
```java
// backend/src/test/java/com/eduerp/modules/payroll/dto/PayrollDtoTest.java
package com.eduerp.modules.payroll.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.payroll.PayrollConstants;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Hợp đồng dữ liệu không có hành vi - test này khoá chặt field order/kiểu, vì nhiều task sau
 * (usecase, controller, frontend) đều phải xây đúng record này. */
class PayrollDtoTest {

    @Test
    void contractResponseExposesAllPayslipAndContractFields() {
        var response = new ContractResponse(UUID.randomUUID(), UUID.randomUUID(), "Nguyễn Văn A", "a@eduerp.local",
                PayrollConstants.ContractType.OFFICIAL, PayrollConstants.ContractStatus.ACTIVE,
                new BigDecimal("10000000"), null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 1, 1), null, List.of(new AllowanceResponse("Xăng xe", new BigDecimal("500000"))),
                "contracts/key.pdf");

        assertThat(response.accountFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.allowances()).hasSize(1);
    }

    @Test
    void payslipResponseExposesComputedNetPayAndInProbation() {
        var response = new PayslipResponse(UUID.randomUUID(), UUID.randomUUID(), "B", PayrollConstants.ContractType.COLLABORATOR,
                new BigDecimal("1500000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1500000"), new BigDecimal("10"), false);

        assertThat(response.netPay()).isEqualTo(new BigDecimal("1500000"));
        assertThat(response.inProbation()).isFalse();
    }

    @Test
    void payrollRunDetailResponseBundlesRunAndPayslips() {
        var run = new PayrollRunResponse(UUID.randomUUID(), 2026, 1, PayrollConstants.PayrollRunStatus.DRAFT, 0,
                BigDecimal.ZERO);
        var detail = new PayrollRunDetailResponse(run, List.of());

        assertThat(detail.run().year()).isEqualTo(2026);
        assertThat(detail.payslips()).isEmpty();
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=PayrollDtoTest`
Expected: FAIL — compile error: cannot find symbol `ContractResponse`/`PayslipResponse`/etc. (package `dto` chưa tồn tại).
- [ ] **Step 3: Write minimal implementation**
```java
// backend/src/main/java/com/eduerp/modules/payroll/dto/AllowanceRequest.java
package com.eduerp.modules.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AllowanceRequest(@NotBlank @Size(max = 100) String name, @NotNull BigDecimal amount) {
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/dto/AllowanceResponse.java
package com.eduerp.modules.payroll.dto;

import java.math.BigDecimal;

public record AllowanceResponse(String name, BigDecimal amount) {
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/dto/CreateContractRequest.java
package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateContractRequest(@NotNull UUID accountId, @NotNull PayrollConstants.ContractType contractType,
        BigDecimal baseSalary, BigDecimal hourlyRate, LocalDate probationStartDate, LocalDate probationEndDate,
        @NotNull LocalDate startDate, @NotNull List<AllowanceRequest> allowances) {
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/dto/UpdateContractRequest.java
package com.eduerp.modules.payroll.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UpdateContractRequest(BigDecimal baseSalary, BigDecimal hourlyRate, LocalDate probationEndDate,
        @NotNull List<AllowanceRequest> allowances) {
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/dto/ContractResponse.java
package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ContractResponse(UUID id, UUID accountId, String accountFullName, String accountEmail,
        PayrollConstants.ContractType contractType, PayrollConstants.ContractStatus status, BigDecimal baseSalary,
        BigDecimal hourlyRate, LocalDate probationStartDate, LocalDate probationEndDate, LocalDate startDate,
        LocalDate endDate, List<AllowanceResponse> allowances, String contractFileKey) {
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/dto/CreatePayrollRunRequest.java
package com.eduerp.modules.payroll.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreatePayrollRunRequest(@Min(2000) int year, @Min(1) @Max(12) int month) {
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/dto/UpdatePayslipRequest.java
package com.eduerp.modules.payroll.dto;

import java.math.BigDecimal;

/** hoursWorked chỉ có ý nghĩa với COLLABORATOR; incomeTaxWithheld là ô TNCN nhập tay (spec mục 2). */
public record UpdatePayslipRequest(BigDecimal hoursWorked, BigDecimal incomeTaxWithheld, String note) {
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/dto/PayrollRunResponse.java
package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import java.math.BigDecimal;
import java.util.UUID;

public record PayrollRunResponse(UUID id, int year, int month, PayrollConstants.PayrollRunStatus status,
        int payslipCount, BigDecimal totalGrossPay) {
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/dto/PayslipResponse.java
package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import java.math.BigDecimal;
import java.util.UUID;

public record PayslipResponse(UUID id, UUID accountId, String accountFullName,
        PayrollConstants.ContractType contractType, BigDecimal grossPay, BigDecimal socialInsuranceEmployee,
        BigDecimal socialInsuranceEmployer, BigDecimal incomeTaxWithheld, BigDecimal netPay, BigDecimal hoursWorked,
        boolean inProbation) {
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/dto/PayrollRunDetailResponse.java
package com.eduerp.modules.payroll.dto;

import java.util.List;

public record PayrollRunDetailResponse(PayrollRunResponse run, List<PayslipResponse> payslips) {
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/dto/package-info.java
/**
 * Hợp đồng vào/ra của module payroll. Phải {@code @NamedInterface} nếu không
 * {@code ApplicationModules.verify()} sẽ fail - mirror {@code modules.courses.dto}.
 */
@org.springframework.modulith.NamedInterface("dto")
package com.eduerp.modules.payroll.dto;
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/PayrollEvents.java
package com.eduerp.modules.payroll;

import java.util.UUID;

public final class PayrollEvents {

    private PayrollEvents() {
    }

    public record ContractCreated(UUID contractId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record ContractTerminated(UUID contractId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record PayrollRunApproved(UUID payrollRunId, UUID actorAccountId, UUID actorBranchId) {
    }
}
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=PayrollDtoTest`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/eduerp/modules/payroll/dto/ backend/src/main/java/com/eduerp/modules/payroll/PayrollEvents.java \
        backend/src/test/java/com/eduerp/modules/payroll/dto/
git commit -m "feat(payroll): add dto package and PayrollEvents"
```

---

### Task 4: `modules.audit` — nghe 3 `PayrollEvents`

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/audit/AuditConstants.java`
- Modify: `backend/src/main/java/com/eduerp/modules/audit/internal/listener/AuditEventListeners.java`
- Test: `backend/src/test/java/com/eduerp/modules/audit/PayrollEventAuditingIT.java`

**Interfaces:**
- Consumes: `PayrollEvents.ContractCreated/ContractTerminated/PayrollRunApproved` (Task 3), `AuditManagement.recentActions(String entityType, String action, int limit): List<RecentAuditAction>` (đã có sẵn), `RecentAuditAction.entityId(): String` (đã có sẵn).
- Produces: `AuditConstants.Actions.CONTRACT_CREATE/CONTRACT_TERMINATE/PAYROLL_RUN_APPROVE`, `AuditConstants.EntityTypes.CONTRACT/PAYROLL_RUN` — không ai dùng ở task khác, chỉ tự kiểm bằng IT test này (mirror cách các module khác được audit chỉ kiểm qua IT của chính feature, nhưng ở đây tạo riêng một IT test tối giản vì các usecase tạo/duyệt hợp đồng/kỳ lương chưa tồn tại tại thời điểm task này chạy - đặt sớm để Task 10/15 có listener sẵn khi IT của chúng assert audit log).

- [ ] **Step 1: Write the failing test**
```java
// backend/src/test/java/com/eduerp/modules/audit/PayrollEventAuditingIT.java
package com.eduerp.modules.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.payroll.PayrollEvents;
import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class PayrollEventAuditingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    AuditManagement audit;

    @Test
    void auditsContractCreated() {
        var contractId = UUID.randomUUID();
        events.publishEvent(new PayrollEvents.ContractCreated(contractId, UUID.randomUUID(), UUID.randomUUID()));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.CONTRACT,
                    AuditConstants.Actions.CONTRACT_CREATE, 20).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(contractId.toString());
        });
    }

    @Test
    void auditsContractTerminated() {
        var contractId = UUID.randomUUID();
        events.publishEvent(new PayrollEvents.ContractTerminated(contractId, UUID.randomUUID(), UUID.randomUUID()));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.CONTRACT,
                    AuditConstants.Actions.CONTRACT_TERMINATE, 20).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(contractId.toString());
        });
    }

    @Test
    void auditsPayrollRunApproved() {
        var runId = UUID.randomUUID();
        events.publishEvent(new PayrollEvents.PayrollRunApproved(runId, UUID.randomUUID(), UUID.randomUUID()));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.PAYROLL_RUN,
                    AuditConstants.Actions.PAYROLL_RUN_APPROVE, 20).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(runId.toString());
        });
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=PayrollEventAuditingIT`
Expected: FAIL — compile error: cannot find symbol `AuditConstants.EntityTypes.CONTRACT` (chưa thêm).
- [ ] **Step 3: Write minimal implementation**
Trong `AuditConstants.Actions`, thêm:
```java
        public static final String CONTRACT_CREATE = "CONTRACT_CREATE";
        public static final String CONTRACT_TERMINATE = "CONTRACT_TERMINATE";
        public static final String PAYROLL_RUN_APPROVE = "PAYROLL_RUN_APPROVE";
```
Trong `AuditConstants.EntityTypes`, thêm:
```java
        public static final String CONTRACT = "CONTRACT";
        public static final String PAYROLL_RUN = "PAYROLL_RUN";
```
Trong `AuditEventListeners.java`, thêm import `com.eduerp.modules.payroll.PayrollEvents;` và 3 method:
```java
    @ApplicationModuleListener
    void on(PayrollEvents.ContractCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.CONTRACT_CREATE,
                AuditConstants.EntityTypes.CONTRACT, event.contractId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(PayrollEvents.ContractTerminated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.CONTRACT_TERMINATE,
                AuditConstants.EntityTypes.CONTRACT, event.contractId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(PayrollEvents.PayrollRunApproved event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.PAYROLL_RUN_APPROVE,
                AuditConstants.EntityTypes.PAYROLL_RUN, event.payrollRunId().toString(), event.actorBranchId()));
    }
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=PayrollEventAuditingIT`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/eduerp/modules/audit/ backend/src/test/java/com/eduerp/modules/audit/
git commit -m "feat(audit): listen to payroll contract and run events"
```

---

### Task 5: Migration V15 (4 bảng) + `EmploymentContract` + `ContractAllowance` + repository + IT

**Files:**
- Create: `backend/src/main/resources/db/migration/V15__create_payroll_tables.sql`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/internal/model/EmploymentContract.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/internal/model/ContractAllowance.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/internal/model/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/internal/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/internal/repository/EmploymentContractRepository.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/internal/repository/package-info.java`
- Test: `backend/src/test/java/com/eduerp/modules/payroll/internal/repository/EmploymentContractRepositoryIT.java`

**Interfaces:**
- Consumes: `PayrollConstants.ContractType/ContractStatus` (Task 2). V15 cũng tạo bảng `payroll_runs`/`payslips` dùng ở Task 6 (một migration file, theo đúng spec mục 3.6 — Java entity cho hai bảng đó được thêm ở Task 6, sau khi bảng đã tồn tại).
- Produces: `EmploymentContract(UUID accountId, PayrollConstants.ContractType contractType, BigDecimal baseSalary, BigDecimal hourlyRate, LocalDate probationStartDate, LocalDate probationEndDate, LocalDate startDate, List<ContractAllowance> allowances)` constructor; getters `getId/getAccountId/getContractType/getStatus/getBaseSalary/getHourlyRate/getProbationStartDate/getProbationEndDate/getStartDate/getEndDate/getContractFileKey/getAllowances(): List<ContractAllowance>`; setters `setBaseSalary/setHourlyRate/setProbationEndDate/setContractFileKey/setAllowances(List<ContractAllowance>)`; method `terminate(): void`. `ContractAllowance(String name, BigDecimal amount)` với `getName()/getAmount()`. `EmploymentContractRepository.existsByAccountIdAndStatus/findByAccountIdAndStatus/findAllByStatus/findAllByAccountId/findAll` — dùng từ Task 9 trở đi.

- [ ] **Step 1: Write the failing test**
```java
// backend/src/test/java/com/eduerp/modules/payroll/internal/repository/EmploymentContractRepositoryIT.java
package com.eduerp.modules.payroll.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.ContractAllowance;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class EmploymentContractRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    EmploymentContractRepository contracts;

    @Test
    void savesAnOfficialContractWithAllowances() {
        var accountId = UUID.randomUUID();
        var contract = new EmploymentContract(accountId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 1, 1), List.of(new ContractAllowance("Xăng xe", new BigDecimal("500000"))));

        var saved = contracts.save(contract);
        var found = contracts.findById(saved.getId()).orElseThrow();

        assertThat(found.getAccountId()).isEqualTo(accountId);
        assertThat(found.getStatus()).isEqualTo(PayrollConstants.ContractStatus.ACTIVE);
        assertThat(found.getAllowances()).extracting("name").containsExactly("Xăng xe");
    }

    @Test
    void existsByAccountIdAndStatusFindsOnlyActiveContracts() {
        var accountId = UUID.randomUUID();
        contracts.save(new EmploymentContract(accountId, PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), null, null, LocalDate.of(2025, 1, 1), List.of()));

        assertThat(contracts.existsByAccountIdAndStatus(accountId, PayrollConstants.ContractStatus.ACTIVE)).isTrue();
        assertThat(contracts.existsByAccountIdAndStatus(accountId, PayrollConstants.ContractStatus.TERMINATED))
                .isFalse();
    }

    @Test
    void terminateSetsStatusAndEndDate() {
        var contract = contracts.save(new EmploymentContract(UUID.randomUUID(),
                PayrollConstants.ContractType.OFFICIAL, new BigDecimal("8000000"), null, null, null,
                LocalDate.of(2025, 6, 1), List.of()));

        contract.terminate();
        contracts.save(contract);
        var found = contracts.findById(contract.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(PayrollConstants.ContractStatus.TERMINATED);
        assertThat(found.getEndDate()).isNotNull();
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=EmploymentContractRepositoryIT`
Expected: FAIL — compile error: cannot find symbol `EmploymentContract`/`EmploymentContractRepository` (package `internal.model`/`internal.repository` chưa tồn tại).
- [ ] **Step 3: Write minimal implementation**
```sql
-- backend/src/main/resources/db/migration/V15__create_payroll_tables.sql
CREATE TABLE employment_contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    contract_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    base_salary NUMERIC(14,2),
    hourly_rate NUMERIC(14,2),
    probation_start_date DATE,
    probation_end_date DATE,
    start_date DATE NOT NULL,
    end_date DATE,
    contract_file_key VARCHAR(512)
);

CREATE INDEX idx_employment_contracts_account ON employment_contracts (account_id);
-- Một account chỉ một hợp đồng ACTIVE tại một thời điểm - enforce ở usecase (CreateContract, Task 9),
-- không dùng partial unique index ở đây để tránh phụ thuộc cú pháp CREATE UNIQUE INDEX ... WHERE
-- đặc thù Postgres trong migration nền tảng.

CREATE TABLE contract_allowances (
    contract_id UUID NOT NULL REFERENCES employment_contracts(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    amount NUMERIC(14,2) NOT NULL
);

CREATE INDEX idx_contract_allowances_contract ON contract_allowances (contract_id);

CREATE TABLE payroll_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    year INT NOT NULL,
    month INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    rejection_reason VARCHAR(1000),
    UNIQUE (year, month)
);

CREATE TABLE payslips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payroll_run_id UUID NOT NULL REFERENCES payroll_runs(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id),
    contract_type VARCHAR(16) NOT NULL,
    gross_pay NUMERIC(14,2) NOT NULL,
    allowances_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    social_insurance_employee NUMERIC(14,2) NOT NULL DEFAULT 0,
    social_insurance_employer NUMERIC(14,2) NOT NULL DEFAULT 0,
    income_tax_withheld NUMERIC(14,2) NOT NULL DEFAULT 0,
    hours_worked NUMERIC(10,2),
    in_probation BOOLEAN NOT NULL DEFAULT false,
    note VARCHAR(1000)
);

CREATE INDEX idx_payslips_run ON payslips (payroll_run_id);
CREATE INDEX idx_payslips_account ON payslips (account_id);
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/internal/model/ContractAllowance.java
package com.eduerp.modules.payroll.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** @ElementCollection, không có PK riêng - mirror teacher_profile_subjects (Phase 2.5), shape chuẩn. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractAllowance {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    public ContractAllowance(String name, BigDecimal amount) {
        this.name = name;
        this.amount = amount;
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/internal/model/EmploymentContract.java
package com.eduerp.modules.payroll.internal.model;

import com.eduerp.modules.payroll.PayrollConstants;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "employment_contracts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmploymentContract {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, updatable = false)
    private PayrollConstants.ContractType contractType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollConstants.ContractStatus status;

    @Setter
    @Column(name = "base_salary")
    private BigDecimal baseSalary;

    @Setter
    @Column(name = "hourly_rate")
    private BigDecimal hourlyRate;

    @Column(name = "probation_start_date", updatable = false)
    private LocalDate probationStartDate;

    @Setter
    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Setter
    @Column(name = "contract_file_key", length = 512)
    private String contractFileKey;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "contract_allowances", joinColumns = @JoinColumn(name = "contract_id"))
    private final List<ContractAllowance> allowances = new ArrayList<>();

    public EmploymentContract(UUID accountId, PayrollConstants.ContractType contractType, BigDecimal baseSalary,
            BigDecimal hourlyRate, LocalDate probationStartDate, LocalDate probationEndDate, LocalDate startDate,
            List<ContractAllowance> allowances) {
        this.accountId = accountId;
        this.contractType = contractType;
        this.baseSalary = baseSalary;
        this.hourlyRate = hourlyRate;
        this.probationStartDate = probationStartDate;
        this.probationEndDate = probationEndDate;
        this.startDate = startDate;
        this.status = PayrollConstants.ContractStatus.ACTIVE;
        this.allowances.addAll(allowances);
    }

    public List<ContractAllowance> getAllowances() {
        return List.copyOf(allowances);
    }

    public void setAllowances(List<ContractAllowance> allowances) {
        this.allowances.clear();
        this.allowances.addAll(allowances);
    }

    /** Review Focus #5: kết thúc hợp đồng lao động độc lập với trạng thái tài khoản đăng nhập -
     * không có logic nào ở đây hay nơi gọi tham chiếu Account.status. */
    public void terminate() {
        this.status = PayrollConstants.ContractStatus.TERMINATED;
        this.endDate = LocalDate.now();
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/internal/model/package-info.java
package com.eduerp.modules.payroll.internal.model;
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/internal/package-info.java
package com.eduerp.modules.payroll.internal;
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/internal/repository/EmploymentContractRepository.java
package com.eduerp.modules.payroll.internal.repository;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, UUID> {
    boolean existsByAccountIdAndStatus(UUID accountId, PayrollConstants.ContractStatus status);

    Optional<EmploymentContract> findByAccountIdAndStatus(UUID accountId, PayrollConstants.ContractStatus status);

    List<EmploymentContract> findAllByStatus(PayrollConstants.ContractStatus status);

    Page<EmploymentContract> findAllByAccountId(UUID accountId, Pageable pageable);

    Page<EmploymentContract> findAll(Pageable pageable);
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/internal/repository/package-info.java
package com.eduerp.modules.payroll.internal.repository;
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=EmploymentContractRepositoryIT`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/resources/db/migration/V15__create_payroll_tables.sql \
        backend/src/main/java/com/eduerp/modules/payroll/internal/ \
        backend/src/test/java/com/eduerp/modules/payroll/internal/repository/EmploymentContractRepositoryIT.java
git commit -m "feat(payroll): add EmploymentContract entity and repository"
```

---

### Task 6: `PayrollRun` + `Payslip` entities + repositories + IT (Review Focus #4 ở tầng DB)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/payroll/internal/model/PayrollRun.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/internal/model/Payslip.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/internal/repository/PayrollRunRepository.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/internal/repository/PayslipRepository.java`
- Test: `backend/src/test/java/com/eduerp/modules/payroll/internal/repository/PayrollRunRepositoryIT.java`

**Interfaces:**
- Consumes: `PayrollConstants.ContractType/PayrollRunStatus` (Task 2). Bảng `payroll_runs`/`payslips` đã tồn tại từ V15 (Task 5).
- Produces: `PayrollRun(int year, int month)` constructor; `getId/getYear/getMonth/getStatus/getRejectionReason`; `submitForApproval()/approve()/reject(String reason)`. `Payslip(PayrollRun payrollRun, UUID accountId, PayrollConstants.ContractType contractType, BigDecimal grossPay, BigDecimal allowancesTotal, BigDecimal socialInsuranceEmployee, BigDecimal socialInsuranceEmployer, BigDecimal hoursWorked, boolean inProbation)` constructor; `getId/getPayrollRun/getAccountId/getContractType/getGrossPay/getAllowancesTotal/getSocialInsuranceEmployee/getSocialInsuranceEmployer/getIncomeTaxWithheld/getHoursWorked/getNote/isInProbation`; setters `setGrossPay/setSocialInsuranceEmployee/setSocialInsuranceEmployer/setIncomeTaxWithheld/setHoursWorked/setNote`. `PayrollRunRepository.existsByYearAndMonth/findByYearAndMonth/findAll`. `PayslipRepository.findAllByPayrollRun_Id(UUID): List<Payslip>`. Dùng từ Task 11 trở đi.

> **Lưu ý (tự thêm):** `Payslip` có thêm field `allowancesTotal` không nằm trong danh sách field ở spec mục 3.2 — cần để `PayrollRules.netPay(grossPay, allowances, ...)` (Task 7) có input `allowances` mà không phải JOIN ngược về `EmploymentContract` lúc đọc (phụ cấp trên hợp đồng có thể bị sửa sau khi phiếu lương đã chốt — phiếu lương phải là bản snapshot đông cứng tại thời điểm tạo, không phải live-join). Tương tự, `inProbation` được lưu là snapshot tại thời điểm tạo (Review Focus #1: trạng thái tại đầu kỳ), không tính lại mỗi lần đọc.

- [ ] **Step 1: Write the failing test**
```java
// backend/src/test/java/com/eduerp/modules/payroll/internal/repository/PayrollRunRepositoryIT.java
package com.eduerp.modules.payroll.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import java.math.BigDecimal;
import java.util.UUID;
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
class PayrollRunRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    PayrollRunRepository runs;

    @Autowired
    PayslipRepository payslips;

    @Test
    void savesARunWithPayslips() {
        var run = runs.save(new PayrollRun(2026, 1));
        payslips.save(new Payslip(run, UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), BigDecimal.ZERO, new BigDecimal("1050000"), new BigDecimal("2150000"),
                null, false));

        assertThat(payslips.findAllByPayrollRun_Id(run.getId())).hasSize(1);
    }

    /** Review Focus #4 ở tầng DB: unique (year, month) chặn trùng kỳ, usecase (Task 11) phải bắt lỗi
     * này TRƯỚC khi chạm DB bằng existsByYearAndMonth, không để lộ exception thô này ra HTTP. */
    @Test
    void rejectsADuplicateYearMonthAtDatabaseLevel() {
        runs.saveAndFlush(new PayrollRun(2026, 2));

        assertThatThrownBy(() -> runs.saveAndFlush(new PayrollRun(2026, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByYearAndMonthFindsExistingRun() {
        runs.save(new PayrollRun(2026, 3));

        assertThat(runs.existsByYearAndMonth(2026, 3)).isTrue();
        assertThat(runs.existsByYearAndMonth(2026, 4)).isFalse();
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=PayrollRunRepositoryIT`
Expected: FAIL — compile error: cannot find symbol `PayrollRun`/`Payslip`/`PayrollRunRepository`/`PayslipRepository`.
- [ ] **Step 3: Write minimal implementation**
```java
// backend/src/main/java/com/eduerp/modules/payroll/internal/model/PayrollRun.java
package com.eduerp.modules.payroll.internal.model;

import com.eduerp.modules.payroll.PayrollConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "payroll_runs", uniqueConstraints = @UniqueConstraint(columnNames = {"year", "month"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollRun {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private int year;

    @Column(nullable = false, updatable = false)
    private int month;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollConstants.PayrollRunStatus status;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    public PayrollRun(int year, int month) {
        this.year = year;
        this.month = month;
        this.status = PayrollConstants.PayrollRunStatus.DRAFT;
    }

    public void submitForApproval() {
        this.status = PayrollConstants.PayrollRunStatus.PENDING_APPROVAL;
    }

    public void approve() {
        this.status = PayrollConstants.PayrollRunStatus.APPROVED;
    }

    public void reject(String reason) {
        this.status = PayrollConstants.PayrollRunStatus.DRAFT;
        this.rejectionReason = reason;
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/internal/model/Payslip.java
package com.eduerp.modules.payroll.internal.model;

import com.eduerp.modules.payroll.PayrollConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "payslips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payslip {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /** payroll_run là quan hệ JPA thật - PayrollRun cùng module (rule #3 chỉ cấm xuyên module). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", updatable = false)
    private PayrollRun payrollRun;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, updatable = false)
    private PayrollConstants.ContractType contractType;

    @Setter
    @Column(name = "gross_pay", nullable = false)
    private BigDecimal grossPay;

    @Column(name = "allowances_total", nullable = false)
    private BigDecimal allowancesTotal;

    @Setter
    @Column(name = "social_insurance_employee", nullable = false)
    private BigDecimal socialInsuranceEmployee;

    @Setter
    @Column(name = "social_insurance_employer", nullable = false)
    private BigDecimal socialInsuranceEmployer;

    @Setter
    @Column(name = "income_tax_withheld", nullable = false)
    private BigDecimal incomeTaxWithheld = BigDecimal.ZERO;

    @Setter
    @Column(name = "hours_worked")
    private BigDecimal hoursWorked;

    @Setter
    @Column(length = 1000)
    private String note;

    @Column(name = "in_probation", nullable = false)
    private boolean inProbation;

    public Payslip(PayrollRun payrollRun, UUID accountId, PayrollConstants.ContractType contractType,
            BigDecimal grossPay, BigDecimal allowancesTotal, BigDecimal socialInsuranceEmployee,
            BigDecimal socialInsuranceEmployer, BigDecimal hoursWorked, boolean inProbation) {
        this.payrollRun = payrollRun;
        this.accountId = accountId;
        this.contractType = contractType;
        this.grossPay = grossPay;
        this.allowancesTotal = allowancesTotal;
        this.socialInsuranceEmployee = socialInsuranceEmployee;
        this.socialInsuranceEmployer = socialInsuranceEmployer;
        this.hoursWorked = hoursWorked;
        this.inProbation = inProbation;
        this.incomeTaxWithheld = BigDecimal.ZERO;
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/internal/repository/PayrollRunRepository.java
package com.eduerp.modules.payroll.internal.repository;

import com.eduerp.modules.payroll.internal.model.PayrollRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {
    boolean existsByYearAndMonth(int year, int month);

    Optional<PayrollRun> findByYearAndMonth(int year, int month);

    Page<PayrollRun> findAll(Pageable pageable);
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/internal/repository/PayslipRepository.java
package com.eduerp.modules.payroll.internal.repository;

import com.eduerp.modules.payroll.internal.model.Payslip;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {
    List<Payslip> findAllByPayrollRun_Id(UUID payrollRunId);
}
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=PayrollRunRepositoryIT`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/eduerp/modules/payroll/internal/model/PayrollRun.java \
        backend/src/main/java/com/eduerp/modules/payroll/internal/model/Payslip.java \
        backend/src/main/java/com/eduerp/modules/payroll/internal/repository/PayrollRunRepository.java \
        backend/src/main/java/com/eduerp/modules/payroll/internal/repository/PayslipRepository.java \
        backend/src/test/java/com/eduerp/modules/payroll/internal/repository/PayrollRunRepositoryIT.java
git commit -m "feat(payroll): add PayrollRun and Payslip entities and repositories"
```

---

### Task 7: `internal/rules/PayrollRules` (pure logic) + unit tests (Review Focus #1)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/payroll/internal/rules/PayrollRules.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/internal/rules/package-info.java`
- Test: `backend/src/test/java/com/eduerp/modules/payroll/internal/rules/PayrollRulesTest.java`

**Interfaces:**
- Consumes: `EmploymentContract.getContractType/getBaseSalary/getHourlyRate/getProbationStartDate/getProbationEndDate` (Task 5), `ContractAllowance.getAmount` (Task 5), `PayrollConstants.ContractType/StatutoryRates` (Task 2).
- Produces: `PayrollRules.baseGrossPay(EmploymentContract, LocalDate payPeriodStart, BigDecimal hoursWorked): BigDecimal`, `PayrollRules.isInProbation(EmploymentContract, LocalDate asOf): boolean`, `PayrollRules.totalAllowances(List<ContractAllowance>): BigDecimal`, `PayrollRules.socialInsuranceEmployeeShare(BigDecimal grossPay, PayrollConstants.ContractType): BigDecimal`, `PayrollRules.socialInsuranceEmployerShare(BigDecimal grossPay, PayrollConstants.ContractType): BigDecimal`, `PayrollRules.netPay(BigDecimal grossPay, BigDecimal allowances, BigDecimal socialInsuranceEmployeeShare, BigDecimal incomeTaxWithheld): BigDecimal` — tất cả `static`, pure, không I/O. Dùng từ Task 9/11/12 trở đi.

> Không áp dụng rounding/scale cố định lên kết quả `BigDecimal` (không có yêu cầu nào trong spec về làm tròn tiền) - giữ scale tự nhiên từ phép `multiply`/`add`/`subtract`; định dạng hiển thị là việc của frontend, ngoài phạm vi rule thuần này.

- [ ] **Step 1: Write the failing test**
```java
// backend/src/test/java/com/eduerp/modules/payroll/internal/rules/PayrollRulesTest.java
package com.eduerp.modules.payroll.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.ContractAllowance;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayrollRulesTest {

    /** Review Focus #1: probationEndDate rơi giữa kỳ lương (15/1, kỳ chốt là tháng 1) vẫn tính
     * NGUYÊN THÁNG theo trạng thái tại ngày đầu kỳ (1/1, vẫn trong thử việc) - không chia theo ngày. */
    @Test
    void baseGrossPayUsesWholeMonthProbationRateWhenProbationEndsMidPeriod() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 1), List.of());

        var grossPay = PayrollRules.baseGrossPay(contract, LocalDate.of(2026, 1, 1), BigDecimal.ZERO);

        assertThat(grossPay).isEqualTo(new BigDecimal("10000000").multiply(new BigDecimal("0.85")));
    }

    @Test
    void baseGrossPayIsFullSalaryWhenNotInProbationAtPeriodStart() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 1, 1), List.of());

        var grossPay = PayrollRules.baseGrossPay(contract, LocalDate.of(2026, 1, 1), BigDecimal.ZERO);

        assertThat(grossPay).isEqualTo(new BigDecimal("10000000"));
    }

    @Test
    void baseGrossPayForCollaboratorIsHourlyRateTimesHoursWorked() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), null, null, LocalDate.of(2025, 1, 1), List.of());

        var grossPay = PayrollRules.baseGrossPay(contract, LocalDate.of(2026, 1, 1), new BigDecimal("20"));

        assertThat(grossPay).isEqualTo(new BigDecimal("150000").multiply(new BigDecimal("20")));
    }

    @Test
    void isInProbationIsFalseWhenNoProbationDatesSet() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), null, null, LocalDate.of(2025, 1, 1), List.of());

        assertThat(PayrollRules.isInProbation(contract, LocalDate.of(2026, 1, 1))).isFalse();
    }

    @Test
    void totalAllowancesSumsAllAmounts() {
        var allowances = List.of(new ContractAllowance("Xăng xe", new BigDecimal("500000")),
                new ContractAllowance("Ăn trưa", new BigDecimal("300000")));

        assertThat(PayrollRules.totalAllowances(allowances)).isEqualTo(new BigDecimal("800000"));
    }

    @Test
    void socialInsuranceAppliesOnlyToOfficialContracts() {
        var grossPay = new BigDecimal("10000000");

        assertThat(PayrollRules.socialInsuranceEmployeeShare(grossPay, PayrollConstants.ContractType.OFFICIAL))
                .isEqualTo(grossPay.multiply(PayrollConstants.StatutoryRates.EMPLOYEE_SHARE));
        assertThat(PayrollRules.socialInsuranceEmployerShare(grossPay, PayrollConstants.ContractType.OFFICIAL))
                .isEqualTo(grossPay.multiply(PayrollConstants.StatutoryRates.EMPLOYER_SHARE));
        assertThat(PayrollRules.socialInsuranceEmployeeShare(grossPay, PayrollConstants.ContractType.COLLABORATOR))
                .isEqualTo(BigDecimal.ZERO);
        assertThat(PayrollRules.socialInsuranceEmployerShare(grossPay, PayrollConstants.ContractType.COLLABORATOR))
                .isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void netPayAddsAllowancesAndSubtractsInsuranceAndTax() {
        var netPay = PayrollRules.netPay(new BigDecimal("10000000"), new BigDecimal("500000"),
                new BigDecimal("1050000"), new BigDecimal("200000"));

        assertThat(netPay).isEqualTo(new BigDecimal("10000000").add(new BigDecimal("500000"))
                .subtract(new BigDecimal("1050000")).subtract(new BigDecimal("200000")));
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=PayrollRulesTest`
Expected: FAIL — compile error: cannot find symbol `PayrollRules`.
- [ ] **Step 3: Write minimal implementation**
```java
// backend/src/main/java/com/eduerp/modules/payroll/internal/rules/PayrollRules.java
package com.eduerp.modules.payroll.internal.rules;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.internal.model.ContractAllowance;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Pure, 100% unit-test được bằng new, không I/O - mirror tinh thần internal/rules của các module khác. */
public final class PayrollRules {

    private PayrollRules() {
    }

    /**
     * Review Focus #1: {@code payPeriodStart} luôn là ngày đầu kỳ lương (year-month-01) - trạng thái
     * thử việc được chốt TẠI ngày này cho NGUYÊN cả kỳ, không chia theo ngày trong tháng dù
     * probationEndDate rơi giữa kỳ (quyết định tại plan, spec mục 6.1).
     */
    public static BigDecimal baseGrossPay(EmploymentContract contract, LocalDate payPeriodStart,
            BigDecimal hoursWorked) {
        if (contract.getContractType() == PayrollConstants.ContractType.OFFICIAL) {
            var base = contract.getBaseSalary();
            return isInProbation(contract, payPeriodStart) ? base.multiply(PayrollConstants.StatutoryRates.PROBATION_RATE)
                    : base;
        }
        return contract.getHourlyRate().multiply(hoursWorked);
    }

    public static boolean isInProbation(EmploymentContract contract, LocalDate asOf) {
        var start = contract.getProbationStartDate();
        var end = contract.getProbationEndDate();
        if (start == null || end == null) {
            return false;
        }
        return !asOf.isBefore(start) && !asOf.isAfter(end);
    }

    public static BigDecimal totalAllowances(List<ContractAllowance> allowances) {
        return allowances.stream().map(ContractAllowance::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal socialInsuranceEmployeeShare(BigDecimal grossPay, PayrollConstants.ContractType type) {
        return type == PayrollConstants.ContractType.OFFICIAL
                ? grossPay.multiply(PayrollConstants.StatutoryRates.EMPLOYEE_SHARE) : BigDecimal.ZERO;
    }

    public static BigDecimal socialInsuranceEmployerShare(BigDecimal grossPay, PayrollConstants.ContractType type) {
        return type == PayrollConstants.ContractType.OFFICIAL
                ? grossPay.multiply(PayrollConstants.StatutoryRates.EMPLOYER_SHARE) : BigDecimal.ZERO;
    }

    public static BigDecimal netPay(BigDecimal grossPay, BigDecimal allowances, BigDecimal socialInsuranceEmployeeShare,
            BigDecimal incomeTaxWithheld) {
        return grossPay.add(allowances).subtract(socialInsuranceEmployeeShare).subtract(incomeTaxWithheld);
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/internal/rules/package-info.java
package com.eduerp.modules.payroll.internal.rules;
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=PayrollRulesTest`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/eduerp/modules/payroll/internal/rules/ \
        backend/src/test/java/com/eduerp/modules/payroll/internal/rules/
git commit -m "feat(payroll): add PayrollRules pure calculation logic"
```

---

### Task 8: `integrations.storage` — `StorageProperties` + `StorageClient` (AWS SDK v2) + Testcontainers MinIO IT

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/.env.example`
- Modify: `backend/.env.docker`
- Modify: `backend/.env.docker.example`
- Modify: `docker-compose.prod.yml`
- Create: `backend/src/main/java/com/eduerp/integrations/storage/StorageProperties.java`
- Create: `backend/src/main/java/com/eduerp/integrations/storage/StorageClient.java`
- Create: `backend/src/main/java/com/eduerp/integrations/storage/package-info.java`
- Test: `backend/src/test/java/com/eduerp/integrations/storage/StorageClientIT.java`

**Interfaces:**
- Consumes: nothing from payroll (độc lập, mirror `integrations.mail`).
- Produces: `StorageClient.upload(String keyPrefix, String fileName, byte[] content, String contentType): String`, `StorageClient.download(String key): byte[]`, `StorageClient.delete(String key): void` — dùng ở Task 10 (`UploadContractFile`/`DownloadContractFile`).

> **AWS SDK v2 version — `software.amazon.awssdk:s3:2.34.0`:** xác định qua `search.maven.org` (solr query `g:"software.amazon.awssdk" AND a:"s3"`, core=gav) — bản ghi đáng tin cậy gần nhất trả về là `2.34.0`, publish timestamp `1758318408000` ms = 2025-09-19 (một bản ghi "2.46.7" với timestamp dị thường ~2026-11 xuất hiện trong kết quả nhưng không nhất quán với chuỗi thời gian còn lại nên không dùng). Vì AWS SDK v2 phát hành gần như hàng ngày, **phiên bản này CẦN được xác minh lại trên Maven Central/mvnrepository.com ngay trước khi merge** — rất có thể đã có bản mới hơn tại thời điểm thực thi plan.
>
> **Quyết định MinIO-vs-mock cho IT test:** dùng Testcontainers MinIO thật (`org.testcontainers:minio`, không khai version — để `spring-boot-dependencies` BOM của `spring-boot-starter-parent:3.4.1` quản lý, đúng cách mọi `org.testcontainers:*` khác trong `pom.xml` hiện tại không khai version), KHÔNG mock `StorageClient`. Lý do: mọi `*IT.java` hiện có trong repo đều dùng Testcontainers thật cho Postgres/Redis, không có precedent mock nào ở tầng IT — giữ nhất quán văn hoá test của dự án.
>
> **Hạ tầng dev — không thêm service `minio` mới vào `docker-compose.yml`:** máy dev đã có container MinIO dùng chung cho nhiều dự án (`agent-minio`, cổng `9010`/`9011`, xác nhận bằng `docker ps`), đúng mô hình `docker-compose.yml` hiện tại đang dùng cho Postgres/Redis (`agent-postgres:5433`, `redis-stack:6379`, qua `host.docker.internal`) — KHÔNG tự dựng container riêng trùng lặp hạ tầng máy đã có. `STORAGE_ENDPOINT` dev trỏ `http://localhost:9010` (chạy native) hoặc `http://host.docker.internal:9010` (`.env.docker`).
>
> **Hạ tầng prod — service `minio` đã tồn tại sẵn trong `docker-compose.prod.yml`** (thêm từ trước, comment ghi rõ "chưa có code nào gọi tới" — nay đã có). Chỉ cần bổ sung biến `STORAGE_*` vào block `environment` của service `app`, trỏ `STORAGE_ENDPOINT=http://minio:9000` và dùng lại `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD` đã có trong `.env.docker.prod.example`.

- [ ] **Step 1: Write the failing test**
```java
// backend/src/test/java/com/eduerp/integrations/storage/StorageClientIT.java
package com.eduerp.integrations.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
@SpringBootTest
class StorageClientIT {

    private static final String BUCKET = "eduerp-test";

    @Container
    static MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z");

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.endpoint", minio::getS3URL);
        registry.add("storage.region", () -> "us-east-1");
        registry.add("storage.bucket", () -> BUCKET);
        registry.add("storage.access-key", minio::getUserName);
        registry.add("storage.secret-key", minio::getPassword);
    }

    @BeforeAll
    static void createBucket() {
        var client = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(minio.getS3URL()))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
                .build();
        client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @Autowired
    StorageClient storageClient;

    @Test
    void uploadsDownloadsAndDeletesAFile() {
        var content = "hợp đồng pdf giả lập".getBytes(StandardCharsets.UTF_8);

        var key = storageClient.upload("contracts/test", "contract.pdf", content, "application/pdf");
        var downloaded = storageClient.download(key);
        storageClient.delete(key);

        assertThat(downloaded).isEqualTo(content);
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=StorageClientIT`
Expected: FAIL — compile error: cannot find symbol `StorageClient`/`MinIOContainer` (chưa thêm class và dependency).
- [ ] **Step 3: Write minimal implementation**
Thêm vào `backend/pom.xml`, trong `<dependencies>`:
```xml
    <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>s3</artifactId>
      <version>2.34.0</version>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>minio</artifactId>
      <scope>test</scope>
    </dependency>
```
```java
// backend/src/main/java/com/eduerp/integrations/storage/StorageProperties.java
package com.eduerp.integrations.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** endpoint trống = AWS S3 thật (prod); có giá trị = MinIO/S3-compatible tự host (dev) - spec mục 3.5. */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(String endpoint, String region, String bucket, String accessKey, String secretKey) {
}
```
```java
// backend/src/main/java/com/eduerp/integrations/storage/StorageClient.java
package com.eduerp.integrations.storage;

import java.net.URI;
import java.util.UUID;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Cơ chế thuần: không biết nội dung/ý nghĩa nghiệp vụ của bất kỳ file nào - mirror MailClient. */
@Component
public class StorageClient {

    private final S3Client s3Client;
    private final StorageProperties properties;

    StorageClient(StorageProperties properties) {
        this.properties = properties;
        var credentials = AwsBasicCredentials.create(properties.accessKey(), properties.secretKey());
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials));
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            // MinIO (dev) cần endpoint riêng + path-style; AWS S3 thật (prod) để trống endpoint, dùng
            // virtual-hosted-style mặc định của SDK - cùng một đoạn code chạy đúng cả hai môi trường.
            builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
        }
        this.s3Client = builder.build();
    }

    public String upload(String keyPrefix, String fileName, byte[] content, String contentType) {
        var key = keyPrefix + "/" + UUID.randomUUID() + "-" + fileName;
        s3Client.putObject(
                PutObjectRequest.builder().bucket(properties.bucket()).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(content));
        return key;
    }

    public byte[] download(String key) {
        return s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build())
                .asByteArray();
    }

    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }
}
```
```java
// backend/src/main/java/com/eduerp/integrations/storage/package-info.java
/**
 * Lưu trữ file qua S3-compatible object storage (AWS S3 thật ở production, MinIO tự host ở dev).
 * Cơ chế thuần: không biết nội dung/ý nghĩa nghiệp vụ của bất kỳ file nào - module nghiệp vụ
 * (modules.payroll) tự đặt key, gọi StorageClient.upload/download/delete.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Object Storage")
package com.eduerp.integrations.storage;
```
Thêm vào `backend/src/main/resources/application.yml` (sau block `mail:`):
```yaml
storage:
  endpoint: ${STORAGE_ENDPOINT:}
  region: ${STORAGE_REGION:us-east-1}
  bucket: ${STORAGE_BUCKET:eduerp-contracts}
  access-key: ${STORAGE_ACCESS_KEY:}
  secret-key: ${STORAGE_SECRET_KEY:}
```
Thêm vào `backend/.env.example`:
```
# --- Storage (S3-compatible; trống = AWS S3 thật, có giá trị = MinIO/S3-compatible tự host) ---
# Máy dev đã có container agent-minio dùng chung (cổng 9010/9011, xem docker ps) - không dựng thêm.
STORAGE_ENDPOINT=http://localhost:9010
STORAGE_REGION=us-east-1
STORAGE_BUCKET=eduerp-contracts
STORAGE_ACCESS_KEY=
STORAGE_SECRET_KEY=
```
Thêm vào `backend/.env.docker` và `backend/.env.docker.example` (app chạy trong container, trỏ qua `host.docker.internal` như `DB_HOST`/`REDIS_HOST`):
```
STORAGE_ENDPOINT=http://host.docker.internal:9010
STORAGE_REGION=us-east-1
STORAGE_BUCKET=eduerp-contracts
STORAGE_ACCESS_KEY=
STORAGE_SECRET_KEY=
```
Trong `docker-compose.prod.yml`, thêm vào block `environment:` của service `app` (sau `IDENTITY_SECURE_COOKIES`):
```yaml
      STORAGE_ENDPOINT: http://minio:9000
      STORAGE_REGION: us-east-1
      STORAGE_BUCKET: eduerp-contracts
      STORAGE_ACCESS_KEY: ${MINIO_ROOT_USER}
      STORAGE_SECRET_KEY: ${MINIO_ROOT_PASSWORD}
```
Đồng thời cập nhật comment ở đầu service `minio` trong file này (dòng 5-7), bỏ câu "chưa có code nào trong app gọi tới nó" vì nay đã có `integrations.storage` dùng nó.
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=StorageClientIT`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/pom.xml backend/src/main/resources/application.yml backend/.env.example \
        backend/.env.docker backend/.env.docker.example docker-compose.prod.yml \
        backend/src/main/java/com/eduerp/integrations/storage/ \
        backend/src/test/java/com/eduerp/integrations/storage/
git commit -m "feat(storage): add S3-compatible StorageClient integration"
```

---

### Task 9: `CreateContract` usecase + `PayrollManagement.getActiveContractFor` (Review Focus #2, #3)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/payroll/usecase/CreateContract.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/usecase/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/PayrollManagement.java`
- Test: `backend/src/test/java/com/eduerp/modules/payroll/usecase/CreateContractTest.java`

**Interfaces:**
- Consumes: `EmploymentContractRepository.existsByAccountIdAndStatus/save` (Task 5), `IdentityManagement.summariesOf(Collection<UUID>): Map<UUID, IdentityManagement.AccountBasicInfo>` (đã có sẵn), `AppValidationException(String field, String message)` (đã có sẵn), `PayrollEvents.ContractCreated` (Task 3), `ContractResponse/AllowanceResponse/CreateContractRequest/AllowanceRequest` (Task 3), `InvalidContractTermsException/ContractAlreadyActiveException` (Task 2).
- Produces: `CreateContract.execute(UUID actorAccountId, UUID actorBranchId, CreateContractRequest request): ContractResponse` — dùng ở Task 10 (`ContractAdminController`). `PayrollManagement.getActiveContractFor(UUID accountId): Optional<ContractResponse>` — facade tối thiểu, mirror `CoursesManagement.courseExists`/`TeachersManagement.teacherProfileExists` (chưa module nào khác gọi tới ở phase này, chuẩn bị sẵn cho tương lai, không suy đoán method nào khác).

> **Quyết định facade (lệch so với cách đọc chữ của spec mục 3.2):** Spec liệt kê đủ 12 method (`createContract`, `updateContract`, ...) trên `PayrollManagement` như thể controller gọi qua facade. Nhưng spec mục 3.2 (web/) cũng yêu cầu "mirror `CourseAdminController`/`ClassAdminController` 1:1 về cấu trúc" — mà hai controller đó inject **usecase trực tiếp**, không qua `CoursesManagement` (file đã đọc: `CoursesManagement` chỉ có `courseExists`/`classExists`, không có `createCourse`). Ưu tiên theo cấu trúc controller cụ thể (yêu cầu hành động rõ hơn) hơn danh sách method liệt kê (mang tính mô tả năng lực): `ContractAdminController`/`PayrollRunAdminController` (Task 10/15) inject usecase trực tiếp; `PayrollManagement` chỉ giữ `getActiveContractFor` — method duy nhất có khả năng cần cho module khác trong tương lai, đúng mức tối giản "chỉ thêm cái cần ngay" mà `CoursesManagement`/`TeachersManagement` đang áp dụng.

- [ ] **Step 1: Write the failing test**
```java
// backend/src/test/java/com/eduerp/modules/payroll/usecase/CreateContractTest.java
package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.ContractAlreadyActiveException;
import com.eduerp.modules.payroll.InvalidContractTermsException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollEvents;
import com.eduerp.modules.payroll.dto.CreateContractRequest;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CreateContractTest {

    private final EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
    private final IdentityManagement identity = mock(IdentityManagement.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CreateContract useCase = new CreateContract(contracts, identity, events);

    private void stubAccountExists(UUID accountId) {
        when(identity.summariesOf(List.of(accountId)))
                .thenReturn(Map.of(accountId, new IdentityManagement.AccountBasicInfo(accountId, "a@eduerp.local", "A")));
    }

    @Test
    void rejectsAnAccountIdThatDoesNotExist() {
        var accountId = UUID.randomUUID();
        when(identity.summariesOf(List.of(accountId))).thenReturn(Map.of());
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.now(), List.of());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(AppValidationException.class);
    }

    /** Review Focus #3. */
    @Test
    void rejectsASecondActiveContractForTheSameAccount() {
        var accountId = UUID.randomUUID();
        stubAccountExists(accountId);
        when(contracts.existsByAccountIdAndStatus(accountId, PayrollConstants.ContractStatus.ACTIVE)).thenReturn(true);
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.now(), List.of());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(ContractAlreadyActiveException.class);
    }

    @Test
    void rejectsOfficialContractWithoutBaseSalary() {
        var accountId = UUID.randomUUID();
        stubAccountExists(accountId);
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.OFFICIAL, null, null, null,
                null, LocalDate.now(), List.of());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(InvalidContractTermsException.class);
    }

    @Test
    void rejectsCollaboratorContractWithoutHourlyRate() {
        var accountId = UUID.randomUUID();
        stubAccountExists(accountId);
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.COLLABORATOR, null, null,
                null, null, LocalDate.now(), List.of());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(InvalidContractTermsException.class);
    }

    /** Review Focus #2. */
    @Test
    void rejectsCollaboratorContractWithProbationDates() {
        var accountId = UUID.randomUUID();
        stubAccountExists(accountId);
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), LocalDate.now(), LocalDate.now().plusMonths(2), LocalDate.now(), List.of());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), request))
                .isInstanceOf(InvalidContractTermsException.class);
    }

    @Test
    void createsAnOfficialContractAndPublishesContractCreated() {
        var accountId = UUID.randomUUID();
        stubAccountExists(accountId);
        when(contracts.save(any(EmploymentContract.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, LocalDate.now(), LocalDate.now().plusMonths(2), LocalDate.now(),
                List.of());

        var response = useCase.execute(UUID.randomUUID(), UUID.randomUUID(), request);

        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.accountFullName()).isEqualTo("A");
        assertThat(response.contractType()).isEqualTo(PayrollConstants.ContractType.OFFICIAL);
        verify(events).publishEvent(any(PayrollEvents.ContractCreated.class));
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=CreateContractTest`
Expected: FAIL — compile error: cannot find symbol `CreateContract`.
- [ ] **Step 3: Write minimal implementation**
```java
// backend/src/main/java/com/eduerp/modules/payroll/usecase/package-info.java
/**
 * Một class = một use case = một method {@code public execute(...)} (rule #7). Method phải
 * {@code public} để proxy AOP của Spring bọc được {@code @Transactional}.
 */
package com.eduerp.modules.payroll.usecase;
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/usecase/CreateContract.java
package com.eduerp.modules.payroll.usecase;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.ContractAlreadyActiveException;
import com.eduerp.modules.payroll.InvalidContractTermsException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollEvents;
import com.eduerp.modules.payroll.dto.AllowanceResponse;
import com.eduerp.modules.payroll.dto.ContractResponse;
import com.eduerp.modules.payroll.dto.CreateContractRequest;
import com.eduerp.modules.payroll.internal.model.ContractAllowance;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateContract {

    private final EmploymentContractRepository contracts;
    private final IdentityManagement identity;
    private final ApplicationEventPublisher events;

    CreateContract(EmploymentContractRepository contracts, IdentityManagement identity,
            ApplicationEventPublisher events) {
        this.contracts = contracts;
        this.identity = identity;
        this.events = events;
    }

    @Transactional
    public ContractResponse execute(UUID actorAccountId, UUID actorBranchId, CreateContractRequest request) {
        var accountInfo = identity.summariesOf(List.of(request.accountId()));
        if (!accountInfo.containsKey(request.accountId())) {
            throw new AppValidationException("accountId", "Tài khoản không tồn tại");
        }
        if (contracts.existsByAccountIdAndStatus(request.accountId(), PayrollConstants.ContractStatus.ACTIVE)) {
            throw new ContractAlreadyActiveException(request.accountId());
        }
        validateContractTerms(request.contractType(), request.baseSalary(), request.hourlyRate(),
                request.probationStartDate(), request.probationEndDate());

        var allowances = request.allowances().stream().map(a -> new ContractAllowance(a.name(), a.amount())).toList();
        var saved = contracts.save(new EmploymentContract(request.accountId(), request.contractType(),
                request.baseSalary(), request.hourlyRate(), request.probationStartDate(),
                request.probationEndDate(), request.startDate(), allowances));
        events.publishEvent(new PayrollEvents.ContractCreated(saved.getId(), actorAccountId, actorBranchId));
        return toResponse(saved, accountInfo);
    }

    private static void validateContractTerms(PayrollConstants.ContractType type, BigDecimal baseSalary,
            BigDecimal hourlyRate, LocalDate probationStartDate, LocalDate probationEndDate) {
        if (type == PayrollConstants.ContractType.OFFICIAL) {
            if (baseSalary == null) {
                throw new InvalidContractTermsException("Hợp đồng chính thức phải có baseSalary");
            }
        } else {
            if (hourlyRate == null) {
                throw new InvalidContractTermsException("Hợp đồng CTV phải có hourlyRate");
            }
            if (probationStartDate != null || probationEndDate != null) {
                throw new InvalidContractTermsException("Hợp đồng CTV không áp dụng thử việc");
            }
        }
    }

    static ContractResponse toResponse(EmploymentContract contract,
            Map<UUID, IdentityManagement.AccountBasicInfo> accountInfo) {
        var account = accountInfo.get(contract.getAccountId());
        var allowances = contract.getAllowances().stream()
                .map(a -> new AllowanceResponse(a.getName(), a.getAmount())).toList();
        return new ContractResponse(contract.getId(), contract.getAccountId(),
                account == null ? null : account.fullName(), account == null ? null : account.email(),
                contract.getContractType(), contract.getStatus(), contract.getBaseSalary(), contract.getHourlyRate(),
                contract.getProbationStartDate(), contract.getProbationEndDate(), contract.getStartDate(),
                contract.getEndDate(), allowances, contract.getContractFileKey());
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/PayrollManagement.java
package com.eduerp.modules.payroll;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.dto.ContractResponse;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.eduerp.modules.payroll.usecase.CreateContract;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Facade của module payroll - type duy nhất module khác được phép gọi (rule #1). Chưa module nào
 * gọi tới ở phase này (giống CoursesManagement/TeachersManagement) - method này là điểm bắt đầu
 * tối thiểu, không suy đoán thêm method nào khác chưa ai cần. */
@Service
public class PayrollManagement {

    private final EmploymentContractRepository contracts;
    private final IdentityManagement identity;

    PayrollManagement(EmploymentContractRepository contracts, IdentityManagement identity) {
        this.contracts = contracts;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public Optional<ContractResponse> getActiveContractFor(UUID accountId) {
        return contracts.findByAccountIdAndStatus(accountId, PayrollConstants.ContractStatus.ACTIVE)
                .map(contract -> CreateContract.toResponse(contract, identity.summariesOf(List.of(accountId))));
    }
}
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=CreateContractTest`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/eduerp/modules/payroll/usecase/ backend/src/main/java/com/eduerp/modules/payroll/PayrollManagement.java \
        backend/src/test/java/com/eduerp/modules/payroll/usecase/CreateContractTest.java
git commit -m "feat(payroll): add CreateContract usecase and PayrollManagement facade"
```

---

### Task 10: `UpdateContract`/`TerminateContract`/`ListContracts`/`UploadContractFile`/`DownloadContractFile` + `ContractAdminController` + full IT

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/payroll/usecase/UpdateContract.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/usecase/TerminateContract.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/usecase/ListContracts.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/usecase/UploadContractFile.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/usecase/DownloadContractFile.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/web/ContractAdminController.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/web/package-info.java`
- Test: `backend/src/test/java/com/eduerp/modules/payroll/web/ContractAdminControllerIT.java`

**Interfaces:**
- Consumes: `CreateContract.toResponse` (Task 9, package-visible static helper), `StorageClient.upload/download` (Task 8), `EmploymentContractRepository` (Task 5), `AccessConstants.AccessRules.CREATE_PAYROLL/READ_PAYROLL/UPDATE_PAYROLL` (Task 1), `PayrollEvents.ContractTerminated` (Task 3), `AuditManagement.recentActions` (verify audit, Task 4).
- Produces: `UpdateContract.execute(UUID contractId, UUID actorAccountId, UUID actorBranchId, UpdateContractRequest): ContractResponse`, `TerminateContract.execute(UUID contractId, UUID actorAccountId, UUID actorBranchId): void`, `ListContracts.execute(Pageable, UUID accountIdFilter): PageResponse<ContractResponse>`, `UploadContractFile.execute(UUID contractId, String fileName, String contentType, byte[] content): ContractResponse`, `DownloadContractFile.execute(UUID contractId): byte[]`. HTTP: `GET/POST /api/payroll/contracts`, `PATCH /api/payroll/contracts/{id}`, `POST /api/payroll/contracts/{id}/terminate`, `GET /api/payroll/contracts/{id}/file`.

- [ ] **Step 1: Write the failing test**
```java
// backend/src/test/java/com/eduerp/modules/payroll/web/ContractAdminControllerIT.java
package com.eduerp.modules.payroll.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.AuditManagement;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.dto.CreateContractRequest;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ContractAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String BUCKET = "eduerp-test";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Container
    static MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z");

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.endpoint", minio::getS3URL);
        registry.add("storage.region", () -> "us-east-1");
        registry.add("storage.bucket", () -> BUCKET);
        registry.add("storage.access-key", minio::getUserName);
        registry.add("storage.secret-key", minio::getPassword);
    }

    // Testcontainers' JUnit5 extension khởi động mọi field @Container TRƯỚC khi gọi
    // @DynamicPropertySource (cần thiết để minio.getS3URL() có giá trị thật) - nên tại thời điểm
    // @BeforeAll chạy, container và bucket đều có thể tạo được, container đã start sẵn.
    @org.junit.jupiter.api.BeforeAll
    static void createBucket() {
        var client = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(minio.getS3URL()))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
                .build();
        client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccountRepository accounts;

    @Autowired
    EmploymentContractRepository contracts;

    @Autowired
    AccessManagement access;

    @Autowired
    AuditManagement audit;

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

    private UUID newEmployeeAccount(String email) {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        return accounts.save(account).getId();
    }

    @Test
    void createsThenListsAnOfficialContractAndAuditsIt() throws Exception {
        var admin = signIn("payroll-admin-1@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var employeeId = newEmployeeAccount("employee-1@eduerp.local");
        var request = new CreateContractRequest(employeeId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());

        var createResult = mockMvc.perform(multipart("/api/payroll/contracts")
                        .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(request)))
                        .cookie(admin).with(csrf()))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);
        var contractId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        var listResult = mockMvc.perform(get("/api/payroll/contracts").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(listResult.getResponse().getContentAsString()).contains("employee-1@eduerp.local");

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.CONTRACT,
                    AuditConstants.Actions.CONTRACT_CREATE, 20).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(contractId);
        });
    }

    @Test
    void uploadsAndDownloadsTheContractFile() throws Exception {
        var admin = signIn("payroll-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var employeeId = newEmployeeAccount("employee-2@eduerp.local");
        var request = new CreateContractRequest(employeeId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("8000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());

        var createResult = mockMvc.perform(multipart("/api/payroll/contracts")
                        .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(request)))
                        .file(new MockMultipartFile("file", "contract.pdf", "application/pdf", "nội dung pdf".getBytes()))
                        .cookie(admin).with(csrf()))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);
        var contractId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        var downloadResult = mockMvc.perform(get("/api/payroll/contracts/" + contractId + "/file").cookie(admin))
                .andReturn();
        assertThat(downloadResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(downloadResult.getResponse().getContentAsByteArray()).isEqualTo("nội dung pdf".getBytes());
    }

    /** Review Focus #3 ở tầng HTTP. */
    @Test
    void rejectsASecondActiveContractForTheSameAccountAtHttpLevel() throws Exception {
        var admin = signIn("payroll-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var employeeId = newEmployeeAccount("employee-3@eduerp.local");
        var firstRequest = new CreateContractRequest(employeeId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("9000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());
        mockMvc.perform(multipart("/api/payroll/contracts")
                .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                        objectMapper.writeValueAsBytes(firstRequest)))
                .cookie(admin).with(csrf()));

        var secondRequest = new CreateContractRequest(employeeId, PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), null, null, LocalDate.of(2026, 2, 1), List.of());
        var result = mockMvc.perform(multipart("/api/payroll/contracts")
                        .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(secondRequest)))
                        .cookie(admin).with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("PAYROLL_CONTRACT_ALREADY_ACTIVE");
    }

    @Test
    void terminatesAContractAndAuditsIt() throws Exception {
        var admin = signIn("payroll-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var employeeId = newEmployeeAccount("employee-4@eduerp.local");
        var request = new CreateContractRequest(employeeId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("7000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());
        var createResult = mockMvc.perform(multipart("/api/payroll/contracts")
                        .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(request)))
                        .cookie(admin).with(csrf()))
                .andReturn();
        var contractId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        var terminateResult = mockMvc.perform(
                        post("/api/payroll/contracts/" + contractId + "/terminate").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(terminateResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(contracts.findById(UUID.fromString(contractId)).orElseThrow().getStatus())
                .isEqualTo(PayrollConstants.ContractStatus.TERMINATED);

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.CONTRACT,
                    AuditConstants.Actions.CONTRACT_TERMINATE, 20).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(contractId);
        });
    }

    @Test
    void refusesAnAccountWithOnlyPersonalScopePermissions() throws Exception {
        var teacher = signIn("payroll-outsider@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var employeeId = newEmployeeAccount("employee-5@eduerp.local");
        var request = new CreateContractRequest(employeeId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("9000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());

        var result = mockMvc.perform(multipart("/api/payroll/contracts")
                        .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(request)))
                        .cookie(teacher).with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=ContractAdminControllerIT`
Expected: FAIL — compile error: cannot find symbol `ContractAdminController` (chưa tồn tại); 404 trên mọi endpoint.
- [ ] **Step 3: Write minimal implementation**
```java
// backend/src/main/java/com/eduerp/modules/payroll/usecase/UpdateContract.java
package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.ContractNotFoundException;
import com.eduerp.modules.payroll.dto.ContractResponse;
import com.eduerp.modules.payroll.dto.UpdateContractRequest;
import com.eduerp.modules.payroll.internal.model.ContractAllowance;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateContract {

    private final EmploymentContractRepository contracts;
    private final IdentityManagement identity;

    UpdateContract(EmploymentContractRepository contracts, IdentityManagement identity) {
        this.contracts = contracts;
        this.identity = identity;
    }

    @Transactional
    public ContractResponse execute(UUID contractId, UUID actorAccountId, UUID actorBranchId,
            UpdateContractRequest request) {
        var contract = contracts.findById(contractId).orElseThrow(() -> new ContractNotFoundException(contractId));
        contract.setBaseSalary(request.baseSalary());
        contract.setHourlyRate(request.hourlyRate());
        contract.setProbationEndDate(request.probationEndDate());
        contract.setAllowances(
                request.allowances().stream().map(a -> new ContractAllowance(a.name(), a.amount())).toList());
        var accountInfo = identity.summariesOf(List.of(contract.getAccountId()));
        return CreateContract.toResponse(contract, accountInfo);
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/usecase/TerminateContract.java
package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.ContractAlreadyTerminatedException;
import com.eduerp.modules.payroll.ContractNotFoundException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollEvents;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TerminateContract {

    private final EmploymentContractRepository contracts;
    private final ApplicationEventPublisher events;

    TerminateContract(EmploymentContractRepository contracts, ApplicationEventPublisher events) {
        this.contracts = contracts;
        this.events = events;
    }

    @Transactional
    public void execute(UUID contractId, UUID actorAccountId, UUID actorBranchId) {
        var contract = contracts.findById(contractId).orElseThrow(() -> new ContractNotFoundException(contractId));
        if (contract.getStatus() == PayrollConstants.ContractStatus.TERMINATED) {
            throw new ContractAlreadyTerminatedException(contractId);
        }
        contract.terminate();
        events.publishEvent(new PayrollEvents.ContractTerminated(contractId, actorAccountId, actorBranchId));
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/usecase/ListContracts.java
package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.dto.ContractResponse;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.eduerp.shared.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListContracts {

    private final EmploymentContractRepository contracts;
    private final IdentityManagement identity;

    ListContracts(EmploymentContractRepository contracts, IdentityManagement identity) {
        this.contracts = contracts;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public PageResponse<ContractResponse> execute(Pageable pageable, UUID accountIdFilter) {
        Page<EmploymentContract> page = accountIdFilter == null ? contracts.findAll(pageable)
                : contracts.findAllByAccountId(accountIdFilter, pageable);
        var accountIds = page.getContent().stream().map(EmploymentContract::getAccountId).distinct().toList();
        var accountInfo = identity.summariesOf(accountIds);
        return PageResponse.of(page.map(contract -> CreateContract.toResponse(contract, accountInfo)));
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/usecase/UploadContractFile.java
package com.eduerp.modules.payroll.usecase;

import com.eduerp.integrations.storage.StorageClient;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.ContractNotFoundException;
import com.eduerp.modules.payroll.dto.ContractResponse;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UploadContractFile {

    private final EmploymentContractRepository contracts;
    private final StorageClient storage;
    private final IdentityManagement identity;

    UploadContractFile(EmploymentContractRepository contracts, StorageClient storage, IdentityManagement identity) {
        this.contracts = contracts;
        this.storage = storage;
        this.identity = identity;
    }

    @Transactional
    public ContractResponse execute(UUID contractId, String fileName, String contentType, byte[] content) {
        var contract = contracts.findById(contractId).orElseThrow(() -> new ContractNotFoundException(contractId));
        var key = storage.upload("contracts/" + contractId, fileName, content, contentType);
        contract.setContractFileKey(key);
        var accountInfo = identity.summariesOf(List.of(contract.getAccountId()));
        return CreateContract.toResponse(contract, accountInfo);
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/usecase/DownloadContractFile.java
package com.eduerp.modules.payroll.usecase;

import com.eduerp.integrations.storage.StorageClient;
import com.eduerp.modules.payroll.ContractFileNotFoundException;
import com.eduerp.modules.payroll.ContractNotFoundException;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DownloadContractFile {

    private final EmploymentContractRepository contracts;
    private final StorageClient storage;

    DownloadContractFile(EmploymentContractRepository contracts, StorageClient storage) {
        this.contracts = contracts;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public byte[] execute(UUID contractId) {
        var contract = contracts.findById(contractId).orElseThrow(() -> new ContractNotFoundException(contractId));
        if (contract.getContractFileKey() == null) {
            throw new ContractFileNotFoundException(contractId);
        }
        return storage.download(contract.getContractFileKey());
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/web/package-info.java
/** Adapter HTTP của module payroll: controller mỏng (rule #8 - không chứa nghiệp vụ). */
package com.eduerp.modules.payroll.web;
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/web/ContractAdminController.java
package com.eduerp.modules.payroll.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.payroll.dto.ContractResponse;
import com.eduerp.modules.payroll.dto.CreateContractRequest;
import com.eduerp.modules.payroll.dto.UpdateContractRequest;
import com.eduerp.modules.payroll.usecase.CreateContract;
import com.eduerp.modules.payroll.usecase.DownloadContractFile;
import com.eduerp.modules.payroll.usecase.ListContracts;
import com.eduerp.modules.payroll.usecase.TerminateContract;
import com.eduerp.modules.payroll.usecase.UpdateContract;
import com.eduerp.modules.payroll.usecase.UploadContractFile;
import com.eduerp.shared.AccountPrincipal;
import com.eduerp.shared.PageResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Quản trị hợp đồng lao động - mirror CourseAdminController 1:1 về cấu trúc. */
@RestController
@RequestMapping("/api/payroll/contracts")
class ContractAdminController {

    private final ListContracts listContracts;
    private final CreateContract createContract;
    private final UpdateContract updateContract;
    private final TerminateContract terminateContract;
    private final UploadContractFile uploadContractFile;
    private final DownloadContractFile downloadContractFile;

    ContractAdminController(ListContracts listContracts, CreateContract createContract,
            UpdateContract updateContract, TerminateContract terminateContract,
            UploadContractFile uploadContractFile, DownloadContractFile downloadContractFile) {
        this.listContracts = listContracts;
        this.createContract = createContract;
        this.updateContract = updateContract;
        this.terminateContract = terminateContract;
        this.uploadContractFile = uploadContractFile;
        this.downloadContractFile = downloadContractFile;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_PAYROLL)
    PageResponse<ContractResponse> list(@PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) UUID accountId) {
        return listContracts.execute(pageable, accountId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AccessConstants.AccessRules.CREATE_PAYROLL)
    ContractResponse create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestPart("request") CreateContractRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) throws IOException {
        var created = createContract.execute(principal.accountId(), principal.homeBranchId(), request);
        if (file != null && !file.isEmpty()) {
            return uploadContractFile.execute(created.id(), file.getOriginalFilename(), file.getContentType(),
                    file.getBytes());
        }
        return created;
    }

    @PatchMapping(value = "/{contractId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_PAYROLL)
    ContractResponse update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID contractId,
            @Valid @RequestPart("request") UpdateContractRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) throws IOException {
        var updated = updateContract.execute(contractId, principal.accountId(), principal.homeBranchId(), request);
        if (file != null && !file.isEmpty()) {
            return uploadContractFile.execute(contractId, file.getOriginalFilename(), file.getContentType(),
                    file.getBytes());
        }
        return updated;
    }

    @PostMapping("/{contractId}/terminate")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_PAYROLL)
    void terminate(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID contractId) {
        terminateContract.execute(contractId, principal.accountId(), principal.homeBranchId());
    }

    @GetMapping("/{contractId}/file")
    @PreAuthorize(AccessConstants.AccessRules.READ_PAYROLL)
    ResponseEntity<byte[]> downloadFile(@PathVariable UUID contractId) {
        var content = downloadContractFile.execute(contractId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(content);
    }
}
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=ContractAdminControllerIT`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/eduerp/modules/payroll/usecase/UpdateContract.java \
        backend/src/main/java/com/eduerp/modules/payroll/usecase/TerminateContract.java \
        backend/src/main/java/com/eduerp/modules/payroll/usecase/ListContracts.java \
        backend/src/main/java/com/eduerp/modules/payroll/usecase/UploadContractFile.java \
        backend/src/main/java/com/eduerp/modules/payroll/usecase/DownloadContractFile.java \
        backend/src/main/java/com/eduerp/modules/payroll/web/ \
        backend/src/test/java/com/eduerp/modules/payroll/web/ContractAdminControllerIT.java
git commit -m "feat(payroll): add ContractAdminController with file upload and download"
```

---

### Task 11: `CreatePayrollRun` usecase (Review Focus #4, #5)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/payroll/usecase/CreatePayrollRun.java`
- Test: `backend/src/test/java/com/eduerp/modules/payroll/usecase/CreatePayrollRunTest.java`

**Interfaces:**
- Consumes: `PayrollRunRepository.existsByYearAndMonth/save` (Task 6), `PayslipRepository.saveAll` (Task 6), `EmploymentContractRepository.findAllByStatus` (Task 5), `PayrollRules.baseGrossPay/totalAllowances/socialInsuranceEmployeeShare/socialInsuranceEmployerShare/isInProbation` (Task 7), `PayrollRunAlreadyExistsException` (Task 2), `PayrollRunResponse/CreatePayrollRunRequest` (Task 3).
- Produces: `CreatePayrollRun.execute(UUID actorAccountId, UUID actorBranchId, CreatePayrollRunRequest): PayrollRunResponse` — dùng ở Task 15 (`PayrollRunAdminController`).

> `CreatePayrollRun` **không** publish event (`PayrollEvents` chỉ có 3 record, không có "PayrollRunCreated") và **không** phụ thuộc `IdentityManagement` — cấu trúc constructor này tự nó khoá chặt Review Focus #5 (usecase vật lý không có khả năng truy vấn `Account.status` vì không có dependency nào tới `identity`).

- [ ] **Step 1: Write the failing test**
```java
// backend/src/test/java/com/eduerp/modules/payroll/usecase/CreatePayrollRunTest.java
package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunAlreadyExistsException;
import com.eduerp.modules.payroll.dto.CreatePayrollRunRequest;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreatePayrollRunTest {

    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final PayslipRepository payslips = mock(PayslipRepository.class);
    private final EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
    private final CreatePayrollRun useCase = new CreatePayrollRun(runs, payslips, contracts);

    /** Review Focus #4. */
    @Test
    void rejectsADuplicatePeriod() {
        when(runs.existsByYearAndMonth(2026, 1)).thenReturn(true);

        assertThatThrownBy(
                () -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), new CreatePayrollRunRequest(2026, 1)))
                .isInstanceOf(PayrollRunAlreadyExistsException.class);
    }

    @Test
    void generatesOneDraftPayslipPerActiveContractOnlyWithZeroHoursForCollaborators() {
        var official = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.of(2025, 1, 1), List.of());
        var collaborator = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.COLLABORATOR,
                null, new BigDecimal("150000"), null, null, LocalDate.of(2025, 1, 1), List.of());
        when(runs.existsByYearAndMonth(2026, 2)).thenReturn(false);
        when(runs.save(any(PayrollRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contracts.findAllByStatus(PayrollConstants.ContractStatus.ACTIVE))
                .thenReturn(List.of(official, collaborator));
        when(payslips.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = useCase.execute(UUID.randomUUID(), UUID.randomUUID(), new CreatePayrollRunRequest(2026, 2));

        assertThat(response.payslipCount()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(PayrollConstants.PayrollRunStatus.DRAFT);
        // Tổng gross = 10,000,000 (OFFICIAL, không thử việc) + 0 (COLLABORATOR, hoursWorked=0 khởi tạo).
        assertThat(response.totalGrossPay()).isEqualTo(new BigDecimal("10000000"));
    }

    /** Review Focus #5: usecase này không có dependency nào tới IdentityManagement - cấu trúc
     * constructor tự nó chứng minh CreatePayrollRun không thể truy vấn Account.status, chỉ lọc theo
     * EmploymentContract.status. Test này khoá chặt hành vi "vẫn tính lương dù tài khoản bị khoá". */
    @Test
    void generatesAPayslipRegardlessOfAnyAccountStatusBecauseItHasNoIdentityDependency() {
        var contract = new EmploymentContract(UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.of(2025, 1, 1), List.of());
        when(runs.existsByYearAndMonth(2026, 3)).thenReturn(false);
        when(runs.save(any(PayrollRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contracts.findAllByStatus(PayrollConstants.ContractStatus.ACTIVE)).thenReturn(List.of(contract));
        when(payslips.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = useCase.execute(UUID.randomUUID(), UUID.randomUUID(), new CreatePayrollRunRequest(2026, 3));

        assertThat(response.payslipCount()).isEqualTo(1);
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=CreatePayrollRunTest`
Expected: FAIL — compile error: cannot find symbol `CreatePayrollRun`.
- [ ] **Step 3: Write minimal implementation**
```java
// backend/src/main/java/com/eduerp/modules/payroll/usecase/CreatePayrollRun.java
package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunAlreadyExistsException;
import com.eduerp.modules.payroll.dto.CreatePayrollRunRequest;
import com.eduerp.modules.payroll.dto.PayrollRunResponse;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.payroll.internal.rules.PayrollRules;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Review Focus #5: KHÔNG phụ thuộc IdentityManagement - chỉ lọc EmploymentContract.status, không
 * thể và không được truy vấn Account.status (spec mục 6.5). */
@Service
public class CreatePayrollRun {

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final EmploymentContractRepository contracts;

    CreatePayrollRun(PayrollRunRepository runs, PayslipRepository payslips, EmploymentContractRepository contracts) {
        this.runs = runs;
        this.payslips = payslips;
        this.contracts = contracts;
    }

    @Transactional
    public PayrollRunResponse execute(UUID actorAccountId, UUID actorBranchId, CreatePayrollRunRequest request) {
        if (runs.existsByYearAndMonth(request.year(), request.month())) {
            throw new PayrollRunAlreadyExistsException(request.year(), request.month());
        }
        var payPeriodStart = LocalDate.of(request.year(), request.month(), 1);
        var run = runs.save(new PayrollRun(request.year(), request.month()));

        var activeContracts = contracts.findAllByStatus(PayrollConstants.ContractStatus.ACTIVE);
        var createdPayslips = activeContracts.stream()
                .map(contract -> buildDraftPayslip(run, contract, payPeriodStart))
                .toList();
        payslips.saveAll(createdPayslips);

        return toResponse(run, createdPayslips);
    }

    private static Payslip buildDraftPayslip(PayrollRun run, EmploymentContract contract, LocalDate payPeriodStart) {
        var hoursWorked = contract.getContractType() == PayrollConstants.ContractType.COLLABORATOR
                ? BigDecimal.ZERO : null;
        var grossPay = PayrollRules.baseGrossPay(contract, payPeriodStart,
                hoursWorked == null ? BigDecimal.ZERO : hoursWorked);
        var allowancesTotal = PayrollRules.totalAllowances(contract.getAllowances());
        var socialEmployee = PayrollRules.socialInsuranceEmployeeShare(grossPay, contract.getContractType());
        var socialEmployer = PayrollRules.socialInsuranceEmployerShare(grossPay, contract.getContractType());
        var inProbation = PayrollRules.isInProbation(contract, payPeriodStart);
        return new Payslip(run, contract.getAccountId(), contract.getContractType(), grossPay, allowancesTotal,
                socialEmployee, socialEmployer, hoursWorked, inProbation);
    }

    private static PayrollRunResponse toResponse(PayrollRun run, List<Payslip> createdPayslips) {
        var totalGross = createdPayslips.stream().map(Payslip::getGrossPay).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PayrollRunResponse(run.getId(), run.getYear(), run.getMonth(), run.getStatus(),
                createdPayslips.size(), totalGross);
    }
}
```
> `import java.util.UUID;` cũng cần thêm vào đầu file (dùng cho `actorAccountId`/`actorBranchId`).
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=CreatePayrollRunTest`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/eduerp/modules/payroll/usecase/CreatePayrollRun.java \
        backend/src/test/java/com/eduerp/modules/payroll/usecase/CreatePayrollRunTest.java
git commit -m "feat(payroll): add CreatePayrollRun usecase"
```

---

### Task 12: `UpdatePayslip` usecase

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/payroll/usecase/UpdatePayslip.java`
- Test: `backend/src/test/java/com/eduerp/modules/payroll/usecase/UpdatePayslipTest.java`

**Interfaces:**
- Consumes: `Payslip.getPayrollRun/getContractType/getAccountId` (Task 6), `EmploymentContractRepository.findByAccountIdAndStatus` (Task 5), `PayrollRules.baseGrossPay/socialInsuranceEmployeeShare/socialInsuranceEmployerShare` (Task 7), `PayrollRunNotEditableException/ContractNotFoundException` (Task 2), `UpdatePayslipRequest/PayslipResponse` (Task 3).
- Produces: `UpdatePayslip.execute(UUID payslipId, UUID actorAccountId, UUID actorBranchId, UpdatePayslipRequest): PayslipResponse` — dùng ở Task 15 (`PayrollRunAdminController`).

- [ ] **Step 1: Write the failing test**
```java
// backend/src/test/java/com/eduerp/modules/payroll/usecase/UpdatePayslipTest.java
package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunNotEditableException;
import com.eduerp.modules.payroll.dto.UpdatePayslipRequest;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdatePayslipTest {

    private final PayslipRepository payslipRepository = mock(PayslipRepository.class);
    private final EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
    private final UpdatePayslip useCase = new UpdatePayslip(payslipRepository, contracts);

    @Test
    void rejectsEditingAPayslipWhenRunIsNotDraft() {
        var run = new PayrollRun(2026, 1);
        run.submitForApproval();
        var payslip = new Payslip(run, UUID.randomUUID(), PayrollConstants.ContractType.COLLABORATOR,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
        var payslipId = UUID.randomUUID();
        when(payslipRepository.findById(payslipId)).thenReturn(Optional.of(payslip));

        assertThatThrownBy(() -> useCase.execute(payslipId, UUID.randomUUID(), UUID.randomUUID(),
                new UpdatePayslipRequest(new BigDecimal("10"), BigDecimal.ZERO, null)))
                .isInstanceOf(PayrollRunNotEditableException.class);
    }

    @Test
    void recomputesGrossPayAndInsuranceWhenCollaboratorHoursChange() {
        var run = new PayrollRun(2026, 1);
        var accountId = UUID.randomUUID();
        var payslip = new Payslip(run, accountId, PayrollConstants.ContractType.COLLABORATOR, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
        var contract = new EmploymentContract(accountId, PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), null, null, LocalDate.of(2025, 1, 1), List.of());
        var payslipId = UUID.randomUUID();
        when(payslipRepository.findById(payslipId)).thenReturn(Optional.of(payslip));
        when(contracts.findByAccountIdAndStatus(accountId, PayrollConstants.ContractStatus.ACTIVE))
                .thenReturn(Optional.of(contract));

        var response = useCase.execute(payslipId, UUID.randomUUID(), UUID.randomUUID(),
                new UpdatePayslipRequest(new BigDecimal("20"), new BigDecimal("100000"), "Đã dạy đủ giờ"));

        assertThat(response.grossPay()).isEqualTo(new BigDecimal("150000").multiply(new BigDecimal("20")));
        assertThat(response.hoursWorked()).isEqualTo(new BigDecimal("20"));
        assertThat(response.incomeTaxWithheld()).isEqualTo(new BigDecimal("100000"));
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=UpdatePayslipTest`
Expected: FAIL — compile error: cannot find symbol `UpdatePayslip`.
- [ ] **Step 3: Write minimal implementation**
```java
// backend/src/main/java/com/eduerp/modules/payroll/usecase/UpdatePayslip.java
package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.ContractNotFoundException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunNotEditableException;
import com.eduerp.modules.payroll.dto.PayslipResponse;
import com.eduerp.modules.payroll.dto.UpdatePayslipRequest;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.payroll.internal.rules.PayrollRules;
import com.eduerp.modules.payroll.PayslipNotFoundException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdatePayslip {

    private final PayslipRepository payslips;
    private final EmploymentContractRepository contracts;

    UpdatePayslip(PayslipRepository payslips, EmploymentContractRepository contracts) {
        this.payslips = payslips;
        this.contracts = contracts;
    }

    @Transactional
    public PayslipResponse execute(UUID payslipId, UUID actorAccountId, UUID actorBranchId,
            UpdatePayslipRequest request) {
        var payslip = payslips.findById(payslipId).orElseThrow(() -> new PayslipNotFoundException(payslipId));
        var run = payslip.getPayrollRun();
        if (run.getStatus() != PayrollConstants.PayrollRunStatus.DRAFT) {
            throw new PayrollRunNotEditableException(run.getId());
        }

        if (payslip.getContractType() == PayrollConstants.ContractType.COLLABORATOR && request.hoursWorked() != null) {
            // Hợp đồng dùng để tính lại grossPay là hợp đồng ACTIVE của chính account này - nếu hợp
            // đồng đã bị kết thúc giữa lúc tạo kỳ lương và lúc sửa phiếu, đây là lỗi dữ liệu thật
            // (không phải "không tìm thấy hợp đồng theo id" theo nghĩa thông thường, nhưng tái dùng
            // ContractNotFoundException vì cùng bản chất 404 - hợp đồng không còn tồn tại ở trạng thái
            // cần để tính lương).
            var contract = contracts.findByAccountIdAndStatus(payslip.getAccountId(),
                    PayrollConstants.ContractStatus.ACTIVE).orElseThrow(
                            () -> new ContractNotFoundException(payslip.getAccountId()));
            var payPeriodStart = LocalDate.of(run.getYear(), run.getMonth(), 1);
            var grossPay = PayrollRules.baseGrossPay(contract, payPeriodStart, request.hoursWorked());
            payslip.setHoursWorked(request.hoursWorked());
            payslip.setGrossPay(grossPay);
            payslip.setSocialInsuranceEmployee(
                    PayrollRules.socialInsuranceEmployeeShare(grossPay, payslip.getContractType()));
            payslip.setSocialInsuranceEmployer(
                    PayrollRules.socialInsuranceEmployerShare(grossPay, payslip.getContractType()));
        }
        if (request.incomeTaxWithheld() != null) {
            payslip.setIncomeTaxWithheld(request.incomeTaxWithheld());
        }
        payslip.setNote(request.note());

        var netPay = PayrollRules.netPay(payslip.getGrossPay(), payslip.getAllowancesTotal(),
                payslip.getSocialInsuranceEmployee(), payslip.getIncomeTaxWithheld());
        return new PayslipResponse(payslip.getId(), payslip.getAccountId(), null, payslip.getContractType(),
                payslip.getGrossPay(), payslip.getSocialInsuranceEmployee(), payslip.getSocialInsuranceEmployer(),
                payslip.getIncomeTaxWithheld(), netPay, payslip.getHoursWorked(), payslip.isInProbation());
    }
}
```
> `accountFullName` trả `null` ở đây (usecase này không có `IdentityManagement` - chỉ sửa một phiếu lương, không cần hiển thị lại tên); `GetPayrollRun`/`ListPayrollRuns` (Task 15) là nơi điền tên đầy đủ khi hiển thị danh sách. Dùng `List.getAllowancesTotal()`/`getSocialInsuranceEmployee()` getter đã có từ `@Getter` trên `Payslip` (Task 6).
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=UpdatePayslipTest`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/eduerp/modules/payroll/usecase/UpdatePayslip.java \
        backend/src/test/java/com/eduerp/modules/payroll/usecase/UpdatePayslipTest.java
git commit -m "feat(payroll): add UpdatePayslip usecase"
```

---

### Task 13: `SubmitPayrollRunForApproval` usecase (chặn CTV chưa nhập giờ)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/payroll/usecase/SubmitPayrollRunForApproval.java`
- Test: `backend/src/test/java/com/eduerp/modules/payroll/usecase/SubmitPayrollRunForApprovalTest.java`

**Interfaces:**
- Consumes: `PayrollRunRepository.findById` (Task 6), `PayslipRepository.findAllByPayrollRun_Id` (Task 6), `PayrollRun.submitForApproval()` (Task 6), `PayrollRunNotFoundException/PayrollRunNotEditableException/InvalidContractTermsException` (Task 2).
- Produces: `SubmitPayrollRunForApproval.execute(UUID payrollRunId, UUID actorAccountId, UUID actorBranchId): void` — dùng ở Task 15.

- [ ] **Step 1: Write the failing test**
```java
// backend/src/test/java/com/eduerp/modules/payroll/usecase/SubmitPayrollRunForApprovalTest.java
package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.payroll.InvalidContractTermsException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunNotEditableException;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubmitPayrollRunForApprovalTest {

    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final PayslipRepository payslips = mock(PayslipRepository.class);
    private final SubmitPayrollRunForApproval useCase = new SubmitPayrollRunForApproval(runs, payslips);

    @Test
    void rejectsSubmittingARunThatIsNotDraft() {
        var run = new PayrollRun(2026, 1);
        run.submitForApproval();
        var runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> useCase.execute(runId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(PayrollRunNotEditableException.class);
    }

    /** Spec mục 3.3 bước 4: chặn submit khi còn Payslip CTV với hoursWorked == 0. */
    @Test
    void rejectsSubmittingWhenACollaboratorPayslipHasZeroHours() {
        var run = new PayrollRun(2026, 1);
        var runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        when(payslips.findAllByPayrollRun_Id(runId)).thenReturn(List.of(
                new Payslip(run, UUID.randomUUID(), PayrollConstants.ContractType.COLLABORATOR, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false)));

        assertThatThrownBy(() -> useCase.execute(runId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(InvalidContractTermsException.class);
    }

    @Test
    void submitsWhenAllCollaboratorPayslipsHaveHoursFilledIn() {
        var run = new PayrollRun(2026, 1);
        var runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        when(payslips.findAllByPayrollRun_Id(runId)).thenReturn(List.of(
                new Payslip(run, UUID.randomUUID(), PayrollConstants.ContractType.COLLABORATOR,
                        new BigDecimal("3000000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("20"), false),
                new Payslip(run, UUID.randomUUID(), PayrollConstants.ContractType.OFFICIAL, new BigDecimal("10000000"),
                        BigDecimal.ZERO, new BigDecimal("1050000"), new BigDecimal("2150000"), null, false)));

        useCase.execute(runId, UUID.randomUUID(), UUID.randomUUID());

        assertThat(run.getStatus()).isEqualTo(PayrollConstants.PayrollRunStatus.PENDING_APPROVAL);
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=SubmitPayrollRunForApprovalTest`
Expected: FAIL — compile error: cannot find symbol `SubmitPayrollRunForApproval`.
- [ ] **Step 3: Write minimal implementation**
```java
// backend/src/main/java/com/eduerp/modules/payroll/usecase/SubmitPayrollRunForApproval.java
package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.InvalidContractTermsException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunNotEditableException;
import com.eduerp.modules.payroll.PayrollRunNotFoundException;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmitPayrollRunForApproval {

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;

    SubmitPayrollRunForApproval(PayrollRunRepository runs, PayslipRepository payslips) {
        this.runs = runs;
        this.payslips = payslips;
    }

    @Transactional
    public void execute(UUID payrollRunId, UUID actorAccountId, UUID actorBranchId) {
        var run = runs.findById(payrollRunId).orElseThrow(() -> new PayrollRunNotFoundException(payrollRunId));
        if (run.getStatus() != PayrollConstants.PayrollRunStatus.DRAFT) {
            throw new PayrollRunNotEditableException(payrollRunId);
        }
        var runPayslips = payslips.findAllByPayrollRun_Id(payrollRunId);
        var hasUnfilledCollaboratorHours = runPayslips.stream()
                .anyMatch(p -> p.getContractType() == PayrollConstants.ContractType.COLLABORATOR
                        && p.getHoursWorked() != null && p.getHoursWorked().signum() == 0);
        if (hasUnfilledCollaboratorHours) {
            throw new InvalidContractTermsException("Còn phiếu lương CTV chưa nhập giờ dạy");
        }
        run.submitForApproval();
    }
}
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=SubmitPayrollRunForApprovalTest`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/eduerp/modules/payroll/usecase/SubmitPayrollRunForApproval.java \
        backend/src/test/java/com/eduerp/modules/payroll/usecase/SubmitPayrollRunForApprovalTest.java
git commit -m "feat(payroll): add SubmitPayrollRunForApproval usecase"
```

---

### Task 14: `ApprovePayrollRun` + `RejectPayrollRun` usecases

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/payroll/usecase/ApprovePayrollRun.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/usecase/RejectPayrollRun.java`
- Test: `backend/src/test/java/com/eduerp/modules/payroll/usecase/ApproveRejectPayrollRunTest.java`

**Interfaces:**
- Consumes: `PayrollRunRepository.findById` (Task 6), `PayrollRun.approve()/reject(String)` (Task 6), `PayrollRunNotFoundException/PayrollRunNotPendingApprovalException` (Task 2), `PayrollEvents.PayrollRunApproved` (Task 3).
- Produces: `ApprovePayrollRun.execute(UUID payrollRunId, UUID actorAccountId, UUID actorBranchId): void`, `RejectPayrollRun.execute(UUID payrollRunId, String reason, UUID actorAccountId, UUID actorBranchId): void` — dùng ở Task 15.

- [ ] **Step 1: Write the failing test**
```java
// backend/src/test/java/com/eduerp/modules/payroll/usecase/ApproveRejectPayrollRunTest.java
package com.eduerp.modules.payroll.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollEvents;
import com.eduerp.modules.payroll.PayrollRunNotPendingApprovalException;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ApproveRejectPayrollRunTest {

    private final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ApprovePayrollRun approveUseCase = new ApprovePayrollRun(runs, events);
    private final RejectPayrollRun rejectUseCase = new RejectPayrollRun(runs);

    @Test
    void approveRejectsARunThatIsStillDraft() {
        var run = new PayrollRun(2026, 1);
        var runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> approveUseCase.execute(runId, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(PayrollRunNotPendingApprovalException.class);
    }

    @Test
    void approveTransitionsToApprovedAndPublishesEvent() {
        var run = new PayrollRun(2026, 1);
        run.submitForApproval();
        var runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(Optional.of(run));

        approveUseCase.execute(runId, UUID.randomUUID(), UUID.randomUUID());

        assertThat(run.getStatus()).isEqualTo(PayrollConstants.PayrollRunStatus.APPROVED);
        verify(events).publishEvent(any(PayrollEvents.PayrollRunApproved.class));
    }

    @Test
    void rejectRejectsARunThatIsStillDraft() {
        var run = new PayrollRun(2026, 1);
        var runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> rejectUseCase.execute(runId, "Thiếu giờ CTV", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(PayrollRunNotPendingApprovalException.class);
    }

    @Test
    void rejectTransitionsBackToDraftWithReason() {
        var run = new PayrollRun(2026, 1);
        run.submitForApproval();
        var runId = UUID.randomUUID();
        when(runs.findById(runId)).thenReturn(Optional.of(run));

        rejectUseCase.execute(runId, "Sai số giờ dạy", UUID.randomUUID(), UUID.randomUUID());

        assertThat(run.getStatus()).isEqualTo(PayrollConstants.PayrollRunStatus.DRAFT);
        assertThat(run.getRejectionReason()).isEqualTo("Sai số giờ dạy");
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=ApproveRejectPayrollRunTest`
Expected: FAIL — compile error: cannot find symbol `ApprovePayrollRun`/`RejectPayrollRun`.
- [ ] **Step 3: Write minimal implementation**
```java
// backend/src/main/java/com/eduerp/modules/payroll/usecase/ApprovePayrollRun.java
package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollEvents;
import com.eduerp.modules.payroll.PayrollRunNotFoundException;
import com.eduerp.modules.payroll.PayrollRunNotPendingApprovalException;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovePayrollRun {

    private final PayrollRunRepository runs;
    private final ApplicationEventPublisher events;

    ApprovePayrollRun(PayrollRunRepository runs, ApplicationEventPublisher events) {
        this.runs = runs;
        this.events = events;
    }

    @Transactional
    public void execute(UUID payrollRunId, UUID actorAccountId, UUID actorBranchId) {
        var run = runs.findById(payrollRunId).orElseThrow(() -> new PayrollRunNotFoundException(payrollRunId));
        if (run.getStatus() != PayrollConstants.PayrollRunStatus.PENDING_APPROVAL) {
            throw new PayrollRunNotPendingApprovalException(payrollRunId);
        }
        run.approve();
        events.publishEvent(new PayrollEvents.PayrollRunApproved(payrollRunId, actorAccountId, actorBranchId));
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/usecase/RejectPayrollRun.java
package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunNotFoundException;
import com.eduerp.modules.payroll.PayrollRunNotPendingApprovalException;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RejectPayrollRun {

    private final PayrollRunRepository runs;

    RejectPayrollRun(PayrollRunRepository runs) {
        this.runs = runs;
    }

    @Transactional
    public void execute(UUID payrollRunId, String reason, UUID actorAccountId, UUID actorBranchId) {
        var run = runs.findById(payrollRunId).orElseThrow(() -> new PayrollRunNotFoundException(payrollRunId));
        if (run.getStatus() != PayrollConstants.PayrollRunStatus.PENDING_APPROVAL) {
            throw new PayrollRunNotPendingApprovalException(payrollRunId);
        }
        run.reject(reason);
    }
}
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=ApproveRejectPayrollRunTest`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/eduerp/modules/payroll/usecase/ApprovePayrollRun.java \
        backend/src/main/java/com/eduerp/modules/payroll/usecase/RejectPayrollRun.java \
        backend/src/test/java/com/eduerp/modules/payroll/usecase/ApproveRejectPayrollRunTest.java
git commit -m "feat(payroll): add ApprovePayrollRun and RejectPayrollRun usecases"
```

---

### Task 15: `GetPayrollRun`/`ListPayrollRuns` + `PayrollRunAdminController` + full flow IT (Review Focus #4, #5 ở tầng HTTP)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/payroll/usecase/GetPayrollRun.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/usecase/ListPayrollRuns.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/dto/RejectPayrollRunRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/payroll/web/PayrollRunAdminController.java`
- Test: `backend/src/test/java/com/eduerp/modules/payroll/web/PayrollRunAdminControllerIT.java`

**Interfaces:**
- Consumes: tất cả usecase Task 11-14, `IdentityManagement.summariesOf` (đã có sẵn), `PayrollRunDetailResponse/PayrollRunResponse/PayslipResponse` (Task 3), `AccessConstants.AccessRules.READ_PAYROLL/CREATE_PAYROLL/UPDATE_PAYROLL/APPROVE_PAYROLL` (Task 1).
- Produces: `GetPayrollRun.execute(UUID payrollRunId): PayrollRunDetailResponse`, `ListPayrollRuns.execute(Pageable): PageResponse<PayrollRunResponse>`. `RejectPayrollRunRequest(@NotBlank String reason)` — DTO nhỏ cần thêm vì `rejectPayrollRun` nhận `String reason` trần (spec mục 3.2), nhưng HTTP POST body phải có hình dạng JSON record, mirror cách `UpdateCourseRequest` luôn là record dù chỉ bọc field đơn giản. HTTP: `GET/POST /api/payroll/runs`, `GET /api/payroll/runs/{id}`, `PATCH /api/payroll/runs/{id}/payslips/{payslipId}`, `POST /api/payroll/runs/{id}/submit`, `POST /api/payroll/runs/{id}/approve`, `POST /api/payroll/runs/{id}/reject`.

- [ ] **Step 1: Write the failing test**
```java
// backend/src/test/java/com/eduerp/modules/payroll/web/PayrollRunAdminControllerIT.java
package com.eduerp.modules.payroll.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.audit.AuditConstants;
import com.eduerp.modules.audit.AuditManagement;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.dto.CreateContractRequest;
import com.eduerp.modules.payroll.dto.CreatePayrollRunRequest;
import com.eduerp.modules.payroll.dto.RejectPayrollRunRequest;
import com.eduerp.modules.payroll.dto.UpdatePayslipRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
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
class PayrollRunAdminControllerIT {

    private static final String PASSWORD = "Password123!";

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
    AuditManagement audit;

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

    private UUID createOfficialContract(Cookie admin, String email) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        var accountId = accounts.save(account).getId();
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.OFFICIAL,
                new BigDecimal("10000000"), null, null, null, LocalDate.of(2026, 1, 1), List.of());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/payroll/contracts")
                        .file(new org.springframework.mock.web.MockMultipartFile("request", "",
                                MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request)))
                        .cookie(admin).with(csrf()));
        return accountId;
    }

    private UUID createCollaboratorContract(Cookie admin, String email) throws Exception {
        var account = new Account(email, passwordEncoder.encode(PASSWORD), email, null);
        account.recordFirstLogin();
        var accountId = accounts.save(account).getId();
        var request = new CreateContractRequest(accountId, PayrollConstants.ContractType.COLLABORATOR, null,
                new BigDecimal("150000"), null, null, LocalDate.of(2026, 1, 1), List.of());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/payroll/contracts")
                        .file(new org.springframework.mock.web.MockMultipartFile("request", "",
                                MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request)))
                        .cookie(admin).with(csrf()));
        return accountId;
    }

    @Test
    void runsTheFullDraftToApprovedFlowAndAuditsApproval() throws Exception {
        var admin = signIn("payroll-run-admin-1@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        createOfficialContract(admin, "run-official-1@eduerp.local");
        var collaboratorAccountId = createCollaboratorContract(admin, "run-collaborator-1@eduerp.local");

        var createResult = mockMvc.perform(post("/api/payroll/runs").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(2027, 1))))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);
        var runId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        var detailResult = mockMvc.perform(get("/api/payroll/runs/" + runId).cookie(admin)).andReturn();
        var payslips = objectMapper.readTree(detailResult.getResponse().getContentAsString()).get("payslips");
        String collaboratorPayslipId = null;
        for (var node : payslips) {
            if (collaboratorAccountId.toString().equals(node.get("accountId").asText())) {
                collaboratorPayslipId = node.get("id").asText();
            }
        }
        assertThat(collaboratorPayslipId).isNotNull();

        // Review Focus mục 3.3 bước 4 ở tầng HTTP: chưa nhập giờ CTV thì submit bị chặn 400.
        var earlySubmit = mockMvc.perform(post("/api/payroll/runs/" + runId + "/submit").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(earlySubmit.getResponse().getStatus()).isEqualTo(400);

        mockMvc.perform(patch("/api/payroll/runs/" + runId + "/payslips/" + collaboratorPayslipId)
                .cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdatePayslipRequest(new BigDecimal("20"),
                        BigDecimal.ZERO, "Đã dạy đủ giờ"))));

        var submitResult = mockMvc.perform(post("/api/payroll/runs/" + runId + "/submit").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(submitResult.getResponse().getStatus()).isEqualTo(200);

        var approveResult = mockMvc.perform(post("/api/payroll/runs/" + runId + "/approve").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(approveResult.getResponse().getStatus()).isEqualTo(200);

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.PAYROLL_RUN,
                    AuditConstants.Actions.PAYROLL_RUN_APPROVE, 20).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(runId);
        });
    }

    @Test
    void rejectSendsTheRunBackToDraftWithAReason() throws Exception {
        var admin = signIn("payroll-run-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        createOfficialContract(admin, "run-official-2@eduerp.local");
        mockMvc.perform(post("/api/payroll/runs").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(2027, 2))));
        var listResult = mockMvc.perform(get("/api/payroll/runs").cookie(admin)).andReturn();
        var runId = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items").get(0)
                .get("id").asText();
        mockMvc.perform(post("/api/payroll/runs/" + runId + "/submit").cookie(admin).with(csrf()));

        var rejectResult = mockMvc.perform(post("/api/payroll/runs/" + runId + "/reject").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RejectPayrollRunRequest("Sai số liệu"))))
                .andReturn();

        assertThat(rejectResult.getResponse().getStatus()).isEqualTo(200);
    }

    /** Review Focus #4 ở tầng HTTP. */
    @Test
    void rejectsADuplicatePeriodAtHttpLevel() throws Exception {
        var admin = signIn("payroll-run-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        mockMvc.perform(post("/api/payroll/runs").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(2027, 3))));

        var duplicateResult = mockMvc.perform(post("/api/payroll/runs").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(2027, 3))))
                .andReturn();

        assertThat(duplicateResult.getResponse().getStatus()).isEqualTo(409);
        assertThat(duplicateResult.getResponse().getContentAsString()).contains("PAYROLL_RUN_ALREADY_EXISTS");
    }

    /** Review Focus #5 ở tầng HTTP đầy đủ: khoá tài khoản (DISABLED) rồi tạo kỳ lương, hợp đồng
     * ACTIVE của tài khoản đó vẫn được tính lương. */
    @Test
    void stillPaysAnEmployeeWhoseAccountWasDisabled() throws Exception {
        var admin = signIn("payroll-run-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var employeeId = createOfficialContract(admin, "run-disabled-1@eduerp.local");
        var employeeAccount = accounts.findById(employeeId).orElseThrow();
        employeeAccount.disable();
        accounts.save(employeeAccount);

        var createResult = mockMvc.perform(post("/api/payroll/runs").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(2027, 4))))
                .andReturn();
        var runId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        var detailResult = mockMvc.perform(get("/api/payroll/runs/" + runId).cookie(admin)).andReturn();
        var payslips = objectMapper.readTree(detailResult.getResponse().getContentAsString()).get("payslips");
        var hasDisabledEmployeePayslip = false;
        for (var node : payslips) {
            if (employeeId.toString().equals(node.get("accountId").asText())) {
                hasDisabledEmployeePayslip = true;
            }
        }
        assertThat(hasDisabledEmployeePayslip).isTrue();
    }

    @Test
    void refusesApprovingWithoutApprovePermission() throws Exception {
        var accountant = signIn("payroll-accountant@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        // Lưu ý: trong hệ thống thật, "Kế toán" là một role tự tạo qua màn RBAC chỉ có
        // CREATE+READ+UPDATE (không APPROVE) - test này dùng trực tiếp account không có quyền nào
        // (TEACHER) để khẳng định @PreAuthorize chặn đúng, không phụ thuộc việc seed thêm role demo.
        var outsider = signIn("payroll-run-outsider@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        mockMvc.perform(post("/api/payroll/runs").cookie(accountant).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreatePayrollRunRequest(2027, 5))));
        var listResult = mockMvc.perform(get("/api/payroll/runs").cookie(accountant)).andReturn();
        var runId = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items").get(0)
                .get("id").asText();
        mockMvc.perform(post("/api/payroll/runs/" + runId + "/submit").cookie(accountant).with(csrf()));

        var result = mockMvc.perform(post("/api/payroll/runs/" + runId + "/approve").cookie(outsider).with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }
}
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=PayrollRunAdminControllerIT`
Expected: FAIL — compile error: cannot find symbol `PayrollRunAdminController`/`RejectPayrollRunRequest` (chưa tồn tại).
- [ ] **Step 3: Write minimal implementation**
```java
// backend/src/main/java/com/eduerp/modules/payroll/dto/RejectPayrollRunRequest.java
package com.eduerp.modules.payroll.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectPayrollRunRequest(@NotBlank String reason) {
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/usecase/GetPayrollRun.java
package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.PayrollRunNotFoundException;
import com.eduerp.modules.payroll.dto.PayrollRunDetailResponse;
import com.eduerp.modules.payroll.dto.PayrollRunResponse;
import com.eduerp.modules.payroll.dto.PayslipResponse;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.payroll.internal.rules.PayrollRules;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPayrollRun {

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final IdentityManagement identity;

    GetPayrollRun(PayrollRunRepository runs, PayslipRepository payslips, IdentityManagement identity) {
        this.runs = runs;
        this.payslips = payslips;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public PayrollRunDetailResponse execute(UUID payrollRunId) {
        var run = runs.findById(payrollRunId).orElseThrow(() -> new PayrollRunNotFoundException(payrollRunId));
        var runPayslips = payslips.findAllByPayrollRun_Id(payrollRunId);
        var accountIds = runPayslips.stream().map(Payslip::getAccountId).distinct().toList();
        var accountInfo = identity.summariesOf(accountIds);
        var payslipResponses = runPayslips.stream().map(p -> toPayslipResponse(p, accountInfo)).toList();
        var totalGross = runPayslips.stream().map(Payslip::getGrossPay).reduce(BigDecimal.ZERO, BigDecimal::add);
        var runResponse = new PayrollRunResponse(run.getId(), run.getYear(), run.getMonth(), run.getStatus(),
                runPayslips.size(), totalGross);
        return new PayrollRunDetailResponse(runResponse, payslipResponses);
    }

    static PayslipResponse toPayslipResponse(Payslip p, Map<UUID, IdentityManagement.AccountBasicInfo> accountInfo) {
        var account = accountInfo.get(p.getAccountId());
        var netPay = PayrollRules.netPay(p.getGrossPay(), p.getAllowancesTotal(), p.getSocialInsuranceEmployee(),
                p.getIncomeTaxWithheld());
        return new PayslipResponse(p.getId(), p.getAccountId(), account == null ? null : account.fullName(),
                p.getContractType(), p.getGrossPay(), p.getSocialInsuranceEmployee(), p.getSocialInsuranceEmployer(),
                p.getIncomeTaxWithheld(), netPay, p.getHoursWorked(), p.isInProbation());
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/usecase/ListPayrollRuns.java
package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.dto.PayrollRunResponse;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.shared.PageResponse;
import java.math.BigDecimal;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListPayrollRuns {

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;

    ListPayrollRuns(PayrollRunRepository runs, PayslipRepository payslips) {
        this.runs = runs;
        this.payslips = payslips;
    }

    @Transactional(readOnly = true)
    public PageResponse<PayrollRunResponse> execute(Pageable pageable) {
        var page = runs.findAll(pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    private PayrollRunResponse toResponse(PayrollRun run) {
        var runPayslips = payslips.findAllByPayrollRun_Id(run.getId());
        var totalGross = runPayslips.stream().map(Payslip::getGrossPay).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PayrollRunResponse(run.getId(), run.getYear(), run.getMonth(), run.getStatus(),
                runPayslips.size(), totalGross);
    }
}
```
```java
// backend/src/main/java/com/eduerp/modules/payroll/web/PayrollRunAdminController.java
package com.eduerp.modules.payroll.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.payroll.dto.CreatePayrollRunRequest;
import com.eduerp.modules.payroll.dto.PayrollRunDetailResponse;
import com.eduerp.modules.payroll.dto.PayrollRunResponse;
import com.eduerp.modules.payroll.dto.PayslipResponse;
import com.eduerp.modules.payroll.dto.RejectPayrollRunRequest;
import com.eduerp.modules.payroll.dto.UpdatePayslipRequest;
import com.eduerp.modules.payroll.usecase.ApprovePayrollRun;
import com.eduerp.modules.payroll.usecase.CreatePayrollRun;
import com.eduerp.modules.payroll.usecase.GetPayrollRun;
import com.eduerp.modules.payroll.usecase.ListPayrollRuns;
import com.eduerp.modules.payroll.usecase.RejectPayrollRun;
import com.eduerp.modules.payroll.usecase.SubmitPayrollRunForApproval;
import com.eduerp.modules.payroll.usecase.UpdatePayslip;
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

/** Quản trị kỳ lương - mirror CourseAdminController 1:1 về cấu trúc. */
@RestController
@RequestMapping("/api/payroll/runs")
class PayrollRunAdminController {

    private final ListPayrollRuns listPayrollRuns;
    private final CreatePayrollRun createPayrollRun;
    private final GetPayrollRun getPayrollRun;
    private final UpdatePayslip updatePayslip;
    private final SubmitPayrollRunForApproval submitPayrollRunForApproval;
    private final ApprovePayrollRun approvePayrollRun;
    private final RejectPayrollRun rejectPayrollRun;

    PayrollRunAdminController(ListPayrollRuns listPayrollRuns, CreatePayrollRun createPayrollRun,
            GetPayrollRun getPayrollRun, UpdatePayslip updatePayslip,
            SubmitPayrollRunForApproval submitPayrollRunForApproval, ApprovePayrollRun approvePayrollRun,
            RejectPayrollRun rejectPayrollRun) {
        this.listPayrollRuns = listPayrollRuns;
        this.createPayrollRun = createPayrollRun;
        this.getPayrollRun = getPayrollRun;
        this.updatePayslip = updatePayslip;
        this.submitPayrollRunForApproval = submitPayrollRunForApproval;
        this.approvePayrollRun = approvePayrollRun;
        this.rejectPayrollRun = rejectPayrollRun;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_PAYROLL)
    PageResponse<PayrollRunResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return listPayrollRuns.execute(pageable);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_PAYROLL)
    PayrollRunResponse create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreatePayrollRunRequest request) {
        return createPayrollRun.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @GetMapping("/{runId}")
    @PreAuthorize(AccessConstants.AccessRules.READ_PAYROLL)
    PayrollRunDetailResponse get(@PathVariable UUID runId) {
        return getPayrollRun.execute(runId);
    }

    @PatchMapping("/{runId}/payslips/{payslipId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_PAYROLL)
    PayslipResponse updatePayslip(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID runId,
            @PathVariable UUID payslipId, @Valid @RequestBody UpdatePayslipRequest request) {
        return updatePayslip.execute(payslipId, principal.accountId(), principal.homeBranchId(), request);
    }

    @PostMapping("/{runId}/submit")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_PAYROLL)
    void submit(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID runId) {
        submitPayrollRunForApproval.execute(runId, principal.accountId(), principal.homeBranchId());
    }

    @PostMapping("/{runId}/approve")
    @PreAuthorize(AccessConstants.AccessRules.APPROVE_PAYROLL)
    void approve(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID runId) {
        approvePayrollRun.execute(runId, principal.accountId(), principal.homeBranchId());
    }

    @PostMapping("/{runId}/reject")
    @PreAuthorize(AccessConstants.AccessRules.APPROVE_PAYROLL)
    void reject(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID runId,
            @Valid @RequestBody RejectPayrollRunRequest request) {
        rejectPayrollRun.execute(runId, request.reason(), principal.accountId(), principal.homeBranchId());
    }
}
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=PayrollRunAdminControllerIT`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/eduerp/modules/payroll/usecase/GetPayrollRun.java \
        backend/src/main/java/com/eduerp/modules/payroll/usecase/ListPayrollRuns.java \
        backend/src/main/java/com/eduerp/modules/payroll/dto/RejectPayrollRunRequest.java \
        backend/src/main/java/com/eduerp/modules/payroll/web/PayrollRunAdminController.java \
        backend/src/test/java/com/eduerp/modules/payroll/web/PayrollRunAdminControllerIT.java
git commit -m "feat(payroll): add PayrollRunAdminController with full approval flow"
```

---

### Task 16: `package-info` (`@ApplicationModule`) + `ModularityTests` pin `modules.payroll`/`integrations.storage`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/payroll/package-info.java`
- Modify: `backend/src/test/java/com/eduerp/ModularityTests.java`

**Interfaces:**
- Consumes: toàn bộ module `modules.payroll`/`integrations.storage` đã xây ở Task 2-15.
- Produces: không có API mới — task này khoá ranh giới module vào build (rule Spring Modulith `detection-strategy: explicitly-annotated`: thiếu `@ApplicationModule` thì package coi như không tồn tại với `ApplicationModules.verify()`, im lặng không kiểm biên gì cho nó).

- [ ] **Step 1: Write the failing test**
Sửa trực tiếp assertion đã có trong `ModularityTests.everyDomainAndIntegrationPackageIsADetectedModule`:
```java
        assertThat(detected).containsExactlyInAnyOrder("core", "shared", "modules.identity", "modules.access",
                "modules.organization", "modules.audit", "modules.dashboard", "modules.courses",
                "modules.teachers", "modules.students", "modules.payroll", "integrations.cache",
                "integrations.mail", "integrations.notification", "integrations.storage");
```
- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend && mvn test -Dtest=ModularityTests`
Expected: FAIL — `everyDomainAndIntegrationPackageIsADetectedModule` thất bại vì `modules.payroll`/`integrations.storage` thiếu `@ApplicationModule` nên chưa được Spring Modulith detect, set thực tế không khớp set mong đợi. (`verifiesModularStructure` có thể cũng FAIL vì package `modules.payroll`/`integrations.storage` tồn tại nhưng không có `package-info.java` ở base package.)
- [ ] **Step 3: Write minimal implementation**
```java
// backend/src/main/java/com/eduerp/modules/payroll/package-info.java
/**
 * Module Payroll & Nhân sự - sở hữu hợp đồng lao động (EmploymentContract) và chu kỳ lương
 * (PayrollRun/Payslip). Gộp cả hai vì luôn dùng cùng nhau trong một vòng nghiệp vụ (hợp đồng là
 * input để tính lương) - tách modules.hr riêng là over-engineering khi chưa có use case nào cần
 * EmploymentContract độc lập mà không liên quan lương (spec mục 3.1).
 *
 * <p>Phụ thuộc một chiều {@code access} (AccessConstants cho @PreAuthorize), {@code identity}
 * (IdentityManagement.summariesOf để hiển thị tên/email) và {@code integrations.storage}
 * (StorageClient để lưu file hợp đồng PDF). Không module nào đọc ngược từ {@code payroll}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payroll & Nhân sự")
package com.eduerp.modules.payroll;
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend && mvn test -Dtest=ModularityTests`
Expected: PASS
- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/com/eduerp/modules/payroll/package-info.java backend/src/test/java/com/eduerp/ModularityTests.java
git commit -m "feat(payroll): register modules.payroll and integrations.storage as application modules"
```

---

### Task 17: Frontend `entities/payroll` (schema + api + keys + hooks)

**Files:**
- Create: `frontend/src/entities/payroll/model/payroll-schema.ts`
- Create: `frontend/src/entities/payroll/api/payroll-api.ts`
- Create: `frontend/src/entities/payroll/api/payroll-keys.ts`
- Create: `frontend/src/entities/payroll/api/use-contracts.ts`
- Create: `frontend/src/entities/payroll/api/use-payroll-runs.ts`
- Create: `frontend/src/entities/payroll/api/use-payroll-run-detail.ts`
- Create: `frontend/src/entities/payroll/index.ts`

**Interfaces:**
- Consumes: `apiClient.get/post/patch` (hiện có ở `@/shared/api/api-client` — `postForm`/`patchForm`/`getBlob` được thêm ở Task 18, dùng ở Task 19, chưa cần ở task này), `pageResponseSchema` (`@/shared/api/schemas`), `API_ROUTE.payroll.*` (thêm ở Task 18 — khai báo tạm `API_ROUTE.payroll` trong `api-routes.ts` thực hiện Ở Task 18, nhưng vì `payroll-api.ts` cần `API_ROUTE.payroll.*` ngay tại task này, **thứ tự đảo**: phần `API_ROUTE.payroll`/`APP_ROUTE.payroll*` của Task 18 phải làm trước phần `entities/payroll` — xem ghi chú đảo thứ tự ngay dưới).
- Produces: Zod schemas + types dùng ở Task 19/20: `CONTRACT_TYPE`, `ContractType`, `CONTRACT_STATUS`, `PAYROLL_RUN_STATUS`, `PayrollRunStatus`, `contractSummarySchema`/`ContractSummary`, `payslipSchema`/`Payslip`, `payrollRunSummarySchema`/`PayrollRunSummary`, `payrollRunDetailSchema`/`PayrollRunDetail`, `CreateContractPayload`, `UpdateContractPayload`, `CreatePayrollRunPayload`, `UpdatePayslipPayload`, `RejectPayrollRunPayload`. `contractApi.{listContracts,createContract,updateContract,terminateContract,downloadContractFile}`, `payrollRunApi.{listPayrollRuns,createPayrollRun,getPayrollRun,updatePayslip,submitForApproval,approvePayrollRun,rejectPayrollRun}`, `payrollKeys`, `useContracts(page,size,accountId?)`, `usePayrollRuns(page,size)`, `usePayrollRunDetail(runId)`.

> **Lưu ý thứ tự thật khi thực thi (không đảo ngược task, chỉ đảo lệnh `git add`/implement bên trong):** vì `payroll-api.ts` tham chiếu `API_ROUTE.payroll`, khi thực thi Task 17 trước Task 18 theo đúng thứ tự plan, hãy thêm đoạn `API_ROUTE.payroll = {...}` vào `api-routes.ts` **như một phần của Task 17** (không chờ Task 18) — Task 18 chỉ còn thêm `ACCESS_RULE`/`APP_ROUTE`/`apiClient`. Điều này được nêu rõ ở bước 3 dưới đây.
> **BigDecimal qua wire là `number`, không phải `string`:** Jackson serialize `BigDecimal` thành JSON number theo mặc định (xác nhận: backend không cấu hình `WRITE_BIGDECIMAL_AS_PLAIN`/serializer riêng nào). Việc dùng `z.number()` (không phải `z.string()`) cho mọi field tiền ở đây là lựa chọn ĐÚNG theo hợp đồng JSON thật của backend Task 3 — không mirror gợi ý "giống accountSummarySchema" của spec mục 4 vì `accountSummarySchema` (đã đọc: `frontend/src/entities/account/model/account-schema.ts`) không có field tiền nào để mirror, gợi ý đó không có cơ sở thật trong code hiện tại.
> **Không có test runner frontend** (xác nhận qua `package.json`) — cổng RED/GREEN là `npm run typecheck`.

- [ ] **Step 1: Write the failing test**
Không có file test riêng (schema/api thuần, không hành vi phức tạp) — cổng RED là chính file `payroll-api.ts` chưa tồn tại khiến `tsc` báo lỗi nếu bất kỳ chỗ nào import nó. Tạo trước một import giữ chỗ hợp lệ bằng cách viết thẳng các file Step 3 rồi chạy typecheck — nếu có lỗi kiểu (field order sai, union enum sai) `tsc` sẽ báo ngay tại chính các file này (ví dụ gõ nhầm tên field trong `contractSummarySchema` khi dùng ở `payroll-api.ts` sẽ bị `tsc` bắt vì kiểu suy ra từ `z.infer` không khớp).
- [ ] **Step 2: Run test to verify it fails**
Run: `cd frontend && npm run typecheck`
Expected: FAIL — `Cannot find module '@/entities/payroll'` khi các file khác (chưa viết) sẽ cần nó; ngay tại bước này nếu chưa có file nào import thì lệnh này PASS vô nghĩa (0 file liên quan) — vì vậy bước RED thật của task này là: thêm dòng `import "@/entities/payroll";` tạm vào `frontend/src/app/app.tsx` (sẽ xoá ở Task 21 khi wiring thật thay vào) để buộc `tsc` phải resolve module ngay — FAIL vì module chưa tồn tại.
- [ ] **Step 3: Write minimal implementation**
Trong `frontend/src/shared/constants/api-routes.ts`, thêm vào object `API_ROUTE` (sau `students:`):
```ts
  payroll: {
    contracts: "/api/payroll/contracts",
    contract: (contractId: string) => `/api/payroll/contracts/${contractId}`,
    contractTerminate: (contractId: string) => `/api/payroll/contracts/${contractId}/terminate`,
    contractFile: (contractId: string) => `/api/payroll/contracts/${contractId}/file`,
    runs: "/api/payroll/runs",
    run: (runId: string) => `/api/payroll/runs/${runId}`,
    runSubmit: (runId: string) => `/api/payroll/runs/${runId}/submit`,
    runApprove: (runId: string) => `/api/payroll/runs/${runId}/approve`,
    runReject: (runId: string) => `/api/payroll/runs/${runId}/reject`,
    payslip: (runId: string, payslipId: string) => `/api/payroll/runs/${runId}/payslips/${payslipId}`,
  },
```
```ts
// frontend/src/entities/payroll/model/payroll-schema.ts
import { z } from "zod";

/** Khớp PayrollConstants.ContractType ở backend. */
export const CONTRACT_TYPE = {
  official: "OFFICIAL",
  collaborator: "COLLABORATOR",
} as const;

export type ContractType = (typeof CONTRACT_TYPE)[keyof typeof CONTRACT_TYPE];

export const CONTRACT_TYPE_LABEL: Record<ContractType, string> = {
  [CONTRACT_TYPE.official]: "HĐLĐ chính thức",
  [CONTRACT_TYPE.collaborator]: "CTV / thời vụ",
};

/** Khớp PayrollConstants.ContractStatus ở backend. */
export const CONTRACT_STATUS = {
  active: "ACTIVE",
  terminated: "TERMINATED",
} as const;

export type ContractStatus = (typeof CONTRACT_STATUS)[keyof typeof CONTRACT_STATUS];

export const CONTRACT_STATUS_LABEL: Record<ContractStatus, string> = {
  [CONTRACT_STATUS.active]: "Đang hiệu lực",
  [CONTRACT_STATUS.terminated]: "Đã kết thúc",
};

/** Khớp PayrollConstants.PayrollRunStatus ở backend. */
export const PAYROLL_RUN_STATUS = {
  draft: "DRAFT",
  pendingApproval: "PENDING_APPROVAL",
  approved: "APPROVED",
} as const;

export type PayrollRunStatus = (typeof PAYROLL_RUN_STATUS)[keyof typeof PAYROLL_RUN_STATUS];

export const PAYROLL_RUN_STATUS_LABEL: Record<PayrollRunStatus, string> = {
  [PAYROLL_RUN_STATUS.draft]: "Nháp",
  [PAYROLL_RUN_STATUS.pendingApproval]: "Chờ duyệt",
  [PAYROLL_RUN_STATUS.approved]: "Đã duyệt",
};

export const allowanceSchema = z.object({
  name: z.string(),
  amount: z.number(),
});

export type Allowance = z.infer<typeof allowanceSchema>;

export const contractSummarySchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  accountFullName: z.string().nullable(),
  accountEmail: z.string().nullable(),
  contractType: z.enum([CONTRACT_TYPE.official, CONTRACT_TYPE.collaborator]),
  status: z.enum([CONTRACT_STATUS.active, CONTRACT_STATUS.terminated]),
  baseSalary: z.number().nullable(),
  hourlyRate: z.number().nullable(),
  probationStartDate: z.string().nullable(),
  probationEndDate: z.string().nullable(),
  startDate: z.string(),
  endDate: z.string().nullable(),
  allowances: z.array(allowanceSchema),
  contractFileKey: z.string().nullable(),
});

export type ContractSummary = z.infer<typeof contractSummarySchema>;

export const payslipSchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  accountFullName: z.string().nullable(),
  contractType: z.enum([CONTRACT_TYPE.official, CONTRACT_TYPE.collaborator]),
  grossPay: z.number(),
  socialInsuranceEmployee: z.number(),
  socialInsuranceEmployer: z.number(),
  incomeTaxWithheld: z.number(),
  netPay: z.number(),
  hoursWorked: z.number().nullable(),
  inProbation: z.boolean(),
});

export type Payslip = z.infer<typeof payslipSchema>;

export const payrollRunSummarySchema = z.object({
  id: z.string().uuid(),
  year: z.number().int(),
  month: z.number().int(),
  status: z.enum([PAYROLL_RUN_STATUS.draft, PAYROLL_RUN_STATUS.pendingApproval, PAYROLL_RUN_STATUS.approved]),
  payslipCount: z.number().int(),
  totalGrossPay: z.number(),
});

export type PayrollRunSummary = z.infer<typeof payrollRunSummarySchema>;

export const payrollRunDetailSchema = z.object({
  run: payrollRunSummarySchema,
  payslips: z.array(payslipSchema),
});

export type PayrollRunDetail = z.infer<typeof payrollRunDetailSchema>;

export interface AllowancePayload {
  readonly name: string;
  readonly amount: number;
}

export interface CreateContractPayload {
  readonly accountId: string;
  readonly contractType: ContractType;
  readonly baseSalary: number | null;
  readonly hourlyRate: number | null;
  readonly probationStartDate: string | null;
  readonly probationEndDate: string | null;
  readonly startDate: string;
  readonly allowances: readonly AllowancePayload[];
}

export interface UpdateContractPayload {
  readonly baseSalary: number | null;
  readonly hourlyRate: number | null;
  readonly probationEndDate: string | null;
  readonly allowances: readonly AllowancePayload[];
}

export interface CreatePayrollRunPayload {
  readonly year: number;
  readonly month: number;
}

export interface UpdatePayslipPayload {
  readonly hoursWorked: number | null;
  readonly incomeTaxWithheld: number | null;
  readonly note: string | null;
}

export interface RejectPayrollRunPayload {
  readonly reason: string;
}
```
```ts
// frontend/src/entities/payroll/api/payroll-api.ts
import {
  contractSummarySchema,
  payrollRunDetailSchema,
  payrollRunSummarySchema,
  payslipSchema,
  type CreateContractPayload,
  type CreatePayrollRunPayload,
  type RejectPayrollRunPayload,
  type UpdateContractPayload,
  type UpdatePayslipPayload,
} from "@/entities/payroll/model/payroll-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";

const contractPageSchema = pageResponseSchema(contractSummarySchema);
const payrollRunPageSchema = pageResponseSchema(payrollRunSummarySchema);

function toContractFormData(payload: CreateContractPayload | UpdateContractPayload, file: File | null): FormData {
  const formData = new FormData();
  formData.append("request", new Blob([JSON.stringify(payload)], { type: "application/json" }));
  if (file) {
    formData.append("file", file);
  }
  return formData;
}

export const contractApi = {
  async listContracts(page: number, size: number, accountId?: string) {
    return contractPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.payroll.contracts, { page, size, accountId }),
    );
  },

  async createContract(payload: CreateContractPayload, file: File | null) {
    return contractSummarySchema.parse(
      await apiClient.postForm<unknown>(API_ROUTE.payroll.contracts, toContractFormData(payload, file)),
    );
  },

  async updateContract(contractId: string, payload: UpdateContractPayload, file: File | null) {
    return contractSummarySchema.parse(
      await apiClient.patchForm<unknown>(API_ROUTE.payroll.contract(contractId), toContractFormData(payload, file)),
    );
  },

  async terminateContract(contractId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.payroll.contractTerminate(contractId));
  },

  async downloadContractFile(contractId: string): Promise<Blob> {
    return apiClient.getBlob(API_ROUTE.payroll.contractFile(contractId));
  },
} as const;

export const payrollRunApi = {
  async listPayrollRuns(page: number, size: number) {
    return payrollRunPageSchema.parse(await apiClient.get<unknown>(API_ROUTE.payroll.runs, { page, size }));
  },

  async createPayrollRun(payload: CreatePayrollRunPayload) {
    return payrollRunSummarySchema.parse(await apiClient.post<unknown>(API_ROUTE.payroll.runs, payload));
  },

  async getPayrollRun(runId: string) {
    return payrollRunDetailSchema.parse(await apiClient.get<unknown>(API_ROUTE.payroll.run(runId)));
  },

  async updatePayslip(runId: string, payslipId: string, payload: UpdatePayslipPayload) {
    return payslipSchema.parse(
      await apiClient.patch<unknown>(API_ROUTE.payroll.payslip(runId, payslipId), payload),
    );
  },

  async submitForApproval(runId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.payroll.runSubmit(runId));
  },

  async approvePayrollRun(runId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.payroll.runApprove(runId));
  },

  async rejectPayrollRun(runId: string, payload: RejectPayrollRunPayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.payroll.runReject(runId), payload);
  },
} as const;
```
```ts
// frontend/src/entities/payroll/api/payroll-keys.ts
export const payrollKeys = {
  contracts: {
    all: ["payroll", "contract"] as const,
    lists: () => [...payrollKeys.contracts.all, "list"] as const,
    list: (page: number, size: number, accountId?: string) =>
      [...payrollKeys.contracts.lists(), { page, size, accountId }] as const,
  },
  runs: {
    all: ["payroll", "run"] as const,
    lists: () => [...payrollKeys.runs.all, "list"] as const,
    list: (page: number, size: number) => [...payrollKeys.runs.lists(), { page, size }] as const,
    detail: (runId: string) => [...payrollKeys.runs.all, "detail", runId] as const,
  },
} as const;
```
```ts
// frontend/src/entities/payroll/api/use-contracts.ts
import { contractApi } from "@/entities/payroll/api/payroll-api";
import { payrollKeys } from "@/entities/payroll/api/payroll-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useContracts(page: number, size: number, accountId?: string) {
  return useQuery({
    queryKey: payrollKeys.contracts.list(page, size, accountId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => contractApi.listContracts(page, size, accountId),
  });
}
```
```ts
// frontend/src/entities/payroll/api/use-payroll-runs.ts
import { payrollRunApi } from "@/entities/payroll/api/payroll-api";
import { payrollKeys } from "@/entities/payroll/api/payroll-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function usePayrollRuns(page: number, size: number) {
  return useQuery({
    queryKey: payrollKeys.runs.list(page, size),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => payrollRunApi.listPayrollRuns(page, size),
  });
}
```
```ts
// frontend/src/entities/payroll/api/use-payroll-run-detail.ts
import { payrollRunApi } from "@/entities/payroll/api/payroll-api";
import { payrollKeys } from "@/entities/payroll/api/payroll-keys";
import { QUERY_RETRY_COUNT } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function usePayrollRunDetail(runId: string) {
  return useQuery({
    queryKey: payrollKeys.runs.detail(runId),
    retry: QUERY_RETRY_COUNT,
    queryFn: () => payrollRunApi.getPayrollRun(runId),
  });
}
```
```ts
// frontend/src/entities/payroll/index.ts
export { contractApi, payrollRunApi } from "@/entities/payroll/api/payroll-api";
export { payrollKeys } from "@/entities/payroll/api/payroll-keys";
export { useContracts } from "@/entities/payroll/api/use-contracts";
export { usePayrollRunDetail } from "@/entities/payroll/api/use-payroll-run-detail";
export { usePayrollRuns } from "@/entities/payroll/api/use-payroll-runs";
export {
  allowanceSchema,
  CONTRACT_STATUS,
  CONTRACT_STATUS_LABEL,
  CONTRACT_TYPE,
  CONTRACT_TYPE_LABEL,
  contractSummarySchema,
  PAYROLL_RUN_STATUS,
  PAYROLL_RUN_STATUS_LABEL,
  payrollRunDetailSchema,
  payrollRunSummarySchema,
  payslipSchema,
  type Allowance,
  type AllowancePayload,
  type ContractStatus,
  type ContractSummary,
  type ContractType,
  type CreateContractPayload,
  type CreatePayrollRunPayload,
  type Payslip,
  type PayrollRunDetail,
  type PayrollRunStatus,
  type PayrollRunSummary,
  type RejectPayrollRunPayload,
  type UpdateContractPayload,
  type UpdatePayslipPayload,
} from "@/entities/payroll/model/payroll-schema";
```
Xoá dòng `import "@/entities/payroll";` tạm đã thêm ở Step 2 khỏi `app.tsx` (Task 21 sẽ import thật qua lazy route).
- [ ] **Step 4: Run test to verify it passes**
Run: `cd frontend && npm run typecheck`
Expected: PASS (0 lỗi) — lưu ý `apiClient.postForm`/`patchForm`/`getBlob` chưa tồn tại tới hết Task 18, nên nếu typecheck chạy NGAY sau Step 3 của task này mà chưa làm Task 18, `payroll-api.ts` sẽ còn lỗi "Property 'postForm' does not exist". Vì vậy PASS thật của bước này chỉ đạt được sau khi Task 18 cũng hoàn tất — ghi rõ ở đây để người thực thi không hoảng khi thấy lỗi tạm thời giữa hai task liên tiếp.
- [ ] **Step 5: Commit**
```bash
git add frontend/src/entities/payroll/ frontend/src/shared/constants/api-routes.ts
git commit -m "feat(payroll): add entities/payroll schema, api and query hooks"
```

---

### Task 18: `permissions.ts`/`app-routes.ts` + `apiClient.postForm`/`patchForm`/`getBlob`

**Files:**
- Modify: `frontend/src/shared/constants/permissions.ts`
- Modify: `frontend/src/shared/constants/app-routes.ts`
- Modify: `frontend/src/shared/api/api-client.ts`

**Interfaces:**
- Consumes: `ApiRequest`/`send`/`request`/`readBody`/`url` (nội bộ `ApiClient`, đã đọc toàn bộ file).
- Produces: `RESOURCE.payroll: "PAYROLL"`, `ACCESS_RULE.readPayroll/createPayroll/updatePayroll/approvePayroll: PermissionRequirement` — dùng ở Task 19/20/21. `APP_ROUTE.payrollContracts: "/admin/payroll/contracts"`, `APP_ROUTE.payrollRuns: "/admin/payroll/runs"`, `APP_ROUTE.payrollRunDetail: "/admin/payroll/runs/:runId"` (route template cho `<Route path>`, dùng ở Task 21) + `buildPayrollRunDetailPath(runId: string): string` (link thật, dùng ở Task 20). `apiClient.postForm<T>(path, formData): Promise<T>`, `apiClient.patchForm<T>(path, formData): Promise<T>`, `apiClient.getBlob(path): Promise<Blob>` — dùng ở Task 17's `payroll-api.ts` (đã viết ở Task 17, chỉ compile được từ sau task này).

- [ ] **Step 1: Write the failing test**
Không có unit test (đã xác nhận: repo không có test runner frontend). Cổng RED: `payroll-api.ts` (Task 17) hiện đang gọi `apiClient.postForm`/`patchForm`/`getBlob` — các method này chưa tồn tại trên class `ApiClient`.
- [ ] **Step 2: Run test to verify it fails**
Run: `cd frontend && npm run typecheck`
Expected: FAIL — `Property 'postForm' does not exist on type 'ApiClient'` (và tương tự cho `patchForm`/`getBlob`) tại `frontend/src/entities/payroll/api/payroll-api.ts`.
- [ ] **Step 3: Write minimal implementation**
Trong `frontend/src/shared/constants/permissions.ts`, thêm vào `RESOURCE` (sau `student:`):
```ts
  payroll: "PAYROLL",
```
thêm vào `RESOURCE_LABEL` (sau dòng `student`):
```ts
  [RESOURCE.payroll]: "Lương & Hợp đồng",
```
thêm vào `ACCESS_RULE` (sau `createAccount`):
```ts
  readPayroll: { resource: RESOURCE.payroll, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createPayroll: {
    resource: RESOURCE.payroll,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updatePayroll: {
    resource: RESOURCE.payroll,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  approvePayroll: {
    resource: RESOURCE.payroll,
    action: ACTION.approve,
    scope: PERMISSION_SCOPE.organization,
  },
```
Trong `frontend/src/shared/constants/app-routes.ts`, thêm vào `APP_ROUTE` (sau `students:`):
```ts
  payrollContracts: "/admin/payroll/contracts",
  payrollRuns: "/admin/payroll/runs",
  payrollRunDetail: "/admin/payroll/runs/:runId",
```
và thêm hàm xuất riêng (sau khai báo `APP_ROUTE`, trước `RESET_TOKEN_PARAM`):
```ts
/** Link thật tới trang chi tiết một kỳ lương - APP_ROUTE.payrollRunDetail chỉ là route template cho <Route path>. */
export function buildPayrollRunDetailPath(runId: string): string {
  return `/admin/payroll/runs/${runId}`;
}
```
Trong `frontend/src/shared/api/api-client.ts`, sửa method `send` (thêm nhận diện `FormData`, không set JSON content-type, không `JSON.stringify` khi là `FormData`):
```ts
  private async send(path: string, request: ApiRequest): Promise<Response> {
    const method = request.method ?? HTTP_METHOD.get;
    const headers = new Headers({ [HTTP_HEADER.accept]: MEDIA_TYPE.json });
    const isFormData = request.body instanceof FormData;

    if (request.body !== undefined && !isFormData) {
      headers.set(HTTP_HEADER.contentType, MEDIA_TYPE.json);
    }
    if (MUTATING_METHODS.includes(method)) {
      const csrfToken = cookies.read(CSRF_COOKIE);
      if (csrfToken) {
        headers.set(HTTP_HEADER.csrfToken, csrfToken);
      }
    }

    try {
      return await fetch(this.url(path, request.query), {
        method,
        headers,
        credentials: "include",
        body: request.body === undefined ? undefined
          : isFormData ? (request.body as FormData) : JSON.stringify(request.body),
      });
    } catch {
      throw ApiError.networkUnreachable();
    }
  }
```
Thêm 3 method public vào `ApiClient` (ngay sau `patch`):
```ts
  async postForm<T>(path: string, formData: FormData): Promise<T> {
    return this.request<T>(path, { method: HTTP_METHOD.post, body: formData });
  }

  async patchForm<T>(path: string, formData: FormData): Promise<T> {
    return this.request<T>(path, { method: HTTP_METHOD.patch, body: formData });
  }

  async getBlob(path: string): Promise<Blob> {
    const response = await this.send(path, { method: HTTP_METHOD.get });
    if (!response.ok) {
      throw await ApiError.fromResponse(response);
    }
    return response.blob();
  }
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd frontend && npm run typecheck`
Expected: PASS (0 lỗi)
- [ ] **Step 5: Commit**
```bash
git add frontend/src/shared/constants/permissions.ts frontend/src/shared/constants/app-routes.ts \
        frontend/src/shared/api/api-client.ts
git commit -m "feat(payroll): add payroll permissions, routes and multipart api-client support"
```

---

### Task 19: `modules/payroll` — form schemas, mutations, `use-contracts-page-controller`, trang Hợp đồng

**Files:**
- Create: `frontend/src/modules/payroll/model/payroll-forms.ts`
- Create: `frontend/src/modules/payroll/api/use-contracts-mutations.ts`
- Create: `frontend/src/modules/payroll/hooks/use-contracts-page-controller.ts`
- Create: `frontend/src/modules/payroll/ui/allowances-field-list.tsx`
- Create: `frontend/src/modules/payroll/ui/contracts-table.tsx`
- Create: `frontend/src/modules/payroll/ui/create-contract-dialog.tsx`
- Create: `frontend/src/modules/payroll/ui/edit-contract-dialog.tsx`
- Create: `frontend/src/modules/payroll/pages/contracts-page.tsx`

**Interfaces:**
- Consumes: `useContracts` (Task 17), `contractApi.createContract/updateContract/terminateContract` + `payrollKeys` (Task 17), `allowanceSchema`/`ContractSummary`/`CONTRACT_TYPE`/`CONTRACT_TYPE_LABEL`/`CONTRACT_STATUS_LABEL`/`CreateContractPayload`/`UpdateContractPayload` (Task 17), `ACCESS_RULE.readPayroll/createPayroll/updatePayroll` (Task 18), `useZodForm` (có sẵn), `Can`/`RequirePermission` (có sẵn), `GlassButton`/`GlassModal`/`GlassInput`/`GlassSelect`/`FormField`/`Badge`/`PageHeader`/`GlassPanel`/`Pagination`/`EmptyState`/`ErrorNotice`/`Skeleton` (có sẵn).
- Produces: `ContractsPage` — export dùng ở Task 21 (`modules/payroll/index.ts`).

- [ ] **Step 1: Write the failing test**
Không có test runner — cổng RED là `npm run typecheck` sau khi thêm các import vào `frontend/src/app/app.tsx` tạm thời (xoá ở Task 21): `import { ContractsPage } from "@/modules/payroll";` — module `@/modules/payroll` (barrel ở Task 21) chưa tồn tại nên lỗi resolve.
- [ ] **Step 2: Run test to verify it fails**
Run: `cd frontend && npm run typecheck`
Expected: FAIL — `Cannot find module '@/modules/payroll'`.
- [ ] **Step 3: Write minimal implementation**
```ts
// frontend/src/modules/payroll/model/payroll-forms.ts
import { allowanceSchema, CONTRACT_TYPE } from "@/entities/payroll";
import { z } from "zod";

export const createContractFormSchema = z.object({
  accountId: z.string().uuid("Chọn tài khoản nhân viên"),
  contractType: z.enum([CONTRACT_TYPE.official, CONTRACT_TYPE.collaborator]),
  baseSalary: z.string().trim().transform((value) => (value.length === 0 ? null : Number(value))),
  hourlyRate: z.string().trim().transform((value) => (value.length === 0 ? null : Number(value))),
  probationStartDate: z.string().trim().transform((value) => (value.length === 0 ? null : value)),
  probationEndDate: z.string().trim().transform((value) => (value.length === 0 ? null : value)),
  startDate: z.string().min(1, "Chọn ngày bắt đầu"),
  allowances: z.array(allowanceSchema),
});

export const updateContractFormSchema = z.object({
  baseSalary: z.string().trim().transform((value) => (value.length === 0 ? null : Number(value))),
  hourlyRate: z.string().trim().transform((value) => (value.length === 0 ? null : Number(value))),
  probationEndDate: z.string().trim().transform((value) => (value.length === 0 ? null : value)),
  allowances: z.array(allowanceSchema),
});
```
```ts
// frontend/src/modules/payroll/api/use-contracts-mutations.ts
import { contractApi, payrollKeys, type CreateContractPayload, type UpdateContractPayload } from "@/entities/payroll";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useContractsInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: payrollKeys.contracts.all });
  };
}

export function useCreateContract() {
  const invalidate = useContractsInvalidation();
  return useMutation({
    mutationFn: ({ payload, file }: { payload: CreateContractPayload; file: File | null }) =>
      contractApi.createContract(payload, file),
    onSuccess: invalidate,
  });
}

export function useUpdateContract(contractId: string) {
  const invalidate = useContractsInvalidation();
  return useMutation({
    mutationFn: ({ payload, file }: { payload: UpdateContractPayload; file: File | null }) =>
      contractApi.updateContract(contractId, payload, file),
    onSuccess: invalidate,
  });
}

export function useTerminateContract() {
  const invalidate = useContractsInvalidation();
  return useMutation({
    mutationFn: (contractId: string) => contractApi.terminateContract(contractId),
    onSuccess: invalidate,
  });
}
```
```ts
// frontend/src/modules/payroll/hooks/use-contracts-page-controller.ts
import { useContracts, type ContractSummary } from "@/entities/payroll";
import { useTerminateContract } from "@/modules/payroll/api/use-contracts-mutations";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useCallback, useState } from "react";

/** Toàn bộ state/mutation của trang Hợp đồng - ui/contracts-page.tsx chỉ render (Mandate #2). */
export function useContractsPageController() {
  const [page, setPage] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editing, setEditing] = useState<ContractSummary | null>(null);
  const contracts = useContracts(page, DEFAULT_PAGE_SIZE);
  const terminateContract = useTerminateContract();

  const openCreateDialog = useCallback(() => setCreateDialogOpen(true), []);
  const closeCreateDialog = useCallback(() => setCreateDialogOpen(false), []);
  const stopEditing = useCallback(() => setEditing(null), []);

  const onTerminate = useCallback(
    (contractId: string) => {
      void terminateContract.mutateAsync(contractId);
    },
    [terminateContract],
  );

  return {
    page,
    setPage,
    contracts,
    createDialogOpen,
    openCreateDialog,
    closeCreateDialog,
    editing,
    startEditing: setEditing,
    stopEditing,
    onTerminate,
    isTerminating: terminateContract.isPending,
  };
}
```
```tsx
// frontend/src/modules/payroll/ui/allowances-field-list.tsx
import type { AllowancePayload } from "@/entities/payroll";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { Plus, Trash2 } from "lucide-react";

export interface AllowancesFieldListProps {
  readonly allowances: readonly AllowancePayload[];
  readonly onChange: (allowances: readonly AllowancePayload[]) => void;
}

/** Thuần render + echo sự kiện ra ngoài qua onChange - không tự giữ state (Mandate #2). */
export function AllowancesFieldList({ allowances, onChange }: AllowancesFieldListProps) {
  return (
    <div className="flex flex-col gap-2">
      <span className="text-xs font-semibold tracking-wide text-slate-600 uppercase">Phụ cấp</span>
      {allowances.map((allowance, index) => (
        <div key={index} className="flex gap-2">
          <GlassInput
            placeholder="Tên phụ cấp"
            value={allowance.name}
            onChange={(event) =>
              onChange(allowances.map((a, i) => (i === index ? { ...a, name: event.target.value } : a)))
            }
          />
          <GlassInput
            type="number"
            placeholder="Số tiền"
            value={allowance.amount}
            onChange={(event) =>
              onChange(allowances.map((a, i) => (i === index ? { ...a, amount: Number(event.target.value) } : a)))
            }
          />
          <GlassButton
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => onChange(allowances.filter((_, i) => i !== index))}
            icon={<Trash2 size={14} aria-hidden />}
          >
            Xoá
          </GlassButton>
        </div>
      ))}
      <GlassButton
        type="button"
        variant="secondary"
        size="sm"
        onClick={() => onChange([...allowances, { name: "", amount: 0 }])}
        icon={<Plus size={14} aria-hidden />}
      >
        Thêm phụ cấp
      </GlassButton>
    </div>
  );
}
```
```tsx
// frontend/src/modules/payroll/ui/contracts-table.tsx
import { CONTRACT_STATUS_LABEL, CONTRACT_TYPE_LABEL, type ContractSummary } from "@/entities/payroll";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { Pencil, UserX } from "lucide-react";

export interface ContractsTableProps {
  readonly rows: readonly ContractSummary[];
  readonly onEdit: (contract: ContractSummary) => void;
  readonly onTerminate: (contractId: string) => void;
  readonly isTerminating: boolean;
}

export function ContractsTable({ rows, onEdit, onTerminate, isTerminating }: ContractsTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((contract) => (
        <li
          key={contract.id}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_auto_auto_auto]"
        >
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">{contract.accountFullName ?? contract.accountEmail}</p>
            <p className="truncate text-xs text-mist-500">{CONTRACT_TYPE_LABEL[contract.contractType]}</p>
          </div>
          <Badge tone={contract.status === "ACTIVE" ? "positive" : "neutral"}>
            {CONTRACT_STATUS_LABEL[contract.status]}
          </Badge>
          <Can {...ACCESS_RULE.updatePayroll}>
            <GlassButton variant="secondary" size="sm" onClick={() => onEdit(contract)} icon={<Pencil size={14} aria-hidden />}>
              Sửa
            </GlassButton>
          </Can>
          <Can {...ACCESS_RULE.updatePayroll}>
            {contract.status === "ACTIVE" ? (
              <GlassButton
                variant="ghost"
                size="sm"
                loading={isTerminating}
                onClick={() => onTerminate(contract.id)}
                icon={<UserX size={14} aria-hidden />}
              >
                Kết thúc hợp đồng
              </GlassButton>
            ) : null}
          </Can>
        </li>
      ))}
    </ul>
  );
}
```
```tsx
// frontend/src/modules/payroll/ui/create-contract-dialog.tsx
import { CONTRACT_TYPE, CONTRACT_TYPE_LABEL } from "@/entities/payroll";
import { useCreateContract } from "@/modules/payroll/api/use-contracts-mutations";
import { createContractFormSchema } from "@/modules/payroll/model/payroll-forms";
import { AllowancesFieldList } from "@/modules/payroll/ui/allowances-field-list";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";
import { useState } from "react";

export interface CreateContractDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateContractDialog({ open, onClose }: CreateContractDialogProps) {
  const createContract = useCreateContract();
  const [file, setFile] = useState<File | null>(null);

  const form = useZodForm({
    schema: createContractFormSchema,
    initialValues: {
      accountId: "",
      contractType: CONTRACT_TYPE.official,
      baseSalary: "",
      hourlyRate: "",
      probationStartDate: "",
      probationEndDate: "",
      startDate: "",
      allowances: [],
    },
    onSubmit: async (values) => {
      await createContract.mutateAsync({ payload: values, file });
      form.reset();
      setFile(null);
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Tạo hợp đồng lao động"
      description="Áp dụng cho mọi nhân viên có hợp đồng, không riêng giáo viên.">
      <form id="create-contract-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Mã tài khoản nhân viên (UUID)" htmlFor="contract-account-id" error={form.fieldErrors.accountId}>
          <GlassInput id="contract-account-id" value={form.values.accountId}
            onChange={(event) => form.setValue("accountId", event.target.value)} />
        </FormField>

        <FormField label="Loại hợp đồng" htmlFor="contract-type">
          <GlassSelect id="contract-type" value={form.values.contractType}
            onChange={(event) => form.setValue("contractType", event.target.value as typeof form.values.contractType)}>
            <option value={CONTRACT_TYPE.official}>{CONTRACT_TYPE_LABEL[CONTRACT_TYPE.official]}</option>
            <option value={CONTRACT_TYPE.collaborator}>{CONTRACT_TYPE_LABEL[CONTRACT_TYPE.collaborator]}</option>
          </GlassSelect>
        </FormField>

        <FormField label="Lương cố định (OFFICIAL)" htmlFor="contract-base-salary" hint="Bắt buộc với hợp đồng chính thức.">
          <GlassInput id="contract-base-salary" type="number" value={form.values.baseSalary}
            onChange={(event) => form.setValue("baseSalary", event.target.value)} />
        </FormField>

        <FormField label="Đơn giá/giờ (CTV)" htmlFor="contract-hourly-rate" hint="Bắt buộc với hợp đồng CTV/thời vụ.">
          <GlassInput id="contract-hourly-rate" type="number" value={form.values.hourlyRate}
            onChange={(event) => form.setValue("hourlyRate", event.target.value)} />
        </FormField>

        <FormField label="Ngày bắt đầu thử việc" htmlFor="contract-probation-start" hint="Chỉ áp dụng HĐLĐ chính thức.">
          <GlassInput id="contract-probation-start" type="date" value={form.values.probationStartDate}
            onChange={(event) => form.setValue("probationStartDate", event.target.value)} />
        </FormField>

        <FormField label="Ngày kết thúc thử việc" htmlFor="contract-probation-end">
          <GlassInput id="contract-probation-end" type="date" value={form.values.probationEndDate}
            onChange={(event) => form.setValue("probationEndDate", event.target.value)} />
        </FormField>

        <FormField label="Ngày bắt đầu hợp đồng" htmlFor="contract-start-date" error={form.fieldErrors.startDate}>
          <GlassInput id="contract-start-date" type="date" value={form.values.startDate}
            onChange={(event) => form.setValue("startDate", event.target.value)} />
        </FormField>

        <AllowancesFieldList allowances={form.values.allowances} onChange={(a) => form.setValue("allowances", [...a])} />

        <FormField label="File hợp đồng (PDF)" htmlFor="contract-file" hint="Không bắt buộc.">
          <input id="contract-file" type="file" accept="application/pdf"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>Huỷ</GlassButton>
        <GlassButton type="submit" form="create-contract-form" loading={form.isSubmitting}>Tạo hợp đồng</GlassButton>
      </footer>
    </GlassModal>
  );
}
```
```tsx
// frontend/src/modules/payroll/ui/edit-contract-dialog.tsx
import type { ContractSummary } from "@/entities/payroll";
import { useUpdateContract } from "@/modules/payroll/api/use-contracts-mutations";
import { updateContractFormSchema } from "@/modules/payroll/model/payroll-forms";
import { AllowancesFieldList } from "@/modules/payroll/ui/allowances-field-list";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { useState } from "react";

export interface EditContractDialogProps {
  readonly contract: ContractSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function EditContractDialog({ contract, open, onClose }: EditContractDialogProps) {
  const updateContract = useUpdateContract(contract.id);
  const [file, setFile] = useState<File | null>(null);

  const form = useZodForm({
    schema: updateContractFormSchema,
    initialValues: {
      baseSalary: contract.baseSalary === null ? "" : String(contract.baseSalary),
      hourlyRate: contract.hourlyRate === null ? "" : String(contract.hourlyRate),
      probationEndDate: contract.probationEndDate ?? "",
      allowances: contract.allowances,
    },
    onSubmit: async (values) => {
      await updateContract.mutateAsync({ payload: values, file });
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Cập nhật hợp đồng" description={contract.accountFullName ?? contract.accountEmail ?? ""}>
      <form id="edit-contract-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Lương cố định (OFFICIAL)" htmlFor="edit-contract-base-salary">
          <GlassInput id="edit-contract-base-salary" type="number" value={form.values.baseSalary}
            onChange={(event) => form.setValue("baseSalary", event.target.value)} />
        </FormField>

        <FormField label="Đơn giá/giờ (CTV)" htmlFor="edit-contract-hourly-rate">
          <GlassInput id="edit-contract-hourly-rate" type="number" value={form.values.hourlyRate}
            onChange={(event) => form.setValue("hourlyRate", event.target.value)} />
        </FormField>

        <FormField label="Ngày kết thúc thử việc" htmlFor="edit-contract-probation-end">
          <GlassInput id="edit-contract-probation-end" type="date" value={form.values.probationEndDate}
            onChange={(event) => form.setValue("probationEndDate", event.target.value)} />
        </FormField>

        <AllowancesFieldList allowances={form.values.allowances} onChange={(a) => form.setValue("allowances", [...a])} />

        <FormField label="File hợp đồng (PDF)" htmlFor="edit-contract-file" hint="Để trống nếu không đổi file.">
          <input id="edit-contract-file" type="file" accept="application/pdf"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>Huỷ</GlassButton>
        <GlassButton type="submit" form="edit-contract-form" loading={form.isSubmitting}>Lưu</GlassButton>
      </footer>
    </GlassModal>
  );
}
```
```tsx
// frontend/src/modules/payroll/pages/contracts-page.tsx
import { Can, RequirePermission } from "@/entities/permission";
import { useContractsPageController } from "@/modules/payroll/hooks/use-contracts-page-controller";
import { ContractsTable } from "@/modules/payroll/ui/contracts-table";
import { CreateContractDialog } from "@/modules/payroll/ui/create-contract-dialog";
import { EditContractDialog } from "@/modules/payroll/ui/edit-contract-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { FileText, Plus } from "lucide-react";

export function ContractsPage() {
  const controller = useContractsPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readPayroll}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Hợp đồng lao động"
          description="Áp dụng cho mọi nhân viên có hợp đồng - giáo viên và nhân viên hành chính."
          actions={
            <Can {...ACCESS_RULE.createPayroll}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Hợp đồng mới
              </GlassButton>
            </Can>
          }
        />

        {controller.contracts.isError ? <ErrorNotice error={controller.contracts.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {controller.contracts.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {controller.contracts.data ? (
            controller.contracts.data.items.length === 0 ? (
              <EmptyState icon={<FileText size={28} aria-hidden />} title="Chưa có hợp đồng nào"
                description="Tạo hợp đồng đầu tiên để bắt đầu chốt lương." />
            ) : (
              <>
                <ContractsTable
                  rows={controller.contracts.data.items}
                  onEdit={controller.startEditing}
                  onTerminate={controller.onTerminate}
                  isTerminating={controller.isTerminating}
                />
                <Pagination
                  page={controller.contracts.data.page}
                  totalPages={controller.contracts.data.totalPages}
                  totalItems={controller.contracts.data.totalItems}
                  onPageChange={controller.setPage}
                  itemLabel="hợp đồng"
                />
              </>
            )
          ) : null}
        </GlassPanel>

        <CreateContractDialog open={controller.createDialogOpen} onClose={controller.closeCreateDialog} />

        {controller.editing === null ? null : (
          <EditContractDialog key={controller.editing.id} contract={controller.editing} open
            onClose={controller.stopEditing} />
        )}
      </div>
    </RequirePermission>
  );
}
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd frontend && npm run typecheck`
Expected: PASS (0 lỗi) — lưu ý `ContractsPage` chỉ resolve được qua `@/modules/payroll` sau khi barrel `modules/payroll/index.ts` được tạo ở Task 21; tới hết task này import tạm ở `app.tsx` vẫn còn lỗi nếu trỏ qua barrel — đổi tạm sang import trực tiếp `@/modules/payroll/pages/contracts-page` để xác nhận PASS ở bước này, rồi xoá dòng import tạm trước khi qua Task 20.
- [ ] **Step 5: Commit**
```bash
git add frontend/src/modules/payroll/model/payroll-forms.ts frontend/src/modules/payroll/api/use-contracts-mutations.ts \
        frontend/src/modules/payroll/hooks/use-contracts-page-controller.ts frontend/src/modules/payroll/ui/allowances-field-list.tsx \
        frontend/src/modules/payroll/ui/contracts-table.tsx frontend/src/modules/payroll/ui/create-contract-dialog.tsx \
        frontend/src/modules/payroll/ui/edit-contract-dialog.tsx frontend/src/modules/payroll/pages/contracts-page.tsx
git commit -m "feat(payroll): add contracts page with create, edit and terminate"
```

---

### Task 20: `modules/payroll` — mutations kỳ lương, `use-payroll-run-detail-controller`, trang Kỳ lương

**Files:**
- Create: `frontend/src/modules/payroll/model/payroll-run-forms.ts`
- Create: `frontend/src/modules/payroll/api/use-payroll-run-mutations.ts`
- Create: `frontend/src/modules/payroll/hooks/use-payroll-runs-page-controller.ts`
- Create: `frontend/src/modules/payroll/hooks/use-payroll-run-detail-controller.ts`
- Create: `frontend/src/modules/payroll/ui/payroll-runs-table.tsx`
- Create: `frontend/src/modules/payroll/ui/create-payroll-run-dialog.tsx`
- Create: `frontend/src/modules/payroll/ui/payslips-table.tsx`
- Create: `frontend/src/modules/payroll/ui/reject-payroll-run-dialog.tsx`
- Create: `frontend/src/modules/payroll/pages/payroll-runs-page.tsx`
- Create: `frontend/src/modules/payroll/pages/payroll-run-detail-page.tsx`

**Interfaces:**
- Consumes: `usePayrollRuns`/`usePayrollRunDetail` (Task 17), `payrollRunApi.createPayrollRun/updatePayslip/submitForApproval/approvePayrollRun/rejectPayrollRun` + `payrollKeys` (Task 17), `PAYROLL_RUN_STATUS_LABEL`/`CONTRACT_TYPE_LABEL`/`PayrollRunDetail`/`Payslip`/`CreatePayrollRunPayload`/`UpdatePayslipPayload`/`RejectPayrollRunPayload` (Task 17), `ACCESS_RULE.readPayroll/createPayroll/updatePayroll/approvePayroll` (Task 18), `buildPayrollRunDetailPath` (Task 18).
- Produces: `PayrollRunsPage`, `PayrollRunDetailPage` — export dùng ở Task 21.

- [ ] **Step 1: Write the failing test**
Cổng RED: thêm tạm `import { PayrollRunsPage, PayrollRunDetailPage } from "@/modules/payroll/pages/payroll-runs-page";`-kiểu import trực tiếp vào `app.tsx` (xoá trước khi merge, barrel thật ở Task 21).
- [ ] **Step 2: Run test to verify it fails**
Run: `cd frontend && npm run typecheck`
Expected: FAIL — `Cannot find module '@/modules/payroll/pages/payroll-runs-page'`.
- [ ] **Step 3: Write minimal implementation**
```ts
// frontend/src/modules/payroll/model/payroll-run-forms.ts
import { z } from "zod";

export const createPayrollRunFormSchema = z.object({
  year: z.string().min(1, "Nhập năm").transform((value) => Number(value)),
  month: z.string().min(1, "Nhập tháng").transform((value) => Number(value)),
});

export const updatePayslipFormSchema = z.object({
  hoursWorked: z.string().trim().transform((value) => (value.length === 0 ? null : Number(value))),
  incomeTaxWithheld: z.string().trim().transform((value) => (value.length === 0 ? null : Number(value))),
  note: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
});

export const rejectPayrollRunFormSchema = z.object({
  reason: z.string().min(1, "Nhập lý do từ chối"),
});
```
```ts
// frontend/src/modules/payroll/api/use-payroll-run-mutations.ts
import {
  payrollKeys,
  payrollRunApi,
  type CreatePayrollRunPayload,
  type RejectPayrollRunPayload,
  type UpdatePayslipPayload,
} from "@/entities/payroll";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useRunsInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: payrollKeys.runs.all });
  };
}

export function useCreatePayrollRun() {
  const invalidate = useRunsInvalidation();
  return useMutation({
    mutationFn: (payload: CreatePayrollRunPayload) => payrollRunApi.createPayrollRun(payload),
    onSuccess: invalidate,
  });
}

export function useUpdatePayslip(runId: string) {
  const invalidate = useRunsInvalidation();
  return useMutation({
    mutationFn: ({ payslipId, payload }: { payslipId: string; payload: UpdatePayslipPayload }) =>
      payrollRunApi.updatePayslip(runId, payslipId, payload),
    onSuccess: invalidate,
  });
}

export function useSubmitPayrollRunForApproval(runId: string) {
  const invalidate = useRunsInvalidation();
  return useMutation({
    mutationFn: () => payrollRunApi.submitForApproval(runId),
    onSuccess: invalidate,
  });
}

export function useApprovePayrollRun(runId: string) {
  const invalidate = useRunsInvalidation();
  return useMutation({
    mutationFn: () => payrollRunApi.approvePayrollRun(runId),
    onSuccess: invalidate,
  });
}

export function useRejectPayrollRun(runId: string) {
  const invalidate = useRunsInvalidation();
  return useMutation({
    mutationFn: (payload: RejectPayrollRunPayload) => payrollRunApi.rejectPayrollRun(runId, payload),
    onSuccess: invalidate,
  });
}
```
```ts
// frontend/src/modules/payroll/hooks/use-payroll-runs-page-controller.ts
import { usePayrollRuns } from "@/entities/payroll";
import { useCreatePayrollRun } from "@/modules/payroll/api/use-payroll-run-mutations";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useCallback, useState } from "react";

export function usePayrollRunsPageController() {
  const [page, setPage] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const runs = usePayrollRuns(page, DEFAULT_PAGE_SIZE);
  const createPayrollRun = useCreatePayrollRun();

  const openCreateDialog = useCallback(() => setCreateDialogOpen(true), []);
  const closeCreateDialog = useCallback(() => setCreateDialogOpen(false), []);

  return { page, setPage, runs, createDialogOpen, openCreateDialog, closeCreateDialog, createPayrollRun };
}
```
```ts
// frontend/src/modules/payroll/hooks/use-payroll-run-detail-controller.ts
import { usePayrollRunDetail } from "@/entities/payroll";
import {
  useApprovePayrollRun,
  useRejectPayrollRun,
  useSubmitPayrollRunForApproval,
  useUpdatePayslip,
} from "@/modules/payroll/api/use-payroll-run-mutations";
import { useCallback, useState } from "react";

/** Toàn bộ state/mutation của trang chi tiết kỳ lương - ui/payroll-run-detail-page.tsx chỉ render. */
export function usePayrollRunDetailController(runId: string) {
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const detail = usePayrollRunDetail(runId);
  const updatePayslip = useUpdatePayslip(runId);
  const submitForApproval = useSubmitPayrollRunForApproval(runId);
  const approve = useApprovePayrollRun(runId);
  const reject = useRejectPayrollRun(runId);

  const isDraft = detail.data?.run.status === "DRAFT";

  const onSubmitForApproval = useCallback(() => {
    void submitForApproval.mutateAsync();
  }, [submitForApproval]);

  const onApprove = useCallback(() => {
    void approve.mutateAsync();
  }, [approve]);

  const openRejectDialog = useCallback(() => setRejectDialogOpen(true), []);
  const closeRejectDialog = useCallback(() => setRejectDialogOpen(false), []);

  return {
    detail,
    isDraft,
    updatePayslip,
    onSubmitForApproval,
    isSubmitting: submitForApproval.isPending,
    onApprove,
    isApproving: approve.isPending,
    reject,
    rejectDialogOpen,
    openRejectDialog,
    closeRejectDialog,
  };
}
```
```tsx
// frontend/src/modules/payroll/ui/payroll-runs-table.tsx
import { PAYROLL_RUN_STATUS_LABEL, type PayrollRunSummary } from "@/entities/payroll";
import { buildPayrollRunDetailPath } from "@/shared/constants/app-routes";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";

export interface PayrollRunsTableProps {
  readonly rows: readonly PayrollRunSummary[];
}

export function PayrollRunsTable({ rows }: PayrollRunsTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((run) => (
        <li
          key={run.id}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto]"
        >
          <p className="truncate text-sm text-mist-100">Kỳ lương {run.month}/{run.year}</p>
          <Badge tone={run.status === "APPROVED" ? "positive" : "neutral"}>{PAYROLL_RUN_STATUS_LABEL[run.status]}</Badge>
          <p className="text-xs text-mist-500">{run.payslipCount} phiếu lương</p>
          <Link to={buildPayrollRunDetailPath(run.id)}>
            <GlassButton variant="secondary" size="sm" icon={<ChevronRight size={14} aria-hidden />}>
              Xem chi tiết
            </GlassButton>
          </Link>
        </li>
      ))}
    </ul>
  );
}
```
```tsx
// frontend/src/modules/payroll/ui/create-payroll-run-dialog.tsx
import { useCreatePayrollRun } from "@/modules/payroll/api/use-payroll-run-mutations";
import { createPayrollRunFormSchema } from "@/modules/payroll/model/payroll-run-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface CreatePayrollRunDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreatePayrollRunDialog({ open, onClose }: CreatePayrollRunDialogProps) {
  const createPayrollRun = useCreatePayrollRun();

  const form = useZodForm({
    schema: createPayrollRunFormSchema,
    initialValues: { year: String(new Date().getFullYear()), month: String(new Date().getMonth() + 1) },
    onSubmit: async (values) => {
      await createPayrollRun.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Tạo kỳ lương mới"
      description="Tự sinh phiếu lương nháp cho mọi hợp đồng đang hiệu lực.">
      <form id="create-payroll-run-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Năm" htmlFor="payroll-run-year" error={form.fieldErrors.year}>
          <GlassInput id="payroll-run-year" type="number" value={form.values.year}
            onChange={(event) => form.setValue("year", event.target.value)} />
        </FormField>

        <FormField label="Tháng" htmlFor="payroll-run-month" error={form.fieldErrors.month}>
          <GlassInput id="payroll-run-month" type="number" min={1} max={12} value={form.values.month}
            onChange={(event) => form.setValue("month", event.target.value)} />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>Huỷ</GlassButton>
        <GlassButton type="submit" form="create-payroll-run-form" loading={form.isSubmitting}>Tạo kỳ lương</GlassButton>
      </footer>
    </GlassModal>
  );
}
```
```tsx
// frontend/src/modules/payroll/ui/payslips-table.tsx
import { CONTRACT_TYPE_LABEL, type Payslip } from "@/entities/payroll";
import type { UpdatePayslipPayload } from "@/entities/payroll";
import { GlassInput } from "@/shared/ui/glass-input";

export interface PayslipsTableProps {
  readonly rows: readonly Payslip[];
  readonly editable: boolean;
  readonly onUpdate: (payslipId: string, payload: UpdatePayslipPayload) => void;
}

/** Sửa được tại chỗ khi editable (PayrollRun.status === DRAFT) - echo thay đổi ra ngoài qua onUpdate,
 * không tự gọi API (Mandate #2: logic mutation nằm ở controller hook, không ở đây). */
export function PayslipsTable({ rows, editable, onUpdate }: PayslipsTableProps) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-left text-xs uppercase text-mist-500">
          <th>Nhân viên</th>
          <th>Loại HĐ</th>
          <th>Giờ dạy</th>
          <th>Lương gộp</th>
          <th>BHXH (NLĐ)</th>
          <th>Thuế TNCN</th>
          <th>Thực nhận</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((payslip) => (
          <tr key={payslip.id} className="border-t border-white/10">
            <td>{payslip.accountFullName}</td>
            <td>{CONTRACT_TYPE_LABEL[payslip.contractType]}</td>
            <td>
              {editable && payslip.contractType === "COLLABORATOR" ? (
                <GlassInput
                  type="number"
                  value={payslip.hoursWorked ?? ""}
                  onChange={(event) =>
                    onUpdate(payslip.id, {
                      hoursWorked: Number(event.target.value),
                      incomeTaxWithheld: payslip.incomeTaxWithheld,
                      note: null,
                    })
                  }
                />
              ) : (
                (payslip.hoursWorked ?? "-")
              )}
            </td>
            <td>{payslip.grossPay.toLocaleString("vi-VN")}</td>
            <td>{payslip.socialInsuranceEmployee.toLocaleString("vi-VN")}</td>
            <td>
              {editable ? (
                <GlassInput
                  type="number"
                  value={payslip.incomeTaxWithheld}
                  onChange={(event) =>
                    onUpdate(payslip.id, {
                      hoursWorked: payslip.hoursWorked,
                      incomeTaxWithheld: Number(event.target.value),
                      note: null,
                    })
                  }
                />
              ) : (
                payslip.incomeTaxWithheld.toLocaleString("vi-VN")
              )}
            </td>
            <td>{payslip.netPay.toLocaleString("vi-VN")}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
```
```tsx
// frontend/src/modules/payroll/ui/reject-payroll-run-dialog.tsx
import { useRejectPayrollRun } from "@/modules/payroll/api/use-payroll-run-mutations";
import { rejectPayrollRunFormSchema } from "@/modules/payroll/model/payroll-run-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface RejectPayrollRunDialogProps {
  readonly runId: string;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function RejectPayrollRunDialog({ runId, open, onClose }: RejectPayrollRunDialogProps) {
  const rejectPayrollRun = useRejectPayrollRun(runId);

  const form = useZodForm({
    schema: rejectPayrollRunFormSchema,
    initialValues: { reason: "" },
    onSubmit: async (values) => {
      await rejectPayrollRun.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Từ chối kỳ lương" description="Kỳ lương về lại trạng thái Nháp.">
      <form id="reject-payroll-run-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}
        <FormField label="Lý do từ chối" htmlFor="reject-reason" error={form.fieldErrors.reason}>
          <GlassInput id="reject-reason" value={form.values.reason}
            onChange={(event) => form.setValue("reason", event.target.value)} />
        </FormField>
      </form>
      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>Huỷ</GlassButton>
        <GlassButton type="submit" form="reject-payroll-run-form" loading={form.isSubmitting}>Từ chối</GlassButton>
      </footer>
    </GlassModal>
  );
}
```
```tsx
// frontend/src/modules/payroll/pages/payroll-runs-page.tsx
import { Can, RequirePermission } from "@/entities/permission";
import { usePayrollRunsPageController } from "@/modules/payroll/hooks/use-payroll-runs-page-controller";
import { CreatePayrollRunDialog } from "@/modules/payroll/ui/create-payroll-run-dialog";
import { PayrollRunsTable } from "@/modules/payroll/ui/payroll-runs-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Plus, Wallet } from "lucide-react";

export function PayrollRunsPage() {
  const controller = usePayrollRunsPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readPayroll}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Kỳ lương"
          description="Kế toán tạo kỳ lương, chủ trung tâm duyệt rồi khoá."
          actions={
            <Can {...ACCESS_RULE.createPayroll}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Kỳ lương mới
              </GlassButton>
            </Can>
          }
        />

        {controller.runs.isError ? <ErrorNotice error={controller.runs.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {controller.runs.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {controller.runs.data ? (
            controller.runs.data.items.length === 0 ? (
              <EmptyState icon={<Wallet size={28} aria-hidden />} title="Chưa có kỳ lương nào"
                description="Tạo kỳ lương đầu tiên để bắt đầu chốt lương." />
            ) : (
              <>
                <PayrollRunsTable rows={controller.runs.data.items} />
                <Pagination
                  page={controller.runs.data.page}
                  totalPages={controller.runs.data.totalPages}
                  totalItems={controller.runs.data.totalItems}
                  onPageChange={controller.setPage}
                  itemLabel="kỳ lương"
                />
              </>
            )
          ) : null}
        </GlassPanel>

        <CreatePayrollRunDialog open={controller.createDialogOpen} onClose={controller.closeCreateDialog} />
      </div>
    </RequirePermission>
  );
}
```
```tsx
// frontend/src/modules/payroll/pages/payroll-run-detail-page.tsx
import { Can, RequirePermission } from "@/entities/permission";
import { PAYROLL_RUN_STATUS_LABEL } from "@/entities/payroll";
import { usePayrollRunDetailController } from "@/modules/payroll/hooks/use-payroll-run-detail-controller";
import { PayslipsTable } from "@/modules/payroll/ui/payslips-table";
import { RejectPayrollRunDialog } from "@/modules/payroll/ui/reject-payroll-run-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { useParams } from "react-router-dom";

export function PayrollRunDetailPage() {
  const { runId = "" } = useParams<{ runId: string }>();
  const controller = usePayrollRunDetailController(runId);

  return (
    <RequirePermission {...ACCESS_RULE.readPayroll}>
      <div className="flex flex-col gap-6">
        {controller.detail.isPending ? <Skeleton className="h-24" /> : null}
        {controller.detail.isError ? <ErrorNotice error={controller.detail.error} /> : null}

        {controller.detail.data ? (
          <>
            <PageHeader
              title={`Kỳ lương ${controller.detail.data.run.month}/${controller.detail.data.run.year}`}
              description={PAYROLL_RUN_STATUS_LABEL[controller.detail.data.run.status]}
              actions={
                <div className="flex gap-2">
                  {controller.isDraft ? (
                    <GlassButton onClick={controller.onSubmitForApproval} loading={controller.isSubmitting}>
                      Gửi duyệt
                    </GlassButton>
                  ) : null}
                  <Can {...ACCESS_RULE.approvePayroll}>
                    {controller.detail.data.run.status === "PENDING_APPROVAL" ? (
                      <>
                        <GlassButton onClick={controller.onApprove} loading={controller.isApproving}>
                          Duyệt
                        </GlassButton>
                        <GlassButton variant="secondary" onClick={controller.openRejectDialog}>
                          Từ chối
                        </GlassButton>
                      </>
                    ) : null}
                  </Can>
                </div>
              }
            />

            <GlassPanel>
              <PayslipsTable
                rows={controller.detail.data.payslips}
                editable={controller.isDraft}
                onUpdate={(payslipId, payload) => controller.updatePayslip.mutate({ payslipId, payload })}
              />
            </GlassPanel>

            <RejectPayrollRunDialog runId={runId} open={controller.rejectDialogOpen} onClose={controller.closeRejectDialog} />
          </>
        ) : null}
      </div>
    </RequirePermission>
  );
}
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd frontend && npm run typecheck`
Expected: PASS (0 lỗi); xoá import tạm khỏi `app.tsx` trước khi qua Task 21.
- [ ] **Step 5: Commit**
```bash
git add frontend/src/modules/payroll/model/payroll-run-forms.ts frontend/src/modules/payroll/api/use-payroll-run-mutations.ts \
        frontend/src/modules/payroll/hooks/use-payroll-runs-page-controller.ts frontend/src/modules/payroll/hooks/use-payroll-run-detail-controller.ts \
        frontend/src/modules/payroll/ui/payroll-runs-table.tsx frontend/src/modules/payroll/ui/create-payroll-run-dialog.tsx \
        frontend/src/modules/payroll/ui/payslips-table.tsx frontend/src/modules/payroll/ui/reject-payroll-run-dialog.tsx \
        frontend/src/modules/payroll/pages/payroll-runs-page.tsx frontend/src/modules/payroll/pages/payroll-run-detail-page.tsx
git commit -m "feat(payroll): add payroll runs list and detail pages with approval flow"
```

---

### Task 21: Wiring — barrel `modules/payroll`, router, nav

**Files:**
- Create: `frontend/src/modules/payroll/index.ts`
- Modify: `frontend/src/app/router/app-router.tsx`
- Modify: `frontend/src/app/layouts/nav-items.ts`

**Interfaces:**
- Consumes: `ContractsPage` (Task 19), `PayrollRunsPage`/`PayrollRunDetailPage` (Task 20), `APP_ROUTE.payrollContracts/payrollRuns/payrollRunDetail` (Task 18), `ACCESS_RULE.readPayroll` (Task 18).
- Produces: route `/admin/payroll/contracts`, `/admin/payroll/runs`, `/admin/payroll/runs/:runId` sau `RequireAuth`; mục nav "Lương & Hợp đồng" (chỉ hiện khi `usePermissions().allows(ACCESS_RULE.readPayroll)`, qua cơ chế `requirement` có sẵn của `NAV_SECTIONS`).

- [ ] **Step 1: Write the failing test**
Xoá mọi import tạm còn sót lại từ Step 2 của Task 17/19/20 trong `app.tsx` (nếu còn) — cổng RED là chính `app-router.tsx`/`nav-items.ts` sau khi thêm route/nav item trỏ tới `@/modules/payroll` khi barrel đó vẫn còn là file trống/chưa đúng export.
- [ ] **Step 2: Run test to verify it fails**
Run: `cd frontend && npm run typecheck`
Expected: FAIL — `Module '"@/modules/payroll"' has no exported member 'ContractsPage'` (barrel chưa viết).
- [ ] **Step 3: Write minimal implementation**
```ts
// frontend/src/modules/payroll/index.ts
export { PayrollRunDetailPage } from "@/modules/payroll/pages/payroll-run-detail-page";
export { PayrollRunsPage } from "@/modules/payroll/pages/payroll-runs-page";
export { ContractsPage } from "@/modules/payroll/pages/contracts-page";
```
Trong `frontend/src/app/router/app-router.tsx`, thêm sau khai báo `StudentsPage`:
```ts
const ContractsPage = lazy(() =>
  import("@/modules/payroll").then((module) => ({ default: module.ContractsPage })),
);
const PayrollRunsPage = lazy(() =>
  import("@/modules/payroll").then((module) => ({ default: module.PayrollRunsPage })),
);
const PayrollRunDetailPage = lazy(() =>
  import("@/modules/payroll").then((module) => ({ default: module.PayrollRunDetailPage })),
);
```
và thêm 3 `<Route>` trong block `<Route element={<RequireAuth />}>` (sau `APP_ROUTE.students`):
```tsx
          <Route path={APP_ROUTE.payrollContracts} element={<ContractsPage />} />
          <Route path={APP_ROUTE.payrollRuns} element={<PayrollRunsPage />} />
          <Route path={APP_ROUTE.payrollRunDetail} element={<PayrollRunDetailPage />} />
```
Trong `frontend/src/app/layouts/nav-items.ts`, thêm vào mảng `items` của section `"Điều hành & Nghiệp vụ"` (sau mục Học viên):
```ts
      { path: APP_ROUTE.payrollContracts, label: "Hợp đồng lao động", requirement: ACCESS_RULE.readPayroll },
      { path: APP_ROUTE.payrollRuns, label: "Kỳ lương", requirement: ACCESS_RULE.readPayroll },
```
- [ ] **Step 4: Run test to verify it passes**
Run: `cd frontend && npm run typecheck && npm run lint`
Expected: PASS (0 lỗi cả hai lệnh) — đây là cổng cuối cùng của Phase 4 (checklist frontend trong `AGENTS.md`).
- [ ] **Step 5: Commit**
```bash
git add frontend/src/modules/payroll/index.ts frontend/src/app/router/app-router.tsx frontend/src/app/layouts/nav-items.ts
git commit -m "feat(payroll): wire contracts and payroll run pages into router and nav"
```

---

## Ghi chú vận hành (ngoài phạm vi code, cần tài liệu hướng dẫn riêng)

- Tổ chức tự tạo nhóm quyền "Kế toán" (CREATE_PAYROLL + READ_PAYROLL + UPDATE_PAYROLL, không APPROVE_PAYROLL) và "Chủ trung tâm" (đủ 4 quyền, gồm APPROVE_PAYROLL) qua màn RBAC có sẵn (`modules.access`) — không cần code thêm, chỉ cần hướng dẫn vận hành (spec mục 3.6).
- Trước khi dùng `StorageClient` ở MinIO dev (`agent-minio`, cổng 9010/9011) hoặc MinIO prod, phải tạo bucket `eduerp-contracts` thủ công (qua `mc` hoặc console MinIO) — `StorageClient` không tự tạo bucket.
- Phiên bản `software.amazon.awssdk:s3` (Task 8) cần tái xác minh trên Maven Central ngay trước khi merge nhánh.
