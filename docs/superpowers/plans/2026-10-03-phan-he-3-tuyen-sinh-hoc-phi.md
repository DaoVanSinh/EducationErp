# Phân hệ 3 — Tuyển sinh & Học phí Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ghi danh học viên vào lớp, phát hành hoá đơn học phí tối đa 3 đợt theo Course, thu tiền qua MoMo/VNPay (link + IPN tự động) hoặc tiền mặt, và tự đánh dấu hoá đơn quá hạn mỗi đêm.

**Architecture:** Ba thành phần mới theo Spring Modulith — `modules.enrollment` (vòng đời ghi danh, đọc `modules.courses`/`modules.students` qua facade), `modules.billing` (Invoice + Payment, đọc `modules.enrollment`/`modules.courses` qua facade, gọi `integrations.payment` trực tiếp như `modules.payroll` gọi `integrations.storage`), và `integrations.payment` (2 client cổng thanh toán, không phụ thuộc module nghiệp vụ nào). Frontend mirror `entities/payroll` + `modules/payroll`: entity giữ schema Zod + query keys + fetcher, module giữ mutation + controller hook + UI khai báo.

**Tech Stack:** Spring Boot 3.4.1, Java 21, Spring Modulith 1.3.1, PostgreSQL 16 + Flyway, Hibernate 6, Lombok, JUnit 5 + AssertJ + Mockito + Testcontainers + Awaitility; React 19 + Vite + TypeScript + TanStack Query v5 + Zod + Tailwind v4.

**Spec:** `docs/superpowers/specs/2026-10-03-phan-he-3-tuyen-sinh-hoc-phi-design.md`

## Global Constraints

- **Không hardcode string literal.** Backend: hằng gom vào `<Module>Constants.java` dưới static inner class (`Limits`, `Schedules`, `CacheNamespaces`); hằng chỉ dùng trong một class (tên tham số cổng thanh toán) gom vào static inner class `Params`/`Values` ngay trong class đó. Frontend: `@/shared/constants/{permissions,api-routes,app-routes,http}.ts`.
- **Facade là type DUY NHẤT module khác được gọi** (`CoursesManagement`, `StudentsManagement`, `EnrollmentManagement`). Không `@ManyToOne` xuyên module; FK mức DB thì có, quan hệ JPA thì không.
- **Một class = một usecase = một method `public execute(...)`**, `@Transactional` phải `public` (proxy AOP của Spring).
- **Mọi lỗi nghiệp vụ** là subclass của `<Module>Exception` (sealed, `permits` liệt kê đủ), mang `errorCode` ổn định, trả ra ngoài dưới dạng RFC 9457 `ProblemDetail` qua `core.exception.GlobalExceptionHandler`. Prefix errorCode theo module: `ENROLLMENT_*`, `BILLING_*` (mirror `PAYROLL_*`).
- **Cyclomatic complexity < 15**, component UI < 200 dòng, không nested ternary.
- **Tiền VND scale 0** — cột DB `NUMERIC(14,0)`, `BigDecimal` làm tròn `setScale(0, RoundingMode.HALF_UP)` trong `BillingRules`.
- **Migration tạo theo đúng thứ tự version tăng dần** V16 → V17 → V18 → V19. Không bao giờ tạo file version cao trước file version thấp (Flyway mặc định từ chối migration "out of order" trên DB đã chạy).
- **`spring.modulith.detection-strategy: explicitly-annotated`** — mọi package module mới PHẢI có `@ApplicationModule` trong `package-info.java` và PHẢI được thêm vào danh sách trong `ModularityTests.everyDomainAndIntegrationPackageIsADetectedModule`, nếu không build đỏ.
- **Không thêm dependency mới.** `RestClient`/`ObjectMapper` đã có qua `spring-boot-starter-web`; HMAC-SHA256/SHA512 dùng `javax.crypto.Mac` của JDK.
- **Không push trực tiếp `main`/`develop`.** Nhánh `feature/admissions-tuition` (đã ở nhánh này). Commit theo Conventional Commits, tiếng Anh.
- **Bốn điểm plan quyết khác spec, có lý do, ghi rõ tại task sở hữu:**
  1. `PaymentGatewayClient.verifyCallback` ném `integrations.payment.PaymentSignatureException` (không ném `BillingException` — `integrations.payment` không được phụ thuộc `modules.billing`); `HandlePaymentCallback` bắt và ném lại `InvalidCallbackSignatureException` như spec mục 5 yêu cầu.
  2. `PaymentCallbackController` trả **một response cố định duy nhất** cho mọi kết quả (chữ ký sai / orderId lạ / thành công), không trả 400 như spec mục 5 bước 1 — vì spec mục 11 Review Focus #5 (bắt buộc) đòi hai trường hợp đầu không phân biệt được từ bên ngoài. Review Focus thắng; mã 400 chỉ còn trong log.
  3. `GET /api/billing/payments/{gatewayTransactionId}/status` là endpoint **public** trên `PaymentStatusController`, không gắn `READ_INVOICE` như bảng ở spec mục 5 — vì spec mục 10 yêu cầu trang `/payment/return/:gateway` chạy được khi phụ huynh CHƯA đăng nhập. `gatewayTransactionId` là chuỗi không đoán được (`invoiceId`-`installment`-`millis`) nên đóng vai capability token, và `PaymentResponse` không chứa dữ liệu cá nhân nào.
  4. Ba path public KHÔNG thêm vào `permitAll()` của `modules.identity.web.IdentitySecurityConfig` (cách spec mục 5 gợi ý): `identity` đọc `billing` sẽ sinh **vòng phụ thuộc** `identity → billing → courses → identity` (`courses.usecase.CreateClass` import `IdentityManagement`) và `ApplicationModules.verify()` fail. `modules.billing` tự khai một `SecurityFilterChain` `@Order(1)` chỉ cho 3 path của mình — đúng thiết kế mà javadoc `core.security.SecurityBootstrapConfig` đã mô tả. Xem Task 22.

## Review Focus

1. **Ghi danh trùng** `(studentProfileId, classId)` khi bản ghi đầu còn `ACTIVE` — phải bị chặn ở CẢ tầng usecase (`existsByStudentProfileIdAndClassIdAndStatus`) lẫn tầng DB (partial unique index `WHERE status = 'ACTIVE'`). Test tầng usecase ở Task 6, test tầng DB ở Task 5.
2. **Ranh giới `ClassFullException`** — ghi danh thứ `maxSeats - 1` thành công, thứ `maxSeats` cũng thành công, thứ `maxSeats + 1` bị chặn. Test ở Task 6.
3. **Tổng 3 đợt vượt `tuitionFee`** — đợt 1 = 50%, đợt 2 = 50%, đợt 3 với số tiền dương bất kỳ phải ném `InvoiceAmountExceedsTuitionException` (không phải `InstallmentLimitExceededException`). Test ở Task 13.
4. **Idempotency `HandlePaymentCallback`** — gọi 2 lần cùng `gatewayTransactionId` thành công thì `Invoice.amountPaid` chỉ cộng một lần và `Payment.status` lần 2 vẫn là `SUCCESS` (không bị ghi đè). Test ở Task 15.
5. **Callback không làm oracle** — "chữ ký sai" và "orderId không tồn tại" trả về status + body giống nhau từng byte ở cả endpoint VNPay và MoMo. Test ở Task 22.

---

### Task 1: `Course.tuitionFee` + migration V16

**Files:**
- Create: `backend/src/main/resources/db/migration/V16__add_course_tuition_fee.sql`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/internal/model/Course.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/dto/CreateCourseRequest.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/dto/UpdateCourseRequest.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/dto/CourseResponse.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/usecase/CreateCourse.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/usecase/UpdateCourse.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/usecase/ListCourses.java`
- Test: `backend/src/test/java/com/eduerp/modules/courses/internal/repository/CourseRepositoryIT.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `Course.getTuitionFee(): BigDecimal` (nullable), `Course.setTuitionFee(BigDecimal)`;
  `CreateCourseRequest(String code, String name, String description, Integer standardSessionCount, BigDecimal tuitionFee)`;
  `UpdateCourseRequest(String name, String description, Integer standardSessionCount, BigDecimal tuitionFee, boolean active)`;
  `CourseResponse(UUID id, String code, String name, String description, Integer standardSessionCount, BigDecimal tuitionFee, boolean active)`;
  cột DB `courses.tuition_fee NUMERIC(14,0)`.

- [ ] **Step 1: Write the failing test** — thêm vào cuối `CourseRepositoryIT` (và thêm `import java.math.BigDecimal;`):

```java
    @Test
    void savesACourseWithATuitionFee() {
        courses.save(new Course("TA-IELTS", "IELTS 6.5", "Mô tả", 36, new BigDecimal("12000000")));

        var found = courses.findByCode("TA-IELTS").orElseThrow();

        assertThat(found.getTuitionFee()).isEqualByComparingTo(new BigDecimal("12000000"));
    }

    /** Học phí để trống được: khoá học cũ chưa gắn giá vẫn phải lưu/đọc bình thường. */
    @Test
    void savesACourseWithoutATuitionFee() {
        courses.save(new Course("TA-NOFEE", "Chưa gắn giá", null, null, null));

        assertThat(courses.findByCode("TA-NOFEE").orElseThrow().getTuitionFee()).isNull();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=CourseRepositoryIT`
Expected: FAIL — compile error, `Course` constructor không nhận 5 tham số.

- [ ] **Step 3: Add the migration**

`backend/src/main/resources/db/migration/V16__add_course_tuition_fee.sql`:

```sql
-- Học phí tính theo Course, không theo Class (spec mục 12). NUMERIC(14,0): VND không có phần thập
-- phân, scale 0 để con số hiển thị khớp đúng con số lưu xuống.
-- Nullable: khoá học đã tồn tại chưa gắn giá, và khoá học mới có thể tạo trước khi chốt giá.
ALTER TABLE courses ADD COLUMN tuition_fee NUMERIC(14,0);
```

- [ ] **Step 4: Add the field to `Course`**

Trong `Course.java`, thêm `import java.math.BigDecimal;` và chèn field sau `standardSessionCount`:

```java
    /** Học phí toàn khoá, VND, scale 0. Nullable - khoá học có thể chưa chốt giá (spec mục 5). */
    @Setter
    @Column(name = "tuition_fee")
    private BigDecimal tuitionFee;
```

Thay constructor:

```java
    public Course(String code, String name, String description, Integer standardSessionCount,
            BigDecimal tuitionFee) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.standardSessionCount = standardSessionCount;
        this.tuitionFee = tuitionFee;
        this.active = true;
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=CourseRepositoryIT`
Expected: PASS (4 tests).

- [ ] **Step 6: Thread `tuitionFee` through the DTOs and usecases**

`CreateCourseRequest.java`:

```java
package com.eduerp.modules.courses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CreateCourseRequest(@NotBlank String code, @NotBlank String name, String description,
        Integer standardSessionCount, @PositiveOrZero BigDecimal tuitionFee) {
}
```

`UpdateCourseRequest.java`:

```java
package com.eduerp.modules.courses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Không có {@code code}: mã khóa học bất biến sau khi tạo. */
public record UpdateCourseRequest(@NotBlank String name, String description,
        Integer standardSessionCount, @PositiveOrZero BigDecimal tuitionFee, boolean active) {
}
```

`CourseResponse.java`:

```java
package com.eduerp.modules.courses.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CourseResponse(UUID id, String code, String name, String description,
        Integer standardSessionCount, BigDecimal tuitionFee, boolean active) {
}
```

Trong `CreateCourse.execute`, thay lệnh `save`:

```java
        var saved = courses.save(new Course(request.code(), request.name(), request.description(),
                request.standardSessionCount(), request.tuitionFee()));
```

Trong `UpdateCourse.execute`, thêm sau `course.setStandardSessionCount(...)`:

```java
        course.setTuitionFee(request.tuitionFee());
```

Trong `ListCourses.toResponse`:

```java
    private static CourseResponse toResponse(Course course) {
        return new CourseResponse(course.getId(), course.getCode(), course.getName(),
                course.getDescription(), course.getStandardSessionCount(), course.getTuitionFee(),
                course.isActive());
    }
}
```

- [ ] **Step 7: Run the full courses test suite**

Run: `cd backend && mvn test -Dtest='Course*Test,Course*IT,Class*IT'`
Expected: PASS — `CourseAdminControllerIT` còn gọi constructor DTO cũ thì sửa call-site cho đủ 5/5 tham số (`new CreateCourseRequest("...", "...", null, null, null)`).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V16__add_course_tuition_fee.sql \
  backend/src/main/java/com/eduerp/modules/courses backend/src/test/java/com/eduerp/modules/courses
git commit -m "feat(courses): add tuition fee to course catalog"
```

---

### Task 2: Facade mở rộng — `getClassInfo`, `getCourseTuition`, `getProfile`

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/courses/CoursesManagement.java`
- Modify: `backend/src/main/java/com/eduerp/modules/students/StudentsManagement.java`
- Test: `backend/src/test/java/com/eduerp/modules/courses/CoursesManagementIT.java` (create)
- Test: `backend/src/test/java/com/eduerp/modules/students/StudentsManagementIT.java` (create)

**Interfaces:**
- Consumes: `Course.getTuitionFee()` (Task 1).
- Produces:
  `CoursesManagement.ClassInfoResponse(UUID classId, UUID courseId, UUID branchId, int maxSeats, boolean active)`;
  `CoursesManagement.getClassInfo(UUID classId): Optional<ClassInfoResponse>`;
  `CoursesManagement.CourseTuitionResponse(UUID courseId, BigDecimal tuitionFee, boolean active)`;
  `CoursesManagement.getCourseTuition(UUID courseId): Optional<CourseTuitionResponse>`;
  `StudentsManagement.StudentSummaryResponse(UUID studentProfileId, UUID accountId, boolean active)`;
  `StudentsManagement.getProfile(UUID studentProfileId): Optional<StudentSummaryResponse>`.

- [ ] **Step 1: Write the failing test** — `backend/src/test/java/com/eduerp/modules/courses/CoursesManagementIT.java`:

```java
package com.eduerp.modules.courses;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.redis.testcontainers.RedisContainer;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Hai method facade mà modules.enrollment/modules.billing sẽ gọi - hợp đồng ra ngoài module, nên
 * test qua context thật chứ không mock repository. */
@Testcontainers
@SpringBootTest
class CoursesManagementIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    CoursesManagement coursesManagement;

    @Autowired
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    @Autowired
    BranchRepository branches;

    @Autowired
    AccountRepository accounts;

    @Test
    void getClassInfoReturnsTheSnapshotEnrollmentNeeds() {
        var course = courses.save(new Course("FACADE-C1", "Khoá facade 1", null, 24, new BigDecimal("9000000")));
        var branch = branches.save(new Branch("FCD-B1", "Chi nhánh facade 1", null));
        var teacher = accounts.save(new Account("facade-teacher1@eduerp.local", "hash", "GV Facade", null));
        var cls = classes.save(new Class(course, "FACADE-K1", branch.getId(), teacher.getId(), 12));

        var info = coursesManagement.getClassInfo(cls.getId()).orElseThrow();

        assertThat(info.classId()).isEqualTo(cls.getId());
        assertThat(info.courseId()).isEqualTo(course.getId());
        assertThat(info.branchId()).isEqualTo(branch.getId());
        assertThat(info.maxSeats()).isEqualTo(12);
        assertThat(info.active()).isTrue();
    }

    @Test
    void getClassInfoIsEmptyForAnUnknownClass() {
        assertThat(coursesManagement.getClassInfo(UUID.randomUUID())).isEmpty();
    }

    @Test
    void getCourseTuitionReturnsTheConfiguredFee() {
        var course = courses.save(new Course("FACADE-C2", "Khoá facade 2", null, 24, new BigDecimal("12000000")));

        var tuition = coursesManagement.getCourseTuition(course.getId()).orElseThrow();

        assertThat(tuition.courseId()).isEqualTo(course.getId());
        assertThat(tuition.tuitionFee()).isEqualByComparingTo(new BigDecimal("12000000"));
        assertThat(tuition.active()).isTrue();
    }

    /** Khoá chưa gắn giá vẫn trả về Optional có giá trị, tuitionFee = null - modules.billing phân
     * biệt "không có khoá" (empty) với "khoá chưa gắn giá" (tuitionFee null) bằng hai lỗi khác nhau. */
    @Test
    void getCourseTuitionReturnsANullFeeForACourseWithoutAPrice() {
        var course = courses.save(new Course("FACADE-C3", "Khoá facade 3", null, null, null));

        assertThat(coursesManagement.getCourseTuition(course.getId()).orElseThrow().tuitionFee()).isNull();
    }

    @Test
    void getCourseTuitionIsEmptyForAnUnknownCourse() {
        assertThat(coursesManagement.getCourseTuition(UUID.randomUUID())).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=CoursesManagementIT`
Expected: FAIL — compile error: `getClassInfo`/`getCourseTuition` không tồn tại.

- [ ] **Step 3: Add the two facade methods**

`CoursesManagement.java` — thay toàn bộ file:

```java
package com.eduerp.modules.courses;

import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module courses — type DUY NHẤT mà module khác được phép gọi (rule #1).
 * {@code modules.enrollment} đọc {@link ClassInfoResponse} để chốt snapshot lúc ghi danh,
 * {@code modules.billing} đọc {@link CourseTuitionResponse} để chặn tổng các đợt vượt học phí.
 */
@Service
public class CoursesManagement {

    /** Ảnh chụp những gì modules.enrollment cần biết về một lớp - không lộ entity Class ra ngoài. */
    public record ClassInfoResponse(UUID classId, UUID courseId, UUID branchId, int maxSeats, boolean active) {
    }

    /** {@code tuitionFee} null = khoá học chưa chốt giá (khác với "không tìm thấy khoá học"). */
    public record CourseTuitionResponse(UUID courseId, BigDecimal tuitionFee, boolean active) {
    }

    private final CourseRepository courses;
    private final ClassRepository classes;

    CoursesManagement(CourseRepository courses, ClassRepository classes) {
        this.courses = courses;
        this.classes = classes;
    }

    @Transactional(readOnly = true)
    public boolean courseExists(UUID courseId) {
        return courses.existsById(courseId);
    }

    @Transactional(readOnly = true)
    public boolean classExists(UUID classId) {
        return classes.existsById(classId);
    }

    @Transactional(readOnly = true)
    public Optional<ClassInfoResponse> getClassInfo(UUID classId) {
        return classes.findById(classId).map(cls -> new ClassInfoResponse(cls.getId(), cls.getCourse().getId(),
                cls.getBranchId(), cls.getMaxSeats(), cls.isActive()));
    }

    @Transactional(readOnly = true)
    public Optional<CourseTuitionResponse> getCourseTuition(UUID courseId) {
        return courses.findById(courseId)
                .map(course -> new CourseTuitionResponse(course.getId(), course.getTuitionFee(), course.isActive()));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=CoursesManagementIT`
Expected: PASS (5 tests).

- [ ] **Step 5: Write the failing test for `StudentsManagement.getProfile`** — `backend/src/test/java/com/eduerp/modules/students/StudentsManagementIT.java`:

```java
package com.eduerp.modules.students;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import com.redis.testcontainers.RedisContainer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class StudentsManagementIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    StudentsManagement studentsManagement;

    @Autowired
    StudentProfileRepository profiles;

    @Autowired
    AccountRepository accounts;

    @Test
    void getProfileReturnsTheSummaryEnrollmentNeeds() {
        var account = accounts.save(new Account("facade-student1@eduerp.local", "hash", "HV Facade", null));
        var profile = profiles.save(new StudentProfile(account.getId(), null, null, null));

        var summary = studentsManagement.getProfile(profile.getId()).orElseThrow();

        assertThat(summary.studentProfileId()).isEqualTo(profile.getId());
        assertThat(summary.accountId()).isEqualTo(account.getId());
        assertThat(summary.active()).isTrue();
    }

    @Test
    void getProfileReportsAnInactiveProfile() {
        var account = accounts.save(new Account("facade-student2@eduerp.local", "hash", "HV Facade 2", null));
        var profile = new StudentProfile(account.getId(), null, null, null);
        profile.setActive(false);
        var saved = profiles.save(profile);

        assertThat(studentsManagement.getProfile(saved.getId()).orElseThrow().active()).isFalse();
    }

    @Test
    void getProfileIsEmptyForAnUnknownProfile() {
        assertThat(studentsManagement.getProfile(UUID.randomUUID())).isEmpty();
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=StudentsManagementIT`
Expected: FAIL — compile error: `getProfile` không tồn tại.

- [ ] **Step 7: Add `getProfile`** — `StudentsManagement.java` thay toàn bộ file:

```java
package com.eduerp.modules.students;

import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module students. {@code modules.enrollment} gọi {@link #getProfile(UUID)} để xác nhận
 * học viên tồn tại và còn hoạt động trước khi ghi danh (spec mục 4).
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
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=StudentsManagementIT`
Expected: PASS (3 tests).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/courses/CoursesManagement.java \
  backend/src/main/java/com/eduerp/modules/students/StudentsManagement.java \
  backend/src/test/java/com/eduerp/modules/courses/CoursesManagementIT.java \
  backend/src/test/java/com/eduerp/modules/students/StudentsManagementIT.java
git commit -m "feat(courses,students): expose class info, course tuition and student summary on facades"
```

---

### Task 3: RBAC vocabulary cho ENROLLMENT và INVOICE

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/access/AccessConstants.java`
- Test: `backend/src/test/java/com/eduerp/modules/access/AccessConstantsTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `AccessConstants.Resources.ENROLLMENT = "ENROLLMENT"`, `AccessConstants.Resources.INVOICE = "INVOICE"`;
  `AccessConstants.AccessRules.{CREATE,READ,UPDATE}_ENROLLMENT`, `AccessConstants.AccessRules.{CREATE,READ,UPDATE}_INVOICE` — 6 hằng biên dịch dùng trong `@PreAuthorize`.

- [ ] **Step 1: Write the failing test** — thêm vào cuối `AccessConstantsTest`:

```java
    @Test
    void enrollmentAndInvoiceRulesRequireOrganizationScope() {
        assertThat(AccessConstants.AccessRules.CREATE_ENROLLMENT)
                .contains("ENROLLMENT", "CREATE", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.READ_ENROLLMENT)
                .contains("ENROLLMENT", "READ", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.UPDATE_ENROLLMENT)
                .contains("ENROLLMENT", "UPDATE", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.CREATE_INVOICE)
                .contains("INVOICE", "CREATE", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.READ_INVOICE)
                .contains("INVOICE", "READ", "@ORGANIZATION");
        assertThat(AccessConstants.AccessRules.UPDATE_INVOICE)
                .contains("INVOICE", "UPDATE", "@ORGANIZATION");
    }

    /** Phân hệ 3 không có luồng duyệt - không rule nào ở đây được đòi APPROVE (spec mục 7). */
    @Test
    void enrollmentAndInvoiceHaveNoApproveRule() {
        assertThat(AccessConstants.AccessRules.UPDATE_ENROLLMENT).doesNotContain(AccessConstants.Actions.APPROVE);
        assertThat(AccessConstants.AccessRules.UPDATE_INVOICE).doesNotContain(AccessConstants.Actions.APPROVE);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=AccessConstantsTest`
Expected: FAIL — compile error: `CREATE_ENROLLMENT` không tồn tại.

- [ ] **Step 3: Add the two resources**

Trong `AccessConstants.Resources`, thêm ngay sau `PAYROLL`:

```java
        public static final String ENROLLMENT = "ENROLLMENT";
        public static final String INVOICE = "INVOICE";
```

- [ ] **Step 4: Add the six rules**

Trong `AccessConstants.AccessRules`, thêm ngay sau `APPROVE_PAYROLL`:

```java
        public static final String CREATE_ENROLLMENT = CHECK_PREFIX + Resources.ENROLLMENT + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_ENROLLMENT = CHECK_PREFIX + Resources.ENROLLMENT + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        /** Rút/hoàn tất ghi danh là chuyển state, dùng UPDATE - hệ thống chưa dùng DELETE ở đâu. */
        public static final String UPDATE_ENROLLMENT = CHECK_PREFIX + Resources.ENROLLMENT + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String CREATE_INVOICE = CHECK_PREFIX + Resources.INVOICE + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_INVOICE = CHECK_PREFIX + Resources.INVOICE + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        /** Thu tiền/huỷ hoá đơn cũng là chuyển state của Invoice, dùng UPDATE (spec mục 5). */
        public static final String UPDATE_INVOICE = CHECK_PREFIX + Resources.INVOICE + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=AccessConstantsTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/access/AccessConstants.java \
  backend/src/test/java/com/eduerp/modules/access/AccessConstantsTest.java
git commit -m "feat(access): add ENROLLMENT and INVOICE resources with organization-scoped rules"
```

---

### Task 4: `modules.enrollment` scaffold — constants, exceptions, events, DTO

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/EnrollmentConstants.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/EnrollmentException.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/EnrollmentNotFoundException.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/StudentProfileNotFoundException.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/StudentNotActiveException.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/ClassNotFoundException.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/ClassNotActiveException.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/ClassFullException.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/DuplicateActiveEnrollmentException.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/EnrollmentNotActiveException.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/EnrollmentEvents.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/dto/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/dto/CreateEnrollmentRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/dto/EnrollmentResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/usecase/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/web/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/internal/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/internal/model/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/internal/repository/package-info.java`
- Modify: `backend/src/test/java/com/eduerp/ModularityTests.java`
- Test: `backend/src/test/java/com/eduerp/modules/enrollment/EnrollmentConstantsTest.java` (create)
- Test: `backend/src/test/java/com/eduerp/modules/enrollment/EnrollmentExceptionTest.java` (create)

**Interfaces:**
- Consumes: `com.eduerp.core.exception.AppException`.
- Produces: `EnrollmentConstants.EnrollmentStatus { ACTIVE, WITHDRAWN, COMPLETED }`;
  sealed `EnrollmentException` + 8 subclass với errorCode `ENROLLMENT_NOT_FOUND`, `ENROLLMENT_STUDENT_PROFILE_NOT_FOUND`, `ENROLLMENT_STUDENT_NOT_ACTIVE`, `ENROLLMENT_CLASS_NOT_FOUND`, `ENROLLMENT_CLASS_NOT_ACTIVE`, `ENROLLMENT_CLASS_FULL`, `ENROLLMENT_DUPLICATE_ACTIVE`, `ENROLLMENT_NOT_ACTIVE`;
  `EnrollmentEvents.EnrollmentCreated|EnrollmentWithdrawn|EnrollmentCompleted(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId)`;
  `CreateEnrollmentRequest(UUID studentProfileId, UUID classId)`;
  `EnrollmentResponse(UUID id, UUID studentProfileId, UUID classId, UUID courseId, UUID branchId, EnrollmentConstants.EnrollmentStatus status, Instant enrolledAt, Instant withdrawnAt)`.

- [ ] **Step 1: Write the failing tests**

`backend/src/test/java/com/eduerp/modules/enrollment/EnrollmentConstantsTest.java`:

```java
package com.eduerp.modules.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnrollmentConstantsTest {

    @Test
    void enrollmentStatusHasExactlyThreeValuesInLifecycleOrder() {
        assertThat(EnrollmentConstants.EnrollmentStatus.values()).containsExactly(
                EnrollmentConstants.EnrollmentStatus.ACTIVE,
                EnrollmentConstants.EnrollmentStatus.WITHDRAWN,
                EnrollmentConstants.EnrollmentStatus.COMPLETED);
    }

    /** Partial unique index trong V17 so sánh literal 'ACTIVE' - tên enum phải khớp từng chữ. */
    @Test
    void activeStatusNameMatchesTheDatabaseIndexPredicate() {
        assertThat(EnrollmentConstants.EnrollmentStatus.ACTIVE.name()).isEqualTo("ACTIVE");
    }
}
```

`backend/src/test/java/com/eduerp/modules/enrollment/EnrollmentExceptionTest.java`:

```java
package com.eduerp.modules.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class EnrollmentExceptionTest {

    @Test
    void enrollmentNotFoundIsNotFound() {
        var ex = new EnrollmentNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void studentProfileNotFoundIsBadRequest() {
        var ex = new StudentProfileNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_STUDENT_PROFILE_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void studentNotActiveIsConflict() {
        var ex = new StudentNotActiveException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_STUDENT_NOT_ACTIVE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void classNotFoundIsBadRequest() {
        var ex = new ClassNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_CLASS_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void classNotActiveIsConflict() {
        var ex = new ClassNotActiveException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_CLASS_NOT_ACTIVE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void classFullIsConflictAndNamesTheSeatCount() {
        var classId = UUID.randomUUID();
        var ex = new ClassFullException(classId, 12);
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_CLASS_FULL");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("12");
    }

    @Test
    void duplicateActiveEnrollmentIsConflict() {
        var ex = new DuplicateActiveEnrollmentException(UUID.randomUUID(), UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_DUPLICATE_ACTIVE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void enrollmentNotActiveIsConflict() {
        var ex = new EnrollmentNotActiveException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ENROLLMENT_NOT_ACTIVE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -Dtest='EnrollmentConstantsTest,EnrollmentExceptionTest'`
Expected: FAIL — compile error, package `com.eduerp.modules.enrollment` chưa tồn tại.

- [ ] **Step 3: Create the module package-info files**

`modules/enrollment/package-info.java`:

```java
/**
 * Module Enrollment — sở hữu việc ghi danh học viên vào một lớp và vòng đời của bản ghi ghi danh
 * (ACTIVE → WITHDRAWN/COMPLETED). Phụ thuộc một chiều {@code access} ({@code AccessConstants} cho
 * {@code @PreAuthorize}), {@code courses} ({@code CoursesManagement.getClassInfo} để chốt snapshot
 * course/branch/maxSeats) và {@code students} ({@code StudentsManagement.getProfile} để xác nhận
 * học viên còn hoạt động). KHÔNG biết tới {@code modules.billing} — billing đọc ngược qua
 * {@code EnrollmentManagement}, không có cạnh phụ thuộc ngược (spec mục 3).
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — API của module: {@code EnrollmentManagement}, constants, exception, events.</li>
 *   <li>{@code dto} — hợp đồng vào/ra qua HTTP, {@code @NamedInterface("dto")}.</li>
 *   <li>{@code usecase} — một class = một use case (rule #7).</li>
 *   <li>{@code web} — controller mỏng (rule #8).</li>
 *   <li>{@code internal} — chi tiết cài đặt, Spring Modulith che khỏi mọi module khác.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Enrollment")
package com.eduerp.modules.enrollment;
```

`modules/enrollment/dto/package-info.java`:

```java
/**
 * Hợp đồng vào/ra của module enrollment. Phải {@code @NamedInterface} nếu không
 * {@code ApplicationModules.verify()} sẽ fail - mirror {@code modules.payroll.dto}.
 */
@org.springframework.modulith.NamedInterface("dto")
package com.eduerp.modules.enrollment.dto;
```

`modules/enrollment/usecase/package-info.java`:

```java
/**
 * Một class = một use case = một method {@code public execute(...)} (rule #7). Method phải
 * {@code public} để proxy AOP của Spring bọc được {@code @Transactional}.
 */
package com.eduerp.modules.enrollment.usecase;
```

`modules/enrollment/web/package-info.java`:

```java
/** Adapter HTTP của module enrollment: controller mỏng (rule #8 - không chứa nghiệp vụ). */
package com.eduerp.modules.enrollment.web;
```

`modules/enrollment/internal/package-info.java`:

```java
package com.eduerp.modules.enrollment.internal;
```

`modules/enrollment/internal/model/package-info.java`:

```java
package com.eduerp.modules.enrollment.internal.model;
```

`modules/enrollment/internal/repository/package-info.java`:

```java
package com.eduerp.modules.enrollment.internal.repository;
```

- [ ] **Step 4: Create `EnrollmentConstants` and `EnrollmentEvents`**

`EnrollmentConstants.java`:

```java
package com.eduerp.modules.enrollment;

/**
 * Hằng số dùng chung của module enrollment, nằm ở base package (không phải {@code internal}) vì
 * {@code dto} và {@code internal.model} cùng tier, cả hai cần tham chiếu - mirror
 * {@code CoursesConstants}/{@code PayrollConstants}.
 */
public final class EnrollmentConstants {

    private EnrollmentConstants() {
    }

    /** Tên của ACTIVE xuất hiện nguyên văn trong partial unique index của V17 - đổi tên là đổi DB. */
    public enum EnrollmentStatus {
        ACTIVE, WITHDRAWN, COMPLETED
    }

    /** Không có hạn mức nào ở phân hệ này (sĩ số nằm ở Class.maxSeats, thuộc modules.courses). */
    public static final class Limits {
        private Limits() {
        }
    }

    /** Không cần cache namespace nào ở V1 - để trống theo khuôn tier 0 của PayrollConstants. */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }
    }
}
```

`EnrollmentEvents.java`:

```java
package com.eduerp.modules.enrollment;

import java.util.UUID;

/** Mirror {@code CoursesEvents}: ba record phẳng, cùng hình dạng (id, actor, actorBranch). */
public final class EnrollmentEvents {

    private EnrollmentEvents() {
    }

    public record EnrollmentCreated(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record EnrollmentWithdrawn(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record EnrollmentCompleted(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId) {
    }
}
```

- [ ] **Step 5: Create the exception hierarchy**

`EnrollmentException.java`:

```java
package com.eduerp.modules.enrollment;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module enrollment phát ra. */
public sealed class EnrollmentException extends AppException
        permits EnrollmentNotFoundException, StudentProfileNotFoundException, StudentNotActiveException,
        ClassNotFoundException, ClassNotActiveException, ClassFullException, DuplicateActiveEnrollmentException,
        EnrollmentNotActiveException {

    protected EnrollmentException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
```

`EnrollmentNotFoundException.java`:

```java
package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class EnrollmentNotFoundException extends EnrollmentException {
    public EnrollmentNotFoundException(UUID enrollmentId) {
        super("ENROLLMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy ghi danh " + enrollmentId);
    }
}
```

`StudentProfileNotFoundException.java`:

```java
package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** 400 chứ không 404: id học viên là dữ liệu người dùng gửi lên trong body, không phải tài nguyên
 * của chính request này (mirror cách AppValidationException dùng 400 cho id trỏ sang module khác). */
public final class StudentProfileNotFoundException extends EnrollmentException {
    public StudentProfileNotFoundException(UUID studentProfileId) {
        super("ENROLLMENT_STUDENT_PROFILE_NOT_FOUND", HttpStatus.BAD_REQUEST,
                "Không tìm thấy hồ sơ học viên " + studentProfileId);
    }
}
```

`StudentNotActiveException.java`:

```java
package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class StudentNotActiveException extends EnrollmentException {
    public StudentNotActiveException(UUID studentProfileId) {
        super("ENROLLMENT_STUDENT_NOT_ACTIVE", HttpStatus.CONFLICT,
                "Hồ sơ học viên " + studentProfileId + " đã bị vô hiệu hoá");
    }
}
```

`ClassNotFoundException.java`:

```java
package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Cùng tên đơn giản với {@code java.lang.ClassNotFoundException} và
 * {@code modules.courses.ClassNotFoundException} nhưng khác package - lỗi của riêng enrollment, mang
 * errorCode riêng; mọi nơi dùng phải import tường minh (mirror cách courses đã đặt tên). */
public final class ClassNotFoundException extends EnrollmentException {
    public ClassNotFoundException(UUID classId) {
        super("ENROLLMENT_CLASS_NOT_FOUND", HttpStatus.BAD_REQUEST, "Không tìm thấy lớp học " + classId);
    }
}
```

`ClassNotActiveException.java`:

```java
package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ClassNotActiveException extends EnrollmentException {
    public ClassNotActiveException(UUID classId) {
        super("ENROLLMENT_CLASS_NOT_ACTIVE", HttpStatus.CONFLICT, "Lớp học " + classId + " đã bị vô hiệu hoá");
    }
}
```

`ClassFullException.java`:

```java
package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ClassFullException extends EnrollmentException {
    public ClassFullException(UUID classId, int maxSeats) {
        super("ENROLLMENT_CLASS_FULL", HttpStatus.CONFLICT,
                "Lớp học " + classId + " đã đủ " + maxSeats + " chỗ");
    }
}
```

`DuplicateActiveEnrollmentException.java`:

```java
package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class DuplicateActiveEnrollmentException extends EnrollmentException {
    public DuplicateActiveEnrollmentException(UUID studentProfileId, UUID classId) {
        super("ENROLLMENT_DUPLICATE_ACTIVE", HttpStatus.CONFLICT,
                "Học viên " + studentProfileId + " đã ghi danh lớp " + classId + " và còn đang học");
    }
}
```

`EnrollmentNotActiveException.java`:

```java
package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class EnrollmentNotActiveException extends EnrollmentException {
    public EnrollmentNotActiveException(UUID enrollmentId) {
        super("ENROLLMENT_NOT_ACTIVE", HttpStatus.CONFLICT,
                "Ghi danh " + enrollmentId + " không còn ở trạng thái đang học");
    }
}
```

- [ ] **Step 6: Create the two DTO records**

`dto/CreateEnrollmentRequest.java`:

```java
package com.eduerp.modules.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateEnrollmentRequest(@NotNull UUID studentProfileId, @NotNull UUID classId) {
}
```

`dto/EnrollmentResponse.java`:

```java
package com.eduerp.modules.enrollment.dto;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.time.Instant;
import java.util.UUID;

/** Tên field là hợp đồng với Zod schema ở frontend (entities/enrollment) - đổi tên là breaking. */
public record EnrollmentResponse(UUID id, UUID studentProfileId, UUID classId, UUID courseId, UUID branchId,
        EnrollmentConstants.EnrollmentStatus status, Instant enrolledAt, Instant withdrawnAt) {
}
```

- [ ] **Step 7: Register the module in `ModularityTests`**

Trong `ModularityTests.everyDomainAndIntegrationPackageIsADetectedModule`, thêm `"modules.enrollment"` vào `containsExactlyInAnyOrder` ngay sau `"modules.payroll"`:

```java
        assertThat(detected).containsExactlyInAnyOrder("core", "shared", "modules.identity", "modules.access",
                "modules.organization", "modules.audit", "modules.dashboard", "modules.courses",
                "modules.teachers", "modules.students", "modules.payroll", "modules.enrollment",
                "integrations.cache", "integrations.mail", "integrations.notification", "integrations.storage");
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest='EnrollmentConstantsTest,EnrollmentExceptionTest,ModularityTests'`
Expected: PASS (10 + 2 tests).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/enrollment backend/src/test/java/com/eduerp/modules/enrollment \
  backend/src/test/java/com/eduerp/ModularityTests.java
git commit -m "feat(enrollment): scaffold module with constants, exceptions, events and DTOs"
```

---

### Task 5: `Enrollment` entity + repository + migration V17 (Review Focus #1 — tầng DB)

**Files:**
- Create: `backend/src/main/resources/db/migration/V17__create_enrollments.sql`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/internal/model/Enrollment.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/internal/repository/EnrollmentRepository.java`
- Test: `backend/src/test/java/com/eduerp/modules/enrollment/internal/repository/EnrollmentRepositoryIT.java` (create)

**Interfaces:**
- Consumes: `EnrollmentConstants.EnrollmentStatus` (Task 4), `Course`/`Class` (Task 1), `StudentProfile`, `Branch`, `Account`.
- Produces:
  `Enrollment(UUID studentProfileId, UUID classId, UUID courseId, UUID branchId, UUID createdByAccountId)` — khởi tạo `status=ACTIVE`, `enrolledAt=Instant.now()`;
  `Enrollment.withdraw()`, `Enrollment.complete()`, getter `getId/getStudentProfileId/getClassId/getCourseId/getBranchId/getStatus/getEnrolledAt/getWithdrawnAt/getCreatedByAccountId`;
  `EnrollmentRepository.countByClassIdAndStatus(UUID, EnrollmentStatus): long`,
  `EnrollmentRepository.existsByStudentProfileIdAndClassIdAndStatus(UUID, UUID, EnrollmentStatus): boolean`,
  `EnrollmentRepository.search(UUID studentProfileId, UUID classId, Pageable): Page<Enrollment>`.

- [ ] **Step 1: Write the failing test** — `backend/src/test/java/com/eduerp/modules/enrollment/internal/repository/EnrollmentRepositoryIT.java`:

```java
package com.eduerp.modules.enrollment.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Mọi cột UUID của enrollments đều có FK thật (student_profiles, classes, courses, branches,
 * accounts) - phải dựng dữ liệu thật qua repository của từng module, không dùng UUID ngẫu nhiên
 * (mirror ghi chú của PayrollRunRepositoryIT). */
@Testcontainers
@DataJpaTest
class EnrollmentRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

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

    @BeforeEach
    void seedReferences() {
        branchId = branches.save(new Branch("ENR-" + shortId(), "Chi nhánh ghi danh", null)).getId();
        actorId = accounts.save(new Account("enr-actor-" + shortId() + "@eduerp.local", "hash", "Actor", null)).getId();
        var course = courses.save(new Course("ENR-C-" + shortId(), "Khoá ghi danh", null, 24,
                new BigDecimal("9000000")));
        courseId = course.getId();
        var teacherId = accounts.save(new Account("enr-gv-" + shortId() + "@eduerp.local", "hash", "GV", null)).getId();
        classId = classes.save(new Class(course, "ENR-K-" + shortId(), branchId, teacherId, 2)).getId();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private UUID newStudentProfileId() {
        var account = accounts.save(new Account("enr-hv-" + shortId() + "@eduerp.local", "hash", "HV", null));
        return profiles.save(new StudentProfile(account.getId(), null, null, null)).getId();
    }

    private Enrollment newEnrollment(UUID studentProfileId) {
        return new Enrollment(studentProfileId, classId, courseId, branchId, actorId);
    }

    @Test
    void savesAnEnrollmentAsActive() {
        var saved = enrollments.save(newEnrollment(newStudentProfileId()));

        var found = enrollments.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        assertThat(found.getEnrolledAt()).isNotNull();
        assertThat(found.getWithdrawnAt()).isNull();
        assertThat(found.getCourseId()).isEqualTo(courseId);
        assertThat(found.getBranchId()).isEqualTo(branchId);
    }

    /**
     * Review Focus #1, tầng DB: partial unique index {@code (student_profile_id, class_id) WHERE
     * status = 'ACTIVE'} là lớp phòng thủ thứ hai. Usecase (Task 6) phải chặn TRƯỚC khi chạm DB; test
     * này chứng minh dù usecase hỏng thì DB vẫn không nhận bản ghi trùng.
     */
    @Test
    void rejectsASecondActiveEnrollmentForTheSameStudentAndClassAtDatabaseLevel() {
        var studentProfileId = newStudentProfileId();
        enrollments.saveAndFlush(newEnrollment(studentProfileId));

        assertThatThrownBy(() -> enrollments.saveAndFlush(newEnrollment(studentProfileId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Review Focus #1, mặt còn lại: index là PARTIAL nên sau khi rút khỏi lớp, học viên được ghi danh
     * lại cùng lớp đó. Một unique index đầy đủ sẽ chặn sai ở đây.
     */
    @Test
    void allowsReEnrollingAfterWithdrawal() {
        var studentProfileId = newStudentProfileId();
        var first = enrollments.saveAndFlush(newEnrollment(studentProfileId));
        first.withdraw();
        enrollments.saveAndFlush(first);

        var second = enrollments.saveAndFlush(newEnrollment(studentProfileId));

        assertThat(second.getStatus()).isEqualTo(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        assertThat(enrollments.countByClassIdAndStatus(classId, EnrollmentConstants.EnrollmentStatus.ACTIVE))
                .isEqualTo(1);
    }

    @Test
    void countsOnlyActiveEnrollmentsOfTheClass() {
        enrollments.saveAndFlush(newEnrollment(newStudentProfileId()));
        var withdrawn = enrollments.saveAndFlush(newEnrollment(newStudentProfileId()));
        withdrawn.withdraw();
        enrollments.saveAndFlush(withdrawn);

        assertThat(enrollments.countByClassIdAndStatus(classId, EnrollmentConstants.EnrollmentStatus.ACTIVE))
                .isEqualTo(1);
        assertThat(enrollments.countByClassIdAndStatus(classId, EnrollmentConstants.EnrollmentStatus.WITHDRAWN))
                .isEqualTo(1);
    }

    @Test
    void existsByStudentProfileIdAndClassIdAndStatusSeesOnlyTheMatchingStatus() {
        var studentProfileId = newStudentProfileId();
        var enrollment = enrollments.saveAndFlush(newEnrollment(studentProfileId));

        assertThat(enrollments.existsByStudentProfileIdAndClassIdAndStatus(studentProfileId, classId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE)).isTrue();

        enrollment.complete();
        enrollments.saveAndFlush(enrollment);

        assertThat(enrollments.existsByStudentProfileIdAndClassIdAndStatus(studentProfileId, classId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE)).isFalse();
    }

    @Test
    void searchFiltersByEveryCombinationOfStudentAndClass() {
        var studentProfileId = newStudentProfileId();
        enrollments.saveAndFlush(newEnrollment(studentProfileId));
        enrollments.saveAndFlush(newEnrollment(newStudentProfileId()));
        var pageable = PageRequest.of(0, 20);

        assertThat(enrollments.search(null, null, pageable).getTotalElements()).isEqualTo(2);
        assertThat(enrollments.search(studentProfileId, null, pageable).getTotalElements()).isEqualTo(1);
        assertThat(enrollments.search(null, classId, pageable).getTotalElements()).isEqualTo(2);
        assertThat(enrollments.search(studentProfileId, classId, pageable).getTotalElements()).isEqualTo(1);
        assertThat(enrollments.search(studentProfileId, UUID.randomUUID(), pageable).getTotalElements()).isZero();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=EnrollmentRepositoryIT`
Expected: FAIL — compile error, `Enrollment`/`EnrollmentRepository` chưa tồn tại.

- [ ] **Step 3: Write the migration**

`backend/src/main/resources/db/migration/V17__create_enrollments.sql`:

```sql
CREATE TABLE enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_profile_id UUID NOT NULL REFERENCES student_profiles(id),
    class_id UUID NOT NULL REFERENCES classes(id),
    -- course_id/branch_id là snapshot chốt lúc ghi danh (spec mục 4): lớp chuyển chi nhánh về sau
    -- không được làm đổi hoá đơn đã phát hành. FK mức DB để toàn vẹn dữ liệu, không có @ManyToOne
    -- tương ứng trong Java (rule #3: không quan hệ JPA xuyên module).
    course_id UUID NOT NULL REFERENCES courses(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    enrolled_at TIMESTAMPTZ NOT NULL,
    withdrawn_at TIMESTAMPTZ,
    created_by_account_id UUID NOT NULL REFERENCES accounts(id)
);

CREATE INDEX idx_enrollments_student ON enrollments (student_profile_id);
CREATE INDEX idx_enrollments_class ON enrollments (class_id);

-- Review Focus #1: chặn ghi danh trùng ở tầng DB, không chỉ tầng application (defense in depth).
-- PARTIAL (WHERE status = 'ACTIVE', cú pháp Postgres) để học viên đã rút vẫn ghi danh lại được cùng
-- lớp đó - một unique index đầy đủ sẽ chặn sai trường hợp hợp lệ này.
CREATE UNIQUE INDEX uq_enrollments_active_student_class
    ON enrollments (student_profile_id, class_id)
    WHERE status = 'ACTIVE';
```

- [ ] **Step 4: Write the entity**

`modules/enrollment/internal/model/Enrollment.java`:

```java
package com.eduerp.modules.enrollment.internal.model;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Một lần ghi danh học viên vào một lớp. {@code courseId}/{@code branchId} là UUID trần, chốt
 * snapshot lúc ghi danh - {@code classes}/{@code courses}/{@code branches} thuộc module khác nên
 * không có {@code @ManyToOne} (rule #3).
 */
@Entity
@Table(name = "enrollments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "student_profile_id", nullable = false, updatable = false)
    private UUID studentProfileId;

    @Column(name = "class_id", nullable = false, updatable = false)
    private UUID classId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentConstants.EnrollmentStatus status;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private UUID createdByAccountId;

    public Enrollment(UUID studentProfileId, UUID classId, UUID courseId, UUID branchId, UUID createdByAccountId) {
        this.studentProfileId = studentProfileId;
        this.classId = classId;
        this.courseId = courseId;
        this.branchId = branchId;
        this.createdByAccountId = createdByAccountId;
        this.status = EnrollmentConstants.EnrollmentStatus.ACTIVE;
        this.enrolledAt = Instant.now();
    }

    /** Rút khỏi lớp KHÔNG huỷ hoá đơn đã phát hành - chỉ chặn billing tạo đợt mới (spec mục 4). */
    public void withdraw() {
        this.status = EnrollmentConstants.EnrollmentStatus.WITHDRAWN;
        this.withdrawnAt = Instant.now();
    }

    public void complete() {
        this.status = EnrollmentConstants.EnrollmentStatus.COMPLETED;
    }
}
```

- [ ] **Step 5: Write the repository**

`modules/enrollment/internal/repository/EnrollmentRepository.java`:

```java
package com.eduerp.modules.enrollment.internal.repository;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    long countByClassIdAndStatus(UUID classId, EnrollmentConstants.EnrollmentStatus status);

    boolean existsByStudentProfileIdAndClassIdAndStatus(UUID studentProfileId, UUID classId,
            EnrollmentConstants.EnrollmentStatus status);

    /**
     * Hai filter đều optional (spec mục 4 {@code ListEnrollments}). Một query với {@code :p IS NULL}
     * thay vì bốn method {@code findAllBy...} - giữ usecase ở complexity 1 thay vì rẽ 4 nhánh.
     * {@code EnrollmentRepositoryIT.searchFiltersByEveryCombination...} phủ cả 4 tổ hợp nên nếu
     * Hibernate/Postgres không suy được kiểu tham số null thì test đỏ ngay, không lọt ra production.
     */
    @Query("""
            SELECT e FROM Enrollment e
            WHERE (:studentProfileId IS NULL OR e.studentProfileId = :studentProfileId)
              AND (:classId IS NULL OR e.classId = :classId)
            """)
    Page<Enrollment> search(@Param("studentProfileId") UUID studentProfileId, @Param("classId") UUID classId,
            Pageable pageable);
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=EnrollmentRepositoryIT`
Expected: PASS (6 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V17__create_enrollments.sql \
  backend/src/main/java/com/eduerp/modules/enrollment/internal \
  backend/src/test/java/com/eduerp/modules/enrollment/internal
git commit -m "feat(enrollment): add enrollment entity, repository and partial unique index"
```

---

### Task 6: `CreateEnrollment` usecase (Review Focus #1 tầng usecase, #2 ranh giới maxSeats)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/usecase/CreateEnrollment.java`
- Test: `backend/src/test/java/com/eduerp/modules/enrollment/usecase/CreateEnrollmentTest.java` (create)

**Interfaces:**
- Consumes: `StudentsManagement.getProfile` / `CoursesManagement.getClassInfo` (Task 2), `EnrollmentRepository` (Task 5), mọi exception + `EnrollmentEvents` + DTO (Task 4).
- Produces: `CreateEnrollment.execute(UUID actorAccountId, UUID actorBranchId, CreateEnrollmentRequest request): EnrollmentResponse`;
  `CreateEnrollment.toResponse(Enrollment): EnrollmentResponse` (static, package-private — các usecase khác trong `usecase` dùng lại, mirror `CreateContract.toResponse`).

- [ ] **Step 1: Write the failing test** — `backend/src/test/java/com/eduerp/modules/enrollment/usecase/CreateEnrollmentTest.java`:

```java
package com.eduerp.modules.enrollment.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.ClassFullException;
import com.eduerp.modules.enrollment.ClassNotActiveException;
import com.eduerp.modules.enrollment.ClassNotFoundException;
import com.eduerp.modules.enrollment.DuplicateActiveEnrollmentException;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.eduerp.modules.enrollment.StudentNotActiveException;
import com.eduerp.modules.enrollment.StudentProfileNotFoundException;
import com.eduerp.modules.enrollment.dto.CreateEnrollmentRequest;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.students.StudentsManagement;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CreateEnrollmentTest {

    private static final int MAX_SEATS = 12;

    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final StudentsManagement students = mock(StudentsManagement.class);
    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CreateEnrollment useCase = new CreateEnrollment(enrollments, students, courses, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID classBranchId = UUID.randomUUID();

    private CreateEnrollmentRequest request() {
        return new CreateEnrollmentRequest(studentProfileId, classId);
    }

    private void stubActiveStudent() {
        when(students.getProfile(studentProfileId)).thenReturn(Optional.of(
                new StudentsManagement.StudentSummaryResponse(studentProfileId, UUID.randomUUID(), true)));
    }

    private void stubActiveClass() {
        when(courses.getClassInfo(classId)).thenReturn(Optional.of(
                new CoursesManagement.ClassInfoResponse(classId, courseId, classBranchId, MAX_SEATS, true)));
    }

    private void stubSeatsTaken(long taken) {
        when(enrollments.countByClassIdAndStatus(classId, EnrollmentConstants.EnrollmentStatus.ACTIVE))
                .thenReturn(taken);
    }

    private void stubSaveEchoesBack() {
        when(enrollments.save(any(Enrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsAnUnknownStudentProfile() {
        when(students.getProfile(studentProfileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(StudentProfileNotFoundException.class);
    }

    @Test
    void rejectsADeactivatedStudentProfile() {
        when(students.getProfile(studentProfileId)).thenReturn(Optional.of(
                new StudentsManagement.StudentSummaryResponse(studentProfileId, UUID.randomUUID(), false)));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(StudentNotActiveException.class);
    }

    @Test
    void rejectsAnUnknownClass() {
        stubActiveStudent();
        when(courses.getClassInfo(classId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void rejectsADeactivatedClass() {
        stubActiveStudent();
        when(courses.getClassInfo(classId)).thenReturn(Optional.of(
                new CoursesManagement.ClassInfoResponse(classId, courseId, classBranchId, MAX_SEATS, false)));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(ClassNotActiveException.class);
    }

    /** Review Focus #2, ranh giới dưới: chỗ thứ {@code maxSeats - 1} vẫn ghi danh được. */
    @Test
    void acceptsTheSeatBeforeLast() {
        stubActiveStudent();
        stubActiveClass();
        stubSeatsTaken(MAX_SEATS - 2);
        stubSaveEchoesBack();

        assertThatCode(() -> useCase.execute(actorAccountId, actorBranchId, request())).doesNotThrowAnyException();
    }

    /** Review Focus #2, ranh giới chính xác: chỗ thứ {@code maxSeats} (tức đang có maxSeats-1 chỗ đã
     * chiếm) PHẢI ghi danh được - chặn ở đây là chặn sai một chỗ hợp lệ. */
    @Test
    void acceptsTheVeryLastSeat() {
        stubActiveStudent();
        stubActiveClass();
        stubSeatsTaken(MAX_SEATS - 1);
        stubSaveEchoesBack();

        assertThatCode(() -> useCase.execute(actorAccountId, actorBranchId, request())).doesNotThrowAnyException();
    }

    /** Review Focus #2, ranh giới trên: chỗ thứ {@code maxSeats + 1} bị chặn. */
    @Test
    void rejectsTheSeatPastCapacity() {
        stubActiveStudent();
        stubActiveClass();
        stubSeatsTaken(MAX_SEATS);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(ClassFullException.class)
                .hasMessageContaining(String.valueOf(MAX_SEATS));
    }

    /** Review Focus #1, tầng usecase: lần ghi danh thứ hai khi lần đầu còn ACTIVE bị chặn TRƯỚC khi
     * chạm DB, để người dùng nhận ENROLLMENT_DUPLICATE_ACTIVE chứ không phải lỗi ràng buộc thô. */
    @Test
    void rejectsASecondActiveEnrollmentForTheSameStudentAndClass() {
        stubActiveStudent();
        stubActiveClass();
        stubSeatsTaken(1);
        when(enrollments.existsByStudentProfileIdAndClassIdAndStatus(studentProfileId, classId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request()))
                .isInstanceOf(DuplicateActiveEnrollmentException.class);
    }

    @Test
    void snapshotsCourseAndBranchFromTheClassAndPublishesEnrollmentCreated() {
        stubActiveStudent();
        stubActiveClass();
        stubSeatsTaken(0);
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId, request());

        assertThat(response.studentProfileId()).isEqualTo(studentProfileId);
        assertThat(response.classId()).isEqualTo(classId);
        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.branchId()).isEqualTo(classBranchId);
        assertThat(response.status()).isEqualTo(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        assertThat(response.enrolledAt()).isNotNull();
        assertThat(response.withdrawnAt()).isNull();
        verify(events).publishEvent(any(EnrollmentEvents.EnrollmentCreated.class));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=CreateEnrollmentTest`
Expected: FAIL — compile error, `CreateEnrollment` chưa tồn tại.

- [ ] **Step 3: Write the usecase**

`modules/enrollment/usecase/CreateEnrollment.java`:

```java
package com.eduerp.modules.enrollment.usecase;

import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.ClassFullException;
import com.eduerp.modules.enrollment.ClassNotActiveException;
import com.eduerp.modules.enrollment.ClassNotFoundException;
import com.eduerp.modules.enrollment.DuplicateActiveEnrollmentException;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.eduerp.modules.enrollment.StudentNotActiveException;
import com.eduerp.modules.enrollment.StudentProfileNotFoundException;
import com.eduerp.modules.enrollment.dto.CreateEnrollmentRequest;
import com.eduerp.modules.enrollment.dto.EnrollmentResponse;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import com.eduerp.modules.students.StudentsManagement;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Thứ tự kiểm tra đúng theo spec mục 4: học viên → lớp → sĩ số → trùng ghi danh → lưu → publish. */
@Service
public class CreateEnrollment {

    private final EnrollmentRepository enrollments;
    private final StudentsManagement students;
    private final CoursesManagement courses;
    private final ApplicationEventPublisher events;

    CreateEnrollment(EnrollmentRepository enrollments, StudentsManagement students, CoursesManagement courses,
            ApplicationEventPublisher events) {
        this.enrollments = enrollments;
        this.students = students;
        this.courses = courses;
        this.events = events;
    }

    @Transactional
    public EnrollmentResponse execute(UUID actorAccountId, UUID actorBranchId, CreateEnrollmentRequest request) {
        var student = students.getProfile(request.studentProfileId())
                .orElseThrow(() -> new StudentProfileNotFoundException(request.studentProfileId()));
        if (!student.active()) {
            throw new StudentNotActiveException(request.studentProfileId());
        }

        var classInfo = courses.getClassInfo(request.classId())
                .orElseThrow(() -> new ClassNotFoundException(request.classId()));
        if (!classInfo.active()) {
            throw new ClassNotActiveException(request.classId());
        }

        // >= maxSeats: đúng ranh giới ở Review Focus #2 - khi đã có maxSeats chỗ ACTIVE thì chỗ tiếp
        // theo là chỗ thứ maxSeats + 1, không còn chỗ.
        var activeSeats = enrollments.countByClassIdAndStatus(request.classId(),
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        if (activeSeats >= classInfo.maxSeats()) {
            throw new ClassFullException(request.classId(), classInfo.maxSeats());
        }

        // Lớp phòng thủ thứ nhất của Review Focus #1 - partial unique index trong V17 là lớp thứ hai.
        if (enrollments.existsByStudentProfileIdAndClassIdAndStatus(request.studentProfileId(), request.classId(),
                EnrollmentConstants.EnrollmentStatus.ACTIVE)) {
            throw new DuplicateActiveEnrollmentException(request.studentProfileId(), request.classId());
        }

        var saved = enrollments.save(new Enrollment(request.studentProfileId(), request.classId(),
                classInfo.courseId(), classInfo.branchId(), actorAccountId));
        events.publishEvent(new EnrollmentEvents.EnrollmentCreated(saved.getId(), actorAccountId, actorBranchId));
        return toResponse(saved);
    }

    /** Dùng lại ở WithdrawEnrollment/CompleteEnrollment/ListEnrollments - một chỗ map duy nhất. */
    static EnrollmentResponse toResponse(Enrollment enrollment) {
        return new EnrollmentResponse(enrollment.getId(), enrollment.getStudentProfileId(), enrollment.getClassId(),
                enrollment.getCourseId(), enrollment.getBranchId(), enrollment.getStatus(),
                enrollment.getEnrolledAt(), enrollment.getWithdrawnAt());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=CreateEnrollmentTest`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/enrollment/usecase/CreateEnrollment.java \
  backend/src/test/java/com/eduerp/modules/enrollment/usecase/CreateEnrollmentTest.java
git commit -m "feat(enrollment): add create enrollment use case with seat and duplicate guards"
```

---

### Task 7: `WithdrawEnrollment`, `CompleteEnrollment`, `ListEnrollments`, `EnrollmentManagement`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/usecase/WithdrawEnrollment.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/usecase/CompleteEnrollment.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/usecase/GetEnrollment.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/usecase/ListEnrollments.java`
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/EnrollmentManagement.java`
- Test: `backend/src/test/java/com/eduerp/modules/enrollment/usecase/EnrollmentTransitionsTest.java` (create)
- Test: `backend/src/test/java/com/eduerp/modules/enrollment/usecase/ListEnrollmentsTest.java` (create)

**Interfaces:**
- Consumes: `CreateEnrollment.toResponse` (Task 6), `EnrollmentRepository` (Task 5), exceptions/events/DTO (Task 4).
- Produces:
  `WithdrawEnrollment.execute(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId): void`;
  `CompleteEnrollment.execute(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId): void`;
  `GetEnrollment.execute(UUID enrollmentId): EnrollmentResponse`;
  `ListEnrollments.execute(Pageable pageable, UUID studentProfileId, UUID classId): PageResponse<EnrollmentResponse>`;
  `EnrollmentManagement.EnrollmentSummaryResponse(UUID enrollmentId, UUID studentProfileId, UUID courseId, UUID branchId, EnrollmentConstants.EnrollmentStatus status)`;
  `EnrollmentManagement.getEnrollment(UUID enrollmentId): Optional<EnrollmentSummaryResponse>`.

- [ ] **Step 1: Write the failing transition test** — `backend/src/test/java/com/eduerp/modules/enrollment/usecase/EnrollmentTransitionsTest.java`:

```java
package com.eduerp.modules.enrollment.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.eduerp.modules.enrollment.EnrollmentNotActiveException;
import com.eduerp.modules.enrollment.EnrollmentNotFoundException;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class EnrollmentTransitionsTest {

    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final WithdrawEnrollment withdraw = new WithdrawEnrollment(enrollments, events);
    private final CompleteEnrollment complete = new CompleteEnrollment(enrollments, events);
    private final GetEnrollment get = new GetEnrollment(enrollments);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();

    private Enrollment activeEnrollment() {
        return new Enrollment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                actorAccountId);
    }

    @Test
    void withdrawRejectsAnUnknownEnrollment() {
        var enrollmentId = UUID.randomUUID();
        when(enrollments.findById(enrollmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> withdraw.execute(enrollmentId, actorAccountId, actorBranchId))
                .isInstanceOf(EnrollmentNotFoundException.class);
    }

    @Test
    void withdrawMovesAnActiveEnrollmentToWithdrawnAndStampsTheTime() {
        var enrollment = activeEnrollment();
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        withdraw.execute(enrollment.getId(), actorAccountId, actorBranchId);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentConstants.EnrollmentStatus.WITHDRAWN);
        assertThat(enrollment.getWithdrawnAt()).isNotNull();
        verify(events).publishEvent(any(EnrollmentEvents.EnrollmentWithdrawn.class));
    }

    @Test
    void withdrawRefusesAnEnrollmentThatIsNoLongerActive() {
        var enrollment = activeEnrollment();
        enrollment.complete();
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> withdraw.execute(enrollment.getId(), actorAccountId, actorBranchId))
                .isInstanceOf(EnrollmentNotActiveException.class);
    }

    @Test
    void completeMovesAnActiveEnrollmentToCompletedWithoutAWithdrawalTime() {
        var enrollment = activeEnrollment();
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        complete.execute(enrollment.getId(), actorAccountId, actorBranchId);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentConstants.EnrollmentStatus.COMPLETED);
        assertThat(enrollment.getWithdrawnAt()).isNull();
        verify(events).publishEvent(any(EnrollmentEvents.EnrollmentCompleted.class));
    }

    @Test
    void completeRefusesAnEnrollmentThatIsAlreadyWithdrawn() {
        var enrollment = activeEnrollment();
        enrollment.withdraw();
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> complete.execute(enrollment.getId(), actorAccountId, actorBranchId))
                .isInstanceOf(EnrollmentNotActiveException.class);
    }

    @Test
    void getReturnsTheEnrollmentResponse() {
        var enrollment = activeEnrollment();
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        var response = get.execute(enrollment.getId());

        assertThat(response.id()).isEqualTo(enrollment.getId());
        assertThat(response.status()).isEqualTo(EnrollmentConstants.EnrollmentStatus.ACTIVE);
    }

    @Test
    void getRejectsAnUnknownEnrollment() {
        var enrollmentId = UUID.randomUUID();
        when(enrollments.findById(enrollmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> get.execute(enrollmentId)).isInstanceOf(EnrollmentNotFoundException.class);
    }
}
```

- [ ] **Step 2: Write the failing list test** — `backend/src/test/java/com/eduerp/modules/enrollment/usecase/ListEnrollmentsTest.java`:

```java
package com.eduerp.modules.enrollment.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ListEnrollmentsTest {

    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final ListEnrollments useCase = new ListEnrollments(enrollments);

    @Test
    void passesBothOptionalFiltersStraightToTheRepositoryAndMapsThePage() {
        var studentProfileId = UUID.randomUUID();
        var classId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 20);
        var enrollment = new Enrollment(studentProfileId, classId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());
        when(enrollments.search(studentProfileId, classId, pageable))
                .thenReturn(new PageImpl<>(List.of(enrollment), pageable, 1));

        var page = useCase.execute(pageable, studentProfileId, classId);

        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.items()).singleElement()
                .satisfies(item -> assertThat(item.studentProfileId()).isEqualTo(studentProfileId));
    }

    @Test
    void acceptsNullFiltersForAnUnfilteredList() {
        var pageable = PageRequest.of(0, 20);
        when(enrollments.search(null, null, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(useCase.execute(pageable, null, null).items()).isEmpty();
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd backend && mvn test -Dtest='EnrollmentTransitionsTest,ListEnrollmentsTest'`
Expected: FAIL — compile error, 4 class usecase chưa tồn tại.

- [ ] **Step 4: Write `WithdrawEnrollment`**

```java
package com.eduerp.modules.enrollment.usecase;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.eduerp.modules.enrollment.EnrollmentNotActiveException;
import com.eduerp.modules.enrollment.EnrollmentNotFoundException;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rút khỏi lớp giữ nguyên mọi hoá đơn đã phát hành (spec mục 12) - ở đây không có bất kỳ lệnh gọi
 * nào sang modules.billing, và cũng không được phép có (enrollment không biết billing tồn tại). */
@Service
public class WithdrawEnrollment {

    private final EnrollmentRepository enrollments;
    private final ApplicationEventPublisher events;

    WithdrawEnrollment(EnrollmentRepository enrollments, ApplicationEventPublisher events) {
        this.enrollments = enrollments;
        this.events = events;
    }

    @Transactional
    public void execute(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId) {
        var enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        if (enrollment.getStatus() != EnrollmentConstants.EnrollmentStatus.ACTIVE) {
            throw new EnrollmentNotActiveException(enrollmentId);
        }
        enrollment.withdraw();
        events.publishEvent(new EnrollmentEvents.EnrollmentWithdrawn(enrollmentId, actorAccountId, actorBranchId));
    }
}
```

- [ ] **Step 5: Write `CompleteEnrollment`**

```java
package com.eduerp.modules.enrollment.usecase;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.eduerp.modules.enrollment.EnrollmentNotActiveException;
import com.eduerp.modules.enrollment.EnrollmentNotFoundException;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompleteEnrollment {

    private final EnrollmentRepository enrollments;
    private final ApplicationEventPublisher events;

    CompleteEnrollment(EnrollmentRepository enrollments, ApplicationEventPublisher events) {
        this.enrollments = enrollments;
        this.events = events;
    }

    @Transactional
    public void execute(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId) {
        var enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        if (enrollment.getStatus() != EnrollmentConstants.EnrollmentStatus.ACTIVE) {
            throw new EnrollmentNotActiveException(enrollmentId);
        }
        enrollment.complete();
        events.publishEvent(new EnrollmentEvents.EnrollmentCompleted(enrollmentId, actorAccountId, actorBranchId));
    }
}
```

- [ ] **Step 6: Write `GetEnrollment` and `ListEnrollments`**

`GetEnrollment.java`:

```java
package com.eduerp.modules.enrollment.usecase;

import com.eduerp.modules.enrollment.EnrollmentNotFoundException;
import com.eduerp.modules.enrollment.dto.EnrollmentResponse;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetEnrollment {

    private final EnrollmentRepository enrollments;

    GetEnrollment(EnrollmentRepository enrollments) {
        this.enrollments = enrollments;
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse execute(UUID enrollmentId) {
        return enrollments.findById(enrollmentId).map(CreateEnrollment::toResponse)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
    }
}
```

`ListEnrollments.java`:

```java
package com.eduerp.modules.enrollment.usecase;

import com.eduerp.modules.enrollment.dto.EnrollmentResponse;
import com.eduerp.shared.PageResponse;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hai filter optional dồn vào một query ở repository - usecase không rẽ nhánh nào (complexity 1). */
@Service
public class ListEnrollments {

    private final EnrollmentRepository enrollments;

    ListEnrollments(EnrollmentRepository enrollments) {
        this.enrollments = enrollments;
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> execute(Pageable pageable, UUID studentProfileId, UUID classId) {
        return PageResponse.of(enrollments.search(studentProfileId, classId, pageable)
                .map(CreateEnrollment::toResponse));
    }
}
```

- [ ] **Step 7: Write the facade**

`modules/enrollment/EnrollmentManagement.java`:

```java
package com.eduerp.modules.enrollment;

import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module enrollment - type DUY NHẤT module khác được phép gọi (rule #1).
 * {@code modules.billing} gọi {@link #getEnrollment(UUID)} để chốt snapshot student/course/branch và
 * để chặn phát hành hoá đơn mới cho ghi danh không còn {@code ACTIVE} (spec mục 5).
 */
@Service
public class EnrollmentManagement {

    public record EnrollmentSummaryResponse(UUID enrollmentId, UUID studentProfileId, UUID courseId, UUID branchId,
            EnrollmentConstants.EnrollmentStatus status) {
    }

    private final EnrollmentRepository enrollments;

    EnrollmentManagement(EnrollmentRepository enrollments) {
        this.enrollments = enrollments;
    }

    @Transactional(readOnly = true)
    public Optional<EnrollmentSummaryResponse> getEnrollment(UUID enrollmentId) {
        return enrollments.findById(enrollmentId).map(enrollment -> new EnrollmentSummaryResponse(
                enrollment.getId(), enrollment.getStudentProfileId(), enrollment.getCourseId(),
                enrollment.getBranchId(), enrollment.getStatus()));
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest='EnrollmentTransitionsTest,ListEnrollmentsTest,ModularityTests'`
Expected: PASS (7 + 2 + 2 tests).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/enrollment \
  backend/src/test/java/com/eduerp/modules/enrollment
git commit -m "feat(enrollment): add withdraw, complete, read use cases and module facade"
```

---

### Task 8: `integrations.payment` scaffold — enum, records, interface, properties, `application.yml`

**Files:**
- Create: `backend/src/main/java/com/eduerp/integrations/payment/package-info.java`
- Create: `backend/src/main/java/com/eduerp/integrations/payment/PaymentGatewayType.java`
- Create: `backend/src/main/java/com/eduerp/integrations/payment/PaymentRequest.java`
- Create: `backend/src/main/java/com/eduerp/integrations/payment/PaymentUrlResult.java`
- Create: `backend/src/main/java/com/eduerp/integrations/payment/PaymentCallbackResult.java`
- Create: `backend/src/main/java/com/eduerp/integrations/payment/PaymentSignatureException.java`
- Create: `backend/src/main/java/com/eduerp/integrations/payment/PaymentGatewayClient.java`
- Create: `backend/src/main/java/com/eduerp/integrations/payment/MomoProperties.java`
- Create: `backend/src/main/java/com/eduerp/integrations/payment/VnPayProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/java/com/eduerp/ModularityTests.java`
- Test: `backend/src/test/java/com/eduerp/integrations/payment/PaymentContractTest.java` (create)

**Interfaces:**
- Consumes: nothing.
- Produces:
  `PaymentGatewayType { MOMO, VNPAY }`;
  `PaymentRequest(String orderId, BigDecimal amount, String orderInfo, String returnUrl, String ipnUrl)` + static factory `PaymentRequest.withGatewayDefaults(String orderId, BigDecimal amount, String orderInfo)`;
  `PaymentUrlResult(String payUrl, String gatewayOrderId)`;
  `PaymentCallbackResult(String orderId, boolean success, BigDecimal amount, String rawMessage)`;
  `PaymentSignatureException extends RuntimeException` với constructor `(PaymentGatewayType gateway)`;
  `interface PaymentGatewayClient { PaymentGatewayType type(); PaymentUrlResult createPaymentUrl(PaymentRequest); PaymentCallbackResult verifyCallback(Map<String,String>); }`;
  `MomoProperties(String partnerCode, String accessKey, String secretKey, String endpoint, String redirectUrl, String ipnUrl)` (prefix `payment.momo`);
  `VnPayProperties(String tmnCode, String hashSecret, String payUrl, String returnUrl, String ipnUrl)` (prefix `payment.vnpay`).

- [ ] **Step 1: Write the failing test** — `backend/src/test/java/com/eduerp/integrations/payment/PaymentContractTest.java`:

```java
package com.eduerp.integrations.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Hợp đồng của integrations.payment: chỉ 2 cổng, và PaymentRequest để trống URL nghĩa là "dùng cấu
 * hình của chính client" (modules.billing không đọc được MomoProperties/VnPayProperties). */
class PaymentContractTest {

    @Test
    void supportsExactlyTwoGateways() {
        assertThat(PaymentGatewayType.values())
                .containsExactly(PaymentGatewayType.MOMO, PaymentGatewayType.VNPAY);
    }

    @Test
    void withGatewayDefaultsLeavesBothUrlsEmptySoTheClientUsesItsOwnConfiguration() {
        var request = PaymentRequest.withGatewayDefaults("order-1", new BigDecimal("500000"), "Hoc phi dot 1");

        assertThat(request.orderId()).isEqualTo("order-1");
        assertThat(request.amount()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(request.orderInfo()).isEqualTo("Hoc phi dot 1");
        assertThat(request.returnUrl()).isNull();
        assertThat(request.ipnUrl()).isNull();
    }

    @Test
    void signatureExceptionNamesTheGatewayWithoutLeakingAnyOrderId() {
        var ex = new PaymentSignatureException(PaymentGatewayType.VNPAY);

        assertThat(ex.gateway()).isEqualTo(PaymentGatewayType.VNPAY);
        assertThat(ex.getMessage()).contains("VNPAY").doesNotContain("order");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=PaymentContractTest`
Expected: FAIL — compile error, package `com.eduerp.integrations.payment` chưa tồn tại.

- [ ] **Step 3: Create `package-info` and the value types**

`integrations/payment/package-info.java`:

```java
/**
 * Hai cổng thanh toán Việt Nam (MoMo, VNPay) sau một interface duy nhất. Cơ chế thuần: không biết
 * hoá đơn/ghi danh/học phí là gì - module nghiệp vụ ({@code modules.billing}) tự đặt
 * {@code orderId}, tự quyết số tiền, tự lưu bản ghi {@code Payment}. Mirror
 * {@code integrations.storage}: một interface + nhiều implementation, KHÔNG phụ thuộc bất kỳ
 * {@code modules.*} nào (spec mục 6).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payment Gateways")
package com.eduerp.integrations.payment;
```

`PaymentGatewayType.java`:

```java
package com.eduerp.integrations.payment;

public enum PaymentGatewayType {
    MOMO, VNPAY
}
```

`PaymentRequest.java`:

```java
package com.eduerp.integrations.payment;

import java.math.BigDecimal;

/**
 * Yêu cầu tạo link thanh toán. {@code amount} là VND nguyên (scale 0) - VNPay tự nhân 100 bên trong
 * client vì đơn vị của nó là xu.
 *
 * <p>{@code returnUrl}/{@code ipnUrl} để {@code null}/trống nghĩa là "dùng cấu hình của chính
 * client" ({@code MomoProperties}/{@code VnPayProperties}): {@code modules.billing} không đọc được
 * hai property đó (chúng là nội bộ của integration), nên {@link #withGatewayDefaults} là cách gọi
 * bình thường. Hai field vẫn tồn tại để một lời gọi đặc biệt (sandbox, test thủ công) ghi đè được.
 */
public record PaymentRequest(String orderId, BigDecimal amount, String orderInfo, String returnUrl, String ipnUrl) {

    public static PaymentRequest withGatewayDefaults(String orderId, BigDecimal amount, String orderInfo) {
        return new PaymentRequest(orderId, amount, orderInfo, null, null);
    }
}
```

`PaymentUrlResult.java`:

```java
package com.eduerp.integrations.payment;

/** {@code gatewayOrderId} là mã mà cổng dùng để gọi IPN về - billing lưu nó vào
 * {@code Payment.gatewayTransactionId} để tra cứu lúc nhận callback. */
public record PaymentUrlResult(String payUrl, String gatewayOrderId) {
}
```

`PaymentCallbackResult.java`:

```java
package com.eduerp.integrations.payment;

import java.math.BigDecimal;

/** Kết quả sau khi chữ ký đã được xác thực. {@code success=false} = cổng báo giao dịch thất bại
 * (khác hoàn toàn với chữ ký sai - trường hợp đó ném {@link PaymentSignatureException}). */
public record PaymentCallbackResult(String orderId, boolean success, BigDecimal amount, String rawMessage) {
}
```

`PaymentSignatureException.java`:

```java
package com.eduerp.integrations.payment;

/**
 * Chữ ký HMAC của callback không khớp. KHÔNG phải {@code AppException}: {@code integrations.payment}
 * không được phụ thuộc {@code modules.billing} nên không thể ném
 * {@code BillingException.InvalidCallbackSignatureException} tại đây.
 * {@code modules.billing.usecase.HandlePaymentCallback} bắt lỗi này và ném lại
 * {@code InvalidCallbackSignatureException} như spec mục 5 bước 1 mô tả.
 *
 * <p>Message cố tình KHÔNG chứa orderId hay bất kỳ tham số nào của request - không để chuỗi này rơi
 * vào log/response rồi thành oracle (Review Focus #5).
 */
public class PaymentSignatureException extends RuntimeException {

    private final PaymentGatewayType gateway;

    public PaymentSignatureException(PaymentGatewayType gateway) {
        super("Chữ ký callback của cổng " + gateway + " không hợp lệ");
        this.gateway = gateway;
    }

    public PaymentGatewayType gateway() {
        return gateway;
    }
}
```

`PaymentGatewayClient.java`:

```java
package com.eduerp.integrations.payment;

import java.util.Map;

/**
 * Một cổng thanh toán. Mirror {@code StorageClient} ở mức trừu tượng: cơ chế thuần, không biết
 * nghiệp vụ. {@code modules.billing.internal.PaymentGatewayClientResolver} nhận
 * {@code List<PaymentGatewayClient>} từ Spring rồi map theo {@link #type()}.
 */
public interface PaymentGatewayClient {

    PaymentGatewayType type();

    PaymentUrlResult createPaymentUrl(PaymentRequest request);

    /**
     * Xác thực chữ ký rồi dịch tham số thô của cổng sang {@link PaymentCallbackResult}.
     *
     * @throws PaymentSignatureException khi chữ ký không khớp - nơi gọi phải coi request đó là giả.
     */
    PaymentCallbackResult verifyCallback(Map<String, String> rawParams);
}
```

- [ ] **Step 4: Create the two properties records**

`MomoProperties.java`:

```java
package com.eduerp.integrations.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Giá trị sandbox nằm trong {@code application.yml}, secret thật đọc từ biến môi trường - không
 * hardcode secret trong code (spec mục 6), mirror cách {@code storage} đã làm ở Phân hệ 2.6. */
@ConfigurationProperties(prefix = "payment.momo")
public record MomoProperties(String partnerCode, String accessKey, String secretKey, String endpoint,
        String redirectUrl, String ipnUrl) {
}
```

`VnPayProperties.java`:

```java
package com.eduerp.integrations.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.vnpay")
public record VnPayProperties(String tmnCode, String hashSecret, String payUrl, String returnUrl, String ipnUrl) {
}
```

- [ ] **Step 5: Add the config blocks to `application.yml`**

Chèn ngay sau block `storage:` (trước `server:`):

```yaml
payment:
  momo:
    partner-code: ${MOMO_PARTNER_CODE:MOMOSANDBOX}
    access-key: ${MOMO_ACCESS_KEY:}
    secret-key: ${MOMO_SECRET_KEY:}
    endpoint: ${MOMO_ENDPOINT:https://test-payment.momo.vn/v2/gateway/api/create}
    redirect-url: ${MOMO_REDIRECT_URL:http://localhost:5173/payment/return/momo}
    ipn-url: ${MOMO_IPN_URL:http://localhost:8080/api/billing/payments/callback/momo}
  vnpay:
    tmn-code: ${VNPAY_TMN_CODE:}
    hash-secret: ${VNPAY_HASH_SECRET:}
    pay-url: ${VNPAY_PAY_URL:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}
    return-url: ${VNPAY_RETURN_URL:http://localhost:5173/payment/return/vnpay}
    ipn-url: ${VNPAY_IPN_URL:http://localhost:8080/api/billing/payments/callback/vnpay}
```

- [ ] **Step 6: Register the module in `ModularityTests`**

Thêm `"integrations.payment"` vào `containsExactlyInAnyOrder` ngay sau `"integrations.notification"`:

```java
        assertThat(detected).containsExactlyInAnyOrder("core", "shared", "modules.identity", "modules.access",
                "modules.organization", "modules.audit", "modules.dashboard", "modules.courses",
                "modules.teachers", "modules.students", "modules.payroll", "modules.enrollment",
                "integrations.cache", "integrations.mail", "integrations.notification", "integrations.payment",
                "integrations.storage");
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest='PaymentContractTest,ModularityTests'`
Expected: PASS (3 + 2 tests).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/eduerp/integrations/payment backend/src/main/resources/application.yml \
  backend/src/test/java/com/eduerp/integrations/payment backend/src/test/java/com/eduerp/ModularityTests.java
git commit -m "feat(payment): add payment gateway abstraction, value types and sandbox configuration"
```

---

### Task 9: `VnPayPaymentGatewayClient` — HMAC-SHA512 trên tham số sắp theo alphabet

**Files:**
- Create: `backend/src/main/java/com/eduerp/integrations/payment/VnPayPaymentGatewayClient.java`
- Test: `backend/src/test/java/com/eduerp/integrations/payment/VnPayPaymentGatewayClientTest.java` (create)

**Interfaces:**
- Consumes: `PaymentGatewayClient`, `PaymentRequest`, `PaymentUrlResult`, `PaymentCallbackResult`, `PaymentSignatureException`, `VnPayProperties` (Task 8).
- Produces:
  `VnPayPaymentGatewayClient(VnPayProperties properties)` (`@Component`) và constructor test `VnPayPaymentGatewayClient(VnPayProperties properties, Clock clock)`;
  static package-private `hashData(SortedMap<String,String> params): String`;
  static package-private `hmacSha512Hex(String secret, String data): String`;
  `createPaymentUrl` trả `PaymentUrlResult(payUrl + "?" + hashData + "&vnp_SecureHash=" + hash, request.orderId())`.

> **Công thức chốt tại plan (spec mục 2 + mã mẫu VNPay 2.1.0):** sắp key theo alphabet tăng dần →
> ghép `key=URLEncoder.encode(value, UTF_8)` bằng `&` → HMAC-SHA512 với `vnp_HashSecret` → hex chữ
> thường. Chuỗi dùng để ký và chuỗi query gửi đi là MỘT chuỗi duy nhất (cùng phép encode), nên không
> thể lệch nhau. Verify: bỏ `vnp_SecureHash` + `vnp_SecureHashType` khỏi tham số nhận được, hash lại
> phần còn lại bằng đúng quy tắc trên, so khớp không phân biệt hoa/thường.

- [ ] **Step 1: Write the failing test** — `backend/src/test/java/com/eduerp/integrations/payment/VnPayPaymentGatewayClientTest.java`:

```java
package com.eduerp.integrations.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Pin thuật toán, không chỉ test round-trip sign→verify (spec mục 11): nếu ký và verify cùng sai một
 * kiểu thì round-trip vẫn xanh. Mỗi assert dưới đây so với MỘT TRONG HAI nguồn độc lập:
 * <ol>
 *   <li>hex literal cố định, tính sẵn bằng công cụ ngoài dự án (Python {@code hmac} + {@code hashlib});</li>
 *   <li>{@link #referenceHmacSha512} - cài lại HMAC-SHA512 + hex ngay trong test bằng
 *       {@code javax.crypto.Mac}, không gọi code production.</li>
 * </ol>
 */
class VnPayPaymentGatewayClientTest {

    private static final String HASH_SECRET = "VNPAYSECRET123";
    private static final String TMN_CODE = "TMN001";
    private static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private static final String RETURN_URL = "https://eduerp.local/payment/return/vnpay";
    private static final String IPN_URL = "https://eduerp.local/api/billing/payments/callback/vnpay";

    /** 2026-10-03T01:02:03 giờ hệ thống của test - khớp vnp_CreateDate 20261003010203 dưới đây. */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-02T18:02:03Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private final VnPayProperties properties =
            new VnPayProperties(TMN_CODE, HASH_SECRET, PAY_URL, RETURN_URL, IPN_URL);
    private final VnPayPaymentGatewayClient client = new VnPayPaymentGatewayClient(properties, FIXED_CLOCK);

    /** Cài lại HMAC-SHA512 + hex từ đầu, không dùng một dòng code production nào. */
    private static String referenceHmacSha512(String secret, String data) {
        try {
            var mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            var bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(bytes.length * 2);
            for (var b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void typeIsVnpay() {
        assertThat(client.type()).isEqualTo(PaymentGatewayType.VNPAY);
    }

    /** Nguồn độc lập #1: hex literal tính sẵn ngoài dự án cho đúng chuỗi hashData ở assert đầu. */
    @Test
    void hashDataAndSignatureMatchThePinnedVector() {
        var params = new TreeMap<String, String>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", TMN_CODE);
        params.put("vnp_Amount", "50000000");
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", "order-1");
        params.put("vnp_OrderInfo", "Hoc phi dot 1");
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", RETURN_URL);
        params.put("vnp_IpAddr", "127.0.0.1");
        params.put("vnp_CreateDate", "20261003010203");

        var hashData = VnPayPaymentGatewayClient.hashData(params);

        assertThat(hashData).isEqualTo("vnp_Amount=50000000&vnp_Command=pay&vnp_CreateDate=20261003010203"
                + "&vnp_CurrCode=VND&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Hoc+phi+dot+1"
                + "&vnp_OrderType=other"
                + "&vnp_ReturnUrl=https%3A%2F%2Feduerp.local%2Fpayment%2Freturn%2Fvnpay"
                + "&vnp_TmnCode=TMN001&vnp_TxnRef=order-1&vnp_Version=2.1.0");
        assertThat(VnPayPaymentGatewayClient.hmacSha512Hex(HASH_SECRET, hashData)).isEqualTo(
                "562c7e85d55fd0405e85fa3428da53a04a1a84cf3b76048d11354728f2ca8fb4"
                        + "6f4516888217181583e7b1170c4d53305efccd69214b7732d6fb8a4e12fe3f2a");
    }

    /** Nguồn độc lập #2: HMAC tính lại trong test bằng javax.crypto.Mac. */
    @Test
    void hmacImplementationAgreesWithAnIndependentJdkComputation() {
        var data = "vnp_Amount=1&vnp_TxnRef=abc";

        assertThat(VnPayPaymentGatewayClient.hmacSha512Hex(HASH_SECRET, data))
                .isEqualTo(referenceHmacSha512(HASH_SECRET, data));
    }

    @Test
    void createPaymentUrlMultipliesAmountByOneHundredAndAppendsTheSecureHash() {
        var result = client.createPaymentUrl(
                PaymentRequest.withGatewayDefaults("order-1", new BigDecimal("500000"), "Hoc phi dot 1"));

        assertThat(result.gatewayOrderId()).isEqualTo("order-1");
        assertThat(result.payUrl()).startsWith(PAY_URL + "?");
        // Đơn vị của VNPay là xu: 500000 VND -> 50000000.
        assertThat(result.payUrl()).contains("vnp_Amount=50000000");
        assertThat(result.payUrl()).contains("vnp_TxnRef=order-1");
        assertThat(result.payUrl()).contains("vnp_CreateDate=20261003010203");
        assertThat(result.payUrl()).contains("vnp_SecureHash="
                + "562c7e85d55fd0405e85fa3428da53a04a1a84cf3b76048d11354728f2ca8fb4"
                + "6f4516888217181583e7b1170c4d53305efccd69214b7732d6fb8a4e12fe3f2a");
    }

    private Map<String, String> validCallbackParams() {
        var params = new HashMap<String, String>();
        params.put("vnp_Amount", "50000000");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_OrderInfo", "Hoc phi dot 1");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", TMN_CODE);
        params.put("vnp_TransactionNo", "14012345");
        params.put("vnp_TxnRef", "order-1");
        params.put("vnp_SecureHash",
                "a55d8e2ff60d85c8e41391d39c208b23becd89e33847fee2a6c0eff17aa132a7"
                        + "d45215e64662fb81ded05fd5cd7699d1ce15674e46bbb0153c7d63578be97a9e");
        return params;
    }

    @Test
    void verifyCallbackAcceptsThePinnedSignatureAndConvertsAmountBackToVnd() {
        var result = client.verifyCallback(validCallbackParams());

        assertThat(result.orderId()).isEqualTo("order-1");
        assertThat(result.success()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(result.rawMessage()).contains("00");
    }

    /** vnp_SecureHashType cũng phải bị loại khỏi chuỗi hash, nếu không chữ ký thật sẽ bị từ chối. */
    @Test
    void verifyCallbackIgnoresTheSecureHashTypeParameter() {
        var params = validCallbackParams();
        params.put("vnp_SecureHashType", "SHA512");

        assertThat(client.verifyCallback(params).success()).isTrue();
    }

    @Test
    void verifyCallbackAcceptsAnUppercaseSignature() {
        var params = validCallbackParams();
        params.put("vnp_SecureHash", params.get("vnp_SecureHash").toUpperCase());

        assertThat(client.verifyCallback(params).success()).isTrue();
    }

    @Test
    void verifyCallbackReportsAFailedTransactionWithoutRejectingTheSignature() {
        var params = new HashMap<String, String>();
        params.put("vnp_Amount", "50000000");
        params.put("vnp_ResponseCode", "24");
        params.put("vnp_TxnRef", "order-1");
        var hashData = VnPayPaymentGatewayClient.hashData(new TreeMap<>(params));
        params.put("vnp_SecureHash", referenceHmacSha512(HASH_SECRET, hashData));

        var result = client.verifyCallback(params);

        assertThat(result.success()).isFalse();
        assertThat(result.orderId()).isEqualTo("order-1");
    }

    @Test
    void verifyCallbackRejectsATamperedAmount() {
        var params = validCallbackParams();
        params.put("vnp_Amount", "1");

        assertThatThrownBy(() -> client.verifyCallback(params))
                .isInstanceOf(PaymentSignatureException.class);
    }

    @Test
    void verifyCallbackRejectsAMissingSignature() {
        var params = validCallbackParams();
        params.remove("vnp_SecureHash");

        assertThatThrownBy(() -> client.verifyCallback(params))
                .isInstanceOf(PaymentSignatureException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=VnPayPaymentGatewayClientTest`
Expected: FAIL — compile error, `VnPayPaymentGatewayClient` chưa tồn tại.

- [ ] **Step 3: Write the client**

`integrations/payment/VnPayPaymentGatewayClient.java`:

```java
package com.eduerp.integrations.payment;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * VNPay 2.1.0 (spec mục 2). Công thức chốt: sắp key theo alphabet → ghép
 * {@code key=URLEncoder.encode(value)} bằng {@code &} → HMAC-SHA512 với {@code vnp_HashSecret} → hex
 * chữ thường. Chuỗi ký và chuỗi query gửi đi là cùng một chuỗi, nên không thể lệch encode.
 */
@Component
class VnPayPaymentGatewayClient implements PaymentGatewayClient {

    /** Tên tham số của VNPay - chuỗi chỉ dùng trong class này nên gom vào inner class tại đây. */
    private static final class Params {
        private Params() {
        }

        static final String VERSION = "vnp_Version";
        static final String COMMAND = "vnp_Command";
        static final String TMN_CODE = "vnp_TmnCode";
        static final String AMOUNT = "vnp_Amount";
        static final String CURR_CODE = "vnp_CurrCode";
        static final String TXN_REF = "vnp_TxnRef";
        static final String ORDER_INFO = "vnp_OrderInfo";
        static final String ORDER_TYPE = "vnp_OrderType";
        static final String LOCALE = "vnp_Locale";
        static final String RETURN_URL = "vnp_ReturnUrl";
        static final String IP_ADDR = "vnp_IpAddr";
        static final String CREATE_DATE = "vnp_CreateDate";
        static final String SECURE_HASH = "vnp_SecureHash";
        static final String SECURE_HASH_TYPE = "vnp_SecureHashType";
        static final String RESPONSE_CODE = "vnp_ResponseCode";
    }

    private static final class Values {
        private Values() {
        }

        static final String VERSION = "2.1.0";
        static final String COMMAND = "pay";
        static final String CURRENCY = "VND";
        static final String ORDER_TYPE = "other";
        static final String LOCALE = "vn";
        static final String SUCCESS_RESPONSE_CODE = "00";
        static final String HMAC_ALGORITHM = "HmacSHA512";
        /** VNPay ghi nhận IP của máy gọi API. {@code PaymentRequest} (hợp đồng chốt ở spec mục 6)
         * không mang IP người trả tiền, nên dùng IP loopback của server - VNPay chỉ log, không dùng
         * giá trị này để chặn giao dịch. */
        static final String SERVER_IP = "127.0.0.1";
        /** VNPay đếm theo đơn vị xu, nên mọi số tiền VND phải nhân 100 trước khi ký. */
        static final BigDecimal AMOUNT_MULTIPLIER = new BigDecimal("100");
    }

    private static final DateTimeFormatter CREATE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnPayProperties properties;
    private final Clock clock;

    VnPayPaymentGatewayClient(VnPayProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    /** Constructor cho test: {@code vnp_CreateDate} phụ thuộc giờ nên phải chốt được Clock. */
    VnPayPaymentGatewayClient(VnPayProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public PaymentGatewayType type() {
        return PaymentGatewayType.VNPAY;
    }

    @Override
    public PaymentUrlResult createPaymentUrl(PaymentRequest request) {
        var returnUrl = request.returnUrl() == null || request.returnUrl().isBlank()
                ? properties.returnUrl() : request.returnUrl();
        SortedMap<String, String> params = new TreeMap<>();
        params.put(Params.VERSION, Values.VERSION);
        params.put(Params.COMMAND, Values.COMMAND);
        params.put(Params.TMN_CODE, properties.tmnCode());
        params.put(Params.AMOUNT, request.amount().multiply(Values.AMOUNT_MULTIPLIER).toBigInteger().toString());
        params.put(Params.CURR_CODE, Values.CURRENCY);
        params.put(Params.TXN_REF, request.orderId());
        params.put(Params.ORDER_INFO, request.orderInfo());
        params.put(Params.ORDER_TYPE, Values.ORDER_TYPE);
        params.put(Params.LOCALE, Values.LOCALE);
        params.put(Params.RETURN_URL, returnUrl);
        params.put(Params.IP_ADDR, Values.SERVER_IP);
        params.put(Params.CREATE_DATE, LocalDateTime.now(clock).format(CREATE_DATE_FORMAT));

        var hashData = hashData(params);
        var secureHash = hmacSha512Hex(properties.hashSecret(), hashData);
        return new PaymentUrlResult(
                properties.payUrl() + "?" + hashData + "&" + Params.SECURE_HASH + "=" + secureHash,
                request.orderId());
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> rawParams) {
        var received = rawParams.get(Params.SECURE_HASH);
        SortedMap<String, String> signed = new TreeMap<>(rawParams);
        signed.remove(Params.SECURE_HASH);
        signed.remove(Params.SECURE_HASH_TYPE);
        var expected = hmacSha512Hex(properties.hashSecret(), hashData(signed));
        if (received == null || !expected.equalsIgnoreCase(received)) {
            throw new PaymentSignatureException(PaymentGatewayType.VNPAY);
        }
        var responseCode = rawParams.get(Params.RESPONSE_CODE);
        var amountInCents = rawParams.get(Params.AMOUNT);
        var amount = amountInCents == null ? BigDecimal.ZERO
                : new BigDecimal(amountInCents).divide(Values.AMOUNT_MULTIPLIER);
        return new PaymentCallbackResult(rawParams.get(Params.TXN_REF),
                Values.SUCCESS_RESPONSE_CODE.equals(responseCode), amount,
                Params.RESPONSE_CODE + "=" + responseCode);
    }

    /** Sắp key theo alphabet (TreeMap), ghép {@code key=URLEncoder.encode(value)} bằng {@code &}. */
    static String hashData(SortedMap<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    static String hmacSha512Hex(String secret, String data) {
        try {
            var mac = Mac.getInstance(Values.HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), Values.HMAC_ALGORITHM));
            return toHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            // Thuật toán HmacSHA512 luôn có trong JDK - lỗi ở đây là cấu hình JVM sai, không phải
            // tình huống nghiệp vụ, nên để nó nổ ra thay vì âm thầm trả chữ ký rỗng.
            throw new IllegalStateException("Không khởi tạo được " + Values.HMAC_ALGORITHM, e);
        }
    }

    private static String toHex(byte[] bytes) {
        var hex = new StringBuilder(bytes.length * 2);
        for (var b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=VnPayPaymentGatewayClientTest`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/integrations/payment/VnPayPaymentGatewayClient.java \
  backend/src/test/java/com/eduerp/integrations/payment/VnPayPaymentGatewayClientTest.java
git commit -m "feat(payment): add VNPay gateway client with pinned HMAC-SHA512 signing"
```

---

### Task 10: `MomoPaymentGatewayClient` — HMAC-SHA256 trên chuỗi khoá theo thứ tự cố định

**Files:**
- Create: `backend/src/main/java/com/eduerp/integrations/payment/MomoPaymentGatewayClient.java`
- Test: `backend/src/test/java/com/eduerp/integrations/payment/MomoPaymentGatewayClientTest.java` (create)

**Interfaces:**
- Consumes: `PaymentGatewayClient` và các value type (Task 8).
- Produces:
  `MomoPaymentGatewayClient(MomoProperties properties, RestClient.Builder restClientBuilder)` (`@Component`);
  static package-private `createRequestPayloadToSign(String accessKey, String amount, String extraData, String ipnUrl, String orderId, String orderInfo, String partnerCode, String redirectUrl, String requestId, String requestType): String`;
  static package-private `ipnPayloadToSign(Map<String,String> params, String accessKey): String`;
  static package-private `hmacSha256Hex(String secretKey, String payload): String`.

> **Công thức chốt tại plan.** Tạo link (spec mục 2, nguyên văn): HMAC-SHA256 của
> `accessKey=…&amount=…&extraData=…&ipnUrl=…&orderId=…&orderInfo=…&partnerCode=…&redirectUrl=…&requestId=…&requestType=…`
> (KHÔNG url-encode), ký bằng `secretKey`.
> **IPN:** spec viết "verify theo cùng công thức", nhưng payload IPN của MoMo không có
> `ipnUrl`/`redirectUrl`/`requestType` nên công thức tạo link không áp được. Bộ khoá IPN thật của
> MoMo (vẫn alphabet, vẫn HMAC-SHA256, vẫn `secretKey`) là:
> `accessKey, amount, extraData, message, orderId, orderInfo, orderType, partnerCode, payType, requestId, responseTime, resultCode, transId`.
> `resultCode=0` là thành công. Đây là cách đọc duy nhất khả thi của spec mục 2 — ghi lại ở đây để
> người review biết plan không tự ý đổi công thức.

- [ ] **Step 1: Write the failing test** — `backend/src/test/java/com/eduerp/integrations/payment/MomoPaymentGatewayClientTest.java`:

```java
package com.eduerp.integrations.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Không gọi mạng thật (spec mục 11): test chỉ chạm phần thuần - chuỗi ký và HMAC. Giá trị kỳ vọng
 * đến từ hai nguồn độc lập với code production: hex literal tính sẵn ngoài dự án, và
 * {@link #referenceHmacSha256} cài lại HMAC-SHA256 trong chính test.
 */
class MomoPaymentGatewayClientTest {

    private static final String PARTNER_CODE = "PARTNER01";
    private static final String ACCESS_KEY = "ACCESS123";
    private static final String SECRET_KEY = "MOMOSECRET123";
    private static final String ENDPOINT = "https://test-payment.momo.vn/v2/gateway/api/create";
    private static final String REDIRECT_URL = "https://eduerp.local/payment/return/momo";
    private static final String IPN_URL = "https://eduerp.local/api/billing/payments/callback/momo";

    private final MomoProperties properties =
            new MomoProperties(PARTNER_CODE, ACCESS_KEY, SECRET_KEY, ENDPOINT, REDIRECT_URL, IPN_URL);
    private final MomoPaymentGatewayClient client =
            new MomoPaymentGatewayClient(properties, RestClient.builder());

    private static String referenceHmacSha256(String secret, String data) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(bytes.length * 2);
            for (var b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void typeIsMomo() {
        assertThat(client.type()).isEqualTo(PaymentGatewayType.MOMO);
    }

    /** Thứ tự khoá của chuỗi ký tạo link là CỐ ĐỊNH theo spec mục 2, không phải sắp alphabet ngẫu
     * nhiên - đổi một dấu & là MoMo trả về INVALID_SIGNATURE. */
    @Test
    void createRequestPayloadMatchesTheKeyOrderFromTheSpec() {
        var payload = MomoPaymentGatewayClient.createRequestPayloadToSign(ACCESS_KEY, "50000", "", IPN_URL,
                "order-1", "Hoc phi dot 1", PARTNER_CODE, REDIRECT_URL, "req-1", "captureWallet");

        assertThat(payload).isEqualTo("accessKey=ACCESS123&amount=50000&extraData="
                + "&ipnUrl=https://eduerp.local/api/billing/payments/callback/momo"
                + "&orderId=order-1&orderInfo=Hoc phi dot 1&partnerCode=PARTNER01"
                + "&redirectUrl=https://eduerp.local/payment/return/momo"
                + "&requestId=req-1&requestType=captureWallet");
    }

    /** Nguồn độc lập #1: hex literal tính sẵn ngoài dự án cho đúng payload ở test trên. */
    @Test
    void createRequestSignatureMatchesThePinnedVector() {
        var payload = MomoPaymentGatewayClient.createRequestPayloadToSign(ACCESS_KEY, "50000", "", IPN_URL,
                "order-1", "Hoc phi dot 1", PARTNER_CODE, REDIRECT_URL, "req-1", "captureWallet");

        assertThat(MomoPaymentGatewayClient.hmacSha256Hex(SECRET_KEY, payload))
                .isEqualTo("cca848a7e943ba2cd3569b8bf2225e3021ccd308ebf88ffe74c0ab339cb441a6");
    }

    /** Nguồn độc lập #2: HMAC tính lại trong test bằng javax.crypto.Mac. */
    @Test
    void hmacImplementationAgreesWithAnIndependentJdkComputation() {
        var data = "accessKey=ACCESS123&amount=1";

        assertThat(MomoPaymentGatewayClient.hmacSha256Hex(SECRET_KEY, data))
                .isEqualTo(referenceHmacSha256(SECRET_KEY, data));
    }

    private Map<String, String> successfulIpnParams() {
        var params = new HashMap<String, String>();
        params.put("partnerCode", PARTNER_CODE);
        params.put("orderId", "order-1");
        params.put("requestId", "req-1");
        params.put("amount", "50000");
        params.put("orderInfo", "Hoc phi dot 1");
        params.put("orderType", "momo_wallet");
        params.put("transId", "2345678901");
        params.put("resultCode", "0");
        params.put("message", "Successful.");
        params.put("payType", "qr");
        params.put("responseTime", "1767222123000");
        params.put("extraData", "");
        params.put("signature", "87d1bf85b084426172542e76dbd6a20e25243046dfcd101d84bf551484bc4d49");
        return params;
    }

    @Test
    void ipnPayloadMatchesTheDocumentedKeyOrder() {
        var payload = MomoPaymentGatewayClient.ipnPayloadToSign(successfulIpnParams(), ACCESS_KEY);

        assertThat(payload).isEqualTo("accessKey=ACCESS123&amount=50000&extraData=&message=Successful."
                + "&orderId=order-1&orderInfo=Hoc phi dot 1&orderType=momo_wallet&partnerCode=PARTNER01"
                + "&payType=qr&requestId=req-1&responseTime=1767222123000&resultCode=0&transId=2345678901");
    }

    @Test
    void verifyCallbackAcceptsThePinnedIpnSignature() {
        var result = client.verifyCallback(successfulIpnParams());

        assertThat(result.orderId()).isEqualTo("order-1");
        assertThat(result.success()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(result.rawMessage()).contains("Successful.");
    }

    @Test
    void verifyCallbackReportsAFailedTransactionWithoutRejectingTheSignature() {
        var params = successfulIpnParams();
        params.put("resultCode", "1006");
        params.put("message", "Transaction denied.");
        params.put("signature",
                referenceHmacSha256(SECRET_KEY, MomoPaymentGatewayClient.ipnPayloadToSign(params, ACCESS_KEY)));

        var result = client.verifyCallback(params);

        assertThat(result.success()).isFalse();
        assertThat(result.orderId()).isEqualTo("order-1");
    }

    @Test
    void verifyCallbackRejectsATamperedAmount() {
        var params = successfulIpnParams();
        params.put("amount", "1");

        assertThatThrownBy(() -> client.verifyCallback(params)).isInstanceOf(PaymentSignatureException.class);
    }

    @Test
    void verifyCallbackRejectsAMissingSignature() {
        var params = successfulIpnParams();
        params.remove("signature");

        assertThatThrownBy(() -> client.verifyCallback(params)).isInstanceOf(PaymentSignatureException.class);
    }

    /** Tham số thiếu được coi là chuỗi rỗng trong payload - không được ném NullPointerException, vì
     * đó là đường vào từ Internet, ai cũng POST được JSON thiếu field. */
    @Test
    void ipnPayloadTreatsMissingParametersAsEmptyStrings() {
        var payload = MomoPaymentGatewayClient.ipnPayloadToSign(Map.of("orderId", "order-1"), ACCESS_KEY);

        assertThat(payload).isEqualTo("accessKey=ACCESS123&amount=&extraData=&message=&orderId=order-1"
                + "&orderInfo=&orderType=&partnerCode=&payType=&requestId=&responseTime=&resultCode="
                + "&transId=");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=MomoPaymentGatewayClientTest`
Expected: FAIL — compile error, `MomoPaymentGatewayClient` chưa tồn tại.

- [ ] **Step 3: Write the client**

`integrations/payment/MomoPaymentGatewayClient.java`:

```java
package com.eduerp.integrations.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * MoMo One-Time Payment v2 (spec mục 2). Tạo link: POST JSON tới {@code /v2/gateway/api/create},
 * {@code signature} = HMAC-SHA256 của chuỗi khoá theo thứ tự CỐ ĐỊNH ở {@link #createRequestPayloadToSign}
 * (không url-encode), ký bằng {@code secretKey}; response trả {@code payUrl}.
 *
 * <p>IPN: MoMo POST JSON tới {@code ipnUrl}. Payload IPN không có {@code ipnUrl}/{@code redirectUrl}/
 * {@code requestType} nên công thức tạo link không áp được - bộ khoá IPN thật của MoMo nằm ở
 * {@link #ipnPayloadToSign} (xem ghi chú trong plan Task 10). {@code resultCode=0} là thành công.
 */
@Component
class MomoPaymentGatewayClient implements PaymentGatewayClient {

    private static final class Params {
        private Params() {
        }

        static final String PARTNER_CODE = "partnerCode";
        static final String ACCESS_KEY = "accessKey";
        static final String REQUEST_ID = "requestId";
        static final String AMOUNT = "amount";
        static final String ORDER_ID = "orderId";
        static final String ORDER_INFO = "orderInfo";
        static final String ORDER_TYPE = "orderType";
        static final String REDIRECT_URL = "redirectUrl";
        static final String IPN_URL = "ipnUrl";
        static final String REQUEST_TYPE = "requestType";
        static final String EXTRA_DATA = "extraData";
        static final String SIGNATURE = "signature";
        static final String PAY_URL = "payUrl";
        static final String RESULT_CODE = "resultCode";
        static final String MESSAGE = "message";
        static final String PAY_TYPE = "payType";
        static final String RESPONSE_TIME = "responseTime";
        static final String TRANS_ID = "transId";
        static final String LANG = "lang";
    }

    private static final class Values {
        private Values() {
        }

        static final String REQUEST_TYPE = "captureWallet";
        static final String LANG = "vi";
        static final String EXTRA_DATA = "";
        static final String SUCCESS_RESULT_CODE = "0";
        static final String HMAC_ALGORITHM = "HmacSHA256";
    }

    /** Thứ tự khoá của chuỗi ký IPN - alphabet theo đúng tài liệu MoMo, cố định trong một chỗ. */
    private static final List<String> IPN_SIGNED_KEYS = List.of(Params.ACCESS_KEY, Params.AMOUNT,
            Params.EXTRA_DATA, Params.MESSAGE, Params.ORDER_ID, Params.ORDER_INFO, Params.ORDER_TYPE,
            Params.PARTNER_CODE, Params.PAY_TYPE, Params.REQUEST_ID, Params.RESPONSE_TIME, Params.RESULT_CODE,
            Params.TRANS_ID);

    private final MomoProperties properties;
    private final RestClient restClient;

    MomoPaymentGatewayClient(MomoProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public PaymentGatewayType type() {
        return PaymentGatewayType.MOMO;
    }

    @Override
    public PaymentUrlResult createPaymentUrl(PaymentRequest request) {
        var redirectUrl = request.returnUrl() == null || request.returnUrl().isBlank()
                ? properties.redirectUrl() : request.returnUrl();
        var ipnUrl = request.ipnUrl() == null || request.ipnUrl().isBlank()
                ? properties.ipnUrl() : request.ipnUrl();
        var requestId = UUID.randomUUID().toString();
        var amount = request.amount().toBigInteger().toString();
        var signature = hmacSha256Hex(properties.secretKey(), createRequestPayloadToSign(properties.accessKey(),
                amount, Values.EXTRA_DATA, ipnUrl, request.orderId(), request.orderInfo(),
                properties.partnerCode(), redirectUrl, requestId, Values.REQUEST_TYPE));

        var body = new LinkedHashMap<String, String>();
        body.put(Params.PARTNER_CODE, properties.partnerCode());
        body.put(Params.ACCESS_KEY, properties.accessKey());
        body.put(Params.REQUEST_ID, requestId);
        body.put(Params.AMOUNT, amount);
        body.put(Params.ORDER_ID, request.orderId());
        body.put(Params.ORDER_INFO, request.orderInfo());
        body.put(Params.REDIRECT_URL, redirectUrl);
        body.put(Params.IPN_URL, ipnUrl);
        body.put(Params.EXTRA_DATA, Values.EXTRA_DATA);
        body.put(Params.REQUEST_TYPE, Values.REQUEST_TYPE);
        body.put(Params.LANG, Values.LANG);
        body.put(Params.SIGNATURE, signature);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(properties.endpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        var payUrl = response == null ? null : String.valueOf(response.get(Params.PAY_URL));
        return new PaymentUrlResult(payUrl, request.orderId());
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> rawParams) {
        var received = rawParams.get(Params.SIGNATURE);
        var expected = hmacSha256Hex(properties.secretKey(),
                ipnPayloadToSign(rawParams, properties.accessKey()));
        if (received == null || !expected.equalsIgnoreCase(received)) {
            throw new PaymentSignatureException(PaymentGatewayType.MOMO);
        }
        var amountValue = rawParams.get(Params.AMOUNT);
        var amount = amountValue == null || amountValue.isBlank() ? BigDecimal.ZERO : new BigDecimal(amountValue);
        return new PaymentCallbackResult(rawParams.get(Params.ORDER_ID),
                Values.SUCCESS_RESULT_CODE.equals(rawParams.get(Params.RESULT_CODE)), amount,
                String.valueOf(rawParams.get(Params.MESSAGE)));
    }

    /** Thứ tự khoá nguyên văn theo spec mục 2 - KHÔNG url-encode, KHÔNG sắp lại. */
    static String createRequestPayloadToSign(String accessKey, String amount, String extraData, String ipnUrl,
            String orderId, String orderInfo, String partnerCode, String redirectUrl, String requestId,
            String requestType) {
        return Params.ACCESS_KEY + "=" + accessKey
                + "&" + Params.AMOUNT + "=" + amount
                + "&" + Params.EXTRA_DATA + "=" + extraData
                + "&" + Params.IPN_URL + "=" + ipnUrl
                + "&" + Params.ORDER_ID + "=" + orderId
                + "&" + Params.ORDER_INFO + "=" + orderInfo
                + "&" + Params.PARTNER_CODE + "=" + partnerCode
                + "&" + Params.REDIRECT_URL + "=" + redirectUrl
                + "&" + Params.REQUEST_ID + "=" + requestId
                + "&" + Params.REQUEST_TYPE + "=" + requestType;
    }

    /** Tham số thiếu thành chuỗi rỗng: đây là đường vào từ Internet, không được ném NPE. */
    static String ipnPayloadToSign(Map<String, String> params, String accessKey) {
        return IPN_SIGNED_KEYS.stream()
                .map(key -> key + "=" + resolveSignedValue(params, key, accessKey))
                .collect(Collectors.joining("&"));
    }

    private static String resolveSignedValue(Map<String, String> params, String key, String accessKey) {
        if (Params.ACCESS_KEY.equals(key)) {
            // MoMo không gửi accessKey trong IPN - nó nằm ở cấu hình phía mình.
            return accessKey;
        }
        var value = params.get(key);
        return value == null ? "" : value;
    }

    static String hmacSha256Hex(String secretKey, String payload) {
        try {
            var mac = Mac.getInstance(Values.HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), Values.HMAC_ALGORITHM));
            var bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(bytes.length * 2);
            for (var b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Không khởi tạo được " + Values.HMAC_ALGORITHM, e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=MomoPaymentGatewayClientTest`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/integrations/payment/MomoPaymentGatewayClient.java \
  backend/src/test/java/com/eduerp/integrations/payment/MomoPaymentGatewayClientTest.java
git commit -m "feat(payment): add MoMo gateway client with pinned HMAC-SHA256 signing"
```

---

### Task 11: `modules.billing` scaffold — constants, exceptions, events, DTO

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/BillingConstants.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/BillingException.java`
- Create: 12 file exception trong `backend/src/main/java/com/eduerp/modules/billing/`: `InvoiceNotFoundException.java`, `PaymentNotFoundException.java`, `EnrollmentNotFoundException.java`, `EnrollmentNotActiveForBillingException.java`, `CourseNotFoundException.java`, `CourseTuitionNotConfiguredException.java`, `InstallmentLimitExceededException.java`, `InvoiceAmountExceedsTuitionException.java`, `InvoiceNotPayableException.java`, `InvalidPaymentAmountException.java`, `InvalidCallbackSignatureException.java`, `UnknownPaymentGatewayException.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/BillingEvents.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/CreateInvoiceRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/InvoiceResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/PaymentResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/InvoiceDetailResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/InitiateOnlinePaymentRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/InitiateOnlinePaymentResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/dto/RecordManualPaymentRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/web/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/model/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/repository/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/rules/package-info.java`
- Modify: `backend/src/test/java/com/eduerp/ModularityTests.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/BillingConstantsTest.java` (create)
- Test: `backend/src/test/java/com/eduerp/modules/billing/BillingExceptionTest.java` (create)

**Interfaces:**
- Consumes: `core.exception.AppException`, `integrations.payment.PaymentGatewayType` (Task 8).
- Produces:
  `BillingConstants.InvoiceStatus { UNPAID, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED }`,
  `BillingConstants.PaymentMethod { MOMO, VNPAY, MANUAL }`,
  `BillingConstants.PaymentStatus { PENDING, SUCCESS, FAILED }`,
  `BillingConstants.Limits.MAX_INSTALLMENTS_PER_ENROLLMENT = 3`,
  `BillingConstants.Schedules.MARK_OVERDUE_CRON = "0 0 1 * * *"`,
  `BillingConstants.OrderInfo.PREFIX = "Hoc phi dot "`;
  sealed `BillingException` + 12 subclass (errorCode `BILLING_*`);
  `BillingEvents.InvoiceCreated|PaymentReceived|InvoiceOverdue(UUID id, UUID actorAccountId, UUID actorBranchId)`;
  `CreateInvoiceRequest(UUID enrollmentId, BigDecimal amount, LocalDate dueDate)`;
  `InvoiceResponse(UUID id, UUID enrollmentId, UUID studentProfileId, UUID courseId, UUID branchId, int installmentNumber, BigDecimal amount, BigDecimal amountPaid, BillingConstants.InvoiceStatus status, LocalDate dueDate, Instant issuedAt)`;
  `PaymentResponse(UUID id, BigDecimal amount, BillingConstants.PaymentMethod method, BillingConstants.PaymentStatus status, Instant paidAt, Instant createdAt)`;
  `InvoiceDetailResponse(InvoiceResponse invoice, List<PaymentResponse> payments)`;
  `InitiateOnlinePaymentRequest(BillingConstants.PaymentMethod gateway)`;
  `InitiateOnlinePaymentResponse(String payUrl)`;
  `RecordManualPaymentRequest(BigDecimal amount)`.

- [ ] **Step 1: Write the failing tests**

`backend/src/test/java/com/eduerp/modules/billing/BillingConstantsTest.java`:

```java
package com.eduerp.modules.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.integrations.payment.PaymentGatewayType;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BillingConstantsTest {

    @Test
    void invoiceStatusCoversTheWholeLifecycle() {
        assertThat(BillingConstants.InvoiceStatus.values()).containsExactly(
                BillingConstants.InvoiceStatus.UNPAID, BillingConstants.InvoiceStatus.PARTIALLY_PAID,
                BillingConstants.InvoiceStatus.PAID, BillingConstants.InvoiceStatus.OVERDUE,
                BillingConstants.InvoiceStatus.CANCELLED);
    }

    @Test
    void installmentLimitIsThree() {
        assertThat(BillingConstants.Limits.MAX_INSTALLMENTS_PER_ENROLLMENT).isEqualTo(3);
    }

    /**
     * Mỗi {@code PaymentMethod} online phải trùng tên với một {@code PaymentGatewayType} -
     * {@code PaymentGatewayClientResolver} (Task 14) map hai enum bằng {@code name()}, lệch tên là
     * lỗi runtime chứ không phải lỗi biên dịch.
     */
    @Test
    void everyOnlinePaymentMethodNamesAnExistingGatewayType() {
        var gatewayNames = Arrays.stream(PaymentGatewayType.values()).map(Enum::name).toList();

        assertThat(gatewayNames).contains(BillingConstants.PaymentMethod.MOMO.name(),
                BillingConstants.PaymentMethod.VNPAY.name());
        assertThat(gatewayNames).doesNotContain(BillingConstants.PaymentMethod.MANUAL.name());
    }

    @Test
    void markOverdueCronRunsOnceAtOneInTheMorning() {
        assertThat(BillingConstants.Schedules.MARK_OVERDUE_CRON).isEqualTo("0 0 1 * * *");
    }
}
```

`backend/src/test/java/com/eduerp/modules/billing/BillingExceptionTest.java`:

```java
package com.eduerp.modules.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.integrations.payment.PaymentGatewayType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BillingExceptionTest {

    @Test
    void invoiceNotFoundIsNotFound() {
        var ex = new InvoiceNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_INVOICE_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void paymentNotFoundIsNotFound() {
        var ex = new PaymentNotFoundException("order-1");
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_PAYMENT_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void enrollmentNotFoundIsBadRequest() {
        var ex = new EnrollmentNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_ENROLLMENT_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void enrollmentNotActiveForBillingIsConflict() {
        var ex = new EnrollmentNotActiveForBillingException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_ENROLLMENT_NOT_ACTIVE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void courseNotFoundIsBadRequest() {
        var ex = new CourseNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COURSE_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void courseTuitionNotConfiguredIsConflict() {
        var ex = new CourseTuitionNotConfiguredException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_COURSE_TUITION_NOT_CONFIGURED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void installmentLimitExceededIsConflictAndNamesTheLimit() {
        var ex = new InstallmentLimitExceededException(UUID.randomUUID(), 3);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_INSTALLMENT_LIMIT_EXCEEDED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("3");
    }

    @Test
    void invoiceAmountExceedsTuitionIsConflictAndNamesBothNumbers() {
        var ex = new InvoiceAmountExceedsTuitionException(new BigDecimal("13000000"), new BigDecimal("12000000"));
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_INVOICE_AMOUNT_EXCEEDS_TUITION");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("13000000", "12000000");
    }

    @Test
    void invoiceNotPayableIsConflict() {
        var ex = new InvoiceNotPayableException(UUID.randomUUID(), BillingConstants.InvoiceStatus.PAID);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_INVOICE_NOT_PAYABLE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("PAID");
    }

    @Test
    void invalidPaymentAmountIsBadRequest() {
        var ex = new InvalidPaymentAmountException(new BigDecimal("-1"));
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_INVALID_PAYMENT_AMOUNT");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** Message không được chứa orderId - Review Focus #5: không để response/log thành oracle. */
    @Test
    void invalidCallbackSignatureIsBadRequestAndNamesOnlyTheGateway() {
        var ex = new InvalidCallbackSignatureException(PaymentGatewayType.MOMO);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_INVALID_CALLBACK_SIGNATURE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("MOMO").doesNotContain("order");
    }

    @Test
    void unknownPaymentGatewayIsBadRequest() {
        var ex = new UnknownPaymentGatewayException(BillingConstants.PaymentMethod.MANUAL);
        assertThat(ex.getErrorCode()).isEqualTo("BILLING_UNKNOWN_PAYMENT_GATEWAY");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("MANUAL");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn test -Dtest='BillingConstantsTest,BillingExceptionTest'`
Expected: FAIL — compile error, package `com.eduerp.modules.billing` chưa tồn tại.

- [ ] **Step 3: Create the `package-info` files**

`modules/billing/package-info.java`:

```java
/**
 * Module Billing — sở hữu hoá đơn học phí (Invoice, tối đa 3 đợt mỗi ghi danh) và lịch sử thanh
 * toán (Payment). Phụ thuộc một chiều {@code access} ({@code AccessConstants} cho
 * {@code @PreAuthorize}), {@code enrollment} ({@code EnrollmentManagement.getEnrollment} để chốt
 * snapshot và chặn phát hành cho ghi danh đã rút), {@code courses}
 * ({@code CoursesManagement.getCourseTuition} để chặn tổng các đợt vượt học phí) và
 * {@code integrations.payment} ({@code PaymentGatewayClient} - giống cách {@code modules.payroll}
 * gọi {@code integrations.storage}). Không module nào đọc ngược từ {@code billing} (spec mục 3).
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — constants, exception, events (chưa có facade: chưa module nào cần đọc
 *       billing, không suy đoán method chưa ai gọi - mirror quyết định ở CoursesManagement).</li>
 *   <li>{@code dto} — hợp đồng vào/ra qua HTTP, {@code @NamedInterface("dto")}.</li>
 *   <li>{@code usecase} — một class = một use case (rule #7).</li>
 *   <li>{@code web} — controller mỏng (rule #8), gồm cả 3 endpoint public cho cổng thanh toán.</li>
 *   <li>{@code internal} — entity/repository/rules + {@code OverdueInvoiceScheduler}.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Billing & Tuition")
package com.eduerp.modules.billing;
```

`modules/billing/dto/package-info.java`:

```java
/**
 * Hợp đồng vào/ra của module billing. Phải {@code @NamedInterface} nếu không
 * {@code ApplicationModules.verify()} sẽ fail - mirror {@code modules.payroll.dto}.
 */
@org.springframework.modulith.NamedInterface("dto")
package com.eduerp.modules.billing.dto;
```

`modules/billing/usecase/package-info.java`:

```java
/**
 * Một class = một use case = một method {@code public execute(...)} (rule #7). Method phải
 * {@code public} để proxy AOP của Spring bọc được {@code @Transactional}.
 */
package com.eduerp.modules.billing.usecase;
```

`modules/billing/web/package-info.java`:

```java
/** Adapter HTTP của module billing: controller mỏng (rule #8 - không chứa nghiệp vụ). */
package com.eduerp.modules.billing.web;
```

`modules/billing/internal/package-info.java`:

```java
package com.eduerp.modules.billing.internal;
```

`modules/billing/internal/model/package-info.java`:

```java
package com.eduerp.modules.billing.internal.model;
```

`modules/billing/internal/repository/package-info.java`:

```java
package com.eduerp.modules.billing.internal.repository;
```

`modules/billing/internal/rules/package-info.java`:

```java
package com.eduerp.modules.billing.internal.rules;
```

- [ ] **Step 4: Create `BillingConstants` and `BillingEvents`**

`BillingConstants.java`:

```java
package com.eduerp.modules.billing;

/**
 * Hằng số dùng chung của module billing, nằm ở base package (không phải {@code internal}) vì
 * {@code dto} và {@code internal.model} cùng tier, cả hai cần tham chiếu - mirror
 * {@code PayrollConstants}.
 */
public final class BillingConstants {

    private BillingConstants() {
    }

    public enum InvoiceStatus {
        UNPAID, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED
    }

    /** {@code MOMO}/{@code VNPAY} trùng tên {@code integrations.payment.PaymentGatewayType} - đó là
     * cách {@code PaymentGatewayClientResolver} map hai enum ({@code BillingConstantsTest} chốt lại). */
    public enum PaymentMethod {
        MOMO, VNPAY, MANUAL
    }

    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED
    }

    public static final class Limits {
        private Limits() {
        }

        public static final int MAX_INSTALLMENTS_PER_ENROLLMENT = 3;
    }

    /** Cron phải là hằng biên dịch để nhét được vào {@code @Scheduled} - không hardcode trong annotation. */
    public static final class Schedules {
        private Schedules() {
        }

        /** 1:00 sáng mỗi ngày (spec mục 5). */
        public static final String MARK_OVERDUE_CRON = "0 0 1 * * *";
    }

    /** Nội dung hiển thị trên cổng thanh toán - người trả tiền thấy chuỗi này trên app MoMo/VNPay. */
    public static final class OrderInfo {
        private OrderInfo() {
        }

        public static final String PREFIX = "Hoc phi dot ";
    }

    /** Không cần cache namespace nào ở V1 - để trống theo khuôn tier 0 của PayrollConstants. */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }
    }
}
```

`BillingEvents.java`:

```java
package com.eduerp.modules.billing;

import java.util.UUID;

/**
 * {@code actorAccountId} của {@link InvoiceOverdue} là {@code null}: sự kiện do scheduler tự sinh,
 * không có người nào bấm. {@code modules.audit} phải ghi được dòng log với actor rỗng (cột
 * {@code audit_logs.actor_account_id} vốn nullable - xem Task 23).
 */
public final class BillingEvents {

    private BillingEvents() {
    }

    public record InvoiceCreated(UUID invoiceId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record PaymentReceived(UUID invoiceId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record InvoiceOverdue(UUID invoiceId, UUID actorAccountId, UUID actorBranchId) {
    }
}
```

- [ ] **Step 5: Create `BillingException` and the 12 subclasses**

`BillingException.java`:

```java
package com.eduerp.modules.billing;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module billing phát ra. */
public sealed class BillingException extends AppException
        permits InvoiceNotFoundException, PaymentNotFoundException, EnrollmentNotFoundException,
        EnrollmentNotActiveForBillingException, CourseNotFoundException, CourseTuitionNotConfiguredException,
        InstallmentLimitExceededException, InvoiceAmountExceedsTuitionException, InvoiceNotPayableException,
        InvalidPaymentAmountException, InvalidCallbackSignatureException, UnknownPaymentGatewayException {

    protected BillingException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
```

`InvoiceNotFoundException.java`:

```java
package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class InvoiceNotFoundException extends BillingException {
    public InvoiceNotFoundException(UUID invoiceId) {
        super("BILLING_INVOICE_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy hoá đơn " + invoiceId);
    }
}
```

`PaymentNotFoundException.java`:

```java
package com.eduerp.modules.billing;

import org.springframework.http.HttpStatus;

/** Dùng cho endpoint tra trạng thái thanh toán theo {@code gatewayTransactionId}. KHÔNG dùng trong
 * {@code HandlePaymentCallback}: ở đó orderId lạ phải đi vào nhánh "bỏ qua im lặng" (Review Focus #5). */
public final class PaymentNotFoundException extends BillingException {
    public PaymentNotFoundException(String gatewayTransactionId) {
        super("BILLING_PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                "Không tìm thấy giao dịch " + gatewayTransactionId);
    }
}
```

`EnrollmentNotFoundException.java`:

```java
package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Cùng tên đơn giản với {@code modules.enrollment.EnrollmentNotFoundException} nhưng khác package và
 * khác errorCode - đây là lỗi của billing khi id ghi danh trong body không trỏ tới bản ghi nào. */
public final class EnrollmentNotFoundException extends BillingException {
    public EnrollmentNotFoundException(UUID enrollmentId) {
        super("BILLING_ENROLLMENT_NOT_FOUND", HttpStatus.BAD_REQUEST, "Không tìm thấy ghi danh " + enrollmentId);
    }
}
```

`EnrollmentNotActiveForBillingException.java`:

```java
package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Rút khỏi lớp giữ nguyên công nợ đã phát sinh, chỉ chặn phát hành đợt MỚI (spec mục 12). */
public final class EnrollmentNotActiveForBillingException extends BillingException {
    public EnrollmentNotActiveForBillingException(UUID enrollmentId) {
        super("BILLING_ENROLLMENT_NOT_ACTIVE", HttpStatus.CONFLICT,
                "Ghi danh " + enrollmentId + " không còn đang học, không phát hành thêm đợt thu");
    }
}
```

`CourseNotFoundException.java`:

```java
package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class CourseNotFoundException extends BillingException {
    public CourseNotFoundException(UUID courseId) {
        super("BILLING_COURSE_NOT_FOUND", HttpStatus.BAD_REQUEST, "Không tìm thấy khoá học " + courseId);
    }
}
```

`CourseTuitionNotConfiguredException.java`:

```java
package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class CourseTuitionNotConfiguredException extends BillingException {
    public CourseTuitionNotConfiguredException(UUID courseId) {
        super("BILLING_COURSE_TUITION_NOT_CONFIGURED", HttpStatus.CONFLICT,
                "Khoá học " + courseId + " chưa gắn học phí");
    }
}
```

`InstallmentLimitExceededException.java`:

```java
package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class InstallmentLimitExceededException extends BillingException {
    public InstallmentLimitExceededException(UUID enrollmentId, int maxInstallments) {
        super("BILLING_INSTALLMENT_LIMIT_EXCEEDED", HttpStatus.CONFLICT,
                "Ghi danh " + enrollmentId + " đã có đủ " + maxInstallments + " đợt thu");
    }
}
```

`InvoiceAmountExceedsTuitionException.java`:

```java
package com.eduerp.modules.billing;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public final class InvoiceAmountExceedsTuitionException extends BillingException {
    public InvoiceAmountExceedsTuitionException(BigDecimal totalAfterThisInvoice, BigDecimal tuitionFee) {
        super("BILLING_INVOICE_AMOUNT_EXCEEDS_TUITION", HttpStatus.CONFLICT,
                "Tổng các đợt thu " + totalAfterThisInvoice.toPlainString() + " vượt học phí khoá học "
                        + tuitionFee.toPlainString());
    }
}
```

`InvoiceNotPayableException.java`:

```java
package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Dùng cho cả ba trường hợp: thu online, thu thủ công, và huỷ hoá đơn khi trạng thái không cho phép. */
public final class InvoiceNotPayableException extends BillingException {
    public InvoiceNotPayableException(UUID invoiceId, BillingConstants.InvoiceStatus status) {
        super("BILLING_INVOICE_NOT_PAYABLE", HttpStatus.CONFLICT,
                "Hoá đơn " + invoiceId + " đang ở trạng thái " + status + ", không thực hiện được thao tác này");
    }
}
```

`InvalidPaymentAmountException.java`:

```java
package com.eduerp.modules.billing;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public final class InvalidPaymentAmountException extends BillingException {
    public InvalidPaymentAmountException(BigDecimal amount) {
        super("BILLING_INVALID_PAYMENT_AMOUNT", HttpStatus.BAD_REQUEST,
                "Số tiền " + amount.toPlainString() + " không hợp lệ cho hoá đơn này");
    }
}
```

`InvalidCallbackSignatureException.java`:

```java
package com.eduerp.modules.billing;

import com.eduerp.integrations.payment.PaymentGatewayType;
import org.springframework.http.HttpStatus;

/**
 * Chữ ký callback không khớp. Message cố tình chỉ nêu tên cổng, KHÔNG nêu orderId - Review Focus #5:
 * chuỗi này không được trở thành oracle nếu lọt vào log hay response.
 * {@code PaymentCallbackController} (Task 22) bắt lỗi này và vẫn trả đúng response cố định như mọi
 * trường hợp khác, nên HttpStatus ở đây chỉ dùng cho lời gọi nội bộ/test.
 */
public final class InvalidCallbackSignatureException extends BillingException {
    public InvalidCallbackSignatureException(PaymentGatewayType gateway) {
        super("BILLING_INVALID_CALLBACK_SIGNATURE", HttpStatus.BAD_REQUEST,
                "Chữ ký callback của cổng " + gateway + " không hợp lệ");
    }
}
```

`UnknownPaymentGatewayException.java`:

```java
package com.eduerp.modules.billing;

import org.springframework.http.HttpStatus;

/** Ném khi ai đó gọi thu online với {@code MANUAL} (hoặc một method tương lai chưa có client). */
public final class UnknownPaymentGatewayException extends BillingException {
    public UnknownPaymentGatewayException(BillingConstants.PaymentMethod method) {
        super("BILLING_UNKNOWN_PAYMENT_GATEWAY", HttpStatus.BAD_REQUEST,
                "Phương thức " + method + " không phải một cổng thanh toán online");
    }
}
```

- [ ] **Step 6: Create the seven DTO records**

`dto/CreateInvoiceRequest.java`:

```java
package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Kế toán tự nhập số tiền mỗi đợt, hệ thống KHÔNG tự chia đều (spec mục 12). */
public record CreateInvoiceRequest(@NotNull UUID enrollmentId, @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate dueDate) {
}
```

`dto/InvoiceResponse.java`:

```java
package com.eduerp.modules.billing.dto;

import com.eduerp.modules.billing.BillingConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Tên field là hợp đồng với Zod schema ở frontend (entities/billing) - đổi tên là breaking. */
public record InvoiceResponse(UUID id, UUID enrollmentId, UUID studentProfileId, UUID courseId, UUID branchId,
        int installmentNumber, BigDecimal amount, BigDecimal amountPaid, BillingConstants.InvoiceStatus status,
        LocalDate dueDate, Instant issuedAt) {
}
```

`dto/PaymentResponse.java`:

```java
package com.eduerp.modules.billing.dto;

import com.eduerp.modules.billing.BillingConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * KHÔNG chứa {@code gatewayTransactionId} và không chứa dữ liệu cá nhân nào - endpoint tra trạng
 * thái là public (Task 21/22), nên payload phải an toàn khi ai cũng gọi được bằng một
 * {@code gatewayTransactionId} hợp lệ.
 */
public record PaymentResponse(UUID id, BigDecimal amount, BillingConstants.PaymentMethod method,
        BillingConstants.PaymentStatus status, Instant paidAt, Instant createdAt) {
}
```

`dto/InvoiceDetailResponse.java`:

```java
package com.eduerp.modules.billing.dto;

import java.util.List;

/** Mirror {@code PayrollRunDetailResponse(run, payslips)}: tổng thể + danh sách con. */
public record InvoiceDetailResponse(InvoiceResponse invoice, List<PaymentResponse> payments) {
}
```

`dto/InitiateOnlinePaymentRequest.java`:

```java
package com.eduerp.modules.billing.dto;

import com.eduerp.modules.billing.BillingConstants;
import jakarta.validation.constraints.NotNull;

public record InitiateOnlinePaymentRequest(@NotNull BillingConstants.PaymentMethod gateway) {
}
```

`dto/InitiateOnlinePaymentResponse.java`:

```java
package com.eduerp.modules.billing.dto;

public record InitiateOnlinePaymentResponse(String payUrl) {
}
```

`dto/RecordManualPaymentRequest.java`:

```java
package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record RecordManualPaymentRequest(@NotNull @Positive BigDecimal amount) {
}
```

- [ ] **Step 7: Register the module in `ModularityTests`**

Thêm `"modules.billing"` vào `containsExactlyInAnyOrder` ngay sau `"modules.enrollment"`:

```java
        assertThat(detected).containsExactlyInAnyOrder("core", "shared", "modules.identity", "modules.access",
                "modules.organization", "modules.audit", "modules.dashboard", "modules.courses",
                "modules.teachers", "modules.students", "modules.payroll", "modules.enrollment",
                "modules.billing", "integrations.cache", "integrations.mail", "integrations.notification",
                "integrations.payment", "integrations.storage");
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest='BillingConstantsTest,BillingExceptionTest,ModularityTests'`
Expected: PASS (4 + 12 + 2 tests).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing backend/src/test/java/com/eduerp/modules/billing \
  backend/src/test/java/com/eduerp/ModularityTests.java
git commit -m "feat(billing): scaffold module with constants, exceptions, events and DTOs"
```

---

### Task 12: `Invoice` + `Payment` entities, `BillingRules`, repositories, migration V18

**Files:**
- Create: `backend/src/main/resources/db/migration/V18__create_billing_tables.sql`
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/rules/BillingRules.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/model/Invoice.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/model/Payment.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/repository/InvoiceRepository.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/repository/PaymentRepository.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/internal/rules/BillingRulesTest.java` (create)
- Test: `backend/src/test/java/com/eduerp/modules/billing/internal/repository/BillingRepositoryIT.java` (create)

**Interfaces:**
- Consumes: `BillingConstants` (Task 11), bảng `enrollments` (Task 5), `student_profiles`/`courses`/`branches`/`accounts`.
- Produces:
  `BillingRules.gatewayOrderId(UUID invoiceId, int installmentNumber, long epochMilli): String`,
  `BillingRules.remaining(BigDecimal amount, BigDecimal amountPaid): BigDecimal`,
  `BillingRules.statusAfterPayment(BigDecimal amount, BigDecimal amountPaid): BillingConstants.InvoiceStatus`,
  `BillingRules.isPayable(BillingConstants.InvoiceStatus status): boolean`,
  `BillingRules.money(BigDecimal value): BigDecimal`;
  `Invoice(UUID enrollmentId, UUID studentProfileId, UUID courseId, UUID branchId, int installmentNumber, BigDecimal amount, LocalDate dueDate, UUID createdByAccountId)` + `applyPayment(BigDecimal)`, `markOverdue()`, `cancel()`;
  `Payment(Invoice invoice, BigDecimal amount, BillingConstants.PaymentMethod method, String gatewayTransactionId, BillingConstants.PaymentStatus status)` + `markSucceeded()`, `markFailed()`;
  `InvoiceRepository.countByEnrollmentIdAndStatusNot`, `.findAllByEnrollmentIdAndStatusNot`, `.findAllByStatusInAndDueDateBefore`, `.search(UUID, UUID, BillingConstants.InvoiceStatus, Pageable)`;
  `PaymentRepository.findByGatewayTransactionId`, `.findAllByInvoice_IdOrderByCreatedAtDesc`.

- [ ] **Step 1: Write the failing rules test** — `backend/src/test/java/com/eduerp/modules/billing/internal/rules/BillingRulesTest.java`:

```java
package com.eduerp.modules.billing.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.billing.BillingConstants;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure, 100% unit-test được bằng new, không I/O - mirror {@code PayrollRulesTest}. */
class BillingRulesTest {

    @Test
    void moneyRoundsToWholeDongBecauseVndHasNoSubUnitInThisSystem() {
        assertThat(BillingRules.money(new BigDecimal("1000000.49"))).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(BillingRules.money(new BigDecimal("1000000.50"))).isEqualByComparingTo(new BigDecimal("1000001"));
        assertThat(BillingRules.money(new BigDecimal("1000000")).scale()).isZero();
    }

    @Test
    void remainingIsAmountMinusAmountPaid() {
        assertThat(BillingRules.remaining(new BigDecimal("6000000"), new BigDecimal("2000000")))
                .isEqualByComparingTo(new BigDecimal("4000000"));
    }

    @Test
    void statusIsPartiallyPaidWhileSomethingIsStillOwed() {
        assertThat(BillingRules.statusAfterPayment(new BigDecimal("6000000"), new BigDecimal("2000000")))
                .isEqualTo(BillingConstants.InvoiceStatus.PARTIALLY_PAID);
    }

    @Test
    void statusIsPaidWhenPaidExactlyInFull() {
        assertThat(BillingRules.statusAfterPayment(new BigDecimal("6000000"), new BigDecimal("6000000")))
                .isEqualTo(BillingConstants.InvoiceStatus.PAID);
    }

    /** Thanh toán vượt (cổng trả về số lớn hơn) vẫn là PAID, không quay lại PARTIALLY_PAID. */
    @Test
    void statusIsPaidWhenOverpaid() {
        assertThat(BillingRules.statusAfterPayment(new BigDecimal("6000000"), new BigDecimal("6000001")))
                .isEqualTo(BillingConstants.InvoiceStatus.PAID);
    }

    @Test
    void onlyUnpaidAndPartiallyPaidInvoicesArePayable() {
        assertThat(BillingRules.isPayable(BillingConstants.InvoiceStatus.UNPAID)).isTrue();
        assertThat(BillingRules.isPayable(BillingConstants.InvoiceStatus.PARTIALLY_PAID)).isTrue();
        assertThat(BillingRules.isPayable(BillingConstants.InvoiceStatus.PAID)).isFalse();
        assertThat(BillingRules.isPayable(BillingConstants.InvoiceStatus.CANCELLED)).isFalse();
    }

    /** Hoá đơn quá hạn vẫn phải thu được - OVERDUE chỉ là nhãn nhắc nợ, không phải khoá sổ. */
    @Test
    void overdueInvoicesRemainPayable() {
        assertThat(BillingRules.isPayable(BillingConstants.InvoiceStatus.OVERDUE)).isTrue();
    }

    /** VNPay yêu cầu mã giao dịch không trùng trong ngày - millis làm cho mỗi lần bấm là một mã mới,
     * kể cả khi người dùng bấm "Thu online" hai lần trên cùng một hoá đơn. */
    @Test
    void gatewayOrderIdCombinesInvoiceInstallmentAndTimestamp() {
        var invoiceId = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

        assertThat(BillingRules.gatewayOrderId(invoiceId, 2, 1767222123000L))
                .isEqualTo("0f8fad5b-d9cb-469f-a165-70867728950e-2-1767222123000");
    }

    @Test
    void gatewayOrderIdDiffersBetweenTwoAttemptsOnTheSameInvoice() {
        var invoiceId = UUID.randomUUID();

        assertThat(BillingRules.gatewayOrderId(invoiceId, 1, 1L))
                .isNotEqualTo(BillingRules.gatewayOrderId(invoiceId, 1, 2L));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=BillingRulesTest`
Expected: FAIL — compile error, `BillingRules` chưa tồn tại.

- [ ] **Step 3: Write `BillingRules`**

`modules/billing/internal/rules/BillingRules.java`:

```java
package com.eduerp.modules.billing.internal.rules;

import com.eduerp.modules.billing.BillingConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.UUID;

/** Pure, 100% unit-test được bằng new, không I/O - mirror {@code PayrollRules}. */
public final class BillingRules {

    /** Mọi cột tiền của billing là NUMERIC(14,0) - số trả về client phải khớp số sẽ lưu xuống, nếu
     * không thì con số người dùng thấy lúc nhập lệch con số đối soát sau khi tải lại. */
    private static final int MONEY_SCALE = 0;

    /** OVERDUE vẫn thu được: đó là nhãn nhắc nợ, không phải khoá sổ (chỉ PAID/CANCELLED mới chặn). */
    private static final Set<BillingConstants.InvoiceStatus> PAYABLE_STATUSES = Set.of(
            BillingConstants.InvoiceStatus.UNPAID, BillingConstants.InvoiceStatus.PARTIALLY_PAID,
            BillingConstants.InvoiceStatus.OVERDUE);

    private BillingRules() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal remaining(BigDecimal amount, BigDecimal amountPaid) {
        return money(amount.subtract(amountPaid));
    }

    public static BillingConstants.InvoiceStatus statusAfterPayment(BigDecimal amount, BigDecimal amountPaid) {
        return amountPaid.compareTo(amount) >= 0
                ? BillingConstants.InvoiceStatus.PAID : BillingConstants.InvoiceStatus.PARTIALLY_PAID;
    }

    public static boolean isPayable(BillingConstants.InvoiceStatus status) {
        return PAYABLE_STATUSES.contains(status);
    }

    /** VNPay đòi mã giao dịch không trùng trong ngày (spec mục 5) - millis đảm bảo điều đó ngay cả
     * khi cùng một hoá đơn được bấm thu online nhiều lần. */
    public static String gatewayOrderId(UUID invoiceId, int installmentNumber, long epochMilli) {
        return invoiceId + "-" + installmentNumber + "-" + epochMilli;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=BillingRulesTest`
Expected: PASS (9 tests).

- [ ] **Step 5: Write the failing repository test** — `backend/src/test/java/com/eduerp/modules/billing/internal/repository/BillingRepositoryIT.java`:

```java
package com.eduerp.modules.billing.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.model.Payment;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class BillingRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    InvoiceRepository invoices;

    @Autowired
    PaymentRepository payments;

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

    private UUID enrollmentId;
    private UUID studentProfileId;
    private UUID courseId;
    private UUID branchId;
    private UUID actorId;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @BeforeEach
    void seedEnrollment() {
        branchId = branches.save(new Branch("BIL-" + shortId(), "Chi nhánh billing", null)).getId();
        actorId = accounts.save(new Account("bil-actor-" + shortId() + "@eduerp.local", "hash", "Actor", null))
                .getId();
        var course = courses.save(new Course("BIL-C-" + shortId(), "Khoá billing", null, 24,
                new BigDecimal("12000000")));
        courseId = course.getId();
        var teacherId = accounts.save(new Account("bil-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        var classId = classes.save(new Class(course, "BIL-K-" + shortId(), branchId, teacherId, 20)).getId();
        var studentAccountId = accounts
                .save(new Account("bil-hv-" + shortId() + "@eduerp.local", "hash", "HV", null)).getId();
        studentProfileId = profiles.save(new StudentProfile(studentAccountId, null, null, null)).getId();
        enrollmentId = enrollments
                .save(new Enrollment(studentProfileId, classId, courseId, branchId, actorId)).getId();
    }

    private Invoice newInvoice(int installmentNumber, String amount) {
        return new Invoice(enrollmentId, studentProfileId, courseId, branchId, installmentNumber,
                new BigDecimal(amount), LocalDate.of(2026, 11, 30), actorId);
    }

    @Test
    void savesAnInvoiceAsUnpaidWithZeroPaid() {
        var saved = invoices.save(newInvoice(1, "6000000"));

        var found = invoices.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.UNPAID);
        assertThat(found.getAmountPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(found.getInstallmentNumber()).isEqualTo(1);
        assertThat(found.getIssuedAt()).isNotNull();
    }

    @Test
    void countsAndSumsOnlyInvoicesThatAreNotCancelled() {
        invoices.saveAndFlush(newInvoice(1, "6000000"));
        var cancelled = invoices.saveAndFlush(newInvoice(2, "6000000"));
        cancelled.cancel();
        invoices.saveAndFlush(cancelled);

        assertThat(invoices.countByEnrollmentIdAndStatusNot(enrollmentId,
                BillingConstants.InvoiceStatus.CANCELLED)).isEqualTo(1);
        var live = invoices.findAllByEnrollmentIdAndStatusNot(enrollmentId,
                BillingConstants.InvoiceStatus.CANCELLED);
        assertThat(live).singleElement()
                .satisfies(invoice -> assertThat(invoice.getAmount()).isEqualByComparingTo(new BigDecimal("6000000")));
    }

    @Test
    void findsOverdueCandidatesByStatusAndDueDate() {
        var pastDue = invoices.saveAndFlush(new Invoice(enrollmentId, studentProfileId, courseId, branchId, 1,
                new BigDecimal("6000000"), LocalDate.of(2020, 1, 1), actorId));
        invoices.saveAndFlush(new Invoice(enrollmentId, studentProfileId, courseId, branchId, 2,
                new BigDecimal("6000000"), LocalDate.of(2099, 1, 1), actorId));

        var candidates = invoices.findAllByStatusInAndDueDateBefore(
                List.of(BillingConstants.InvoiceStatus.UNPAID, BillingConstants.InvoiceStatus.PARTIALLY_PAID),
                LocalDate.of(2026, 10, 3));

        assertThat(candidates).extracting(Invoice::getId).containsExactly(pastDue.getId());
    }

    @Test
    void searchFiltersByEveryCombinationOfStudentEnrollmentAndStatus() {
        var unpaid = invoices.saveAndFlush(newInvoice(1, "6000000"));
        var cancelled = invoices.saveAndFlush(newInvoice(2, "6000000"));
        cancelled.cancel();
        invoices.saveAndFlush(cancelled);
        var pageable = PageRequest.of(0, 20);

        assertThat(invoices.search(null, null, null, pageable).getTotalElements()).isEqualTo(2);
        assertThat(invoices.search(studentProfileId, null, null, pageable).getTotalElements()).isEqualTo(2);
        assertThat(invoices.search(null, enrollmentId, null, pageable).getTotalElements()).isEqualTo(2);
        assertThat(invoices.search(null, null, BillingConstants.InvoiceStatus.UNPAID, pageable).getContent())
                .extracting(Invoice::getId).containsExactly(unpaid.getId());
        assertThat(invoices.search(studentProfileId, enrollmentId, BillingConstants.InvoiceStatus.CANCELLED,
                pageable).getTotalElements()).isEqualTo(1);
        assertThat(invoices.search(UUID.randomUUID(), null, null, pageable).getTotalElements()).isZero();
    }

    @Test
    void savesPaymentsAgainstAnInvoiceNewestFirst() {
        var invoice = invoices.saveAndFlush(newInvoice(1, "6000000"));
        payments.saveAndFlush(new Payment(invoice, new BigDecimal("2000000"),
                BillingConstants.PaymentMethod.MANUAL, null, BillingConstants.PaymentStatus.SUCCESS));
        payments.saveAndFlush(new Payment(invoice, new BigDecimal("4000000"),
                BillingConstants.PaymentMethod.MOMO, "order-newest", BillingConstants.PaymentStatus.PENDING));

        var history = payments.findAllByInvoice_IdOrderByCreatedAtDesc(invoice.getId());

        assertThat(history).hasSize(2);
        assertThat(payments.findByGatewayTransactionId("order-newest")).isPresent();
        assertThat(payments.findByGatewayTransactionId("order-unknown")).isEmpty();
    }

    /** Hai bản ghi MANUAL cùng gatewayTransactionId = NULL phải cùng tồn tại - unique index trên cột
     * nullable của Postgres không coi NULL là trùng nhau, và đó chính là hành vi cần. */
    @Test
    void allowsManyManualPaymentsWithoutAGatewayTransactionId() {
        var invoice = invoices.saveAndFlush(newInvoice(1, "6000000"));
        payments.saveAndFlush(new Payment(invoice, new BigDecimal("1000000"),
                BillingConstants.PaymentMethod.MANUAL, null, BillingConstants.PaymentStatus.SUCCESS));

        payments.saveAndFlush(new Payment(invoice, new BigDecimal("1000000"),
                BillingConstants.PaymentMethod.MANUAL, null, BillingConstants.PaymentStatus.SUCCESS));

        assertThat(payments.findAllByInvoice_IdOrderByCreatedAtDesc(invoice.getId())).hasSize(2);
    }

    /** Lớp phòng thủ cho Review Focus #4 ở tầng DB: một orderId chỉ sinh được một bản ghi Payment,
     * nên callback gọi lại không thể tạo thêm bản ghi thứ hai cho cùng giao dịch. */
    @Test
    void rejectsADuplicateGatewayTransactionIdAtDatabaseLevel() {
        var invoice = invoices.saveAndFlush(newInvoice(1, "6000000"));
        payments.saveAndFlush(new Payment(invoice, new BigDecimal("6000000"),
                BillingConstants.PaymentMethod.VNPAY, "order-dup", BillingConstants.PaymentStatus.PENDING));

        assertThatThrownBy(() -> payments.saveAndFlush(new Payment(invoice, new BigDecimal("6000000"),
                BillingConstants.PaymentMethod.VNPAY, "order-dup", BillingConstants.PaymentStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=BillingRepositoryIT`
Expected: FAIL — compile error, `Invoice`/`Payment`/repository chưa tồn tại.

- [ ] **Step 7: Write the migration**

`backend/src/main/resources/db/migration/V18__create_billing_tables.sql`:

```sql
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES enrollments(id),
    -- student_profile_id/course_id/branch_id là snapshot chốt lúc phát hành (spec mục 5): FK mức DB
    -- để toàn vẹn dữ liệu, không có @ManyToOne trong Java (rule #3 - module khác).
    student_profile_id UUID NOT NULL REFERENCES student_profiles(id),
    course_id UUID NOT NULL REFERENCES courses(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    installment_number INT NOT NULL,
    amount NUMERIC(14,0) NOT NULL,
    amount_paid NUMERIC(14,0) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'UNPAID',
    due_date DATE NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    created_by_account_id UUID NOT NULL REFERENCES accounts(id)
);

CREATE INDEX idx_invoices_enrollment ON invoices (enrollment_id);
CREATE INDEX idx_invoices_student ON invoices (student_profile_id);
-- Job quét quá hạn (MarkOverdueInvoices) lọc đúng theo (status, due_date); không có index này thì
-- mỗi 1h sáng là một lần quét toàn bảng hoá đơn.
CREATE INDEX idx_invoices_status_due_date ON invoices (status, due_date);

-- Giới hạn 3 đợt/ghi danh được enforce ở usecase (CreateInvoice, Task 13) thay vì CHECK ở đây: số
-- đợt là quyết định nghiệp vụ có thể đổi, và invoice CANCELLED không được tính vào hạn mức - logic
-- đó không diễn đạt được bằng một CHECK trên một dòng.

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    amount NUMERIC(14,0) NOT NULL,
    method VARCHAR(16) NOT NULL,
    -- UNIQUE trên cột nullable: Postgres không coi hai NULL là trùng nhau, nên nhiều bản ghi MANUAL
    -- (không có mã cổng) cùng tồn tại, còn mỗi orderId của cổng chỉ sinh được một bản ghi duy nhất -
    -- lớp phòng thủ DB cho Review Focus #4 (callback gọi 2 lần).
    gateway_transaction_id VARCHAR(128) UNIQUE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_payments_invoice ON payments (invoice_id);
```

- [ ] **Step 8: Write the two entities**

`modules/billing/internal/model/Invoice.java`:

```java
package com.eduerp.modules.billing.internal.model;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.internal.rules.BillingRules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** Một đợt thu học phí. Mọi chuyển trạng thái đi qua method của chính entity - không setter trần cho
 * {@code status}/{@code amountPaid}, để không ai cộng tiền mà quên cập nhật trạng thái. */
@Entity
@Table(name = "invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(name = "student_profile_id", nullable = false, updatable = false)
    private UUID studentProfileId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "installment_number", nullable = false, updatable = false)
    private int installmentNumber;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingConstants.InvoiceStatus status;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private UUID createdByAccountId;

    public Invoice(UUID enrollmentId, UUID studentProfileId, UUID courseId, UUID branchId, int installmentNumber,
            BigDecimal amount, LocalDate dueDate, UUID createdByAccountId) {
        this.enrollmentId = enrollmentId;
        this.studentProfileId = studentProfileId;
        this.courseId = courseId;
        this.branchId = branchId;
        this.installmentNumber = installmentNumber;
        this.amount = BillingRules.money(amount);
        this.dueDate = dueDate;
        this.createdByAccountId = createdByAccountId;
        this.amountPaid = BillingRules.money(BigDecimal.ZERO);
        this.status = BillingConstants.InvoiceStatus.UNPAID;
        this.issuedAt = Instant.now();
    }

    /** Cộng tiền và chuyển trạng thái trong cùng một lệnh - không thể cộng mà quên đổi status. */
    public void applyPayment(BigDecimal paidAmount) {
        this.amountPaid = BillingRules.money(this.amountPaid.add(paidAmount));
        this.status = BillingRules.statusAfterPayment(this.amount, this.amountPaid);
    }

    public void markOverdue() {
        this.status = BillingConstants.InvoiceStatus.OVERDUE;
    }

    public void cancel() {
        this.status = BillingConstants.InvoiceStatus.CANCELLED;
    }
}
```

`modules/billing/internal/model/Payment.java`:

```java
package com.eduerp.modules.billing.internal.model;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.internal.rules.BillingRules;
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
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/** {@code invoice} là quan hệ JPA thật - {@code Invoice} cùng module (rule #3 chỉ cấm xuyên module),
 * mirror {@code Payslip.payrollRun}. */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", updatable = false)
    private Invoice invoice;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private BillingConstants.PaymentMethod method;

    /** Mã giao dịch của cổng, null với MANUAL. UNIQUE ở DB nên hai callback cùng mã không thể tạo
     * hai bản ghi (Review Focus #4, lớp phòng thủ DB). */
    @Column(name = "gateway_transaction_id", length = 128, updatable = false)
    private String gatewayTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingConstants.PaymentStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Payment(Invoice invoice, BigDecimal amount, BillingConstants.PaymentMethod method,
            String gatewayTransactionId, BillingConstants.PaymentStatus status) {
        this.invoice = invoice;
        this.amount = BillingRules.money(amount);
        this.method = method;
        this.gatewayTransactionId = gatewayTransactionId;
        this.status = status;
        this.createdAt = Instant.now();
        if (status == BillingConstants.PaymentStatus.SUCCESS) {
            this.paidAt = this.createdAt;
        }
    }

    public void markSucceeded() {
        this.status = BillingConstants.PaymentStatus.SUCCESS;
        this.paidAt = Instant.now();
    }

    public void markFailed() {
        this.status = BillingConstants.PaymentStatus.FAILED;
    }
}
```

- [ ] **Step 9: Write the two repositories**

`modules/billing/internal/repository/InvoiceRepository.java`:

```java
package com.eduerp.modules.billing.internal.repository;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.internal.model.Invoice;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    /** Invoice CANCELLED không tính vào hạn mức 3 đợt (spec mục 5 bước 3). */
    long countByEnrollmentIdAndStatusNot(UUID enrollmentId, BillingConstants.InvoiceStatus status);

    /** Cũng loại CANCELLED khi cộng tổng để so với học phí (spec mục 5 bước 4). */
    List<Invoice> findAllByEnrollmentIdAndStatusNot(UUID enrollmentId, BillingConstants.InvoiceStatus status);

    List<Invoice> findAllByStatusInAndDueDateBefore(Collection<BillingConstants.InvoiceStatus> statuses,
            LocalDate dueDateBefore);

    /**
     * Ba filter đều optional (spec mục 5 {@code ListInvoices}). Một query với {@code :p IS NULL} thay
     * vì 8 nhánh if trong usecase - giữ complexity ở 1. {@code BillingRepositoryIT.searchFilters...}
     * phủ mọi tổ hợp, nên lỗi suy kiểu tham số null (nếu có) đỏ ngay ở test.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE (:studentProfileId IS NULL OR i.studentProfileId = :studentProfileId)
              AND (:enrollmentId IS NULL OR i.enrollmentId = :enrollmentId)
              AND (:status IS NULL OR i.status = :status)
            """)
    Page<Invoice> search(@Param("studentProfileId") UUID studentProfileId,
            @Param("enrollmentId") UUID enrollmentId,
            @Param("status") BillingConstants.InvoiceStatus status, Pageable pageable);
}
```

`modules/billing/internal/repository/PaymentRepository.java`:

```java
package com.eduerp.modules.billing.internal.repository;

import com.eduerp.modules.billing.internal.model.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId);

    /** Lịch sử thanh toán trong chi tiết hoá đơn, mới nhất trước (spec mục 5). */
    List<Payment> findAllByInvoice_IdOrderByCreatedAtDesc(UUID invoiceId);
}
```

- [ ] **Step 10: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest='BillingRulesTest,BillingRepositoryIT,ModularityTests'`
Expected: PASS (9 + 7 + 2 tests).

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/resources/db/migration/V18__create_billing_tables.sql \
  backend/src/main/java/com/eduerp/modules/billing/internal \
  backend/src/test/java/com/eduerp/modules/billing/internal
git commit -m "feat(billing): add invoice and payment entities, rules and repositories"
```

---

### Task 13: `CreateInvoice` usecase (Review Focus #3 — tổng 3 đợt vượt học phí)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/CreateInvoice.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/usecase/CreateInvoiceTest.java` (create)

**Interfaces:**
- Consumes: `EnrollmentManagement.getEnrollment` (Task 7), `CoursesManagement.getCourseTuition` (Task 2), `InvoiceRepository` (Task 12), DTO/exception/events (Task 11).
- Produces: `CreateInvoice.execute(UUID actorAccountId, UUID actorBranchId, CreateInvoiceRequest request): InvoiceResponse`;
  `CreateInvoice.toResponse(Invoice): InvoiceResponse` (static, package-private — dùng lại ở mọi usecase billing khác).

- [ ] **Step 1: Write the failing test** — `backend/src/test/java/com/eduerp/modules/billing/usecase/CreateInvoiceTest.java`:

```java
package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.CourseNotFoundException;
import com.eduerp.modules.billing.CourseTuitionNotConfiguredException;
import com.eduerp.modules.billing.EnrollmentNotActiveForBillingException;
import com.eduerp.modules.billing.EnrollmentNotFoundException;
import com.eduerp.modules.billing.InstallmentLimitExceededException;
import com.eduerp.modules.billing.InvoiceAmountExceedsTuitionException;
import com.eduerp.modules.billing.dto.CreateInvoiceRequest;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
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

class CreateInvoiceTest {

    private static final BigDecimal TUITION = new BigDecimal("12000000");
    private static final BigDecimal HALF = new BigDecimal("6000000");
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 11, 30);

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final EnrollmentManagement enrollmentManagement = mock(EnrollmentManagement.class);
    private final CoursesManagement coursesManagement = mock(CoursesManagement.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CreateInvoice useCase =
            new CreateInvoice(invoices, enrollmentManagement, coursesManagement, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID enrollmentBranchId = UUID.randomUUID();

    private CreateInvoiceRequest request(BigDecimal amount) {
        return new CreateInvoiceRequest(enrollmentId, amount, DUE_DATE);
    }

    private void stubActiveEnrollment() {
        when(enrollmentManagement.getEnrollment(enrollmentId)).thenReturn(Optional.of(
                new EnrollmentManagement.EnrollmentSummaryResponse(enrollmentId, studentProfileId, courseId,
                        enrollmentBranchId, EnrollmentConstants.EnrollmentStatus.ACTIVE)));
    }

    private void stubTuition(BigDecimal tuitionFee) {
        when(coursesManagement.getCourseTuition(courseId)).thenReturn(Optional.of(
                new CoursesManagement.CourseTuitionResponse(courseId, tuitionFee, true)));
    }

    private Invoice existingInvoice(int installmentNumber, BigDecimal amount) {
        return new Invoice(enrollmentId, studentProfileId, courseId, enrollmentBranchId, installmentNumber, amount,
                DUE_DATE, actorAccountId);
    }

    private void stubExistingInvoices(List<Invoice> existing) {
        when(invoices.countByEnrollmentIdAndStatusNot(enrollmentId, BillingConstants.InvoiceStatus.CANCELLED))
                .thenReturn((long) existing.size());
        when(invoices.findAllByEnrollmentIdAndStatusNot(enrollmentId, BillingConstants.InvoiceStatus.CANCELLED))
                .thenReturn(existing);
    }

    private void stubSaveEchoesBack() {
        when(invoices.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsAnUnknownEnrollment() {
        when(enrollmentManagement.getEnrollment(enrollmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(HALF)))
                .isInstanceOf(EnrollmentNotFoundException.class);
    }

    /** Rút khỏi lớp giữ nguyên hoá đơn cũ nhưng chặn phát hành đợt mới (spec mục 12). */
    @Test
    void rejectsAWithdrawnEnrollment() {
        when(enrollmentManagement.getEnrollment(enrollmentId)).thenReturn(Optional.of(
                new EnrollmentManagement.EnrollmentSummaryResponse(enrollmentId, studentProfileId, courseId,
                        enrollmentBranchId, EnrollmentConstants.EnrollmentStatus.WITHDRAWN)));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(HALF)))
                .isInstanceOf(EnrollmentNotActiveForBillingException.class);
    }

    @Test
    void rejectsAnEnrollmentWhoseCourseIsGone() {
        stubActiveEnrollment();
        when(coursesManagement.getCourseTuition(courseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(HALF)))
                .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void rejectsACourseWithoutATuitionFee() {
        stubActiveEnrollment();
        stubTuition(null);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(HALF)))
                .isInstanceOf(CourseTuitionNotConfiguredException.class);
    }

    @Test
    void numbersTheFirstInstallmentOneAndPublishesInvoiceCreated() {
        stubActiveEnrollment();
        stubTuition(TUITION);
        stubExistingInvoices(List.of());
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId, request(HALF));

        assertThat(response.installmentNumber()).isEqualTo(1);
        assertThat(response.enrollmentId()).isEqualTo(enrollmentId);
        assertThat(response.studentProfileId()).isEqualTo(studentProfileId);
        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.branchId()).isEqualTo(enrollmentBranchId);
        assertThat(response.amount()).isEqualByComparingTo(HALF);
        assertThat(response.amountPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.status()).isEqualTo(BillingConstants.InvoiceStatus.UNPAID);
        assertThat(response.dueDate()).isEqualTo(DUE_DATE);
        verify(events).publishEvent(any(BillingEvents.InvoiceCreated.class));
    }

    @Test
    void numbersTheSecondInstallmentTwo() {
        stubActiveEnrollment();
        stubTuition(TUITION);
        stubExistingInvoices(List.of(existingInvoice(1, HALF)));
        stubSaveEchoesBack();

        assertThat(useCase.execute(actorAccountId, actorBranchId, request(new BigDecimal("3000000")))
                .installmentNumber()).isEqualTo(2);
    }

    /**
     * Review Focus #3: đợt 1 = 50%, đợt 2 = 50% thì đợt 3 với SỐ TIỀN DƯƠNG BẤT KỲ phải bị chặn vì
     * vượt học phí - lỗi đúng phải là INVOICE_AMOUNT_EXCEEDS_TUITION, KHÔNG phải
     * INSTALLMENT_LIMIT_EXCEEDED (hạn mức 3 đợt chưa chạm, mới có 2 đợt).
     */
    @Test
    void rejectsAThirdInstallmentOfAnyAmountOnceTheTuitionIsFullyInvoiced() {
        stubActiveEnrollment();
        stubTuition(TUITION);
        stubExistingInvoices(List.of(existingInvoice(1, HALF), existingInvoice(2, HALF)));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(new BigDecimal("1"))))
                .isInstanceOf(InvoiceAmountExceedsTuitionException.class)
                .hasMessageContaining("12000000");
        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(new BigDecimal("1000000"))))
                .isInstanceOf(InvoiceAmountExceedsTuitionException.class);
    }

    /** Ranh giới: tổng đúng bằng học phí vẫn phải được phát hành, chỉ vượt mới bị chặn. */
    @Test
    void acceptsAnInstallmentThatExactlyCompletesTheTuition() {
        stubActiveEnrollment();
        stubTuition(TUITION);
        stubExistingInvoices(List.of(existingInvoice(1, HALF)));
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId, request(HALF));

        assertThat(response.amount()).isEqualByComparingTo(HALF);
    }

    /** Hạn mức 3 đợt là một lỗi RIÊNG, chỉ gặp khi tổng tiền còn chỗ mà số đợt đã hết (ví dụ 3 đợt
     * nhỏ chưa dùng hết học phí) - nếu không tách, người dùng nhận thông báo sai nguyên nhân. */
    @Test
    void rejectsAFourthInstallmentEvenWhenTuitionBudgetRemains() {
        stubActiveEnrollment();
        stubTuition(TUITION);
        stubExistingInvoices(List.of(existingInvoice(1, new BigDecimal("1000000")),
                existingInvoice(2, new BigDecimal("1000000")), existingInvoice(3, new BigDecimal("1000000"))));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(new BigDecimal("1000000"))))
                .isInstanceOf(InstallmentLimitExceededException.class)
                .hasMessageContaining("3");
    }

    /** Hoá đơn đã huỷ không chiếm chỗ trong 3 đợt và không tính vào tổng (spec mục 5). */
    @Test
    void ignoresCancelledInvoicesInBothTheCountAndTheSum() {
        stubActiveEnrollment();
        stubTuition(TUITION);
        // Repository đã loại CANCELLED, nên usecase chỉ thấy 1 hoá đơn còn sống.
        stubExistingInvoices(List.of(existingInvoice(1, HALF)));
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId, request(HALF));

        assertThat(response.installmentNumber()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(BillingConstants.InvoiceStatus.UNPAID);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=CreateInvoiceTest`
Expected: FAIL — compile error, `CreateInvoice` chưa tồn tại.

- [ ] **Step 3: Write the usecase**

`modules/billing/usecase/CreateInvoice.java`:

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.CourseNotFoundException;
import com.eduerp.modules.billing.CourseTuitionNotConfiguredException;
import com.eduerp.modules.billing.EnrollmentNotActiveForBillingException;
import com.eduerp.modules.billing.EnrollmentNotFoundException;
import com.eduerp.modules.billing.InstallmentLimitExceededException;
import com.eduerp.modules.billing.InvoiceAmountExceedsTuitionException;
import com.eduerp.modules.billing.dto.CreateInvoiceRequest;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thứ tự kiểm tra đúng theo spec mục 5: ghi danh → học phí khoá → hạn mức 3 đợt → tổng tiền → lưu.
 * Hai lỗi "hết đợt" và "vượt học phí" là hai lỗi KHÁC NHAU (Review Focus #3) - kế toán cần biết
 * mình đang chạm giới hạn nào.
 */
@Service
public class CreateInvoice {

    private final InvoiceRepository invoices;
    private final EnrollmentManagement enrollmentManagement;
    private final CoursesManagement coursesManagement;
    private final ApplicationEventPublisher events;

    CreateInvoice(InvoiceRepository invoices, EnrollmentManagement enrollmentManagement,
            CoursesManagement coursesManagement, ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.enrollmentManagement = enrollmentManagement;
        this.coursesManagement = coursesManagement;
        this.events = events;
    }

    @Transactional
    public InvoiceResponse execute(UUID actorAccountId, UUID actorBranchId, CreateInvoiceRequest request) {
        var enrollment = enrollmentManagement.getEnrollment(request.enrollmentId())
                .orElseThrow(() -> new EnrollmentNotFoundException(request.enrollmentId()));
        if (enrollment.status() != EnrollmentConstants.EnrollmentStatus.ACTIVE) {
            throw new EnrollmentNotActiveForBillingException(request.enrollmentId());
        }

        var tuition = coursesManagement.getCourseTuition(enrollment.courseId())
                .orElseThrow(() -> new CourseNotFoundException(enrollment.courseId()));
        if (tuition.tuitionFee() == null) {
            throw new CourseTuitionNotConfiguredException(enrollment.courseId());
        }

        var liveInvoiceCount = invoices.countByEnrollmentIdAndStatusNot(request.enrollmentId(),
                BillingConstants.InvoiceStatus.CANCELLED);
        if (liveInvoiceCount >= BillingConstants.Limits.MAX_INSTALLMENTS_PER_ENROLLMENT) {
            throw new InstallmentLimitExceededException(request.enrollmentId(),
                    BillingConstants.Limits.MAX_INSTALLMENTS_PER_ENROLLMENT);
        }

        var alreadyInvoiced = invoices
                .findAllByEnrollmentIdAndStatusNot(request.enrollmentId(), BillingConstants.InvoiceStatus.CANCELLED)
                .stream().map(Invoice::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var totalAfterThisInvoice = alreadyInvoiced.add(request.amount());
        if (totalAfterThisInvoice.compareTo(tuition.tuitionFee()) > 0) {
            throw new InvoiceAmountExceedsTuitionException(totalAfterThisInvoice, tuition.tuitionFee());
        }

        var installmentNumber = Math.toIntExact(liveInvoiceCount) + 1;
        var saved = invoices.save(new Invoice(request.enrollmentId(), enrollment.studentProfileId(),
                enrollment.courseId(), enrollment.branchId(), installmentNumber, request.amount(),
                request.dueDate(), actorAccountId));
        events.publishEvent(new BillingEvents.InvoiceCreated(saved.getId(), actorAccountId, actorBranchId));
        return toResponse(saved);
    }

    /** Dùng lại ở mọi usecase billing khác - một chỗ map duy nhất (mirror CreateEnrollment.toResponse). */
    static InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(invoice.getId(), invoice.getEnrollmentId(), invoice.getStudentProfileId(),
                invoice.getCourseId(), invoice.getBranchId(), invoice.getInstallmentNumber(), invoice.getAmount(),
                invoice.getAmountPaid(), invoice.getStatus(), invoice.getDueDate(), invoice.getIssuedAt());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=CreateInvoiceTest`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/usecase/CreateInvoice.java \
  backend/src/test/java/com/eduerp/modules/billing/usecase/CreateInvoiceTest.java
git commit -m "feat(billing): add create invoice use case with installment and tuition ceiling guards"
```

---

### Task 14: `PaymentGatewayClientResolver` + `InitiateOnlinePayment`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/PaymentGatewayClientResolver.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/InitiateOnlinePayment.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/internal/PaymentGatewayClientResolverTest.java` (create)
- Test: `backend/src/test/java/com/eduerp/modules/billing/usecase/InitiateOnlinePaymentTest.java` (create)

**Interfaces:**
- Consumes: `PaymentGatewayClient`/`PaymentGatewayType`/`PaymentRequest`/`PaymentUrlResult` (Task 8), `BillingRules` + `Invoice`/`Payment` + repositories (Task 12), DTO/exception (Task 11).
- Produces:
  `PaymentGatewayClientResolver(List<PaymentGatewayClient> clients)` (`@Component`) với `resolve(BillingConstants.PaymentMethod method): PaymentGatewayClient`;
  `InitiateOnlinePayment.execute(UUID invoiceId, BillingConstants.PaymentMethod gateway, UUID actorAccountId, UUID actorBranchId): InitiateOnlinePaymentResponse`.

- [ ] **Step 1: Write the failing resolver test** — `backend/src/test/java/com/eduerp/modules/billing/internal/PaymentGatewayClientResolverTest.java`:

```java
package com.eduerp.modules.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.integrations.payment.PaymentCallbackResult;
import com.eduerp.integrations.payment.PaymentGatewayClient;
import com.eduerp.integrations.payment.PaymentGatewayType;
import com.eduerp.integrations.payment.PaymentRequest;
import com.eduerp.integrations.payment.PaymentUrlResult;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.UnknownPaymentGatewayException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaymentGatewayClientResolverTest {

    /** Client giả, không gọi mạng - chỉ cần type() đúng để kiểm tra bảng map. */
    private record StubClient(PaymentGatewayType type) implements PaymentGatewayClient {
        @Override
        public PaymentUrlResult createPaymentUrl(PaymentRequest request) {
            return new PaymentUrlResult("https://stub/" + type, request.orderId());
        }

        @Override
        public PaymentCallbackResult verifyCallback(Map<String, String> rawParams) {
            throw new UnsupportedOperationException();
        }
    }

    private final PaymentGatewayClientResolver resolver = new PaymentGatewayClientResolver(
            List.of(new StubClient(PaymentGatewayType.MOMO), new StubClient(PaymentGatewayType.VNPAY)));

    @Test
    void resolvesEachOnlineMethodToTheMatchingClient() {
        assertThat(resolver.resolve(BillingConstants.PaymentMethod.MOMO).type())
                .isEqualTo(PaymentGatewayType.MOMO);
        assertThat(resolver.resolve(BillingConstants.PaymentMethod.VNPAY).type())
                .isEqualTo(PaymentGatewayType.VNPAY);
    }

    @Test
    void refusesManualBecauseItIsNotAGateway() {
        assertThatThrownBy(() -> resolver.resolve(BillingConstants.PaymentMethod.MANUAL))
                .isInstanceOf(UnknownPaymentGatewayException.class)
                .hasMessageContaining("MANUAL");
    }

    /** Nếu một client bị thiếu bean (cấu hình sai, profile tắt), lỗi phải là lỗi nghiệp vụ rõ ràng,
     * không phải NullPointerException ở giữa luồng thanh toán. */
    @Test
    void refusesAMethodWhoseClientBeanIsMissing() {
        var partial = new PaymentGatewayClientResolver(List.of(new StubClient(PaymentGatewayType.MOMO)));

        assertThatThrownBy(() -> partial.resolve(BillingConstants.PaymentMethod.VNPAY))
                .isInstanceOf(UnknownPaymentGatewayException.class);
    }
}
```

- [ ] **Step 2: Write the failing usecase test** — `backend/src/test/java/com/eduerp/modules/billing/usecase/InitiateOnlinePaymentTest.java`:

```java
package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.integrations.payment.PaymentGatewayClient;
import com.eduerp.integrations.payment.PaymentRequest;
import com.eduerp.integrations.payment.PaymentUrlResult;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.InvoiceNotPayableException;
import com.eduerp.modules.billing.internal.PaymentGatewayClientResolver;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class InitiateOnlinePaymentTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentGatewayClientResolver resolver = mock(PaymentGatewayClientResolver.class);
    private final PaymentGatewayClient client = mock(PaymentGatewayClient.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final InitiateOnlinePayment useCase =
            new InitiateOnlinePayment(invoices, payments, resolver, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();

    private Invoice invoiceOf(String amount) {
        return new Invoice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2,
                new BigDecimal(amount), LocalDate.of(2026, 11, 30), actorAccountId);
    }

    private void stubGateway() {
        when(resolver.resolve(BillingConstants.PaymentMethod.VNPAY)).thenReturn(client);
        when(client.createPaymentUrl(any(PaymentRequest.class))).thenAnswer(invocation -> {
            PaymentRequest request = invocation.getArgument(0);
            return new PaymentUrlResult("https://sandbox.vnpayment.vn/pay?ref=" + request.orderId(),
                    request.orderId());
        });
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsAnUnknownInvoice() {
        var invoiceId = UUID.randomUUID();
        when(invoices.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(invoiceId, BillingConstants.PaymentMethod.VNPAY, actorAccountId,
                actorBranchId)).isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void rejectsAFullyPaidInvoice() {
        var invoice = invoiceOf("6000000");
        invoice.applyPayment(new BigDecimal("6000000"));
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), BillingConstants.PaymentMethod.VNPAY,
                actorAccountId, actorBranchId)).isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void rejectsACancelledInvoice() {
        var invoice = invoiceOf("6000000");
        invoice.cancel();
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), BillingConstants.PaymentMethod.VNPAY,
                actorAccountId, actorBranchId)).isInstanceOf(InvoiceNotPayableException.class);
    }

    /** Số tiền gửi sang cổng là phần CÒN LẠI, không phải tổng hoá đơn - nếu không, học viên đã đóng
     * một phần sẽ bị thu lại toàn bộ. */
    @Test
    void chargesOnlyTheRemainingAmountAndStoresAPendingPayment() {
        var invoice = invoiceOf("6000000");
        invoice.applyPayment(new BigDecimal("2000000"));
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        stubGateway();

        var response = useCase.execute(invoice.getId(), BillingConstants.PaymentMethod.VNPAY, actorAccountId,
                actorBranchId);

        var saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("4000000"));
        assertThat(saved.getValue().getMethod()).isEqualTo(BillingConstants.PaymentMethod.VNPAY);
        assertThat(saved.getValue().getStatus()).isEqualTo(BillingConstants.PaymentStatus.PENDING);
        assertThat(saved.getValue().getPaidAt()).isNull();
        assertThat(response.payUrl()).contains(saved.getValue().getGatewayTransactionId());
    }

    /** orderId phải bắt đầu bằng invoiceId + số đợt (BillingRules.gatewayOrderId) - callback tra
     * ngược về Payment bằng đúng chuỗi này. */
    @Test
    void buildsAGatewayOrderIdFromTheInvoiceAndInstallment() {
        var invoice = invoiceOf("6000000");
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        stubGateway();

        useCase.execute(invoice.getId(), BillingConstants.PaymentMethod.VNPAY, actorAccountId, actorBranchId);

        var saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().getGatewayTransactionId())
                .startsWith(invoice.getId() + "-" + invoice.getInstallmentNumber() + "-");
    }

    /** Chưa có tiền thật nào vào thì chưa có gì để audit - event chỉ phát khi callback xác nhận
     * (spec mục 5 bước 7). */
    @Test
    void publishesNoEventBecauseNoMoneyHasArrivedYet() {
        var invoice = invoiceOf("6000000");
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        stubGateway();

        useCase.execute(invoice.getId(), BillingConstants.PaymentMethod.VNPAY, actorAccountId, actorBranchId);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void sendsTheInstallmentNumberInTheOrderInfoShownOnTheGateway() {
        var invoice = invoiceOf("6000000");
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        stubGateway();

        useCase.execute(invoice.getId(), BillingConstants.PaymentMethod.VNPAY, actorAccountId, actorBranchId);

        var sent = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(client).createPaymentUrl(sent.capture());
        assertThat(sent.getValue().orderInfo())
                .isEqualTo(BillingConstants.OrderInfo.PREFIX + invoice.getInstallmentNumber());
        assertThat(sent.getValue().returnUrl()).isNull();
        assertThat(sent.getValue().ipnUrl()).isNull();
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd backend && mvn test -Dtest='PaymentGatewayClientResolverTest,InitiateOnlinePaymentTest'`
Expected: FAIL — compile error, `PaymentGatewayClientResolver`/`InitiateOnlinePayment` chưa tồn tại.

- [ ] **Step 4: Write the resolver**

`modules/billing/internal/PaymentGatewayClientResolver.java`:

```java
package com.eduerp.modules.billing.internal;

import com.eduerp.integrations.payment.PaymentGatewayClient;
import com.eduerp.integrations.payment.PaymentGatewayType;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.UnknownPaymentGatewayException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Cầu nối duy nhất giữa từ vựng nghiệp vụ ({@code BillingConstants.PaymentMethod}) và từ vựng hạ
 * tầng ({@code PaymentGatewayType}). Spring đưa vào mọi {@code PaymentGatewayClient} có trong
 * context; {@code MANUAL} không phải cổng nào nên luôn bị từ chối (spec mục 6).
 *
 * <p>Map bằng {@code name()} chứ không bằng một bảng viết tay - {@code BillingConstantsTest} chốt
 * rằng hai enum trùng tên, nên một cổng mới thêm vào là tự động hoạt động.
 */
@Component
public class PaymentGatewayClientResolver {

    private final Map<PaymentGatewayType, PaymentGatewayClient> clientsByType =
            new EnumMap<>(PaymentGatewayType.class);

    PaymentGatewayClientResolver(List<PaymentGatewayClient> clients) {
        clients.forEach(client -> clientsByType.put(client.type(), client));
    }

    public PaymentGatewayClient resolve(BillingConstants.PaymentMethod method) {
        var client = clientsByType.get(toGatewayType(method));
        if (client == null) {
            throw new UnknownPaymentGatewayException(method);
        }
        return client;
    }

    private static PaymentGatewayType toGatewayType(BillingConstants.PaymentMethod method) {
        try {
            return PaymentGatewayType.valueOf(method.name());
        } catch (IllegalArgumentException notAGateway) {
            // MANUAL (hoặc một method tương lai không có cổng tương ứng) - trả null để nhánh kiểm
            // tra bên trên ném đúng lỗi nghiệp vụ thay vì để IllegalArgumentException lọt ra HTTP.
            return null;
        }
    }
}
```

- [ ] **Step 5: Write the usecase**

`modules/billing/usecase/InitiateOnlinePayment.java`:

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.integrations.payment.PaymentRequest;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.InvoiceNotPayableException;
import com.eduerp.modules.billing.dto.InitiateOnlinePaymentResponse;
import com.eduerp.modules.billing.internal.PaymentGatewayClientResolver;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import com.eduerp.modules.billing.internal.rules.BillingRules;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo link/QR thanh toán cho phần CÒN LẠI của hoá đơn và ghi một {@code Payment} ở trạng thái
 * {@code PENDING}. KHÔNG publish event ở đây: tiền chưa thật, chỉ callback/IPN mới là nguồn sự thật
 * (spec mục 5 bước 7).
 */
@Service
public class InitiateOnlinePayment {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final PaymentGatewayClientResolver gateways;
    private final ApplicationEventPublisher events;

    InitiateOnlinePayment(InvoiceRepository invoices, PaymentRepository payments,
            PaymentGatewayClientResolver gateways, ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.payments = payments;
        this.gateways = gateways;
        this.events = events;
    }

    @Transactional
    public InitiateOnlinePaymentResponse execute(UUID invoiceId, BillingConstants.PaymentMethod gateway,
            UUID actorAccountId, UUID actorBranchId) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        if (!BillingRules.isPayable(invoice.getStatus())) {
            throw new InvoiceNotPayableException(invoiceId, invoice.getStatus());
        }

        var client = gateways.resolve(gateway);
        var remaining = BillingRules.remaining(invoice.getAmount(), invoice.getAmountPaid());
        var orderId = BillingRules.gatewayOrderId(invoiceId, invoice.getInstallmentNumber(),
                System.currentTimeMillis());
        var orderInfo = BillingConstants.OrderInfo.PREFIX + invoice.getInstallmentNumber();

        var urlResult = client.createPaymentUrl(PaymentRequest.withGatewayDefaults(orderId, remaining, orderInfo));
        payments.save(new Payment(invoice, remaining, gateway, urlResult.gatewayOrderId(),
                BillingConstants.PaymentStatus.PENDING));
        return new InitiateOnlinePaymentResponse(urlResult.payUrl());
    }
}
```

> `events` được giữ trong constructor dù chưa dùng: `HandlePaymentCallback` là nơi publish
> `PaymentReceived`, và việc usecase này KHÔNG publish là một quyết định được test
> (`publishesNoEventBecauseNoMoneyHasArrivedYet`) — field cho phép test verify `never()` trên đúng
> collaborator mà usecase thật sự nhận. Nếu lint báo field không dùng, giữ nguyên và thêm comment,
> đừng xoá rồi test mất khả năng kiểm chứng.

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest='PaymentGatewayClientResolverTest,InitiateOnlinePaymentTest'`
Expected: PASS (3 + 7 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/internal/PaymentGatewayClientResolver.java \
  backend/src/main/java/com/eduerp/modules/billing/usecase/InitiateOnlinePayment.java \
  backend/src/test/java/com/eduerp/modules/billing/internal/PaymentGatewayClientResolverTest.java \
  backend/src/test/java/com/eduerp/modules/billing/usecase/InitiateOnlinePaymentTest.java
git commit -m "feat(billing): add gateway resolver and initiate online payment use case"
```

---

### Task 15: `HandlePaymentCallback` (Review Focus #4 — idempotency)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/HandlePaymentCallback.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/usecase/HandlePaymentCallbackTest.java` (create)

**Interfaces:**
- Consumes: `PaymentGatewayClientResolver` (Task 14) — thêm `resolve(PaymentGatewayType)`; `PaymentSignatureException`/`PaymentCallbackResult` (Task 8/9/10); `Payment`/`Invoice`/repositories (Task 12).
- Produces: `HandlePaymentCallback.execute(PaymentGatewayType gatewayType, Map<String,String> rawParams): void`;
  `PaymentGatewayClientResolver.resolve(PaymentGatewayType type): PaymentGatewayClient` (overload mới).

- [ ] **Step 1: Write the failing test** — `backend/src/test/java/com/eduerp/modules/billing/usecase/HandlePaymentCallbackTest.java`:

```java
package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.integrations.payment.PaymentCallbackResult;
import com.eduerp.integrations.payment.PaymentGatewayClient;
import com.eduerp.integrations.payment.PaymentGatewayType;
import com.eduerp.integrations.payment.PaymentSignatureException;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.InvalidCallbackSignatureException;
import com.eduerp.modules.billing.internal.PaymentGatewayClientResolver;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class HandlePaymentCallbackTest {

    private static final String ORDER_ID = "order-1";
    private static final Map<String, String> RAW_PARAMS = Map.of("vnp_TxnRef", ORDER_ID);

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentGatewayClientResolver resolver = mock(PaymentGatewayClientResolver.class);
    private final PaymentGatewayClient client = mock(PaymentGatewayClient.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final HandlePaymentCallback useCase =
            new HandlePaymentCallback(invoices, payments, resolver, events);

    private Invoice invoiceOf(String amount) {
        return new Invoice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal(amount), LocalDate.of(2026, 11, 30), UUID.randomUUID());
    }

    private Payment pendingPaymentFor(Invoice invoice, String amount) {
        return new Payment(invoice, new BigDecimal(amount), BillingConstants.PaymentMethod.VNPAY, ORDER_ID,
                BillingConstants.PaymentStatus.PENDING);
    }

    private void stubClient() {
        when(resolver.resolve(PaymentGatewayType.VNPAY)).thenReturn(client);
    }

    private void stubVerifiedResult(boolean success, String amount) {
        stubClient();
        when(client.verifyCallback(anyMap())).thenReturn(
                new PaymentCallbackResult(ORDER_ID, success, new BigDecimal(amount), "ok"));
    }

    /** Chữ ký sai được dịch sang lỗi nghiệp vụ của billing - integrations.payment không được phụ
     * thuộc modules.billing nên nó chỉ ném PaymentSignatureException. */
    @Test
    void translatesAGatewaySignatureFailureIntoABillingException() {
        stubClient();
        when(client.verifyCallback(anyMap()))
                .thenThrow(new PaymentSignatureException(PaymentGatewayType.VNPAY));

        assertThatThrownBy(() -> useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS))
                .isInstanceOf(InvalidCallbackSignatureException.class);
        verify(payments, never()).findByGatewayTransactionId(any());
    }

    /** orderId lạ: bỏ qua im lặng, KHÔNG ném - nếu ném, kẻ tấn công dò được orderId nào tồn tại
     * (Review Focus #5, nửa ở tầng usecase). */
    @Test
    void ignoresAnUnknownOrderIdWithoutThrowing() {
        stubVerifiedResult(true, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.empty());

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void creditsTheInvoiceAndPublishesPaymentReceivedOnSuccess() {
        var invoice = invoiceOf("6000000");
        var payment = pendingPaymentFor(invoice, "6000000");
        stubVerifiedResult(true, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        assertThat(payment.getStatus()).isEqualTo(BillingConstants.PaymentStatus.SUCCESS);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo(new BigDecimal("6000000"));
        assertThat(invoice.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.PAID);
        verify(events, times(1)).publishEvent(any(BillingEvents.PaymentReceived.class));
    }

    @Test
    void leavesTheInvoicePartiallyPaidWhenTheInstallmentIsNotFullyCovered() {
        var invoice = invoiceOf("6000000");
        var payment = pendingPaymentFor(invoice, "2000000");
        stubVerifiedResult(true, "2000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        assertThat(invoice.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.PARTIALLY_PAID);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo(new BigDecimal("2000000"));
    }

    @Test
    void marksThePaymentFailedWithoutTouchingTheInvoiceWhenTheGatewayReportsFailure() {
        var invoice = invoiceOf("6000000");
        var payment = pendingPaymentFor(invoice, "6000000");
        stubVerifiedResult(false, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        assertThat(payment.getStatus()).isEqualTo(BillingConstants.PaymentStatus.FAILED);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(invoice.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.UNPAID);
        verify(events, never()).publishEvent(any());
    }

    /**
     * Review Focus #4: gọi callback thành công HAI lần với cùng {@code gatewayTransactionId} thì
     * {@code amountPaid} chỉ cộng MỘT lần, {@code Payment.status} lần thứ hai vẫn là {@code SUCCESS}
     * (không bị xử lý lại), và chỉ một event {@code PaymentReceived} được phát.
     */
    @Test
    void doesNotDoubleCreditWhenTheSameCallbackArrivesTwice() {
        var invoice = invoiceOf("6000000");
        var payment = pendingPaymentFor(invoice, "6000000");
        stubVerifiedResult(true, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);
        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        assertThat(invoice.getAmountPaid()).isEqualByComparingTo(new BigDecimal("6000000"));
        assertThat(invoice.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(BillingConstants.PaymentStatus.SUCCESS);
        verify(events, times(1)).publishEvent(any(BillingEvents.PaymentReceived.class));
    }

    /** Mặt còn lại của idempotency: một callback THẤT BẠI gửi lại sau khi đã SUCCESS không được hạ
     * Payment về FAILED hay trừ tiền đã ghi nhận. */
    @Test
    void doesNotDowngradeASucceededPaymentWhenAFailureCallbackArrivesLater() {
        var invoice = invoiceOf("6000000");
        var payment = pendingPaymentFor(invoice, "6000000");
        stubVerifiedResult(true, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));
        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);
        when(client.verifyCallback(anyMap()))
                .thenReturn(new PaymentCallbackResult(ORDER_ID, false, new BigDecimal("6000000"), "late failure"));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        assertThat(payment.getStatus()).isEqualTo(BillingConstants.PaymentStatus.SUCCESS);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo(new BigDecimal("6000000"));
    }

    /** Số tiền ghi nhận là số đã lưu trong Payment (do chính hệ thống tính), không phải số cổng gửi
     * về - nếu tin số của cổng thì một callback giả mạo đúng chữ ký vẫn có thể ghi sai công nợ. */
    @Test
    void creditsTheAmountStoredOnThePaymentNotTheAmountReportedByTheGateway() {
        var invoice = invoiceOf("6000000");
        var payment = pendingPaymentFor(invoice, "2000000");
        stubVerifiedResult(true, "6000000");
        when(payments.findByGatewayTransactionId(ORDER_ID)).thenReturn(Optional.of(payment));

        useCase.execute(PaymentGatewayType.VNPAY, RAW_PARAMS);

        assertThat(invoice.getAmountPaid()).isEqualByComparingTo(new BigDecimal("2000000"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=HandlePaymentCallbackTest`
Expected: FAIL — compile error, `HandlePaymentCallback` và overload `resolver.resolve(PaymentGatewayType)` chưa tồn tại.

- [ ] **Step 3: Add the `PaymentGatewayType` overload to the resolver**

Trong `PaymentGatewayClientResolver`, thêm method (giữ nguyên phần còn lại):

```java
    /** Dùng khi đã biết cổng từ chính đường dẫn callback, không qua từ vựng nghiệp vụ. */
    public PaymentGatewayClient resolve(PaymentGatewayType type) {
        var client = clientsByType.get(type);
        if (client == null) {
            throw new UnknownPaymentGatewayException(BillingConstants.PaymentMethod.valueOf(type.name()));
        }
        return client;
    }
```

- [ ] **Step 4: Write the usecase**

`modules/billing/usecase/HandlePaymentCallback.java`:

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.integrations.payment.PaymentCallbackResult;
import com.eduerp.integrations.payment.PaymentGatewayType;
import com.eduerp.integrations.payment.PaymentSignatureException;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.InvalidCallbackSignatureException;
import com.eduerp.modules.billing.internal.PaymentGatewayClientResolver;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nguồn sự thật của việc tiền đã vào. Ba quy tắc không được phá:
 * <ol>
 *   <li>Chữ ký sai → {@link InvalidCallbackSignatureException}, KHÔNG tra cứu gì thêm (không để lộ
 *       orderId nào tồn tại qua thời gian phản hồi hay side effect).</li>
 *   <li>{@code orderId} lạ → chỉ log cảnh báo rồi return, KHÔNG ném (Review Focus #5).</li>
 *   <li>{@code Payment.status != PENDING} → return ngay (Review Focus #4: callback gọi 2 lần không
 *       được cộng tiền 2 lần, và một callback thất bại đến muộn không được hạ bản ghi đã SUCCESS).</li>
 * </ol>
 * Số tiền ghi nhận lấy từ {@code Payment.amount} (hệ thống tự tính lúc tạo link), không lấy từ
 * {@code result.amount()} - không để một payload hợp lệ về chữ ký nhưng sai số tiền ghi sai công nợ.
 */
@Service
public class HandlePaymentCallback {

    private static final Logger log = LoggerFactory.getLogger(HandlePaymentCallback.class);

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final PaymentGatewayClientResolver gateways;
    private final ApplicationEventPublisher events;

    HandlePaymentCallback(InvoiceRepository invoices, PaymentRepository payments,
            PaymentGatewayClientResolver gateways, ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.payments = payments;
        this.gateways = gateways;
        this.events = events;
    }

    @Transactional
    public void execute(PaymentGatewayType gatewayType, Map<String, String> rawParams) {
        var result = verify(gatewayType, rawParams);
        var payment = payments.findByGatewayTransactionId(result.orderId()).orElse(null);
        if (payment == null) {
            log.warn("Bỏ qua callback của cổng {}: không có giao dịch nào khớp mã nhận được", gatewayType);
            return;
        }
        if (payment.getStatus() != BillingConstants.PaymentStatus.PENDING) {
            log.info("Bỏ qua callback trùng của cổng {}: giao dịch đã ở trạng thái {}", gatewayType,
                    payment.getStatus());
            return;
        }
        if (result.success()) {
            applySuccess(payment);
            return;
        }
        payment.markFailed();
    }

    private PaymentCallbackResult verify(PaymentGatewayType gatewayType, Map<String, String> rawParams) {
        try {
            return gateways.resolve(gatewayType).verifyCallback(rawParams);
        } catch (PaymentSignatureException badSignature) {
            throw new InvalidCallbackSignatureException(gatewayType);
        }
    }

    private void applySuccess(Payment payment) {
        payment.markSucceeded();
        var invoice = payment.getInvoice();
        invoice.applyPayment(payment.getAmount());
        invoices.save(invoice);
        events.publishEvent(new BillingEvents.PaymentReceived(invoice.getId(), invoice.getCreatedByAccountId(),
                invoice.getBranchId()));
    }
}
```

> `actorAccountId` của `PaymentReceived` là `invoice.getCreatedByAccountId()`: callback không có
> người gọi, nên actor hợp lý nhất là kế toán đã phát hành hoá đơn — audit vẫn trỏ về một người thật
> thay vì `null`.

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest='HandlePaymentCallbackTest,PaymentGatewayClientResolverTest'`
Expected: PASS (9 + 3 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/usecase/HandlePaymentCallback.java \
  backend/src/main/java/com/eduerp/modules/billing/internal/PaymentGatewayClientResolver.java \
  backend/src/test/java/com/eduerp/modules/billing/usecase/HandlePaymentCallbackTest.java
git commit -m "feat(billing): handle gateway callbacks idempotently"
```

---

### Task 16: `RecordManualPayment` + `CancelInvoice`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/RecordManualPayment.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/CancelInvoice.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/usecase/RecordManualPaymentTest.java` (create)
- Test: `backend/src/test/java/com/eduerp/modules/billing/usecase/CancelInvoiceTest.java` (create)

**Interfaces:**
- Consumes: `Invoice`/`Payment`/repositories/`BillingRules` (Task 12), `CreateInvoice.toResponse` (Task 13), exception/events (Task 11).
- Produces:
  `RecordManualPayment.execute(UUID invoiceId, BigDecimal amount, UUID actorAccountId, UUID actorBranchId): InvoiceResponse`;
  `CancelInvoice.execute(UUID invoiceId, UUID actorAccountId, UUID actorBranchId): void`.

- [ ] **Step 1: Write the failing manual-payment test** — `backend/src/test/java/com/eduerp/modules/billing/usecase/RecordManualPaymentTest.java`:

```java
package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.InvalidPaymentAmountException;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.InvoiceNotPayableException;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RecordManualPaymentTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RecordManualPayment useCase = new RecordManualPayment(invoices, payments, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();

    private Invoice invoiceOf(String amount) {
        return new Invoice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal(amount), LocalDate.of(2026, 11, 30), actorAccountId);
    }

    private Invoice stubbedInvoice(String amount) {
        var invoice = invoiceOf(amount);
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return invoice;
    }

    @Test
    void rejectsAnUnknownInvoice() {
        var invoiceId = UUID.randomUUID();
        when(invoices.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(invoiceId, new BigDecimal("1000000"), actorAccountId,
                actorBranchId)).isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void rejectsACancelledInvoice() {
        var invoice = stubbedInvoice("6000000");
        invoice.cancel();

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), new BigDecimal("1000000"), actorAccountId,
                actorBranchId)).isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void rejectsAZeroOrNegativeAmount() {
        var invoice = stubbedInvoice("6000000");

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), BigDecimal.ZERO, actorAccountId, actorBranchId))
                .isInstanceOf(InvalidPaymentAmountException.class);
        assertThatThrownBy(() -> useCase.execute(invoice.getId(), new BigDecimal("-1"), actorAccountId,
                actorBranchId)).isInstanceOf(InvalidPaymentAmountException.class);
    }

    /** Thu quá số còn lại bị chặn: tiền thừa phải xử lý ngoài hệ thống, không để amountPaid > amount. */
    @Test
    void rejectsAnAmountLargerThanWhatIsStillOwed() {
        var invoice = stubbedInvoice("6000000");
        invoice.applyPayment(new BigDecimal("2000000"));

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), new BigDecimal("4000001"), actorAccountId,
                actorBranchId)).isInstanceOf(InvalidPaymentAmountException.class);
    }

    @Test
    void acceptsAnAmountExactlyEqualToWhatIsStillOwed() {
        var invoice = stubbedInvoice("6000000");
        invoice.applyPayment(new BigDecimal("2000000"));

        var response = useCase.execute(invoice.getId(), new BigDecimal("4000000"), actorAccountId, actorBranchId);

        assertThat(response.status()).isEqualTo(BillingConstants.InvoiceStatus.PAID);
        assertThat(response.amountPaid()).isEqualByComparingTo(new BigDecimal("6000000"));
    }

    @Test
    void storesASucceededManualPaymentWithoutAGatewayTransactionId() {
        var invoice = stubbedInvoice("6000000");

        useCase.execute(invoice.getId(), new BigDecimal("2000000"), actorAccountId, actorBranchId);

        var saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().getMethod()).isEqualTo(BillingConstants.PaymentMethod.MANUAL);
        assertThat(saved.getValue().getStatus()).isEqualTo(BillingConstants.PaymentStatus.SUCCESS);
        assertThat(saved.getValue().getPaidAt()).isNotNull();
        assertThat(saved.getValue().getGatewayTransactionId()).isNull();
        verify(events).publishEvent(any(BillingEvents.PaymentReceived.class));
    }

    /** Hoá đơn quá hạn vẫn thu được - OVERDUE chỉ là nhãn nhắc nợ. */
    @Test
    void acceptsAPaymentOnAnOverdueInvoice() {
        var invoice = stubbedInvoice("6000000");
        invoice.markOverdue();

        var response = useCase.execute(invoice.getId(), new BigDecimal("6000000"), actorAccountId, actorBranchId);

        assertThat(response.status()).isEqualTo(BillingConstants.InvoiceStatus.PAID);
    }
}
```

- [ ] **Step 2: Write the failing cancel test** — `backend/src/test/java/com/eduerp/modules/billing/usecase/CancelInvoiceTest.java`:

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
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.InvoiceNotPayableException;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class CancelInvoiceTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CancelInvoice useCase = new CancelInvoice(invoices, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();

    private Invoice stubbedInvoice() {
        var invoice = new Invoice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal("6000000"), LocalDate.of(2026, 11, 30), actorAccountId);
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        return invoice;
    }

    @Test
    void rejectsAnUnknownInvoice() {
        var invoiceId = UUID.randomUUID();
        when(invoices.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(invoiceId, actorAccountId, actorBranchId))
                .isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void cancelsAnInvoiceThatHasNotReceivedAnyMoney() {
        var invoice = stubbedInvoice();

        useCase.execute(invoice.getId(), actorAccountId, actorBranchId);

        assertThat(invoice.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.CANCELLED);
    }

    /** Đã có tiền vào thì phải hoàn tiền ngoài hệ thống trước, không được huỷ (spec mục 5). */
    @Test
    void refusesToCancelAPartiallyPaidInvoice() {
        var invoice = stubbedInvoice();
        invoice.applyPayment(new BigDecimal("1000000"));

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), actorAccountId, actorBranchId))
                .isInstanceOf(InvoiceNotPayableException.class);
        assertThat(invoice.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.PARTIALLY_PAID);
    }

    @Test
    void refusesToCancelAPaidInvoice() {
        var invoice = stubbedInvoice();
        invoice.applyPayment(new BigDecimal("6000000"));

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), actorAccountId, actorBranchId))
                .isInstanceOf(InvoiceNotPayableException.class);
    }

    /** OVERDUE cũng không huỷ được: chỉ UNPAID mới huỷ - một hoá đơn quá hạn là công nợ thật đang
     * chờ thu, huỷ nó là xoá nợ, không phải sửa lỗi nhập liệu. */
    @Test
    void refusesToCancelAnOverdueInvoice() {
        var invoice = stubbedInvoice();
        invoice.markOverdue();

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), actorAccountId, actorBranchId))
                .isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void refusesToCancelTwice() {
        var invoice = stubbedInvoice();
        invoice.cancel();

        assertThatThrownBy(() -> useCase.execute(invoice.getId(), actorAccountId, actorBranchId))
                .isInstanceOf(InvoiceNotPayableException.class);
    }

    /** Huỷ hoá đơn không phải "nhận tiền" - không có event nào ở phân hệ này cho việc huỷ (spec mục 5
     * chỉ định nghĩa 3 event: InvoiceCreated, PaymentReceived, InvoiceOverdue). */
    @Test
    void publishesNoEventForACancellation() {
        var invoice = stubbedInvoice();

        useCase.execute(invoice.getId(), actorAccountId, actorBranchId);

        verify(events, never()).publishEvent(any());
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd backend && mvn test -Dtest='RecordManualPaymentTest,CancelInvoiceTest'`
Expected: FAIL — compile error, hai usecase chưa tồn tại.

- [ ] **Step 4: Write `RecordManualPayment`**

`modules/billing/usecase/RecordManualPayment.java`:

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.InvalidPaymentAmountException;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.InvoiceNotPayableException;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import com.eduerp.modules.billing.internal.rules.BillingRules;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kế toán nhập tay tiền mặt/chuyển khoản. Không có {@code gatewayTransactionId} - cột đó nullable
 * và unique index của Postgres không coi hai NULL là trùng, nên nhiều lần thu tay cùng tồn tại. */
@Service
public class RecordManualPayment {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final ApplicationEventPublisher events;

    RecordManualPayment(InvoiceRepository invoices, PaymentRepository payments,
            ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.payments = payments;
        this.events = events;
    }

    @Transactional
    public InvoiceResponse execute(UUID invoiceId, BigDecimal amount, UUID actorAccountId, UUID actorBranchId) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        if (!BillingRules.isPayable(invoice.getStatus())) {
            throw new InvoiceNotPayableException(invoiceId, invoice.getStatus());
        }
        var remaining = BillingRules.remaining(invoice.getAmount(), invoice.getAmountPaid());
        if (amount.signum() <= 0 || amount.compareTo(remaining) > 0) {
            throw new InvalidPaymentAmountException(amount);
        }

        payments.save(new Payment(invoice, amount, BillingConstants.PaymentMethod.MANUAL, null,
                BillingConstants.PaymentStatus.SUCCESS));
        invoice.applyPayment(amount);
        events.publishEvent(new BillingEvents.PaymentReceived(invoiceId, actorAccountId, actorBranchId));
        return CreateInvoice.toResponse(invoice);
    }
}
```

- [ ] **Step 5: Write `CancelInvoice`**

`modules/billing/usecase/CancelInvoice.java`:

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.InvoiceNotPayableException;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Huỷ một hoá đơn tạo nhầm. CHỈ cho phép khi {@code UNPAID} - {@code PARTIALLY_PAID} nghĩa là đã có
 * tiền vào, phải hoàn tiền ngoài hệ thống trước; {@code OVERDUE} là công nợ thật đang chờ thu, huỷ
 * nó là xoá nợ chứ không phải sửa lỗi nhập liệu (spec mục 5). Dùng quyền {@code UPDATE_INVOICE}, KHÔNG
 * dùng {@code Actions.DELETE} - toàn hệ thống hiện chưa dùng DELETE ở bất kỳ resource nào.
 */
@Service
public class CancelInvoice {

    private final InvoiceRepository invoices;
    private final ApplicationEventPublisher events;

    CancelInvoice(InvoiceRepository invoices, ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.events = events;
    }

    @Transactional
    public void execute(UUID invoiceId, UUID actorAccountId, UUID actorBranchId) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        if (invoice.getStatus() != BillingConstants.InvoiceStatus.UNPAID) {
            throw new InvoiceNotPayableException(invoiceId, invoice.getStatus());
        }
        invoice.cancel();
    }
}
```

> `events`/`actorAccountId`/`actorBranchId` được giữ để chữ ký usecase đồng dạng với mọi usecase
> khác trong module (controller truyền principal như nhau) và để test
> `publishesNoEventForACancellation` kiểm chứng được rằng KHÔNG có event nào phát ra — spec mục 5
> chỉ định nghĩa 3 event và huỷ hoá đơn không nằm trong đó.

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest='RecordManualPaymentTest,CancelInvoiceTest'`
Expected: PASS (7 + 7 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/usecase/RecordManualPayment.java \
  backend/src/main/java/com/eduerp/modules/billing/usecase/CancelInvoice.java \
  backend/src/test/java/com/eduerp/modules/billing/usecase/RecordManualPaymentTest.java \
  backend/src/test/java/com/eduerp/modules/billing/usecase/CancelInvoiceTest.java
git commit -m "feat(billing): record manual payments and cancel unpaid invoices"
```

---

### Task 17: `MarkOverdueInvoices` + `OverdueInvoiceScheduler` + bật `@EnableScheduling`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/MarkOverdueInvoices.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/internal/OverdueInvoiceScheduler.java`
- Create: `backend/src/main/java/com/eduerp/core/config/SchedulingConfig.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/usecase/MarkOverdueInvoicesTest.java` (create)
- Test: `backend/src/test/java/com/eduerp/modules/billing/internal/OverdueInvoiceSchedulerTest.java` (create)

**Interfaces:**
- Consumes: `InvoiceRepository.findAllByStatusInAndDueDateBefore` (Task 12), `BillingEvents.InvoiceOverdue` + `BillingConstants.Schedules.MARK_OVERDUE_CRON` (Task 11).
- Produces: `MarkOverdueInvoices.execute(): int` (số hoá đơn vừa đánh dấu — để scheduler log được);
  `OverdueInvoiceScheduler.run(): void` (`@Scheduled(cron = BillingConstants.Schedules.MARK_OVERDUE_CRON)`);
  `core.config.SchedulingConfig` (`@EnableScheduling`).

- [ ] **Step 1: Write the failing usecase test** — `backend/src/test/java/com/eduerp/modules/billing/usecase/MarkOverdueInvoicesTest.java`:

```java
package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class MarkOverdueInvoicesTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final MarkOverdueInvoices useCase = new MarkOverdueInvoices(invoices, events);

    private Invoice overdueCandidate() {
        return new Invoice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal("6000000"), LocalDate.of(2020, 1, 1), UUID.randomUUID());
    }

    @Test
    void marksEveryCandidateOverdueAndPublishesOneEventEach() {
        var first = overdueCandidate();
        var second = overdueCandidate();
        when(invoices.findAllByStatusInAndDueDateBefore(anyList(), any(LocalDate.class)))
                .thenReturn(List.of(first, second));

        var marked = useCase.execute();

        assertThat(marked).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.OVERDUE);
        assertThat(second.getStatus()).isEqualTo(BillingConstants.InvoiceStatus.OVERDUE);
        verify(events, times(2)).publishEvent(any(BillingEvents.InvoiceOverdue.class));
    }

    /** Hệ thống tự sinh, không ai bấm - actorAccountId phải là null và audit phải ghi được như thế
     * (xem Task 23). */
    @Test
    void publishesInvoiceOverdueWithoutAnActorBecauseNoHumanTriggeredIt() {
        var invoice = overdueCandidate();
        when(invoices.findAllByStatusInAndDueDateBefore(anyList(), any(LocalDate.class)))
                .thenReturn(List.of(invoice));

        useCase.execute();

        var published = ArgumentCaptor.forClass(BillingEvents.InvoiceOverdue.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue().invoiceId()).isEqualTo(invoice.getId());
        assertThat(published.getValue().actorAccountId()).isNull();
        assertThat(published.getValue().actorBranchId()).isEqualTo(invoice.getBranchId());
    }

    /** Chỉ quét UNPAID và PARTIALLY_PAID: PAID/CANCELLED/OVERDUE không còn là ứng viên, và việc
     * OVERDUE bị loại là lý do job này idempotent tự nhiên (lần chạy sau không thấy nữa). */
    @Test
    void scansOnlyUnpaidAndPartiallyPaidInvoices() {
        when(invoices.findAllByStatusInAndDueDateBefore(anyList(), any(LocalDate.class))).thenReturn(List.of());

        useCase.execute();

        var statuses = ArgumentCaptor.forClass(List.class);
        var dueDate = ArgumentCaptor.forClass(LocalDate.class);
        verify(invoices).findAllByStatusInAndDueDateBefore(statuses.capture(), dueDate.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(BillingConstants.InvoiceStatus.UNPAID,
                BillingConstants.InvoiceStatus.PARTIALLY_PAID);
        assertThat(dueDate.getValue()).isEqualTo(LocalDate.now());
    }

    @Test
    void doesNothingWhenThereIsNoOverdueInvoice() {
        when(invoices.findAllByStatusInAndDueDateBefore(anyList(), any(LocalDate.class))).thenReturn(List.of());

        assertThat(useCase.execute()).isZero();
        verify(events, never()).publishEvent(any());
    }
}
```

- [ ] **Step 2: Write the failing scheduler test** — `backend/src/test/java/com/eduerp/modules/billing/internal/OverdueInvoiceSchedulerTest.java`:

```java
package com.eduerp.modules.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.usecase.MarkOverdueInvoices;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/** Job phải CỰC mỏng: mọi nghiệp vụ nằm trong MarkOverdueInvoices (spec mục 5). Test này chốt cả
 * hai điều đó - nó chỉ gọi usecase, và cron của nó lấy từ hằng số chứ không hardcode. */
class OverdueInvoiceSchedulerTest {

    private final MarkOverdueInvoices useCase = mock(MarkOverdueInvoices.class);
    private final OverdueInvoiceScheduler scheduler = new OverdueInvoiceScheduler(useCase);

    @Test
    void delegatesEverythingToTheUseCase() {
        when(useCase.execute()).thenReturn(3);

        scheduler.run();

        verify(useCase).execute();
    }

    @Test
    void runsOnTheCronDeclaredInBillingConstants() throws Exception {
        var annotation = OverdueInvoiceScheduler.class.getDeclaredMethod("run").getAnnotation(Scheduled.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.cron()).isEqualTo(BillingConstants.Schedules.MARK_OVERDUE_CRON);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd backend && mvn test -Dtest='MarkOverdueInvoicesTest,OverdueInvoiceSchedulerTest'`
Expected: FAIL — compile error, `MarkOverdueInvoices`/`OverdueInvoiceScheduler` chưa tồn tại.

- [ ] **Step 4: Write the usecase**

`modules/billing/usecase/MarkOverdueInvoices.java`:

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent tự nhiên: sau khi đổi sang {@code OVERDUE}, hoá đơn không còn nằm trong tập quét
 * ({@code UNPAID}/{@code PARTIALLY_PAID}) nên lần chạy sau không thấy nữa (spec mục 5).
 *
 * <p>Không nhận request, không nhận actor: hệ thống tự sinh, {@code InvoiceOverdue.actorAccountId}
 * là {@code null}.
 */
@Service
public class MarkOverdueInvoices {

    private static final List<BillingConstants.InvoiceStatus> SCANNED_STATUSES = List.of(
            BillingConstants.InvoiceStatus.UNPAID, BillingConstants.InvoiceStatus.PARTIALLY_PAID);

    private final InvoiceRepository invoices;
    private final ApplicationEventPublisher events;

    MarkOverdueInvoices(InvoiceRepository invoices, ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.events = events;
    }

    /** @return số hoá đơn vừa được đánh dấu quá hạn - để scheduler log lại được một con số thật. */
    @Transactional
    public int execute() {
        var candidates = invoices.findAllByStatusInAndDueDateBefore(SCANNED_STATUSES, LocalDate.now());
        candidates.forEach(this::markOverdue);
        return candidates.size();
    }

    private void markOverdue(Invoice invoice) {
        invoice.markOverdue();
        events.publishEvent(new BillingEvents.InvoiceOverdue(invoice.getId(), null, invoice.getBranchId()));
    }
}
```

- [ ] **Step 5: Write the scheduler and enable scheduling**

`modules/billing/internal/OverdueInvoiceScheduler.java`:

```java
package com.eduerp.modules.billing.internal;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.usecase.MarkOverdueInvoices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Thân job cố tình cực mỏng - chỉ gọi usecase, mọi nghiệp vụ nằm trong {@link MarkOverdueInvoices}
 * (spec mục 5). Cơ chế {@code @EnableScheduling} bật ở {@code core.config.SchedulingConfig}. */
@Component
class OverdueInvoiceScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueInvoiceScheduler.class);

    private final MarkOverdueInvoices useCase;

    OverdueInvoiceScheduler(MarkOverdueInvoices useCase) {
        this.useCase = useCase;
    }

    @Scheduled(cron = BillingConstants.Schedules.MARK_OVERDUE_CRON)
    void run() {
        log.info("Đã đánh dấu quá hạn {} hoá đơn", useCase.execute());
    }
}
```

`core/config/SchedulingConfig.java`:

```java
package com.eduerp.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật cơ chế {@code @Scheduled} một lần cho toàn app - mirror cách
 * {@code core.security.SecurityBootstrapConfig} bật Spring Security: core bật cơ chế, module tự khai
 * job của mình ({@code modules.billing.internal.OverdueInvoiceScheduler} là job đầu tiên). Không có
 * lớp này thì {@code @Scheduled} bị Spring bỏ qua im lặng - job không bao giờ chạy mà cũng không
 * báo lỗi.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest='MarkOverdueInvoicesTest,OverdueInvoiceSchedulerTest,ModularityTests'`
Expected: PASS (4 + 2 + 2 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/usecase/MarkOverdueInvoices.java \
  backend/src/main/java/com/eduerp/modules/billing/internal/OverdueInvoiceScheduler.java \
  backend/src/main/java/com/eduerp/core/config/SchedulingConfig.java \
  backend/src/test/java/com/eduerp/modules/billing/usecase/MarkOverdueInvoicesTest.java \
  backend/src/test/java/com/eduerp/modules/billing/internal/OverdueInvoiceSchedulerTest.java
git commit -m "feat(billing): mark overdue invoices nightly via a thin scheduled job"
```

---

### Task 18: Read use cases — `ListInvoices`, `GetInvoiceDetail`, `GetPaymentStatus`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/ListInvoices.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/GetInvoiceDetail.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/usecase/GetPaymentStatus.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/usecase/BillingReadUseCasesTest.java` (create)

**Interfaces:**
- Consumes: `InvoiceRepository.search`/`PaymentRepository` (Task 12), `CreateInvoice.toResponse` (Task 13), DTO/exception (Task 11).
- Produces:
  `ListInvoices.execute(Pageable pageable, UUID studentProfileId, UUID enrollmentId, BillingConstants.InvoiceStatus status): PageResponse<InvoiceResponse>`;
  `GetInvoiceDetail.execute(UUID invoiceId): InvoiceDetailResponse`;
  `GetPaymentStatus.execute(String gatewayTransactionId): PaymentResponse`;
  `GetInvoiceDetail.toPaymentResponse(Payment): PaymentResponse` (static, package-private — `GetPaymentStatus` dùng lại).

- [ ] **Step 1: Write the failing test** — `backend/src/test/java/com/eduerp/modules/billing/usecase/BillingReadUseCasesTest.java`:

```java
package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.PaymentNotFoundException;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class BillingReadUseCasesTest {

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ListInvoices listInvoices = new ListInvoices(invoices);
    private final GetInvoiceDetail getInvoiceDetail = new GetInvoiceDetail(invoices, payments);
    private final GetPaymentStatus getPaymentStatus = new GetPaymentStatus(payments);

    private Invoice invoiceOf(String amount) {
        return new Invoice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                new BigDecimal(amount), LocalDate.of(2026, 11, 30), UUID.randomUUID());
    }

    @Test
    void listPassesAllThreeOptionalFiltersStraightToTheRepository() {
        var invoice = invoiceOf("6000000");
        var pageable = PageRequest.of(0, 20);
        when(invoices.search(invoice.getStudentProfileId(), invoice.getEnrollmentId(),
                BillingConstants.InvoiceStatus.UNPAID, pageable))
                .thenReturn(new PageImpl<>(List.of(invoice), pageable, 1));

        var page = listInvoices.execute(pageable, invoice.getStudentProfileId(), invoice.getEnrollmentId(),
                BillingConstants.InvoiceStatus.UNPAID);

        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.items()).singleElement()
                .satisfies(item -> assertThat(item.id()).isEqualTo(invoice.getId()));
    }

    @Test
    void listAcceptsAllNullFilters() {
        var pageable = PageRequest.of(0, 20);
        when(invoices.search(null, null, null, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(listInvoices.execute(pageable, null, null, null).items()).isEmpty();
    }

    @Test
    void detailReturnsTheInvoiceWithItsPaymentHistory() {
        var invoice = invoiceOf("6000000");
        var manual = new Payment(invoice, new BigDecimal("2000000"), BillingConstants.PaymentMethod.MANUAL, null,
                BillingConstants.PaymentStatus.SUCCESS);
        var pending = new Payment(invoice, new BigDecimal("4000000"), BillingConstants.PaymentMethod.MOMO,
                "order-1", BillingConstants.PaymentStatus.PENDING);
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(payments.findAllByInvoice_IdOrderByCreatedAtDesc(invoice.getId()))
                .thenReturn(List.of(pending, manual));

        var detail = getInvoiceDetail.execute(invoice.getId());

        assertThat(detail.invoice().id()).isEqualTo(invoice.getId());
        assertThat(detail.payments()).hasSize(2);
        // Thứ tự giữ nguyên thứ tự repository trả về (createdAt DESC) - không sort lại ở usecase.
        assertThat(detail.payments().get(0).method()).isEqualTo(BillingConstants.PaymentMethod.MOMO);
        assertThat(detail.payments().get(0).status()).isEqualTo(BillingConstants.PaymentStatus.PENDING);
        assertThat(detail.payments().get(0).paidAt()).isNull();
        assertThat(detail.payments().get(1).method()).isEqualTo(BillingConstants.PaymentMethod.MANUAL);
        assertThat(detail.payments().get(1).paidAt()).isNotNull();
    }

    @Test
    void detailRejectsAnUnknownInvoice() {
        var invoiceId = UUID.randomUUID();
        when(invoices.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getInvoiceDetail.execute(invoiceId)).isInstanceOf(InvoiceNotFoundException.class);
    }

    @Test
    void paymentStatusReturnsTheTransactionWithoutLeakingItsGatewayId() {
        var invoice = invoiceOf("6000000");
        var payment = new Payment(invoice, new BigDecimal("6000000"), BillingConstants.PaymentMethod.VNPAY,
                "order-1", BillingConstants.PaymentStatus.PENDING);
        when(payments.findByGatewayTransactionId("order-1")).thenReturn(Optional.of(payment));

        var response = getPaymentStatus.execute("order-1");

        assertThat(response.status()).isEqualTo(BillingConstants.PaymentStatus.PENDING);
        assertThat(response.method()).isEqualTo(BillingConstants.PaymentMethod.VNPAY);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("6000000"));
        // PaymentResponse cố ý không có field gatewayTransactionId - endpoint này là public.
        assertThat(PaymentResponseFields.names()).doesNotContain("gatewayTransactionId");
    }

    @Test
    void paymentStatusRejectsAnUnknownTransaction() {
        when(payments.findByGatewayTransactionId("order-unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getPaymentStatus.execute("order-unknown"))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    /** Chốt hợp đồng của PaymentResponse bằng reflection: nếu ai thêm field nhạy cảm vào record này
     * thì test đỏ, vì payload đó đi ra một endpoint không cần đăng nhập. */
    private static final class PaymentResponseFields {
        private PaymentResponseFields() {
        }

        static List<String> names() {
            return java.util.Arrays
                    .stream(com.eduerp.modules.billing.dto.PaymentResponse.class.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName).toList();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=BillingReadUseCasesTest`
Expected: FAIL — compile error, ba usecase chưa tồn tại.

- [ ] **Step 3: Write `ListInvoices`**

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.shared.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ba filter optional dồn vào một query ở repository - usecase không rẽ nhánh nào (complexity 1). */
@Service
public class ListInvoices {

    private final InvoiceRepository invoices;

    ListInvoices(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> execute(Pageable pageable, UUID studentProfileId, UUID enrollmentId,
            BillingConstants.InvoiceStatus status) {
        return PageResponse.of(invoices.search(studentProfileId, enrollmentId, status, pageable)
                .map(CreateInvoice::toResponse));
    }
}
```

- [ ] **Step 4: Write `GetInvoiceDetail`**

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.dto.InvoiceDetailResponse;
import com.eduerp.modules.billing.dto.PaymentResponse;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirror {@code GetPayrollRun}: tổng thể + danh sách con, một query cho mỗi phần, không N+1. */
@Service
public class GetInvoiceDetail {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;

    GetInvoiceDetail(InvoiceRepository invoices, PaymentRepository payments) {
        this.invoices = invoices;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public InvoiceDetailResponse execute(UUID invoiceId) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        // Thứ tự createdAt DESC do chính query quyết định - không sort lại ở đây.
        var history = payments.findAllByInvoice_IdOrderByCreatedAtDesc(invoiceId).stream()
                .map(GetInvoiceDetail::toPaymentResponse).toList();
        return new InvoiceDetailResponse(CreateInvoice.toResponse(invoice), history);
    }

    /** Dùng lại ở {@code GetPaymentStatus} - một chỗ map duy nhất cho Payment. */
    static PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getAmount(), payment.getMethod(), payment.getStatus(),
                payment.getPaidAt(), payment.getCreatedAt());
    }
}
```

- [ ] **Step 5: Write `GetPaymentStatus`**

```java
package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.PaymentNotFoundException;
import com.eduerp.modules.billing.dto.PaymentResponse;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dùng cho trang Return URL ở frontend: IPN là nguồn sự thật và xử lý bất đồng bộ, nên lúc phụ huynh
 * được redirect về, trạng thái có thể còn {@code PENDING} - trang phải poll lại API thay vì tin query
 * param trên URL (spec mục 10).
 */
@Service
public class GetPaymentStatus {

    private final PaymentRepository payments;

    GetPaymentStatus(PaymentRepository payments) {
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public PaymentResponse execute(String gatewayTransactionId) {
        return payments.findByGatewayTransactionId(gatewayTransactionId)
                .map(GetInvoiceDetail::toPaymentResponse)
                .orElseThrow(() -> new PaymentNotFoundException(gatewayTransactionId));
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=BillingReadUseCasesTest`
Expected: PASS (6 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/usecase/ListInvoices.java \
  backend/src/main/java/com/eduerp/modules/billing/usecase/GetInvoiceDetail.java \
  backend/src/main/java/com/eduerp/modules/billing/usecase/GetPaymentStatus.java \
  backend/src/test/java/com/eduerp/modules/billing/usecase/BillingReadUseCasesTest.java
git commit -m "feat(billing): add invoice list, detail and payment status read use cases"
```

---

### Task 19: Migration V19 — seed quyền ENROLLMENT/INVOICE vào nhóm quyền ADMIN

**Files:**
- Create: `backend/src/main/resources/db/migration/V19__seed_enrollment_billing_rbac.sql`
- Test: `backend/src/test/java/com/eduerp/modules/access/internal/repository/EnrollmentBillingRbacSeedIT.java` (create)

**Interfaces:**
- Consumes: `AccessConstants.Resources.ENROLLMENT`/`INVOICE` + `Actions` (Task 3), bảng `permissions`/`permission_group_items` (V2/V3/V5).
- Produces: 6 dòng `permissions` mới (`ENROLLMENT:{CREATE,READ,UPDATE}`, `INVOICE:{CREATE,READ,UPDATE}`) đã gán scope `ORGANIZATION` vào permission group `11111111-0000-0000-0000-000000000001`.

- [ ] **Step 1: Write the failing test** — `backend/src/test/java/com/eduerp/modules/access/internal/repository/EnrollmentBillingRbacSeedIT.java`:

```java
package com.eduerp.modules.access.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.access.AccessConstants;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Mirror {@code DefaultRbacSeedIT}: quyền mới phải thực sự nằm trong nhóm "Toàn quyền hệ thống",
 * nếu không admin đăng nhập xong vẫn nhận 403 ở mọi endpoint của Phân hệ 3. */
@Testcontainers
@DataJpaTest
class EnrollmentBillingRbacSeedIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    RoleRepository roles;

    private List<String> adminPermissionKeys() {
        return roles.findByCode(AccessConstants.RoleCodes.ADMIN).orElseThrow()
                .getPermissionGroups().stream()
                .flatMap(group -> group.getItems().stream())
                .map(item -> item.getPermission().getResource() + ":" + item.getPermission().getAction() + ":"
                        + item.getScope().name())
                .toList();
    }

    @Test
    void adminHasOrganizationScopedEnrollmentPermissions() {
        assertThat(adminPermissionKeys()).contains(
                AccessConstants.Resources.ENROLLMENT + ":" + AccessConstants.Actions.CREATE + ":"
                        + AccessConstants.ScopeNames.ORGANIZATION,
                AccessConstants.Resources.ENROLLMENT + ":" + AccessConstants.Actions.READ + ":"
                        + AccessConstants.ScopeNames.ORGANIZATION,
                AccessConstants.Resources.ENROLLMENT + ":" + AccessConstants.Actions.UPDATE + ":"
                        + AccessConstants.ScopeNames.ORGANIZATION);
    }

    @Test
    void adminHasOrganizationScopedInvoicePermissions() {
        assertThat(adminPermissionKeys()).contains(
                AccessConstants.Resources.INVOICE + ":" + AccessConstants.Actions.CREATE + ":"
                        + AccessConstants.ScopeNames.ORGANIZATION,
                AccessConstants.Resources.INVOICE + ":" + AccessConstants.Actions.READ + ":"
                        + AccessConstants.ScopeNames.ORGANIZATION,
                AccessConstants.Resources.INVOICE + ":" + AccessConstants.Actions.UPDATE + ":"
                        + AccessConstants.ScopeNames.ORGANIZATION);
    }

    /** Phân hệ 3 không có luồng duyệt - không seed APPROVE cho hai resource này (spec mục 7). */
    @Test
    void neitherResourceGetsAnApprovePermission() {
        assertThat(adminPermissionKeys()).noneMatch(key -> key.startsWith(
                AccessConstants.Resources.ENROLLMENT + ":" + AccessConstants.Actions.APPROVE));
        assertThat(adminPermissionKeys()).noneMatch(key -> key.startsWith(
                AccessConstants.Resources.INVOICE + ":" + AccessConstants.Actions.APPROVE));
    }
}
```

> Nếu `PermissionGroupItem` chưa expose `getPermission()`, đọc lại
> `backend/src/main/java/com/eduerp/modules/access/internal/model/PermissionGroupItem.java` và dùng
> đúng getter đang có (ví dụ `getPermission().getResource()` vs `getResource()`); `DefaultRbacSeedIT`
> hiện chỉ đọc `getScope()`, nên đây là lần đầu test đọc tới permission bên trong item.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=EnrollmentBillingRbacSeedIT`
Expected: FAIL — `contains(...)` không tìm thấy 6 key (quyền chưa được seed).

- [ ] **Step 3: Write the migration**

`backend/src/main/resources/db/migration/V19__seed_enrollment_billing_rbac.sql`:

```sql
INSERT INTO permissions (id, resource, action) VALUES
    (gen_random_uuid(), 'ENROLLMENT', 'CREATE'),
    (gen_random_uuid(), 'ENROLLMENT', 'READ'),
    (gen_random_uuid(), 'ENROLLMENT', 'UPDATE'),
    (gen_random_uuid(), 'INVOICE', 'CREATE'),
    (gen_random_uuid(), 'INVOICE', 'READ'),
    (gen_random_uuid(), 'INVOICE', 'UPDATE');

-- V5 đã seed "Toàn quyền hệ thống" xong và đã chạy rồi - không sửa lại được, nên permission mới
-- phải tự thêm dòng gán vào đúng group đó, giống cách V10/V13/V14 đã làm.
-- Không seed ENROLLMENT:APPROVE / INVOICE:APPROVE: phân hệ này không có luồng duyệt (spec mục 7).
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000001', p.id, 'ORGANIZATION'
FROM permissions p WHERE p.resource IN ('ENROLLMENT', 'INVOICE');
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest='EnrollmentBillingRbacSeedIT,DefaultRbacSeedIT'`
Expected: PASS (3 + 1 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V19__seed_enrollment_billing_rbac.sql \
  backend/src/test/java/com/eduerp/modules/access/internal/repository/EnrollmentBillingRbacSeedIT.java
git commit -m "feat(access): seed enrollment and invoice permissions into the admin permission group"
```

---

### Task 20: `EnrollmentAdminController`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/enrollment/web/EnrollmentAdminController.java`
- Test: `backend/src/test/java/com/eduerp/modules/enrollment/web/EnrollmentAdminControllerIT.java` (create)

**Interfaces:**
- Consumes: 4 usecase (Task 6/7), `AccessConstants.AccessRules.{CREATE,READ,UPDATE}_ENROLLMENT` (Task 3), quyền đã seed (Task 19).
- Produces: 5 endpoint HTTP:
  `POST /api/enrollment/enrollments` → `EnrollmentResponse`,
  `GET /api/enrollment/enrollments?page&size&studentProfileId&classId` → `PageResponse<EnrollmentResponse>`,
  `GET /api/enrollment/enrollments/{enrollmentId}` → `EnrollmentResponse`,
  `POST /api/enrollment/enrollments/{enrollmentId}/withdraw` → 200 không body,
  `POST /api/enrollment/enrollments/{enrollmentId}/complete` → 200 không body.

- [ ] **Step 1: Write the failing test** — `backend/src/test/java/com/eduerp/modules/enrollment/web/EnrollmentAdminControllerIT.java`:

```java
package com.eduerp.modules.enrollment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
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
class EnrollmentAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String ENROLLMENTS = "/api/enrollment/enrollments";

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

    private UUID newStudentProfileId() {
        var account = accounts.save(new Account("enr-web-hv-" + shortId() + "@eduerp.local", "hash", "HV", null));
        return profiles.save(new StudentProfile(account.getId(), null, null, null)).getId();
    }

    private UUID newClassId(int maxSeats) {
        var branchId = branches.save(new Branch("ENRW-" + shortId(), "Chi nhánh", null)).getId();
        var course = courses.save(new Course("ENRW-C-" + shortId(), "Khoá", null, 24, new BigDecimal("9000000")));
        var teacherId = accounts.save(new Account("enr-web-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        return classes.save(new Class(course, "ENRW-K-" + shortId(), branchId, teacherId, maxSeats)).getId();
    }

    private String createEnrollment(Cookie admin, UUID studentProfileId, UUID classId) throws Exception {
        var result = mockMvc.perform(post(ENROLLMENTS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEnrollmentRequest(studentProfileId, classId))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void createsReadsAndListsAnEnrollment() throws Exception {
        var admin = signIn("enr-admin-1@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var studentProfileId = newStudentProfileId();
        var classId = newClassId(10);

        var enrollmentId = createEnrollment(admin, studentProfileId, classId);

        var detail = mockMvc.perform(get(ENROLLMENTS + "/" + enrollmentId).cookie(admin)).andReturn();
        assertThat(detail.getResponse().getStatus()).isEqualTo(200);
        var body = objectMapper.readTree(detail.getResponse().getContentAsString());
        assertThat(body.get("studentProfileId").asText()).isEqualTo(studentProfileId.toString());
        assertThat(body.get("classId").asText()).isEqualTo(classId.toString());
        assertThat(body.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(body.get("withdrawnAt").isNull()).isTrue();

        var list = mockMvc.perform(get(ENROLLMENTS).cookie(admin)
                .param("studentProfileId", studentProfileId.toString())).andReturn();
        assertThat(objectMapper.readTree(list.getResponse().getContentAsString()).get("totalItems").asInt())
                .isEqualTo(1);
    }

    /** Review Focus #1 ở tầng HTTP: lần ghi danh thứ hai phải là 409 ENROLLMENT_DUPLICATE_ACTIVE,
     * không phải 500 từ ràng buộc DB. */
    @Test
    void rejectsADuplicateActiveEnrollmentAtHttpLevel() throws Exception {
        var admin = signIn("enr-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var studentProfileId = newStudentProfileId();
        var classId = newClassId(10);
        createEnrollment(admin, studentProfileId, classId);

        var duplicate = mockMvc.perform(post(ENROLLMENTS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEnrollmentRequest(studentProfileId, classId))))
                .andReturn();

        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(duplicate.getResponse().getContentAsString()).contains("ENROLLMENT_DUPLICATE_ACTIVE");
    }

    /** Review Focus #2 ở tầng HTTP với maxSeats = 2: hai ghi danh đầu 200, ghi danh thứ ba 409. */
    @Test
    void rejectsTheSeatPastCapacityAtHttpLevel() throws Exception {
        var admin = signIn("enr-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var classId = newClassId(2);
        createEnrollment(admin, newStudentProfileId(), classId);
        createEnrollment(admin, newStudentProfileId(), classId);

        var overflow = mockMvc.perform(post(ENROLLMENTS).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEnrollmentRequest(newStudentProfileId(), classId))))
                .andReturn();

        assertThat(overflow.getResponse().getStatus()).isEqualTo(409);
        assertThat(overflow.getResponse().getContentAsString()).contains("ENROLLMENT_CLASS_FULL");
    }

    @Test
    void withdrawsThenRefusesToWithdrawAgain() throws Exception {
        var admin = signIn("enr-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = createEnrollment(admin, newStudentProfileId(), newClassId(10));

        var first = mockMvc.perform(post(ENROLLMENTS + "/" + enrollmentId + "/withdraw").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        var second = mockMvc.perform(post(ENROLLMENTS + "/" + enrollmentId + "/withdraw").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(second.getResponse().getContentAsString()).contains("ENROLLMENT_NOT_ACTIVE");
    }

    @Test
    void completesAnEnrollment() throws Exception {
        var admin = signIn("enr-admin-5@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = createEnrollment(admin, newStudentProfileId(), newClassId(10));

        var result = mockMvc.perform(post(ENROLLMENTS + "/" + enrollmentId + "/complete").cookie(admin).with(csrf()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var detail = mockMvc.perform(get(ENROLLMENTS + "/" + enrollmentId).cookie(admin)).andReturn();
        assertThat(objectMapper.readTree(detail.getResponse().getContentAsString()).get("status").asText())
                .isEqualTo("COMPLETED");
    }

    @Test
    void refusesEveryEndpointWithoutEnrollmentPermission() throws Exception {
        var admin = signIn("enr-admin-6@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var outsider = signIn("enr-outsider-1@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var enrollmentId = createEnrollment(admin, newStudentProfileId(), newClassId(10));

        assertThat(mockMvc.perform(get(ENROLLMENTS).cookie(outsider)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(post(ENROLLMENTS + "/" + enrollmentId + "/withdraw").cookie(outsider)
                .with(csrf())).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void requiresAuthentication() throws Exception {
        assertThat(mockMvc.perform(get(ENROLLMENTS)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=EnrollmentAdminControllerIT`
Expected: FAIL — 404 ở mọi endpoint (controller chưa tồn tại).

- [ ] **Step 3: Write the controller**

`modules/enrollment/web/EnrollmentAdminController.java`:

```java
package com.eduerp.modules.enrollment.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.enrollment.dto.CreateEnrollmentRequest;
import com.eduerp.modules.enrollment.dto.EnrollmentResponse;
import com.eduerp.modules.enrollment.usecase.CompleteEnrollment;
import com.eduerp.modules.enrollment.usecase.CreateEnrollment;
import com.eduerp.modules.enrollment.usecase.GetEnrollment;
import com.eduerp.modules.enrollment.usecase.ListEnrollments;
import com.eduerp.modules.enrollment.usecase.WithdrawEnrollment;
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

/** Quản trị ghi danh - mirror CourseAdminController 1:1 về cấu trúc. Không có endpoint xoá: ghi danh
 * chỉ chuyển state sang WITHDRAWN/COMPLETED. */
@RestController
@RequestMapping("/api/enrollment/enrollments")
class EnrollmentAdminController {

    private final ListEnrollments listEnrollments;
    private final CreateEnrollment createEnrollment;
    private final GetEnrollment getEnrollment;
    private final WithdrawEnrollment withdrawEnrollment;
    private final CompleteEnrollment completeEnrollment;

    EnrollmentAdminController(ListEnrollments listEnrollments, CreateEnrollment createEnrollment,
            GetEnrollment getEnrollment, WithdrawEnrollment withdrawEnrollment,
            CompleteEnrollment completeEnrollment) {
        this.listEnrollments = listEnrollments;
        this.createEnrollment = createEnrollment;
        this.getEnrollment = getEnrollment;
        this.withdrawEnrollment = withdrawEnrollment;
        this.completeEnrollment = completeEnrollment;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_ENROLLMENT)
    PageResponse<EnrollmentResponse> list(@PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) UUID studentProfileId,
            @RequestParam(required = false) UUID classId) {
        return listEnrollments.execute(pageable, studentProfileId, classId);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_ENROLLMENT)
    EnrollmentResponse create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreateEnrollmentRequest request) {
        return createEnrollment.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @GetMapping("/{enrollmentId}")
    @PreAuthorize(AccessConstants.AccessRules.READ_ENROLLMENT)
    EnrollmentResponse get(@PathVariable UUID enrollmentId) {
        return getEnrollment.execute(enrollmentId);
    }

    @PostMapping("/{enrollmentId}/withdraw")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_ENROLLMENT)
    void withdraw(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID enrollmentId) {
        withdrawEnrollment.execute(enrollmentId, principal.accountId(), principal.homeBranchId());
    }

    @PostMapping("/{enrollmentId}/complete")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_ENROLLMENT)
    void complete(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID enrollmentId) {
        completeEnrollment.execute(enrollmentId, principal.accountId(), principal.homeBranchId());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=EnrollmentAdminControllerIT`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/enrollment/web/EnrollmentAdminController.java \
  backend/src/test/java/com/eduerp/modules/enrollment/web/EnrollmentAdminControllerIT.java
git commit -m "feat(enrollment): expose enrollment admin endpoints"
```

---

### Task 21: `InvoiceAdminController`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/web/InvoiceAdminController.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/web/InvoiceAdminControllerIT.java` (create)

**Interfaces:**
- Consumes: `CreateInvoice`/`ListInvoices`/`GetInvoiceDetail`/`InitiateOnlinePayment`/`RecordManualPayment`/`CancelInvoice` (Task 13–18), `AccessConstants.AccessRules.{CREATE,READ,UPDATE}_INVOICE` (Task 3), quyền đã seed (Task 19), `EnrollmentAdminController` để dựng dữ liệu trong IT (Task 20).
- Produces: 6 endpoint HTTP:
  `POST /api/billing/invoices` → `InvoiceResponse`,
  `GET /api/billing/invoices?page&size&studentProfileId&enrollmentId&status` → `PageResponse<InvoiceResponse>`,
  `GET /api/billing/invoices/{invoiceId}` → `InvoiceDetailResponse`,
  `POST /api/billing/invoices/{invoiceId}/online-payment` → `InitiateOnlinePaymentResponse`,
  `POST /api/billing/invoices/{invoiceId}/manual-payment` → `InvoiceResponse`,
  `POST /api/billing/invoices/{invoiceId}/cancel` → 200 không body.

- [ ] **Step 1: Write the failing test** — `backend/src/test/java/com/eduerp/modules/billing/web/InvoiceAdminControllerIT.java`:

```java
package com.eduerp.modules.billing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.dto.CreateInvoiceRequest;
import com.eduerp.modules.billing.dto.RecordManualPaymentRequest;
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
class InvoiceAdminControllerIT {

    private static final String PASSWORD = "Password123!";
    private static final String INVOICES = "/api/billing/invoices";
    private static final BigDecimal TUITION = new BigDecimal("12000000");
    private static final BigDecimal HALF = new BigDecimal("6000000");
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

    /** Dựng một ghi danh ACTIVE qua chính HTTP API của enrollment - không chọc thẳng repository, để
     * test cũng chứng minh hai module ghép được với nhau. */
    private String newActiveEnrollmentId(Cookie admin, BigDecimal tuitionFee) throws Exception {
        var branchId = branches.save(new Branch("BILW-" + shortId(), "Chi nhánh", null)).getId();
        var course = courses.save(new Course("BILW-C-" + shortId(), "Khoá", null, 24, tuitionFee));
        var teacherId = accounts.save(new Account("bil-web-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        var classId = classes.save(new Class(course, "BILW-K-" + shortId(), branchId, teacherId, 20)).getId();
        var studentAccountId = accounts
                .save(new Account("bil-web-hv-" + shortId() + "@eduerp.local", "hash", "HV", null)).getId();
        var studentProfileId = profiles.save(new StudentProfile(studentAccountId, null, null, null)).getId();
        var result = mockMvc.perform(post("/api/enrollment/enrollments").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateEnrollmentRequest(studentProfileId, classId))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createInvoice(Cookie admin, String enrollmentId, BigDecimal amount) throws Exception {
        var result = mockMvc.perform(post(INVOICES).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateInvoiceRequest(UUID.fromString(enrollmentId), amount, DUE_DATE))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void createsTwoInstallmentsThenRefusesAnythingBeyondTheTuition() throws Exception {
        var admin = signIn("bil-admin-1@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newActiveEnrollmentId(admin, TUITION);
        createInvoice(admin, enrollmentId, HALF);
        createInvoice(admin, enrollmentId, HALF);

        // Review Focus #3 ở tầng HTTP: đợt 3 với số tiền dương nhỏ nhất cũng bị chặn.
        var third = mockMvc.perform(post(INVOICES).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateInvoiceRequest(
                                UUID.fromString(enrollmentId), new BigDecimal("1"), DUE_DATE))))
                .andReturn();

        assertThat(third.getResponse().getStatus()).isEqualTo(409);
        assertThat(third.getResponse().getContentAsString()).contains("BILLING_INVOICE_AMOUNT_EXCEEDS_TUITION");
    }

    @Test
    void refusesAnInvoiceForACourseWithoutATuitionFee() throws Exception {
        var admin = signIn("bil-admin-2@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newActiveEnrollmentId(admin, null);

        var result = mockMvc.perform(post(INVOICES).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateInvoiceRequest(
                                UUID.fromString(enrollmentId), HALF, DUE_DATE))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("BILLING_COURSE_TUITION_NOT_CONFIGURED");
    }

    @Test
    void recordsAPartialManualPaymentThenCompletesTheInvoice() throws Exception {
        var admin = signIn("bil-admin-3@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newActiveEnrollmentId(admin, TUITION);
        var invoiceId = createInvoice(admin, enrollmentId, HALF);

        var partial = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/manual-payment").cookie(admin)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RecordManualPaymentRequest(new BigDecimal("2000000")))))
                .andReturn();
        assertThat(partial.getResponse().getStatus()).isEqualTo(200);
        var afterPartial = objectMapper.readTree(partial.getResponse().getContentAsString());
        assertThat(afterPartial.get("status").asText())
                .isEqualTo(BillingConstants.InvoiceStatus.PARTIALLY_PAID.name());
        assertThat(afterPartial.get("amountPaid").asLong()).isEqualTo(2_000_000L);

        var rest = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/manual-payment").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RecordManualPaymentRequest(new BigDecimal("4000000")))))
                .andReturn();
        assertThat(objectMapper.readTree(rest.getResponse().getContentAsString()).get("status").asText())
                .isEqualTo(BillingConstants.InvoiceStatus.PAID.name());

        var detail = mockMvc.perform(get(INVOICES + "/" + invoiceId).cookie(admin)).andReturn();
        var payments = objectMapper.readTree(detail.getResponse().getContentAsString()).get("payments");
        assertThat(payments).hasSize(2);
        assertThat(detail.getResponse().getContentAsString()).doesNotContain("gatewayTransactionId");
    }

    @Test
    void refusesAManualPaymentLargerThanTheRemainingBalance() throws Exception {
        var admin = signIn("bil-admin-4@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newActiveEnrollmentId(admin, TUITION);
        var invoiceId = createInvoice(admin, enrollmentId, HALF);

        var result = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/manual-payment").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RecordManualPaymentRequest(new BigDecimal("6000001")))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("BILLING_INVALID_PAYMENT_AMOUNT");
    }

    @Test
    void cancelsAnUnpaidInvoiceAndFreesItsInstallmentSlot() throws Exception {
        var admin = signIn("bil-admin-5@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newActiveEnrollmentId(admin, TUITION);
        var invoiceId = createInvoice(admin, enrollmentId, TUITION);

        var cancel = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/cancel").cookie(admin).with(csrf()))
                .andReturn();
        assertThat(cancel.getResponse().getStatus()).isEqualTo(200);

        // Hoá đơn đã huỷ không chiếm chỗ và không tính vào tổng, nên phát hành lại cả học phí được.
        var reissuedId = createInvoice(admin, enrollmentId, TUITION);
        var reissued = mockMvc.perform(get(INVOICES + "/" + reissuedId).cookie(admin)).andReturn();
        assertThat(objectMapper.readTree(reissued.getResponse().getContentAsString()).get("invoice")
                .get("installmentNumber").asInt()).isEqualTo(1);
    }

    @Test
    void refusesToCancelAnInvoiceThatAlreadyReceivedMoney() throws Exception {
        var admin = signIn("bil-admin-6@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newActiveEnrollmentId(admin, TUITION);
        var invoiceId = createInvoice(admin, enrollmentId, HALF);
        mockMvc.perform(post(INVOICES + "/" + invoiceId + "/manual-payment").cookie(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RecordManualPaymentRequest(new BigDecimal("1000000")))));

        var cancel = mockMvc.perform(post(INVOICES + "/" + invoiceId + "/cancel").cookie(admin).with(csrf()))
                .andReturn();

        assertThat(cancel.getResponse().getStatus()).isEqualTo(409);
        assertThat(cancel.getResponse().getContentAsString()).contains("BILLING_INVOICE_NOT_PAYABLE");
    }

    @Test
    void filtersTheInvoiceListByEnrollmentAndStatus() throws Exception {
        var admin = signIn("bil-admin-7@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var enrollmentId = newActiveEnrollmentId(admin, TUITION);
        createInvoice(admin, enrollmentId, HALF);

        var filtered = mockMvc.perform(get(INVOICES).cookie(admin)
                .param("enrollmentId", enrollmentId)
                .param("status", BillingConstants.InvoiceStatus.UNPAID.name())).andReturn();

        assertThat(filtered.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(filtered.getResponse().getContentAsString()).get("totalItems").asInt())
                .isEqualTo(1);
    }

    @Test
    void refusesEveryEndpointWithoutInvoicePermission() throws Exception {
        var admin = signIn("bil-admin-8@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var outsider = signIn("bil-outsider-1@eduerp.local", AccessConstants.RoleCodes.TEACHER);
        var enrollmentId = newActiveEnrollmentId(admin, TUITION);
        var invoiceId = createInvoice(admin, enrollmentId, HALF);

        assertThat(mockMvc.perform(get(INVOICES).cookie(outsider)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(post(INVOICES + "/" + invoiceId + "/cancel").cookie(outsider).with(csrf()))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void requiresAuthentication() throws Exception {
        assertThat(mockMvc.perform(get(INVOICES)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
```

> Endpoint `online-payment` KHÔNG được test ở đây: nó gọi ra cổng thanh toán thật (MoMo POST
> `/v2/gateway/api/create`). Luồng đó đã có unit test đầy đủ ở Task 14 (`InitiateOnlinePaymentTest`
> với client mock) và Task 9/10 (chữ ký). Thêm một IT gọi mạng thật sẽ làm build phụ thuộc sandbox
> của nhà cung cấp.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=InvoiceAdminControllerIT`
Expected: FAIL — 404 ở mọi endpoint (controller chưa tồn tại).

- [ ] **Step 3: Write the controller**

`modules/billing/web/InvoiceAdminController.java`:

```java
package com.eduerp.modules.billing.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.dto.CreateInvoiceRequest;
import com.eduerp.modules.billing.dto.InitiateOnlinePaymentRequest;
import com.eduerp.modules.billing.dto.InitiateOnlinePaymentResponse;
import com.eduerp.modules.billing.dto.InvoiceDetailResponse;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.dto.RecordManualPaymentRequest;
import com.eduerp.modules.billing.usecase.CancelInvoice;
import com.eduerp.modules.billing.usecase.CreateInvoice;
import com.eduerp.modules.billing.usecase.GetInvoiceDetail;
import com.eduerp.modules.billing.usecase.InitiateOnlinePayment;
import com.eduerp.modules.billing.usecase.ListInvoices;
import com.eduerp.modules.billing.usecase.RecordManualPayment;
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
 * Quản trị hoá đơn học phí - mirror PayrollRunAdminController 1:1 về cấu trúc. Huỷ hoá đơn dùng
 * {@code UPDATE_INVOICE} (không có {@code Actions.DELETE} ở bất kỳ resource nào trong hệ thống này).
 */
@RestController
@RequestMapping("/api/billing/invoices")
class InvoiceAdminController {

    private final ListInvoices listInvoices;
    private final CreateInvoice createInvoice;
    private final GetInvoiceDetail getInvoiceDetail;
    private final InitiateOnlinePayment initiateOnlinePayment;
    private final RecordManualPayment recordManualPayment;
    private final CancelInvoice cancelInvoice;

    InvoiceAdminController(ListInvoices listInvoices, CreateInvoice createInvoice,
            GetInvoiceDetail getInvoiceDetail, InitiateOnlinePayment initiateOnlinePayment,
            RecordManualPayment recordManualPayment, CancelInvoice cancelInvoice) {
        this.listInvoices = listInvoices;
        this.createInvoice = createInvoice;
        this.getInvoiceDetail = getInvoiceDetail;
        this.initiateOnlinePayment = initiateOnlinePayment;
        this.recordManualPayment = recordManualPayment;
        this.cancelInvoice = cancelInvoice;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_INVOICE)
    PageResponse<InvoiceResponse> list(@PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) UUID studentProfileId,
            @RequestParam(required = false) UUID enrollmentId,
            @RequestParam(required = false) BillingConstants.InvoiceStatus status) {
        return listInvoices.execute(pageable, studentProfileId, enrollmentId, status);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_INVOICE)
    InvoiceResponse create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreateInvoiceRequest request) {
        return createInvoice.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize(AccessConstants.AccessRules.READ_INVOICE)
    InvoiceDetailResponse get(@PathVariable UUID invoiceId) {
        return getInvoiceDetail.execute(invoiceId);
    }

    @PostMapping("/{invoiceId}/online-payment")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_INVOICE)
    InitiateOnlinePaymentResponse initiateOnlinePayment(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID invoiceId, @Valid @RequestBody InitiateOnlinePaymentRequest request) {
        return initiateOnlinePayment.execute(invoiceId, request.gateway(), principal.accountId(),
                principal.homeBranchId());
    }

    @PostMapping("/{invoiceId}/manual-payment")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_INVOICE)
    InvoiceResponse recordManualPayment(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID invoiceId, @Valid @RequestBody RecordManualPaymentRequest request) {
        return recordManualPayment.execute(invoiceId, request.amount(), principal.accountId(),
                principal.homeBranchId());
    }

    @PostMapping("/{invoiceId}/cancel")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_INVOICE)
    void cancel(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID invoiceId) {
        cancelInvoice.execute(invoiceId, principal.accountId(), principal.homeBranchId());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn test -Dtest=InvoiceAdminControllerIT`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/web/InvoiceAdminController.java \
  backend/src/test/java/com/eduerp/modules/billing/web/InvoiceAdminControllerIT.java
git commit -m "feat(billing): expose invoice admin endpoints"
```

---

### Task 22: `PaymentCallbackController` + `PaymentStatusController` + Spring Security `permitAll` (Review Focus #5)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/billing/web/PaymentCallbackController.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/web/PaymentStatusController.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/web/BillingPublicPaths.java`
- Create: `backend/src/main/java/com/eduerp/modules/billing/web/BillingSecurityConfig.java`
- Test: `backend/src/test/java/com/eduerp/modules/billing/web/PaymentCallbackControllerIT.java` (create)

> **Không sửa `IdentitySecurityConfig`.** Cách hiển nhiên nhất ("thêm 3 path vào `permitAll()` của
> identity, giống `login`/`forgot-password`" — đúng chữ spec mục 5) tạo ra **vòng phụ thuộc**:
> `identity → billing → courses → identity` (đã kiểm: `courses.usecase.CreateClass` import
> `IdentityManagement`), và `ApplicationModules.verify()` sẽ fail. Thay vào đó `modules.billing` tự
> khai một `SecurityFilterChain` riêng cho đúng 3 path của mình — đây chính là thiết kế mà javadoc của
> `core.security.SecurityBootstrapConfig` đã mô tả sẵn: *"Từng module tự khai `SecurityFilterChain` và
> endpoint của mình (identity hiện là nơi duy nhất làm việc đó)"*. Kết quả: không vòng phụ thuộc,
> không chép chuỗi path sang module khác, và quyết định "3 endpoint này public" nằm đúng trong module
> sở hữu chúng.

**Interfaces:**
- Consumes: `HandlePaymentCallback` (Task 15), `GetPaymentStatus` (Task 18), `VnPayProperties`/`MomoProperties` (Task 8).
- Produces:
  `BillingPublicPaths.VNPAY_CALLBACK = "/api/billing/payments/callback/vnpay"`,
  `BillingPublicPaths.MOMO_CALLBACK = "/api/billing/payments/callback/momo"`,
  `BillingPublicPaths.PAYMENT_STATUS_PATTERN = "/api/billing/payments/*/status"`,
  `BillingPublicPaths.ALL` (`String[]` — truyền thẳng vào `securityMatcher`);
  `BillingSecurityConfig.billingPublicFilterChain(HttpSecurity): SecurityFilterChain` (`@Order(1)`);
  `GET /api/billing/payments/callback/vnpay` → 200 với body cố định `{"RspCode":"00","Message":"Confirm Success"}`;
  `POST /api/billing/payments/callback/momo` → 204 không body;
  `GET /api/billing/payments/{gatewayTransactionId}/status` → `PaymentResponse` (public).

> **Review Focus #5 — thiết kế.** Hai endpoint callback trả về MỘT response cố định cho mọi kết quả:
> chữ ký sai, `orderId` lạ, giao dịch thất bại, hay thành công đều ra cùng status + cùng body. Đây là
> chỗ plan cố ý khác spec mục 5 bước 1 ("controller trả 400"): spec mục 11 Review Focus #5 (bắt buộc)
> đòi "chữ ký sai" và "orderId không tồn tại" không phân biệt được từ bên ngoài, nên 400 sẽ tự tạo
> đúng cái oracle mà nó cấm. Thông tin chẩn đoán vẫn còn nguyên trong log của
> `HandlePaymentCallback`. Body cố định của VNPay là `{"RspCode":"00","Message":"Confirm Success"}`
> theo đúng hợp đồng IPN của VNPay; MoMo chỉ cần 204.

- [ ] **Step 1: Write the failing test** — `backend/src/test/java/com/eduerp/modules/billing/web/PaymentCallbackControllerIT.java`:

```java
package com.eduerp.modules.billing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Review Focus #5: hai endpoint callback không được tạo oracle. Test chốt secret qua property để tự
 * ký được chữ ký hợp lệ, và tự cài lại HMAC trong test (không gọi code production).
 */
@Testcontainers
@SpringBootTest(properties = {
        "payment.vnpay.tmn-code=TMN001",
        "payment.vnpay.hash-secret=VNPAYSECRET123",
        "payment.momo.partner-code=PARTNER01",
        "payment.momo.access-key=ACCESS123",
        "payment.momo.secret-key=MOMOSECRET123"
})
@AutoConfigureMockMvc
class PaymentCallbackControllerIT {

    private static final String VNPAY_CALLBACK = "/api/billing/payments/callback/vnpay";
    private static final String MOMO_CALLBACK = "/api/billing/payments/callback/momo";

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

    private static String hmacHex(String algorithm, String secret, String data) {
        try {
            var mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            var bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(bytes.length * 2);
            for (var b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Dựng query string VNPay có chữ ký ĐÚNG cho một orderId không tồn tại trong DB. */
    private String signedVnpayQuery(String orderId) {
        var params = new TreeMap<String, String>();
        params.put("vnp_Amount", "600000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", "TMN001");
        params.put("vnp_TxnRef", orderId);
        var hashData = params.entrySet().stream()
                .map(entry -> entry.getKey() + "="
                        + java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "&" + right).orElseThrow();
        var plainQuery = params.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "&" + right).orElseThrow();
        return plainQuery + "&vnp_SecureHash=" + hmacHex("HmacSHA512", "VNPAYSECRET123", hashData);
    }

    private Map<String, String> signedMomoBody(String orderId) {
        var body = new LinkedHashMap<String, String>();
        body.put("partnerCode", "PARTNER01");
        body.put("orderId", orderId);
        body.put("requestId", "req-1");
        body.put("amount", "6000000");
        body.put("orderInfo", "Hoc phi dot 1");
        body.put("orderType", "momo_wallet");
        body.put("transId", "2345678901");
        body.put("resultCode", "0");
        body.put("message", "Successful.");
        body.put("payType", "qr");
        body.put("responseTime", "1767222123000");
        body.put("extraData", "");
        var payload = "accessKey=ACCESS123&amount=" + body.get("amount") + "&extraData="
                + "&message=" + body.get("message") + "&orderId=" + orderId
                + "&orderInfo=" + body.get("orderInfo") + "&orderType=" + body.get("orderType")
                + "&partnerCode=" + body.get("partnerCode") + "&payType=" + body.get("payType")
                + "&requestId=" + body.get("requestId") + "&responseTime=" + body.get("responseTime")
                + "&resultCode=" + body.get("resultCode") + "&transId=" + body.get("transId");
        body.put("signature", hmacHex("HmacSHA256", "MOMOSECRET123", payload));
        return body;
    }

    /** Hai endpoint callback phải gọi được KHÔNG kèm cookie đăng nhập và KHÔNG kèm CSRF token - cổng
     * thanh toán gọi server-to-server (spec mục 5). */
    @Test
    void bothCallbackEndpointsArePublicAndCsrfExempt() throws Exception {
        var vnpay = mockMvc.perform(get(VNPAY_CALLBACK + "?" + signedVnpayQuery("order-public-check"))).andReturn();
        var momo = mockMvc.perform(post(MOMO_CALLBACK).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signedMomoBody("order-public-check")))).andReturn();

        assertThat(vnpay.getResponse().getStatus()).isNotIn(401, 403);
        assertThat(momo.getResponse().getStatus()).isNotIn(401, 403);
    }

    /**
     * Review Focus #5, VNPay: chữ ký sai và orderId lạ (chữ ký đúng) trả về status + body GIỐNG NHAU
     * từng byte - không có cách nào từ ngoài biết orderId nào tồn tại.
     */
    @Test
    void vnpayReturnsAnIdenticalResponseForABadSignatureAndForAnUnknownOrderId() throws Exception {
        var badSignature = mockMvc.perform(get(VNPAY_CALLBACK
                + "?vnp_Amount=600000000&vnp_ResponseCode=00&vnp_TmnCode=TMN001&vnp_TxnRef=order-x"
                + "&vnp_SecureHash=deadbeef")).andReturn().getResponse();
        var unknownOrder = mockMvc.perform(get(VNPAY_CALLBACK + "?" + signedVnpayQuery("order-does-not-exist")))
                .andReturn().getResponse();

        assertThat(badSignature.getStatus()).isEqualTo(unknownOrder.getStatus());
        assertThat(badSignature.getContentAsString()).isEqualTo(unknownOrder.getContentAsString());
        assertThat(badSignature.getContentAsString()).doesNotContain("order-x", "SIGNATURE", "signature");
    }

    /** Review Focus #5, MoMo: cùng một yêu cầu, ở cả hai trường hợp. */
    @Test
    void momoReturnsAnIdenticalResponseForABadSignatureAndForAnUnknownOrderId() throws Exception {
        var forged = signedMomoBody("order-y");
        forged.put("signature", "deadbeef");
        var badSignature = mockMvc.perform(post(MOMO_CALLBACK).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(forged))).andReturn().getResponse();
        var unknownOrder = mockMvc.perform(post(MOMO_CALLBACK).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signedMomoBody("order-also-missing"))))
                .andReturn().getResponse();

        assertThat(badSignature.getStatus()).isEqualTo(unknownOrder.getStatus());
        assertThat(badSignature.getContentAsString()).isEqualTo(unknownOrder.getContentAsString());
        assertThat(badSignature.getContentAsString()).doesNotContain("order-y", "SIGNATURE", "signature");
    }

    /** Body cố định của VNPay đúng theo hợp đồng IPN của họ - trả khác đi là VNPay coi như chưa nhận. */
    @Test
    void vnpayAlwaysAnswersWithTheFixedConfirmationBody() throws Exception {
        var response = mockMvc.perform(get(VNPAY_CALLBACK + "?" + signedVnpayQuery("order-fixed-body")))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("{\"RspCode\":\"00\",\"Message\":\"Confirm Success\"}");
    }

    /** Endpoint tra trạng thái cũng public (trang Return URL chạy khi phụ huynh chưa đăng nhập), và
     * một gatewayTransactionId lạ trả 404 ProblemDetail bình thường - endpoint này KHÔNG phải đường
     * vào của cổng thanh toán nên không áp quy tắc "một response duy nhất". */
    @Test
    void paymentStatusIsPublicAndReportsAnUnknownTransactionAsNotFound() throws Exception {
        var response = mockMvc.perform(get("/api/billing/payments/order-not-there/status")).andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("BILLING_PAYMENT_NOT_FOUND");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=PaymentCallbackControllerIT`
Expected: FAIL — 401/403 (chưa permitAll) hoặc 404 (controller chưa tồn tại).

- [ ] **Step 3: Create `BillingPublicPaths`**

`modules/billing/web/BillingPublicPaths.java`:

```java
package com.eduerp.modules.billing.web;

/**
 * Ba đường dẫn public của module billing, khai ở MỘT chỗ để {@link BillingSecurityConfig} và các
 * {@code @RequestMapping} cùng tham chiếu thay vì chép lại chuỗi (không hardcode path trong config).
 * Package-private: chỉ module billing cần biết, KHÔNG expose ra ngoài - nếu để
 * {@code modules.identity} đọc thì sinh vòng {@code identity → billing → courses → identity}.
 *
 * <p>Hai callback public vì cổng thanh toán gọi server-to-server, không có session/cookie của ứng
 * dụng; xác thực đi bằng chữ ký HMAC trong usecase, không bằng Spring Security.
 * {@link #PAYMENT_STATUS_PATTERN} public vì trang {@code /payment/return/:gateway} chạy khi phụ
 * huynh chưa đăng nhập (spec mục 10); {@code gatewayTransactionId} là chuỗi không đoán được nên đóng
 * vai capability token, và {@code PaymentResponse} không chứa dữ liệu cá nhân nào.
 */
final class BillingPublicPaths {

    private BillingPublicPaths() {
    }

    static final String VNPAY_CALLBACK = "/api/billing/payments/callback/vnpay";
    static final String MOMO_CALLBACK = "/api/billing/payments/callback/momo";
    static final String PAYMENT_STATUS_PATTERN = "/api/billing/payments/*/status";

    /** Truyền thẳng vào {@code securityMatcher(...)}. */
    static final String[] ALL = {VNPAY_CALLBACK, MOMO_CALLBACK, PAYMENT_STATUS_PATTERN};
}
```

> Giữ nguyên `backend/src/main/java/com/eduerp/modules/billing/web/package-info.java` như Task 11 đã
> tạo — KHÔNG thêm `@NamedInterface`: không module nào ngoài billing được đọc package này.

- [ ] **Step 4: Write `PaymentCallbackController`**

`modules/billing/web/PaymentCallbackController.java`:

```java
package com.eduerp.modules.billing.web;

import com.eduerp.integrations.payment.PaymentGatewayType;
import com.eduerp.modules.billing.BillingException;
import com.eduerp.modules.billing.usecase.HandlePaymentCallback;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đường vào của cổng thanh toán. PUBLIC, không {@code @PreAuthorize}: MoMo/VNPay gọi
 * server-to-server, không mang session/cookie của ứng dụng (spec mục 5). Xác thực là chữ ký HMAC,
 * kiểm trong usecase.
 *
 * <p><strong>Review Focus #5:</strong> mỗi endpoint trả về MỘT response cố định cho MỌI kết quả -
 * chữ ký sai, {@code orderId} lạ, giao dịch thất bại hay thành công đều giống nhau từng byte. Đây là
 * điểm plan cố ý khác spec mục 5 bước 1 (ở đó viết "trả 400"): một mã trạng thái riêng cho chữ ký
 * sai chính là oracle mà Review Focus #5 cấm. Chẩn đoán vẫn nằm đủ trong log của
 * {@link HandlePaymentCallback}.
 */
@RestController
@RequestMapping("/api/billing/payments/callback")
class PaymentCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackController.class);

    /** Hợp đồng IPN của VNPay: trả khác chuỗi này là VNPay coi như mình chưa nhận và sẽ gọi lại. */
    private static final String VNPAY_ACK_BODY = "{\"RspCode\":\"00\",\"Message\":\"Confirm Success\"}";

    private final HandlePaymentCallback handlePaymentCallback;

    PaymentCallbackController(HandlePaymentCallback handlePaymentCallback) {
        this.handlePaymentCallback = handlePaymentCallback;
    }

    @GetMapping("/vnpay")
    ResponseEntity<String> vnpay(@RequestParam Map<String, String> params) {
        handleQuietly(PaymentGatewayType.VNPAY, params);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(VNPAY_ACK_BODY);
    }

    @PostMapping("/momo")
    ResponseEntity<Void> momo(@RequestBody Map<String, Object> body) {
        handleQuietly(PaymentGatewayType.MOMO, toStringMap(body));
        return ResponseEntity.noContent().build();
    }

    /**
     * Bắt MỌI {@code BillingException} (gồm {@code InvalidCallbackSignatureException}) và trả về
     * đúng response cố định như đường thành công - không để bất kỳ khác biệt nào ra ngoài.
     */
    private void handleQuietly(PaymentGatewayType gatewayType, Map<String, String> params) {
        try {
            handlePaymentCallback.execute(gatewayType, params);
        } catch (BillingException rejected) {
            log.warn("Từ chối callback của cổng {}: {}", gatewayType, rejected.getErrorCode());
        }
    }

    /** MoMo gửi JSON có cả số và chuỗi; chữ ký được tính trên dạng chuỗi của từng giá trị. */
    private static Map<String, String> toStringMap(Map<String, Object> body) {
        var params = new HashMap<String, String>();
        body.forEach((key, value) -> params.put(key, value == null ? "" : String.valueOf(value)));
        return params;
    }
}
```

- [ ] **Step 5: Write `PaymentStatusController`**

`modules/billing/web/PaymentStatusController.java`:

```java
package com.eduerp.modules.billing.web;

import com.eduerp.modules.billing.dto.PaymentResponse;
import com.eduerp.modules.billing.usecase.GetPaymentStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trạng thái thật của một giao dịch, cho trang {@code /payment/return/:gateway} ở frontend poll lại
 * sau khi phụ huynh quay về từ cổng (IPN là nguồn sự thật và xử lý bất đồng bộ - spec mục 10).
 *
 * <p>PUBLIC, không {@code @PreAuthorize}: phụ huynh có thể chưa đăng nhập khi được redirect về. Đây
 * là điểm plan cố ý khác bảng quyền ở spec mục 5 (ở đó ghi {@code READ_INVOICE}) - với
 * {@code READ_INVOICE} thì trang Return URL không thể hoạt động đúng như spec mục 10 mô tả.
 * {@code gatewayTransactionId} ({@code invoiceId}-{@code installment}-{@code millis}) không đoán
 * được nên đóng vai capability token, và {@link PaymentResponse} không chứa dữ liệu cá nhân nào -
 * {@code BillingReadUseCasesTest} chốt lại hình dạng đó bằng reflection.
 */
@RestController
@RequestMapping("/api/billing/payments")
class PaymentStatusController {

    private final GetPaymentStatus getPaymentStatus;

    PaymentStatusController(GetPaymentStatus getPaymentStatus) {
        this.getPaymentStatus = getPaymentStatus;
    }

    @GetMapping("/{gatewayTransactionId}/status")
    PaymentResponse get(@PathVariable String gatewayTransactionId) {
        return getPaymentStatus.execute(gatewayTransactionId);
    }
}
```

- [ ] **Step 6: Open the three paths with a billing-owned filter chain**

`modules/billing/web/BillingSecurityConfig.java`:

```java
package com.eduerp.modules.billing.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Ba endpoint public của billing, khai bằng một {@code SecurityFilterChain} RIÊNG của module - đúng
 * thiết kế mà {@code core.security.SecurityBootstrapConfig} mô tả ("từng module tự khai
 * SecurityFilterChain và endpoint của mình"). KHÔNG thêm path vào
 * {@code identity.web.IdentitySecurityConfig}: {@code identity} đọc {@code billing} sẽ sinh vòng
 * {@code identity → billing → courses → identity} và {@code ApplicationModules.verify()} fail.
 *
 * <p>{@code @Order(1)} để chain này được xét TRƯỚC chain của identity (chain đó không có
 * {@code securityMatcher} nên là catch-all và nhận {@code LOWEST_PRECEDENCE}).
 *
 * <p>CSRF tắt trên đúng ba path này: MoMo gọi IPN bằng POST server-to-server, không thể mang CSRF
 * token của SPA. Cũng không gắn {@code CookieAuthenticationFilter} - ba endpoint này không cần biết
 * "ai đang gọi", xác thực đi bằng chữ ký HMAC trong usecase.
 */
@Configuration
class BillingSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain billingPublicFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(BillingPublicPaths.ALL)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest='PaymentCallbackControllerIT,ModularityTests,SpaCsrfHandshakeIT,AuthControllerIT'`
Expected: PASS (5 + 2 + các test identity cũ). Hai điều cần nhìn kỹ nếu đỏ:
- `ModularityTests.verifiesModularStructure` đỏ → có ai đó vừa cho module khác import
  `billing.web`; `BillingPublicPaths` phải package-private và `billing.web` không được
  `@NamedInterface`.
- `AuthControllerIT`/`SpaCsrfHandshakeIT` đỏ → chain mới đã "ăn" request của identity; kiểm
  `securityMatcher(BillingPublicPaths.ALL)` có đúng 3 path và `@Order(1)` còn nguyên.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/billing/web \
  backend/src/test/java/com/eduerp/modules/billing/web/PaymentCallbackControllerIT.java
git commit -m "feat(billing): add public gateway callback and payment status endpoints"
```

---

### Task 23: `modules.audit` — nghe 6 sự kiện mới

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/audit/AuditConstants.java`
- Modify: `backend/src/main/java/com/eduerp/modules/audit/internal/listener/AuditEventListeners.java`
- Test: `backend/src/test/java/com/eduerp/modules/audit/EnrollmentBillingEventAuditingIT.java` (create)

**Interfaces:**
- Consumes: `EnrollmentEvents` (Task 4), `BillingEvents` (Task 11).
- Produces: `AuditConstants.Actions.{ENROLLMENT_CREATE, ENROLLMENT_WITHDRAW, ENROLLMENT_COMPLETE, INVOICE_CREATE, PAYMENT_RECEIVED, INVOICE_OVERDUE}`;
  `AuditConstants.EntityTypes.{ENROLLMENT, INVOICE}`;
  6 method `@ApplicationModuleListener` mới trong `AuditEventListeners`.

- [ ] **Step 1: Write the failing test** — `backend/src/test/java/com/eduerp/modules/audit/EnrollmentBillingEventAuditingIT.java`:

```java
package com.eduerp.modules.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.redis.testcontainers.RedisContainer;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * {@code @ApplicationModuleListener} = {@code @TransactionalEventListener} với
 * {@code fallbackExecution=false} mặc định - publish trực tiếp từ test (không có transaction thật)
 * sẽ KHÔNG BAO GIỜ gọi listener. Dùng một bean {@code @Transactional} thật để publish, mirror
 * {@code PayrollEventAuditingIT}.
 */
@Testcontainers
@SpringBootTest
class EnrollmentBillingEventAuditingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @TestConfiguration
    static class TransactionalPublisherConfig {
        @Bean
        TransactionalPublisher enrollmentBillingPublisher(ApplicationEventPublisher events) {
            return new TransactionalPublisher(events);
        }
    }

    @Component
    static class TransactionalPublisher {
        private final ApplicationEventPublisher events;

        TransactionalPublisher(ApplicationEventPublisher events) {
            this.events = events;
        }

        @Transactional
        void publish(Object event) {
            events.publishEvent(event);
        }
    }

    @Autowired
    TransactionalPublisher events;

    @Autowired
    AuditManagement audit;

    private void assertAudited(String entityType, String action, UUID entityId) {
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(entityType, action, 50).stream().map(a -> a.entityId()).toList();
            assertThat(entityIds).contains(entityId.toString());
        });
    }

    @Test
    void auditsEnrollmentCreated() {
        var enrollmentId = UUID.randomUUID();
        events.publish(new EnrollmentEvents.EnrollmentCreated(enrollmentId, UUID.randomUUID(), UUID.randomUUID()));

        assertAudited(AuditConstants.EntityTypes.ENROLLMENT, AuditConstants.Actions.ENROLLMENT_CREATE,
                enrollmentId);
    }

    @Test
    void auditsEnrollmentWithdrawn() {
        var enrollmentId = UUID.randomUUID();
        events.publish(new EnrollmentEvents.EnrollmentWithdrawn(enrollmentId, UUID.randomUUID(),
                UUID.randomUUID()));

        assertAudited(AuditConstants.EntityTypes.ENROLLMENT, AuditConstants.Actions.ENROLLMENT_WITHDRAW,
                enrollmentId);
    }

    @Test
    void auditsEnrollmentCompleted() {
        var enrollmentId = UUID.randomUUID();
        events.publish(new EnrollmentEvents.EnrollmentCompleted(enrollmentId, UUID.randomUUID(),
                UUID.randomUUID()));

        assertAudited(AuditConstants.EntityTypes.ENROLLMENT, AuditConstants.Actions.ENROLLMENT_COMPLETE,
                enrollmentId);
    }

    @Test
    void auditsInvoiceCreated() {
        var invoiceId = UUID.randomUUID();
        events.publish(new BillingEvents.InvoiceCreated(invoiceId, UUID.randomUUID(), UUID.randomUUID()));

        assertAudited(AuditConstants.EntityTypes.INVOICE, AuditConstants.Actions.INVOICE_CREATE, invoiceId);
    }

    @Test
    void auditsPaymentReceived() {
        var invoiceId = UUID.randomUUID();
        events.publish(new BillingEvents.PaymentReceived(invoiceId, UUID.randomUUID(), UUID.randomUUID()));

        assertAudited(AuditConstants.EntityTypes.INVOICE, AuditConstants.Actions.PAYMENT_RECEIVED, invoiceId);
    }

    /**
     * {@code InvoiceOverdue} do scheduler tự sinh nên {@code actorAccountId} là {@code null}. Cột
     * {@code audit_logs.actor_account_id} vốn nullable (V7) và {@code AuditLog} không validate gì, nên
     * listener chỉ cần truyền thẳng - test này chốt lại rằng dòng log vẫn được ghi, không bị
     * ném ra lỗi NOT NULL như một số hệ thống khác.
     */
    @Test
    void auditsInvoiceOverdueEvenWithoutAnActor() {
        var invoiceId = UUID.randomUUID();
        events.publish(new BillingEvents.InvoiceOverdue(invoiceId, null, UUID.randomUUID()));

        assertAudited(AuditConstants.EntityTypes.INVOICE, AuditConstants.Actions.INVOICE_OVERDUE, invoiceId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=EnrollmentBillingEventAuditingIT`
Expected: FAIL — compile error, `AuditConstants.Actions.ENROLLMENT_CREATE` chưa tồn tại.

- [ ] **Step 3: Add the audit vocabulary**

Trong `AuditConstants.Actions`, thêm ngay sau `PAYROLL_RUN_APPROVE`:

```java
        public static final String ENROLLMENT_CREATE = "ENROLLMENT_CREATE";
        public static final String ENROLLMENT_WITHDRAW = "ENROLLMENT_WITHDRAW";
        public static final String ENROLLMENT_COMPLETE = "ENROLLMENT_COMPLETE";
        public static final String INVOICE_CREATE = "INVOICE_CREATE";
        public static final String PAYMENT_RECEIVED = "PAYMENT_RECEIVED";
        public static final String INVOICE_OVERDUE = "INVOICE_OVERDUE";
```

Trong `AuditConstants.EntityTypes`, thêm ngay sau `PAYROLL_RUN`:

```java
        public static final String ENROLLMENT = "ENROLLMENT";
        public static final String INVOICE = "INVOICE";
```

- [ ] **Step 4: Add the six listeners**

Trong `AuditEventListeners`, thêm hai import:

```java
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.enrollment.EnrollmentEvents;
```

và thêm sáu method vào cuối class (sau listener `PayrollEvents.PayrollRunApproved`):

```java
    @ApplicationModuleListener
    void on(EnrollmentEvents.EnrollmentCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ENROLLMENT_CREATE,
                AuditConstants.EntityTypes.ENROLLMENT, event.enrollmentId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(EnrollmentEvents.EnrollmentWithdrawn event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ENROLLMENT_WITHDRAW,
                AuditConstants.EntityTypes.ENROLLMENT, event.enrollmentId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(EnrollmentEvents.EnrollmentCompleted event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.ENROLLMENT_COMPLETE,
                AuditConstants.EntityTypes.ENROLLMENT, event.enrollmentId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(BillingEvents.InvoiceCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.INVOICE_CREATE,
                AuditConstants.EntityTypes.INVOICE, event.invoiceId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(BillingEvents.PaymentReceived event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.PAYMENT_RECEIVED,
                AuditConstants.EntityTypes.INVOICE, event.invoiceId().toString(), event.actorBranchId()));
    }

    /** {@code actorAccountId} là {@code null} (scheduler tự sinh, không ai bấm) - truyền thẳng, cột
     * {@code audit_logs.actor_account_id} nullable từ V7 nên dòng log vẫn ghi được. Đây là sự kiện hệ
     * thống đầu tiên trong dự án không có actor; không được thay bằng một UUID giả, vì khi đó nhật ký
     * sẽ nói rằng một người đã làm việc này. */
    @ApplicationModuleListener
    void on(BillingEvents.InvoiceOverdue event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.INVOICE_OVERDUE,
                AuditConstants.EntityTypes.INVOICE, event.invoiceId().toString(), event.actorBranchId()));
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && mvn test -Dtest='EnrollmentBillingEventAuditingIT,PayrollEventAuditingIT,ModularityTests'`
Expected: PASS (6 + 3 + 2 tests).

- [ ] **Step 6: Run the whole backend suite once**

Run: `cd backend && mvn clean verify`
Expected: BUILD SUCCESS — toàn bộ unit test + IT, `ModularityTests` xanh, Flyway V16→V19 chạy sạch
trên container mới.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/audit \
  backend/src/test/java/com/eduerp/modules/audit/EnrollmentBillingEventAuditingIT.java
git commit -m "feat(audit): log enrollment lifecycle and billing events"
```

---

> ## Ghi chú về TDD ở frontend (áp dụng cho Task 24–28)
>
> Repo này **chưa có test runner ở frontend** (`frontend/package.json` không có vitest/jest, và
> `frontend/src` không có file `*.test.*` nào). Plan này **không thêm dependency test mới** — việc đó
> là một quyết định kiến trúc riêng, nằm ngoài spec Phân hệ 3.
>
> Vòng lặp "đỏ → xanh" ở frontend vì thế chạy bằng **trình kiểm kiểu và linter**, đúng như Phase 4
> trong `AGENTS.md` yêu cầu:
>
> ```bash
> cd frontend && npm run typecheck   # tsc --noEmit
> cd frontend && npm run lint        # eslint + eslint-plugin-boundaries
> ```
>
> Mỗi task frontend mở đầu bằng việc viết **nơi tiêu thụ** (consumer) tham chiếu tới symbol chưa tồn
> tại, chạy `npm run typecheck` để thấy nó đỏ, rồi mới viết schema/hook/UI cho nó xanh. `eslint-plugin-boundaries`
> là thứ giữ hướng import `shared → entities → modules → app`, nên `npm run lint` đỏ nếu một module
> import ngược.
>
> **Một chỗ plan đặt file khác spec mục 10, có lý do:** spec viết
> `entities/enrollment/hooks/use-enrollments.ts` và `entities/billing/hooks/use-invoices.ts`, nhưng
> precedent thật của repo đặt query hook của entity trong `api/` (`entities/payroll/api/use-contracts.ts`,
> `entities/course/api/use-courses.ts`, `entities/class/api/use-classes.ts` — không entity nào có thư
> mục `hooks/`). Plan theo precedent: hook đọc của entity nằm trong `api/`, còn `hooks/` chỉ dùng cho
> controller hook của `modules/*` (`modules/payroll/hooks/use-*-controller.ts`) — đúng như spec mục 10
> yêu cầu ở phần tách logic khỏi render.
>
> **Hợp đồng field phải khớp byte-for-byte với DTO backend** (Task 4, 11):
> `EnrollmentResponse(id, studentProfileId, classId, courseId, branchId, status, enrolledAt, withdrawnAt)`,
> `InvoiceResponse(id, enrollmentId, studentProfileId, courseId, branchId, installmentNumber, amount, amountPaid, status, dueDate, issuedAt)`,
> `PaymentResponse(id, amount, method, status, paidAt, createdAt)`,
> `InvoiceDetailResponse(invoice, payments)`, `InitiateOnlinePaymentResponse(payUrl)`.
> Lệch một tên là Zod `.parse()` ném ngay tại biên gọi API.

---

### Task 24: Frontend — shared constants + `tuitionFee` trên `entities/course` và `modules/courses`

**Files:**
- Modify: `frontend/src/shared/constants/permissions.ts`
- Modify: `frontend/src/shared/constants/api-routes.ts`
- Modify: `frontend/src/shared/constants/app-routes.ts`
- Modify: `frontend/src/shared/api/api-client.ts`
- Modify: `frontend/src/entities/course/model/course-schema.ts`
- Modify: `frontend/src/modules/courses/model/courses-forms.ts`
- Modify: `frontend/src/modules/courses/ui/create-course-dialog.tsx`
- Modify: `frontend/src/modules/courses/ui/edit-course-dialog.tsx`
- Modify: `frontend/src/modules/courses/ui/courses-table.tsx`

**Interfaces:**
- Consumes: `CourseResponse.tuitionFee` (Task 1), `AccessConstants.Resources.ENROLLMENT`/`INVOICE` (Task 3), đường dẫn ở Task 20/21/22.
- Produces:
  `RESOURCE.enrollment = "ENROLLMENT"`, `RESOURCE.invoice = "INVOICE"` + hai nhãn trong `RESOURCE_LABEL`;
  `ACCESS_RULE.{readEnrollment,createEnrollment,updateEnrollment,readInvoice,createInvoice,updateInvoice}`;
  `API_ROUTE.enrollment.{enrollments,enrollment,enrollmentWithdraw,enrollmentComplete}`;
  `API_ROUTE.billing.{invoices,invoice,invoiceOnlinePayment,invoiceManualPayment,invoiceCancel,paymentStatus}`;
  `APP_ROUTE.{enrollments,invoices,invoiceDetail,paymentReturn}` + `buildInvoiceDetailPath(invoiceId)` + `buildPaymentReturnPath(gateway)` + `PAYMENT_RETURN_GATEWAY_PARAM`;
  `courseSummarySchema.tuitionFee: number | null`, `CreateCoursePayload.tuitionFee`, `UpdateCoursePayload.tuitionFee`.

- [ ] **Step 1: Make the typecheck fail — thread `tuitionFee` into the course form schemas first**

Trong `frontend/src/modules/courses/model/courses-forms.ts`, thêm field `tuitionFee` vào cả hai schema
(ngay sau `standardSessionCount` trong mỗi object):

```ts
  tuitionFee: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : Number(value)))
    .refine((value) => value === null || value >= 0, "Học phí không được âm"),
```

- [ ] **Step 2: Run typecheck to verify it fails**

Run: `cd frontend && npm run typecheck`
Expected: FAIL — `CreateCoursePayload`/`UpdateCoursePayload` chưa có `tuitionFee`, nên
`createCourse.mutateAsync(values)` ở `create-course-dialog.tsx` không khớp kiểu; và
`initialValues` của hai dialog thiếu khóa `tuitionFee`.

- [ ] **Step 3: Add `tuitionFee` to the course entity contract**

Trong `frontend/src/entities/course/model/course-schema.ts`:

```ts
import { z } from "zod";

export const courseSummarySchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  standardSessionCount: z.number().int().nullable(),
  /** Học phí toàn khoá, VND. null = khoá học chưa chốt giá (backend: Course.tuitionFee nullable). */
  tuitionFee: z.number().nullable(),
  active: z.boolean(),
});

export type CourseSummary = z.infer<typeof courseSummarySchema>;

export interface CreateCoursePayload {
  readonly code: string;
  readonly name: string;
  readonly description: string | null;
  readonly standardSessionCount: number | null;
  readonly tuitionFee: number | null;
}

export interface UpdateCoursePayload {
  readonly name: string;
  readonly description: string | null;
  readonly standardSessionCount: number | null;
  readonly tuitionFee: number | null;
  readonly active: boolean;
}
```

- [ ] **Step 4: Wire the two dialogs and the table**

`create-course-dialog.tsx` — sửa `initialValues` và thêm một `FormField` trước thẻ `</form>`:

```tsx
    initialValues: { code: "", name: "", description: "", standardSessionCount: "", tuitionFee: "" },
```

```tsx
        <FormField label="Học phí toàn khoá (VND)" htmlFor="course-tuition-fee" hint="Không bắt buộc. Để trống nếu chưa chốt giá." error={form.fieldErrors.tuitionFee}>
          <GlassInput
            id="course-tuition-fee"
            type="number"
            min={0}
            step={1000}
            value={form.values.tuitionFee}
            invalid={form.fieldErrors.tuitionFee !== undefined}
            onChange={(event) => form.setValue("tuitionFee", event.target.value)}
          />
        </FormField>
```

`edit-course-dialog.tsx` — sửa `initialValues` và thêm cùng một `FormField` (đổi id để không trùng):

```tsx
    initialValues: {
      name: course.name,
      description: course.description ?? "",
      standardSessionCount: course.standardSessionCount?.toString() ?? "",
      tuitionFee: course.tuitionFee?.toString() ?? "",
      active: course.active,
    },
```

```tsx
        <FormField label="Học phí toàn khoá (VND)" htmlFor="edit-course-tuition-fee" hint="Để trống nếu chưa chốt giá." error={form.fieldErrors.tuitionFee}>
          <GlassInput
            id="edit-course-tuition-fee"
            type="number"
            min={0}
            step={1000}
            value={form.values.tuitionFee}
            invalid={form.fieldErrors.tuitionFee !== undefined}
            onChange={(event) => form.setValue("tuitionFee", event.target.value)}
          />
        </FormField>
```

`courses-table.tsx` — thay dòng mô tả phụ để kế toán thấy ngay khoá nào chưa gắn giá (dùng
`formatter.count` đã có ở `@/shared/lib/format`, thêm import `import { formatter } from "@/shared/lib/format";`):

```tsx
            <p className="truncate text-xs text-mist-500">
              {course.tuitionFee === null
                ? "Chưa gắn học phí"
                : `${formatter.count(course.tuitionFee)} đ`}
            </p>
```

- [ ] **Step 5: Run typecheck to verify it passes**

Run: `cd frontend && npm run typecheck`
Expected: PASS (0 lỗi).

- [ ] **Step 6: Add the permission constants**

Trong `frontend/src/shared/constants/permissions.ts`:

- `RESOURCE`: thêm sau `payroll`:

```ts
  enrollment: "ENROLLMENT",
  invoice: "INVOICE",
```

- `RESOURCE_LABEL`: thêm sau dòng `payroll`:

```ts
  [RESOURCE.enrollment]: "Ghi danh",
  [RESOURCE.invoice]: "Hoá đơn học phí",
```

- `ACCESS_RULE`: thêm sau `approvePayroll` (6 rule, đều scope ORGANIZATION như backend):

```ts
  readEnrollment: {
    resource: RESOURCE.enrollment,
    action: ACTION.read,
    scope: PERMISSION_SCOPE.organization,
  },
  createEnrollment: {
    resource: RESOURCE.enrollment,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateEnrollment: {
    resource: RESOURCE.enrollment,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  readInvoice: { resource: RESOURCE.invoice, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createInvoice: {
    resource: RESOURCE.invoice,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateInvoice: {
    resource: RESOURCE.invoice,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
```

- [ ] **Step 7: Add the API routes**

Trong `frontend/src/shared/constants/api-routes.ts`, thêm hai nhóm sau nhóm `payroll`:

```ts
  enrollment: {
    enrollments: "/api/enrollment/enrollments",
    enrollment: (enrollmentId: string) => `/api/enrollment/enrollments/${enrollmentId}`,
    enrollmentWithdraw: (enrollmentId: string) => `/api/enrollment/enrollments/${enrollmentId}/withdraw`,
    enrollmentComplete: (enrollmentId: string) => `/api/enrollment/enrollments/${enrollmentId}/complete`,
  },
  billing: {
    invoices: "/api/billing/invoices",
    invoice: (invoiceId: string) => `/api/billing/invoices/${invoiceId}`,
    invoiceOnlinePayment: (invoiceId: string) => `/api/billing/invoices/${invoiceId}/online-payment`,
    invoiceManualPayment: (invoiceId: string) => `/api/billing/invoices/${invoiceId}/manual-payment`,
    invoiceCancel: (invoiceId: string) => `/api/billing/invoices/${invoiceId}/cancel`,
    /** Public ở backend - trang Return URL gọi được khi phụ huynh chưa đăng nhập. */
    paymentStatus: (gatewayTransactionId: string) =>
      `/api/billing/payments/${gatewayTransactionId}/status`,
  },
```

Và thêm endpoint tra trạng thái vào danh sách không thử refresh phiên — nó public, một 401 ở đó không
bao giờ sửa được bằng refresh và sẽ làm trang Return URL nhảy về màn hình đăng nhập:

```ts
export const ENDPOINTS_WITHOUT_SESSION_RETRY: readonly string[] = [
  API_ROUTE.auth.login,
  API_ROUTE.auth.completeInvite,
  API_ROUTE.auth.refresh,
  API_ROUTE.account.forgotPassword,
  API_ROUTE.account.resetPassword,
];

/**
 * Endpoint public có path động nên không so khớp được bằng danh sách chuỗi ở trên -
 * {@code apiClient} tự bỏ qua lượt refresh với mọi path bắt đầu bằng tiền tố này.
 */
export const PUBLIC_PATH_PREFIXES: readonly string[] = ["/api/billing/payments/"];
```

Trong `frontend/src/shared/api/api-client.ts`, sửa dòng import hiện có thành:

```ts
import { API_ROUTE, ENDPOINTS_WITHOUT_SESSION_RETRY, PUBLIC_PATH_PREFIXES } from "@/shared/constants/api-routes";
```

rồi mở rộng điều kiện bỏ qua refresh trong `sendWithSessionRetry`:

```ts
    if (response.status === HTTP_STATUS.unauthorized && !this.skipsSessionRetry(path)) {
```

và thêm method private vào class:

```ts
  private skipsSessionRetry(path: string): boolean {
    return (
      ENDPOINTS_WITHOUT_SESSION_RETRY.includes(path) ||
      PUBLIC_PATH_PREFIXES.some((prefix) => path.startsWith(prefix))
    );
  }
```

- [ ] **Step 8: Add the app routes**

Trong `frontend/src/shared/constants/app-routes.ts`, thêm vào `APP_ROUTE` sau `payrollRunDetail`:

```ts
  enrollments: "/admin/enrollments",
  invoices: "/admin/billing/invoices",
  invoiceDetail: "/admin/billing/invoices/:invoiceId",
  /** Public: phụ huynh quay về từ cổng thanh toán, có thể chưa đăng nhập (spec mục 10). */
  paymentReturn: "/payment/return/:gateway",
```

và thêm hai helper + một hằng tên tham số ở cuối file:

```ts
/** Link thật tới trang chi tiết một hoá đơn - APP_ROUTE.invoiceDetail chỉ là route template. */
export function buildInvoiceDetailPath(invoiceId: string): string {
  return `/admin/billing/invoices/${invoiceId}`;
}

/** Link thật tới trang Return URL của một cổng - phải khớp payment.*.redirect-url/return-url ở backend. */
export function buildPaymentReturnPath(gateway: string): string {
  return `/payment/return/${gateway}`;
}

/** Tên param cổng thanh toán trong APP_ROUTE.paymentReturn, dùng với useParams(). */
export const PAYMENT_RETURN_GATEWAY_PARAM = "gateway";
```

- [ ] **Step 9: Run typecheck and lint**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: PASS cả hai, 0 lỗi.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/shared frontend/src/entities/course frontend/src/modules/courses
git commit -m "feat(frontend): add tuition fee to course forms and enrollment/billing constants"
```

---

### Task 25: Frontend — `entities/enrollment` và `entities/billing`

**Files:**
- Create: `frontend/src/entities/enrollment/model/enrollment-schema.ts`
- Create: `frontend/src/entities/enrollment/api/enrollment-api.ts`
- Create: `frontend/src/entities/enrollment/api/enrollment-keys.ts`
- Create: `frontend/src/entities/enrollment/api/use-enrollments.ts`
- Create: `frontend/src/entities/enrollment/index.ts`
- Create: `frontend/src/entities/billing/model/billing-schema.ts`
- Create: `frontend/src/entities/billing/api/billing-api.ts`
- Create: `frontend/src/entities/billing/api/billing-keys.ts`
- Create: `frontend/src/entities/billing/api/use-invoices.ts`
- Create: `frontend/src/entities/billing/api/use-invoice-detail.ts`
- Create: `frontend/src/entities/billing/api/use-payment-status.ts`
- Create: `frontend/src/entities/billing/index.ts`

**Interfaces:**
- Consumes: `API_ROUTE.enrollment`/`API_ROUTE.billing` (Task 24), `pageResponseSchema` (`@/shared/api/schemas`), `apiClient`.
- Produces:
  `ENROLLMENT_STATUS`/`ENROLLMENT_STATUS_LABEL`, `enrollmentSummarySchema`, `type EnrollmentSummary`, `type CreateEnrollmentPayload`;
  `enrollmentApi.{listEnrollments,getEnrollment,createEnrollment,withdrawEnrollment,completeEnrollment}`;
  `enrollmentKeys.{all,lists,list,detail}`; `useEnrollments(page, size, studentProfileId?, classId?)`;
  `INVOICE_STATUS`/`INVOICE_STATUS_LABEL`, `PAYMENT_METHOD`/`PAYMENT_METHOD_LABEL`, `PAYMENT_STATUS`/`PAYMENT_STATUS_LABEL`,
  `invoiceSummarySchema`, `paymentSchema`, `invoiceDetailSchema`, `type InvoiceSummary`, `type Payment`, `type InvoiceDetail`,
  `type CreateInvoicePayload`, `type InitiateOnlinePaymentPayload`, `type RecordManualPaymentPayload`, `type OnlineGateway`;
  `billingApi.{listInvoices,getInvoice,createInvoice,initiateOnlinePayment,recordManualPayment,cancelInvoice,getPaymentStatus}`;
  `billingKeys.{all,lists,list,detail,paymentStatus}`; `useInvoices(...)`, `useInvoiceDetail(invoiceId)`, `usePaymentStatus(gatewayTransactionId)`.

- [ ] **Step 1: Write the enrollment schema**

`frontend/src/entities/enrollment/model/enrollment-schema.ts`:

```ts
import { z } from "zod";

/** Khớp EnrollmentConstants.EnrollmentStatus ở backend. */
export const ENROLLMENT_STATUS = {
  active: "ACTIVE",
  withdrawn: "WITHDRAWN",
  completed: "COMPLETED",
} as const;

export type EnrollmentStatus = (typeof ENROLLMENT_STATUS)[keyof typeof ENROLLMENT_STATUS];

export const ENROLLMENT_STATUS_LABEL: Record<EnrollmentStatus, string> = {
  [ENROLLMENT_STATUS.active]: "Đang học",
  [ENROLLMENT_STATUS.withdrawn]: "Đã rút",
  [ENROLLMENT_STATUS.completed]: "Đã hoàn tất",
};

/** Khớp từng field với EnrollmentResponse ở backend - lệch tên là parse() ném ngay tại biên. */
export const enrollmentSummarySchema = z.object({
  id: z.string().uuid(),
  studentProfileId: z.string().uuid(),
  classId: z.string().uuid(),
  courseId: z.string().uuid(),
  branchId: z.string().uuid(),
  status: z.enum([ENROLLMENT_STATUS.active, ENROLLMENT_STATUS.withdrawn, ENROLLMENT_STATUS.completed]),
  enrolledAt: z.string(),
  withdrawnAt: z.string().nullable(),
});

export type EnrollmentSummary = z.infer<typeof enrollmentSummarySchema>;

export interface CreateEnrollmentPayload {
  readonly studentProfileId: string;
  readonly classId: string;
}
```

- [ ] **Step 2: Write the enrollment keys and API**

`frontend/src/entities/enrollment/api/enrollment-keys.ts`:

```ts
export const enrollmentKeys = {
  all: ["enrollment"] as const,
  lists: () => [...enrollmentKeys.all, "list"] as const,
  list: (page: number, size: number, studentProfileId?: string, classId?: string) =>
    [...enrollmentKeys.lists(), { page, size, studentProfileId, classId }] as const,
  detail: (enrollmentId: string) => [...enrollmentKeys.all, "detail", enrollmentId] as const,
} as const;
```

`frontend/src/entities/enrollment/api/enrollment-api.ts`:

```ts
import {
  enrollmentSummarySchema,
  type CreateEnrollmentPayload,
} from "@/entities/enrollment/model/enrollment-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";

const enrollmentPageSchema = pageResponseSchema(enrollmentSummarySchema);

export const enrollmentApi = {
  async listEnrollments(page: number, size: number, studentProfileId?: string, classId?: string) {
    return enrollmentPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.enrollment.enrollments, {
        page,
        size,
        studentProfileId,
        classId,
      }),
    );
  },

  async getEnrollment(enrollmentId: string) {
    return enrollmentSummarySchema.parse(
      await apiClient.get<unknown>(API_ROUTE.enrollment.enrollment(enrollmentId)),
    );
  },

  async createEnrollment(payload: CreateEnrollmentPayload) {
    return enrollmentSummarySchema.parse(
      await apiClient.post<unknown>(API_ROUTE.enrollment.enrollments, payload),
    );
  },

  async withdrawEnrollment(enrollmentId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.enrollment.enrollmentWithdraw(enrollmentId));
  },

  async completeEnrollment(enrollmentId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.enrollment.enrollmentComplete(enrollmentId));
  },
} as const;
```

`frontend/src/entities/enrollment/api/use-enrollments.ts`:

```ts
import { enrollmentApi } from "@/entities/enrollment/api/enrollment-api";
import { enrollmentKeys } from "@/entities/enrollment/api/enrollment-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useEnrollments(page: number, size: number, studentProfileId?: string, classId?: string) {
  return useQuery({
    queryKey: enrollmentKeys.list(page, size, studentProfileId, classId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => enrollmentApi.listEnrollments(page, size, studentProfileId, classId),
  });
}
```

`frontend/src/entities/enrollment/index.ts`:

```ts
export { enrollmentApi } from "@/entities/enrollment/api/enrollment-api";
export { enrollmentKeys } from "@/entities/enrollment/api/enrollment-keys";
export { useEnrollments } from "@/entities/enrollment/api/use-enrollments";
export {
  ENROLLMENT_STATUS,
  ENROLLMENT_STATUS_LABEL,
  enrollmentSummarySchema,
  type CreateEnrollmentPayload,
  type EnrollmentStatus,
  type EnrollmentSummary,
} from "@/entities/enrollment/model/enrollment-schema";
```

- [ ] **Step 3: Write the billing schema**

`frontend/src/entities/billing/model/billing-schema.ts`:

```ts
import { z } from "zod";

/** Khớp BillingConstants.InvoiceStatus ở backend. */
export const INVOICE_STATUS = {
  unpaid: "UNPAID",
  partiallyPaid: "PARTIALLY_PAID",
  paid: "PAID",
  overdue: "OVERDUE",
  cancelled: "CANCELLED",
} as const;

export type InvoiceStatus = (typeof INVOICE_STATUS)[keyof typeof INVOICE_STATUS];

export const INVOICE_STATUS_LABEL: Record<InvoiceStatus, string> = {
  [INVOICE_STATUS.unpaid]: "Chưa thu",
  [INVOICE_STATUS.partiallyPaid]: "Thu một phần",
  [INVOICE_STATUS.paid]: "Đã thu đủ",
  [INVOICE_STATUS.overdue]: "Quá hạn",
  [INVOICE_STATUS.cancelled]: "Đã huỷ",
};

/** Khớp BillingConstants.PaymentMethod ở backend. */
export const PAYMENT_METHOD = {
  momo: "MOMO",
  vnpay: "VNPAY",
  manual: "MANUAL",
} as const;

export type PaymentMethod = (typeof PAYMENT_METHOD)[keyof typeof PAYMENT_METHOD];

/** Chỉ hai cổng online - MANUAL không gửi được sang endpoint online-payment (backend ném
 * BILLING_UNKNOWN_PAYMENT_GATEWAY), nên kiểu này chặn luôn ở client. */
export type OnlineGateway = typeof PAYMENT_METHOD.momo | typeof PAYMENT_METHOD.vnpay;

export const ONLINE_GATEWAYS: readonly OnlineGateway[] = [PAYMENT_METHOD.momo, PAYMENT_METHOD.vnpay];

export const PAYMENT_METHOD_LABEL: Record<PaymentMethod, string> = {
  [PAYMENT_METHOD.momo]: "MoMo",
  [PAYMENT_METHOD.vnpay]: "VNPay",
  [PAYMENT_METHOD.manual]: "Tiền mặt / chuyển khoản",
};

/** Khớp BillingConstants.PaymentStatus ở backend. */
export const PAYMENT_STATUS = {
  pending: "PENDING",
  success: "SUCCESS",
  failed: "FAILED",
} as const;

export type PaymentStatus = (typeof PAYMENT_STATUS)[keyof typeof PAYMENT_STATUS];

export const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  [PAYMENT_STATUS.pending]: "Đang xử lý",
  [PAYMENT_STATUS.success]: "Thành công",
  [PAYMENT_STATUS.failed]: "Thất bại",
};

/** Khớp từng field với InvoiceResponse ở backend. */
export const invoiceSummarySchema = z.object({
  id: z.string().uuid(),
  enrollmentId: z.string().uuid(),
  studentProfileId: z.string().uuid(),
  courseId: z.string().uuid(),
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

export type InvoiceSummary = z.infer<typeof invoiceSummarySchema>;

/** Khớp từng field với PaymentResponse ở backend - KHÔNG có gatewayTransactionId (payload này đi ra
 * một endpoint public, backend cố ý không trả mã giao dịch). */
export const paymentSchema = z.object({
  id: z.string().uuid(),
  amount: z.number(),
  method: z.enum([PAYMENT_METHOD.momo, PAYMENT_METHOD.vnpay, PAYMENT_METHOD.manual]),
  status: z.enum([PAYMENT_STATUS.pending, PAYMENT_STATUS.success, PAYMENT_STATUS.failed]),
  paidAt: z.string().nullable(),
  createdAt: z.string(),
});

export type Payment = z.infer<typeof paymentSchema>;

/** Khớp InvoiceDetailResponse(invoice, payments) ở backend. */
export const invoiceDetailSchema = z.object({
  invoice: invoiceSummarySchema,
  payments: z.array(paymentSchema),
});

export type InvoiceDetail = z.infer<typeof invoiceDetailSchema>;

/** Khớp InitiateOnlinePaymentResponse(payUrl) ở backend. */
export const payUrlSchema = z.object({
  payUrl: z.string(),
});

export interface CreateInvoicePayload {
  readonly enrollmentId: string;
  readonly amount: number;
  /** ISO date (yyyy-MM-dd) - backend nhận LocalDate. */
  readonly dueDate: string;
}

export interface InitiateOnlinePaymentPayload {
  readonly gateway: OnlineGateway;
}

export interface RecordManualPaymentPayload {
  readonly amount: number;
}
```

- [ ] **Step 4: Write the billing keys and API**

`frontend/src/entities/billing/api/billing-keys.ts`:

```ts
export const billingKeys = {
  all: ["billing"] as const,
  lists: () => [...billingKeys.all, "invoice", "list"] as const,
  list: (page: number, size: number, enrollmentId?: string, studentProfileId?: string, status?: string) =>
    [...billingKeys.lists(), { page, size, enrollmentId, studentProfileId, status }] as const,
  detail: (invoiceId: string) => [...billingKeys.all, "invoice", "detail", invoiceId] as const,
  paymentStatus: (gatewayTransactionId: string) =>
    [...billingKeys.all, "payment", "status", gatewayTransactionId] as const,
} as const;
```

`frontend/src/entities/billing/api/billing-api.ts`:

```ts
import {
  invoiceDetailSchema,
  invoiceSummarySchema,
  paymentSchema,
  payUrlSchema,
  type CreateInvoicePayload,
  type InitiateOnlinePaymentPayload,
  type RecordManualPaymentPayload,
} from "@/entities/billing/model/billing-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";

const invoicePageSchema = pageResponseSchema(invoiceSummarySchema);

export const billingApi = {
  async listInvoices(
    page: number,
    size: number,
    enrollmentId?: string,
    studentProfileId?: string,
    status?: string,
  ) {
    return invoicePageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.billing.invoices, {
        page,
        size,
        enrollmentId,
        studentProfileId,
        status,
      }),
    );
  },

  async getInvoice(invoiceId: string) {
    return invoiceDetailSchema.parse(await apiClient.get<unknown>(API_ROUTE.billing.invoice(invoiceId)));
  },

  async createInvoice(payload: CreateInvoicePayload) {
    return invoiceSummarySchema.parse(await apiClient.post<unknown>(API_ROUTE.billing.invoices, payload));
  },

  async initiateOnlinePayment(invoiceId: string, payload: InitiateOnlinePaymentPayload) {
    return payUrlSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.billing.invoiceOnlinePayment(invoiceId), payload),
    );
  },

  async recordManualPayment(invoiceId: string, payload: RecordManualPaymentPayload) {
    return invoiceSummarySchema.parse(
      await apiClient.post<unknown>(API_ROUTE.billing.invoiceManualPayment(invoiceId), payload),
    );
  },

  async cancelInvoice(invoiceId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.billing.invoiceCancel(invoiceId));
  },

  /** Endpoint public - gọi được khi chưa đăng nhập (trang Return URL). */
  async getPaymentStatus(gatewayTransactionId: string) {
    return paymentSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.billing.paymentStatus(gatewayTransactionId)),
    );
  },
} as const;
```

- [ ] **Step 5: Write the three billing query hooks**

`frontend/src/entities/billing/api/use-invoices.ts`:

```ts
import { billingApi } from "@/entities/billing/api/billing-api";
import { billingKeys } from "@/entities/billing/api/billing-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useInvoices(
  page: number,
  size: number,
  enrollmentId?: string,
  studentProfileId?: string,
  status?: string,
) {
  return useQuery({
    queryKey: billingKeys.list(page, size, enrollmentId, studentProfileId, status),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => billingApi.listInvoices(page, size, enrollmentId, studentProfileId, status),
  });
}
```

`frontend/src/entities/billing/api/use-invoice-detail.ts`:

```ts
import { billingApi } from "@/entities/billing/api/billing-api";
import { billingKeys } from "@/entities/billing/api/billing-keys";
import { QUERY_RETRY_COUNT } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function useInvoiceDetail(invoiceId: string) {
  return useQuery({
    queryKey: billingKeys.detail(invoiceId),
    retry: QUERY_RETRY_COUNT,
    queryFn: () => billingApi.getInvoice(invoiceId),
  });
}
```

`frontend/src/entities/billing/api/use-payment-status.ts`:

```ts
import { billingApi } from "@/entities/billing/api/billing-api";
import { billingKeys } from "@/entities/billing/api/billing-keys";
import { PAYMENT_STATUS } from "@/entities/billing/model/billing-schema";
import { QUERY_RETRY_COUNT } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

/** Khoảng poll khi giao dịch còn PENDING: IPN xử lý bất đồng bộ nên lúc phụ huynh được redirect về,
 * trạng thái thật thường chưa kịp cập nhật (spec mục 10). Dừng poll ngay khi đã có kết quả cuối. */
const PAYMENT_STATUS_POLL_MS = 3_000;

export function usePaymentStatus(gatewayTransactionId: string) {
  return useQuery({
    queryKey: billingKeys.paymentStatus(gatewayTransactionId),
    retry: QUERY_RETRY_COUNT,
    enabled: gatewayTransactionId.length > 0,
    refetchInterval: (query) =>
      query.state.data?.status === PAYMENT_STATUS.pending ? PAYMENT_STATUS_POLL_MS : false,
    queryFn: () => billingApi.getPaymentStatus(gatewayTransactionId),
  });
}
```

- [ ] **Step 6: Write the billing barrel**

`frontend/src/entities/billing/index.ts`:

```ts
export { billingApi } from "@/entities/billing/api/billing-api";
export { billingKeys } from "@/entities/billing/api/billing-keys";
export { useInvoiceDetail } from "@/entities/billing/api/use-invoice-detail";
export { useInvoices } from "@/entities/billing/api/use-invoices";
export { usePaymentStatus } from "@/entities/billing/api/use-payment-status";
export {
  INVOICE_STATUS,
  INVOICE_STATUS_LABEL,
  invoiceDetailSchema,
  invoiceSummarySchema,
  ONLINE_GATEWAYS,
  PAYMENT_METHOD,
  PAYMENT_METHOD_LABEL,
  PAYMENT_STATUS,
  PAYMENT_STATUS_LABEL,
  paymentSchema,
  payUrlSchema,
  type CreateInvoicePayload,
  type InitiateOnlinePaymentPayload,
  type InvoiceDetail,
  type InvoiceStatus,
  type InvoiceSummary,
  type OnlineGateway,
  type Payment,
  type PaymentMethod,
  type PaymentStatus,
  type RecordManualPaymentPayload,
} from "@/entities/billing/model/billing-schema";
```

- [ ] **Step 7: Run typecheck and lint**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: PASS cả hai, 0 lỗi. Nếu `eslint-plugin-boundaries` báo lỗi, kiểm tra `eslint.config.*` xem
layer `entities` có cần khai tên thư mục mới không — hai entity này chỉ import `@/shared/*` nên không
vi phạm hướng `shared → entities`.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/entities/enrollment frontend/src/entities/billing
git commit -m "feat(frontend): add enrollment and billing entity contracts"
```

---

### Task 26: Frontend — `modules/enrollment`

**Files:**
- Create: `frontend/src/modules/enrollment/api/use-enrollment-mutations.ts`
- Create: `frontend/src/modules/enrollment/model/enrollment-forms.ts`
- Create: `frontend/src/modules/enrollment/hooks/use-enrollments-page-controller.ts`
- Create: `frontend/src/modules/enrollment/ui/enrollments-table.tsx`
- Create: `frontend/src/modules/enrollment/ui/create-enrollment-dialog.tsx`
- Create: `frontend/src/modules/enrollment/pages/enrollments-page.tsx`
- Create: `frontend/src/modules/enrollment/index.ts`

**Interfaces:**
- Consumes: `@/entities/enrollment` (Task 25), `@/entities/student` (`useStudents`, `StudentProfileSummary`), `@/entities/class` (`useClasses`, `ClassSummary`), `@/entities/permission` (`Can`, `RequirePermission`), `ACCESS_RULE` (Task 24), `buildInvoiceDetailPath` không dùng ở đây.
- Produces:
  `useCreateEnrollment()`, `useWithdrawEnrollment()`, `useCompleteEnrollment()`;
  `createEnrollmentFormSchema`;
  `useEnrollmentsPageController()` trả `{ page, setPage, enrollments, studentFilter, setStudentFilter, classFilter, setClassFilter, students, classes, createDialogOpen, openCreateDialog, closeCreateDialog, onWithdraw, onComplete, isMutating }`;
  `EnrollmentsTable`, `CreateEnrollmentDialog`, `EnrollmentsPage` (export qua `index.ts`).

- [ ] **Step 1: Write the page (consumer) first so typecheck fails**

`frontend/src/modules/enrollment/pages/enrollments-page.tsx`:

```tsx
import { Can, RequirePermission } from "@/entities/permission";
import { useEnrollmentsPageController } from "@/modules/enrollment/hooks/use-enrollments-page-controller";
import { CreateEnrollmentDialog } from "@/modules/enrollment/ui/create-enrollment-dialog";
import { EnrollmentsTable } from "@/modules/enrollment/ui/enrollments-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { GlassSelect } from "@/shared/ui/glass-select";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { ClipboardList, Plus } from "lucide-react";

export function EnrollmentsPage() {
  const controller = useEnrollmentsPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readEnrollment}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Ghi danh"
          description="Ghi danh học viên vào lớp. Rút khỏi lớp không xoá công nợ đã phát hành."
          actions={
            <Can {...ACCESS_RULE.createEnrollment}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Ghi danh mới
              </GlassButton>
            </Can>
          }
        />

        {controller.enrollments.isError ? <ErrorNotice error={controller.enrollments.error} /> : null}

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
            <GlassSelect
              aria-label="Lọc theo lớp"
              value={controller.classFilter}
              onChange={(event) => controller.setClassFilter(event.target.value)}
            >
              <option value="">Mọi lớp</option>
              {(controller.classes.data?.items ?? []).map((item) => (
                <option key={item.id} value={item.id}>
                  {item.code} — {item.courseName}
                </option>
              ))}
            </GlassSelect>
          </div>

          {controller.enrollments.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {controller.enrollments.data ? <EnrollmentsListSection controller={controller} /> : null}
        </GlassPanel>

        <CreateEnrollmentDialog
          open={controller.createDialogOpen}
          onClose={controller.closeCreateDialog}
          students={controller.students.data?.items ?? []}
          classes={controller.classes.data?.items ?? []}
        />
      </div>
    </RequirePermission>
  );
}

/** Tách nhánh rỗng/có dữ liệu ra component riêng - tránh nested ternary trong JSX (Mandate #3). */
function EnrollmentsListSection({
  controller,
}: {
  readonly controller: ReturnType<typeof useEnrollmentsPageController>;
}) {
  const page = controller.enrollments.data;
  if (!page || page.items.length === 0) {
    return (
      <EmptyState
        icon={<ClipboardList size={28} aria-hidden />}
        title="Chưa có ghi danh nào"
        description="Ghi danh học viên đầu tiên để bắt đầu phát hành học phí."
      />
    );
  }
  return (
    <>
      <EnrollmentsTable
        rows={page.items}
        onWithdraw={controller.onWithdraw}
        onComplete={controller.onComplete}
        isMutating={controller.isMutating}
      />
      <Pagination
        page={page.page}
        totalPages={page.totalPages}
        totalItems={page.totalItems}
        onPageChange={controller.setPage}
        itemLabel="ghi danh"
      />
    </>
  );
}
```

- [ ] **Step 2: Run typecheck to verify it fails**

Run: `cd frontend && npm run typecheck`
Expected: FAIL — không resolve được `@/modules/enrollment/hooks/use-enrollments-page-controller`,
`.../ui/create-enrollment-dialog`, `.../ui/enrollments-table`.

- [ ] **Step 3: Write the mutations**

`frontend/src/modules/enrollment/api/use-enrollment-mutations.ts`:

```ts
import { enrollmentApi, enrollmentKeys, type CreateEnrollmentPayload } from "@/entities/enrollment";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useEnrollmentsInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: enrollmentKeys.all });
  };
}

export function useCreateEnrollment() {
  const invalidate = useEnrollmentsInvalidation();
  return useMutation({
    mutationFn: (payload: CreateEnrollmentPayload) => enrollmentApi.createEnrollment(payload),
    onSuccess: invalidate,
  });
}

export function useWithdrawEnrollment() {
  const invalidate = useEnrollmentsInvalidation();
  return useMutation({
    mutationFn: (enrollmentId: string) => enrollmentApi.withdrawEnrollment(enrollmentId),
    onSuccess: invalidate,
  });
}

export function useCompleteEnrollment() {
  const invalidate = useEnrollmentsInvalidation();
  return useMutation({
    mutationFn: (enrollmentId: string) => enrollmentApi.completeEnrollment(enrollmentId),
    onSuccess: invalidate,
  });
}
```

- [ ] **Step 4: Write the form schema**

`frontend/src/modules/enrollment/model/enrollment-forms.ts`:

```ts
import { z } from "zod";

export const createEnrollmentFormSchema = z.object({
  studentProfileId: z.string().uuid("Chọn học viên"),
  classId: z.string().uuid("Chọn lớp học"),
});
```

- [ ] **Step 5: Write the controller hook**

`frontend/src/modules/enrollment/hooks/use-enrollments-page-controller.ts`:

```ts
import { useEnrollments } from "@/entities/enrollment";
import { useClasses } from "@/entities/class";
import { useStudents } from "@/entities/student";
import {
  useCompleteEnrollment,
  useWithdrawEnrollment,
} from "@/modules/enrollment/api/use-enrollment-mutations";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useCallback, useState } from "react";

/** Số dòng nạp cho hai dropdown tham chiếu (học viên/lớp) - đủ cho một trung tâm, không cần phân trang. */
const REFERENCE_PAGE_SIZE = 200;

/** Toàn bộ state/mutation của trang ghi danh - ui/* chỉ render (Mandate #2). */
export function useEnrollmentsPageController() {
  const [page, setPage] = useState(0);
  const [studentFilter, setStudentFilter] = useState("");
  const [classFilter, setClassFilter] = useState("");
  const [createDialogOpen, setCreateDialogOpen] = useState(false);

  const enrollments = useEnrollments(
    page,
    DEFAULT_PAGE_SIZE,
    studentFilter.length === 0 ? undefined : studentFilter,
    classFilter.length === 0 ? undefined : classFilter,
  );
  const students = useStudents(0, REFERENCE_PAGE_SIZE);
  const classes = useClasses(0, REFERENCE_PAGE_SIZE);
  const withdraw = useWithdrawEnrollment();
  const complete = useCompleteEnrollment();

  const openCreateDialog = useCallback(() => setCreateDialogOpen(true), []);
  const closeCreateDialog = useCallback(() => setCreateDialogOpen(false), []);

  const onWithdraw = useCallback(
    (enrollmentId: string) => {
      void withdraw.mutateAsync(enrollmentId);
    },
    [withdraw],
  );

  const onComplete = useCallback(
    (enrollmentId: string) => {
      void complete.mutateAsync(enrollmentId);
    },
    [complete],
  );

  return {
    page,
    setPage,
    enrollments,
    studentFilter,
    setStudentFilter,
    classFilter,
    setClassFilter,
    students,
    classes,
    createDialogOpen,
    openCreateDialog,
    closeCreateDialog,
    onWithdraw,
    onComplete,
    isMutating: withdraw.isPending || complete.isPending,
  };
}
```

> Đã xác nhận chữ ký của hai hook tham chiếu trong repo hiện tại:
> `useClasses(page: number, size: number)` (`frontend/src/entities/class/api/use-classes.ts`) và
> `useStudents(page: number, size: number)` (`frontend/src/entities/student/api/use-students.ts`) —
> cả hai chỉ nhận `(page, size)`. Không thêm tham số mới cho chúng ở task này.

- [ ] **Step 6: Write the table**

`frontend/src/modules/enrollment/ui/enrollments-table.tsx`:

```tsx
import { ENROLLMENT_STATUS, ENROLLMENT_STATUS_LABEL, type EnrollmentSummary } from "@/entities/enrollment";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { CheckCircle2, LogOut } from "lucide-react";

export interface EnrollmentsTableProps {
  readonly rows: readonly EnrollmentSummary[];
  readonly onWithdraw: (enrollmentId: string) => void;
  readonly onComplete: (enrollmentId: string) => void;
  readonly isMutating: boolean;
}

export function EnrollmentsTable({ rows, onWithdraw, onComplete, isMutating }: EnrollmentsTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((enrollment, index) => (
        <m.li
          key={enrollment.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,2fr)_auto_auto_auto]"
        >
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">Học viên {enrollment.studentProfileId.slice(0, 8)}</p>
            <p className="truncate text-xs text-mist-500">Lớp {enrollment.classId.slice(0, 8)}</p>
          </div>
          <Badge tone={enrollment.status === ENROLLMENT_STATUS.active ? "positive" : "neutral"}>
            {ENROLLMENT_STATUS_LABEL[enrollment.status]}
          </Badge>
          <p className="text-xs text-mist-500">{formatter.dateTime(enrollment.enrolledAt)}</p>
          <EnrollmentRowActions
            enrollment={enrollment}
            onWithdraw={onWithdraw}
            onComplete={onComplete}
            isMutating={isMutating}
          />
        </m.li>
      ))}
    </ul>
  );
}

/** Hai nút chỉ có nghĩa khi ghi danh còn ACTIVE - tách ra để không nhét điều kiện vào JSX của bảng. */
function EnrollmentRowActions({
  enrollment,
  onWithdraw,
  onComplete,
  isMutating,
}: {
  readonly enrollment: EnrollmentSummary;
  readonly onWithdraw: (enrollmentId: string) => void;
  readonly onComplete: (enrollmentId: string) => void;
  readonly isMutating: boolean;
}) {
  if (enrollment.status !== ENROLLMENT_STATUS.active) {
    return <span className="text-xs text-mist-500">Không còn thao tác</span>;
  }
  return (
    <Can {...ACCESS_RULE.updateEnrollment}>
      <div className="flex gap-2 justify-self-start lg:justify-self-end">
        <GlassButton
          variant="secondary"
          size="sm"
          disabled={isMutating}
          onClick={() => onComplete(enrollment.id)}
          icon={<CheckCircle2 size={14} aria-hidden />}
        >
          Hoàn tất
        </GlassButton>
        <GlassButton
          variant="ghost"
          size="sm"
          disabled={isMutating}
          onClick={() => onWithdraw(enrollment.id)}
          icon={<LogOut size={14} aria-hidden />}
        >
          Rút
        </GlassButton>
      </div>
    </Can>
  );
}
```

- [ ] **Step 7: Write the create dialog**

`frontend/src/modules/enrollment/ui/create-enrollment-dialog.tsx`:

```tsx
import type { ClassSummary } from "@/entities/class";
import type { StudentProfileSummary } from "@/entities/student";
import { useCreateEnrollment } from "@/modules/enrollment/api/use-enrollment-mutations";
import { createEnrollmentFormSchema } from "@/modules/enrollment/model/enrollment-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateEnrollmentDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly students: readonly StudentProfileSummary[];
  readonly classes: readonly ClassSummary[];
}

export function CreateEnrollmentDialog({ open, onClose, students, classes }: CreateEnrollmentDialogProps) {
  const createEnrollment = useCreateEnrollment();

  const form = useZodForm({
    schema: createEnrollmentFormSchema,
    initialValues: { studentProfileId: "", classId: "" },
    onSubmit: async (values) => {
      await createEnrollment.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Ghi danh học viên"
      description="Một học viên chỉ có một ghi danh đang học cho mỗi lớp. Lớp đã đủ chỗ sẽ bị từ chối."
    >
      <form id="create-enrollment-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Học viên" htmlFor="enrollment-student" error={form.fieldErrors.studentProfileId}>
          <GlassSelect
            id="enrollment-student"
            className="w-full"
            containerClassName="w-full"
            value={form.values.studentProfileId}
            invalid={form.fieldErrors.studentProfileId !== undefined}
            onChange={(event) => form.setValue("studentProfileId", event.target.value)}
          >
            <option value="">Chọn học viên</option>
            {students
              .filter((student) => student.active)
              .map((student) => (
                <option key={student.id} value={student.id}>
                  {student.fullName ?? student.email ?? student.id}
                </option>
              ))}
          </GlassSelect>
        </FormField>

        <FormField
          label="Lớp học"
          htmlFor="enrollment-class"
          hint="Chỉ hiện lớp đang hoạt động."
          error={form.fieldErrors.classId}
        >
          <GlassSelect
            id="enrollment-class"
            className="w-full"
            containerClassName="w-full"
            value={form.values.classId}
            invalid={form.fieldErrors.classId !== undefined}
            onChange={(event) => form.setValue("classId", event.target.value)}
          >
            <option value="">Chọn lớp</option>
            {classes
              .filter((item) => item.active)
              .map((item) => (
                <option key={item.id} value={item.id}>
                  {item.code} — {item.courseName} ({item.maxSeats} chỗ)
                </option>
              ))}
          </GlassSelect>
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-enrollment-form" loading={form.isSubmitting}>
          Ghi danh
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

- [ ] **Step 8: Write the barrel**

`frontend/src/modules/enrollment/index.ts`:

```ts
export { EnrollmentsPage } from "@/modules/enrollment/pages/enrollments-page";
```

- [ ] **Step 9: Run typecheck and lint**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: PASS cả hai, 0 lỗi.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/modules/enrollment
git commit -m "feat(frontend): add enrollment admin page with create, withdraw and complete actions"
```

---

### Task 27: Frontend — `modules/billing` (danh sách hoá đơn + chi tiết + thu tiền)

**Files:**
- Create: `frontend/src/modules/billing/api/use-billing-mutations.ts`
- Create: `frontend/src/modules/billing/model/billing-forms.ts`
- Create: `frontend/src/modules/billing/hooks/use-invoices-page-controller.ts`
- Create: `frontend/src/modules/billing/hooks/use-invoice-detail-controller.ts`
- Create: `frontend/src/modules/billing/ui/invoices-table.tsx`
- Create: `frontend/src/modules/billing/ui/create-invoice-dialog.tsx`
- Create: `frontend/src/modules/billing/ui/manual-payment-dialog.tsx`
- Create: `frontend/src/modules/billing/ui/online-payment-picker.tsx`
- Create: `frontend/src/modules/billing/ui/payments-table.tsx`
- Create: `frontend/src/modules/billing/pages/invoices-page.tsx`
- Create: `frontend/src/modules/billing/pages/invoice-detail-page.tsx`
- Create: `frontend/src/modules/billing/index.ts`

**Interfaces:**
- Consumes: `@/entities/billing` (Task 25), `@/entities/enrollment` (`useEnrollments`), `ACCESS_RULE`/`buildInvoiceDetailPath` (Task 24).
- Produces:
  `useCreateInvoice()`, `useInitiateOnlinePayment(invoiceId)`, `useRecordManualPayment(invoiceId)`, `useCancelInvoice(invoiceId)`;
  `createInvoiceFormSchema`, `manualPaymentFormSchema`;
  `useInvoicesPageController()` → `{ page, setPage, invoices, enrollmentFilter, setEnrollmentFilter, statusFilter, setStatusFilter, enrollments, createDialogOpen, openCreateDialog, closeCreateDialog }`;
  `useInvoiceDetailController(invoiceId)` → `{ detail, remaining, isPayable, canCancel, manualDialogOpen, openManualDialog, closeManualDialog, onPayOnline, isPayingOnline, onCancel, isCancelling }`
  (`canCancel` tách riêng khỏi `isPayable`: backend chỉ cho huỷ khi `UNPAID`, còn `OVERDUE` vẫn thu được nhưng không huỷ được);
  `InvoicesTable`, `CreateInvoiceDialog`, `ManualPaymentDialog`, `OnlinePaymentPicker`, `PaymentsTable`, `InvoicesPage`, `InvoiceDetailPage`.

- [ ] **Step 1: Write the two pages first so typecheck fails**

`frontend/src/modules/billing/pages/invoices-page.tsx`:

```tsx
import { INVOICE_STATUS, INVOICE_STATUS_LABEL } from "@/entities/billing";
import { Can, RequirePermission } from "@/entities/permission";
import { useInvoicesPageController } from "@/modules/billing/hooks/use-invoices-page-controller";
import { CreateInvoiceDialog } from "@/modules/billing/ui/create-invoice-dialog";
import { InvoicesTable } from "@/modules/billing/ui/invoices-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { GlassSelect } from "@/shared/ui/glass-select";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Plus, Receipt } from "lucide-react";

const INVOICE_STATUS_OPTIONS = Object.values(INVOICE_STATUS);

export function InvoicesPage() {
  const controller = useInvoicesPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readInvoice}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Hoá đơn học phí"
          description="Mỗi ghi danh thu tối đa 3 đợt, tổng các đợt không vượt học phí khoá học."
          actions={
            <Can {...ACCESS_RULE.createInvoice}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Tạo đợt thu
              </GlassButton>
            </Can>
          }
        />

        {controller.invoices.isError ? <ErrorNotice error={controller.invoices.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          <div className="flex flex-wrap gap-2">
            <GlassSelect
              aria-label="Lọc theo ghi danh"
              value={controller.enrollmentFilter}
              onChange={(event) => controller.setEnrollmentFilter(event.target.value)}
            >
              <option value="">Mọi ghi danh</option>
              {(controller.enrollments.data?.items ?? []).map((enrollment) => (
                <option key={enrollment.id} value={enrollment.id}>
                  {enrollment.id.slice(0, 8)} — HV {enrollment.studentProfileId.slice(0, 8)}
                </option>
              ))}
            </GlassSelect>
            <GlassSelect
              aria-label="Lọc theo trạng thái"
              value={controller.statusFilter}
              onChange={(event) => controller.setStatusFilter(event.target.value)}
            >
              <option value="">Mọi trạng thái</option>
              {INVOICE_STATUS_OPTIONS.map((status) => (
                <option key={status} value={status}>
                  {INVOICE_STATUS_LABEL[status]}
                </option>
              ))}
            </GlassSelect>
          </div>

          {controller.invoices.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {controller.invoices.data ? <InvoicesListSection controller={controller} /> : null}
        </GlassPanel>

        <CreateInvoiceDialog
          open={controller.createDialogOpen}
          onClose={controller.closeCreateDialog}
          enrollments={controller.enrollments.data?.items ?? []}
        />
      </div>
    </RequirePermission>
  );
}

function InvoicesListSection({
  controller,
}: {
  readonly controller: ReturnType<typeof useInvoicesPageController>;
}) {
  const page = controller.invoices.data;
  if (!page || page.items.length === 0) {
    return (
      <EmptyState
        icon={<Receipt size={28} aria-hidden />}
        title="Chưa có hoá đơn nào"
        description="Tạo đợt thu đầu tiên cho một ghi danh đang học."
      />
    );
  }
  return (
    <>
      <InvoicesTable rows={page.items} />
      <Pagination
        page={page.page}
        totalPages={page.totalPages}
        totalItems={page.totalItems}
        onPageChange={controller.setPage}
        itemLabel="hoá đơn"
      />
    </>
  );
}
```

`frontend/src/modules/billing/pages/invoice-detail-page.tsx`:

```tsx
import { INVOICE_STATUS_LABEL } from "@/entities/billing";
import { Can, RequirePermission } from "@/entities/permission";
import { useInvoiceDetailController } from "@/modules/billing/hooks/use-invoice-detail-controller";
import { ManualPaymentDialog } from "@/modules/billing/ui/manual-payment-dialog";
import { OnlinePaymentPicker } from "@/modules/billing/ui/online-payment-picker";
import { PaymentsTable } from "@/modules/billing/ui/payments-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { Ban, Banknote } from "lucide-react";
import { useParams } from "react-router-dom";

export function InvoiceDetailPage() {
  const { invoiceId = "" } = useParams<{ invoiceId: string }>();
  const controller = useInvoiceDetailController(invoiceId);

  return (
    <RequirePermission {...ACCESS_RULE.readInvoice}>
      <div className="flex flex-col gap-6">
        <PageHeader title="Chi tiết hoá đơn" description="Số dư còn lại, các khoản đã thu và lịch sử giao dịch." />

        {controller.detail.isError ? <ErrorNotice error={controller.detail.error} /> : null}
        {controller.detail.isPending ? <Skeleton className="h-40" /> : null}

        {controller.detail.data ? (
          <>
            <GlassPanel className="flex flex-col gap-4">
              <div className="flex flex-wrap items-center gap-3">
                <p className="text-sm text-mist-100">Đợt {controller.detail.data.invoice.installmentNumber}</p>
                <Badge tone={controller.isPayable ? "neutral" : "positive"}>
                  {INVOICE_STATUS_LABEL[controller.detail.data.invoice.status]}
                </Badge>
                <p className="text-xs text-mist-500">
                  Hạn {controller.detail.data.invoice.dueDate}
                </p>
              </div>
              <dl className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <AmountCell label="Số tiền đợt này" value={controller.detail.data.invoice.amount} />
                <AmountCell label="Đã thu" value={controller.detail.data.invoice.amountPaid} />
                <AmountCell label="Còn lại" value={controller.remaining} />
              </dl>
              <Can {...ACCESS_RULE.updateInvoice}>
                <InvoiceActions controller={controller} />
              </Can>
            </GlassPanel>

            <GlassPanel className="flex flex-col gap-4">
              <h2 className="text-sm font-semibold text-mist-100">Lịch sử thanh toán</h2>
              <PaymentsTable rows={controller.detail.data.payments} />
            </GlassPanel>

            <ManualPaymentDialog
              invoiceId={invoiceId}
              open={controller.manualDialogOpen}
              onClose={controller.closeManualDialog}
              remaining={controller.remaining}
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

/** Ba nút chỉ có nghĩa khi hoá đơn còn thu được - tách ra để trang không rẽ nhánh trong JSX. */
function InvoiceActions({
  controller,
}: {
  readonly controller: ReturnType<typeof useInvoiceDetailController>;
}) {
  if (!controller.isPayable) {
    return <p className="text-xs text-mist-500">Hoá đơn đã chốt, không còn thao tác thu tiền.</p>;
  }
  return (
    <div className="flex flex-wrap gap-2">
      <OnlinePaymentPicker onPay={controller.onPayOnline} isPaying={controller.isPayingOnline} />
      <GlassButton
        variant="secondary"
        size="sm"
        onClick={controller.openManualDialog}
        icon={<Banknote size={14} aria-hidden />}
      >
        Ghi nhận thanh toán thủ công
      </GlassButton>
      <GlassButton
        variant="ghost"
        size="sm"
        disabled={!controller.canCancel || controller.isCancelling}
        onClick={controller.onCancel}
        icon={<Ban size={14} aria-hidden />}
      >
        Huỷ hoá đơn
      </GlassButton>
    </div>
  );
}
```

- [ ] **Step 2: Run typecheck to verify it fails**

Run: `cd frontend && npm run typecheck`
Expected: FAIL — không resolve được hai controller hook và bốn component trong `ui/`.

- [ ] **Step 3: Write the mutations**

`frontend/src/modules/billing/api/use-billing-mutations.ts`:

```ts
import {
  billingApi,
  billingKeys,
  type CreateInvoicePayload,
  type InitiateOnlinePaymentPayload,
  type RecordManualPaymentPayload,
} from "@/entities/billing";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useInvoicesInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: billingKeys.all });
  };
}

export function useCreateInvoice() {
  const invalidate = useInvoicesInvalidation();
  return useMutation({
    mutationFn: (payload: CreateInvoicePayload) => billingApi.createInvoice(payload),
    onSuccess: invalidate,
  });
}

/** Không invalidate: lúc này chưa có tiền nào vào, hoá đơn chưa đổi trạng thái (backend chỉ lưu một
 * Payment PENDING). Trạng thái thật đến từ IPN, trang chi tiết sẽ thấy khi người dùng quay lại. */
export function useInitiateOnlinePayment(invoiceId: string) {
  return useMutation({
    mutationFn: (payload: InitiateOnlinePaymentPayload) =>
      billingApi.initiateOnlinePayment(invoiceId, payload),
  });
}

export function useRecordManualPayment(invoiceId: string) {
  const invalidate = useInvoicesInvalidation();
  return useMutation({
    mutationFn: (payload: RecordManualPaymentPayload) => billingApi.recordManualPayment(invoiceId, payload),
    onSuccess: invalidate,
  });
}

export function useCancelInvoice(invoiceId: string) {
  const invalidate = useInvoicesInvalidation();
  return useMutation({
    mutationFn: () => billingApi.cancelInvoice(invoiceId),
    onSuccess: invalidate,
  });
}
```

- [ ] **Step 4: Write the form schemas**

`frontend/src/modules/billing/model/billing-forms.ts`:

```ts
import { z } from "zod";

export const createInvoiceFormSchema = z.object({
  enrollmentId: z.string().uuid("Chọn ghi danh"),
  amount: z
    .string()
    .min(1, "Nhập số tiền")
    .transform((value) => Number(value))
    .refine((value) => Number.isFinite(value) && value > 0, "Số tiền phải lớn hơn 0"),
  dueDate: z.string().min(1, "Chọn hạn thanh toán"),
});

export const manualPaymentFormSchema = z.object({
  amount: z
    .string()
    .min(1, "Nhập số tiền")
    .transform((value) => Number(value))
    .refine((value) => Number.isFinite(value) && value > 0, "Số tiền phải lớn hơn 0"),
});
```

- [ ] **Step 5: Write the two controller hooks**

`frontend/src/modules/billing/hooks/use-invoices-page-controller.ts`:

```ts
import { useInvoices } from "@/entities/billing";
import { useEnrollments } from "@/entities/enrollment";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useCallback, useState } from "react";

const REFERENCE_PAGE_SIZE = 200;

export function useInvoicesPageController() {
  const [page, setPage] = useState(0);
  const [enrollmentFilter, setEnrollmentFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [createDialogOpen, setCreateDialogOpen] = useState(false);

  const invoices = useInvoices(
    page,
    DEFAULT_PAGE_SIZE,
    enrollmentFilter.length === 0 ? undefined : enrollmentFilter,
    undefined,
    statusFilter.length === 0 ? undefined : statusFilter,
  );
  const enrollments = useEnrollments(0, REFERENCE_PAGE_SIZE);

  const openCreateDialog = useCallback(() => setCreateDialogOpen(true), []);
  const closeCreateDialog = useCallback(() => setCreateDialogOpen(false), []);

  return {
    page,
    setPage,
    invoices,
    enrollmentFilter,
    setEnrollmentFilter,
    statusFilter,
    setStatusFilter,
    enrollments,
    createDialogOpen,
    openCreateDialog,
    closeCreateDialog,
  };
}
```

`frontend/src/modules/billing/hooks/use-invoice-detail-controller.ts`:

```ts
import { INVOICE_STATUS, useInvoiceDetail, type OnlineGateway } from "@/entities/billing";
import {
  useCancelInvoice,
  useInitiateOnlinePayment,
} from "@/modules/billing/api/use-billing-mutations";
import { useCallback, useState } from "react";

/** Trạng thái còn thu được tiền - khớp BillingRules.isPayable ở backend (UNPAID/PARTIALLY_PAID/OVERDUE). */
const PAYABLE_STATUSES: readonly string[] = [
  INVOICE_STATUS.unpaid,
  INVOICE_STATUS.partiallyPaid,
  INVOICE_STATUS.overdue,
];

/** Toàn bộ state/mutation của trang chi tiết hoá đơn - ui/* chỉ render (Mandate #2). */
export function useInvoiceDetailController(invoiceId: string) {
  const [manualDialogOpen, setManualDialogOpen] = useState(false);
  const detail = useInvoiceDetail(invoiceId);
  const initiateOnlinePayment = useInitiateOnlinePayment(invoiceId);
  const cancelInvoice = useCancelInvoice(invoiceId);

  const invoice = detail.data?.invoice;
  const remaining = invoice === undefined ? 0 : invoice.amount - invoice.amountPaid;
  const isPayable = invoice !== undefined && PAYABLE_STATUSES.includes(invoice.status);
  // Backend chỉ cho huỷ khi còn UNPAID (đã có tiền vào thì phải hoàn tiền ngoài hệ thống trước).
  const canCancel = invoice?.status === INVOICE_STATUS.unpaid;

  const openManualDialog = useCallback(() => setManualDialogOpen(true), []);
  const closeManualDialog = useCallback(() => setManualDialogOpen(false), []);

  const onPayOnline = useCallback(
    (gateway: OnlineGateway) => {
      void initiateOnlinePayment.mutateAsync({ gateway }).then((result) => {
        // Mở tab mới thay vì điều hướng cả SPA: kế toán vẫn giữ trang hoá đơn đang mở.
        window.open(result.payUrl, "_blank", "noopener,noreferrer");
      });
    },
    [initiateOnlinePayment],
  );

  const onCancel = useCallback(() => {
    void cancelInvoice.mutateAsync();
  }, [cancelInvoice]);

  return {
    detail,
    remaining,
    isPayable,
    canCancel,
    manualDialogOpen,
    openManualDialog,
    closeManualDialog,
    onPayOnline,
    isPayingOnline: initiateOnlinePayment.isPending,
    onCancel,
    isCancelling: cancelInvoice.isPending,
  };
}
```

- [ ] **Step 6: Write `invoices-table.tsx` and `payments-table.tsx`**

`frontend/src/modules/billing/ui/invoices-table.tsx`:

```tsx
import { INVOICE_STATUS, INVOICE_STATUS_LABEL, type InvoiceSummary } from "@/entities/billing";
import { buildInvoiceDetailPath } from "@/shared/constants/app-routes";
import { formatter } from "@/shared/lib/format";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";

export interface InvoicesTableProps {
  readonly rows: readonly InvoiceSummary[];
}

function toneOf(status: InvoiceSummary["status"]): "positive" | "neutral" {
  return status === INVOICE_STATUS.paid ? "positive" : "neutral";
}

export function InvoicesTable({ rows }: InvoicesTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((invoice, index) => (
        <m.li
          key={invoice.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto_auto]"
        >
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">Đợt {invoice.installmentNumber}</p>
            <p className="truncate text-xs text-mist-500">Ghi danh {invoice.enrollmentId.slice(0, 8)}</p>
          </div>
          <p className="text-sm text-mist-100">{formatter.count(invoice.amount)} đ</p>
          <p className="text-xs text-mist-500">Đã thu {formatter.count(invoice.amountPaid)} đ</p>
          <Badge tone={toneOf(invoice.status)}>{INVOICE_STATUS_LABEL[invoice.status]}</Badge>
          <Link to={buildInvoiceDetailPath(invoice.id)}>
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

`frontend/src/modules/billing/ui/payments-table.tsx`:

```tsx
import {
  PAYMENT_METHOD_LABEL,
  PAYMENT_STATUS,
  PAYMENT_STATUS_LABEL,
  type Payment,
} from "@/entities/billing";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { Banknote } from "lucide-react";

export interface PaymentsTableProps {
  readonly rows: readonly Payment[];
}

export function PaymentsTable({ rows }: PaymentsTableProps) {
  if (rows.length === 0) {
    return (
      <EmptyState
        icon={<Banknote size={28} aria-hidden />}
        title="Chưa có giao dịch nào"
        description="Hoá đơn này chưa nhận khoản thanh toán nào."
      />
    );
  }
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((payment) => (
        <li
          key={payment.id}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto]"
        >
          <p className="truncate text-sm text-mist-100">{PAYMENT_METHOD_LABEL[payment.method]}</p>
          <p className="text-sm text-mist-100">{formatter.count(payment.amount)} đ</p>
          <Badge tone={payment.status === PAYMENT_STATUS.success ? "positive" : "neutral"}>
            {PAYMENT_STATUS_LABEL[payment.status]}
          </Badge>
          <p className="text-xs text-mist-500">{formatter.dateTime(payment.createdAt)}</p>
        </li>
      ))}
    </ul>
  );
}
```

- [ ] **Step 7: Write the three dialogs/pickers**

`frontend/src/modules/billing/ui/create-invoice-dialog.tsx`:

```tsx
import { ENROLLMENT_STATUS, type EnrollmentSummary } from "@/entities/enrollment";
import { useCreateInvoice } from "@/modules/billing/api/use-billing-mutations";
import { createInvoiceFormSchema } from "@/modules/billing/model/billing-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateInvoiceDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly enrollments: readonly EnrollmentSummary[];
}

export function CreateInvoiceDialog({ open, onClose, enrollments }: CreateInvoiceDialogProps) {
  const createInvoice = useCreateInvoice();

  const form = useZodForm({
    schema: createInvoiceFormSchema,
    initialValues: { enrollmentId: "", amount: "", dueDate: "" },
    onSubmit: async (values) => {
      await createInvoice.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Tạo đợt thu học phí"
      description="Tối đa 3 đợt cho mỗi ghi danh; tổng các đợt không vượt học phí khoá học."
    >
      <form id="create-invoice-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Ghi danh" htmlFor="invoice-enrollment" hint="Chỉ ghi danh đang học phát hành được." error={form.fieldErrors.enrollmentId}>
          <GlassSelect
            id="invoice-enrollment"
            className="w-full"
            containerClassName="w-full"
            value={form.values.enrollmentId}
            invalid={form.fieldErrors.enrollmentId !== undefined}
            onChange={(event) => form.setValue("enrollmentId", event.target.value)}
          >
            <option value="">Chọn ghi danh</option>
            {enrollments
              .filter((enrollment) => enrollment.status === ENROLLMENT_STATUS.active)
              .map((enrollment) => (
                <option key={enrollment.id} value={enrollment.id}>
                  {enrollment.id.slice(0, 8)} — HV {enrollment.studentProfileId.slice(0, 8)}
                </option>
              ))}
          </GlassSelect>
        </FormField>

        <FormField label="Số tiền đợt này (VND)" htmlFor="invoice-amount" error={form.fieldErrors.amount}>
          <GlassInput
            id="invoice-amount"
            type="number"
            min={1}
            step={1000}
            value={form.values.amount}
            invalid={form.fieldErrors.amount !== undefined}
            onChange={(event) => form.setValue("amount", event.target.value)}
          />
        </FormField>

        <FormField label="Hạn thanh toán" htmlFor="invoice-due-date" error={form.fieldErrors.dueDate}>
          <GlassInput
            id="invoice-due-date"
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
        <GlassButton type="submit" form="create-invoice-form" loading={form.isSubmitting}>
          Tạo đợt thu
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

`frontend/src/modules/billing/ui/manual-payment-dialog.tsx`:

```tsx
import { useRecordManualPayment } from "@/modules/billing/api/use-billing-mutations";
import { manualPaymentFormSchema } from "@/modules/billing/model/billing-forms";
import { formatter } from "@/shared/lib/format";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface ManualPaymentDialogProps {
  readonly invoiceId: string;
  readonly open: boolean;
  readonly onClose: () => void;
  readonly remaining: number;
}

export function ManualPaymentDialog({ invoiceId, open, onClose, remaining }: ManualPaymentDialogProps) {
  const recordManualPayment = useRecordManualPayment(invoiceId);

  const form = useZodForm({
    schema: manualPaymentFormSchema,
    initialValues: { amount: "" },
    onSubmit: async (values) => {
      await recordManualPayment.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Ghi nhận thanh toán thủ công"
      description="Tiền mặt hoặc chuyển khoản do kế toán nhập tay."
    >
      <form id="manual-payment-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField
          label="Số tiền (VND)"
          htmlFor="manual-payment-amount"
          hint={`Còn lại ${formatter.count(remaining)} đ. Thu quá số còn lại sẽ bị từ chối.`}
          error={form.fieldErrors.amount}
        >
          <GlassInput
            id="manual-payment-amount"
            type="number"
            min={1}
            max={remaining}
            step={1000}
            autoFocus
            value={form.values.amount}
            invalid={form.fieldErrors.amount !== undefined}
            onChange={(event) => form.setValue("amount", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="manual-payment-form" loading={form.isSubmitting}>
          Ghi nhận
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

`frontend/src/modules/billing/ui/online-payment-picker.tsx`:

```tsx
import { ONLINE_GATEWAYS, PAYMENT_METHOD_LABEL, type OnlineGateway } from "@/entities/billing";
import { GlassButton } from "@/shared/ui/glass-button";
import { CreditCard } from "lucide-react";

export interface OnlinePaymentPickerProps {
  readonly onPay: (gateway: OnlineGateway) => void;
  readonly isPaying: boolean;
}

/** Một nút cho mỗi cổng - đơn giản và rõ hơn một dropdown rồi phải bấm nút thứ hai. */
export function OnlinePaymentPicker({ onPay, isPaying }: OnlinePaymentPickerProps) {
  return (
    <div className="flex flex-wrap gap-2">
      {ONLINE_GATEWAYS.map((gateway) => (
        <GlassButton
          key={gateway}
          size="sm"
          disabled={isPaying}
          onClick={() => onPay(gateway)}
          icon={<CreditCard size={14} aria-hidden />}
        >
          Thu qua {PAYMENT_METHOD_LABEL[gateway]}
        </GlassButton>
      ))}
    </div>
  );
}
```

- [ ] **Step 8: Write the barrel**

`frontend/src/modules/billing/index.ts`:

```ts
export { InvoiceDetailPage } from "@/modules/billing/pages/invoice-detail-page";
export { InvoicesPage } from "@/modules/billing/pages/invoices-page";
```

- [ ] **Step 9: Run typecheck and lint**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: PASS cả hai, 0 lỗi.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/modules/billing
git commit -m "feat(frontend): add invoice list and detail pages with online and manual collection"
```

---

### Task 28: Frontend — trang `/payment/return/:gateway` public + đăng ký route và menu

**Files:**
- Create: `frontend/src/modules/billing/hooks/use-payment-return-controller.ts`
- Create: `frontend/src/modules/billing/pages/payment-return-page.tsx`
- Modify: `frontend/src/modules/billing/index.ts`
- Modify: `frontend/src/app/router/app-router.tsx`
- Modify: `frontend/src/app/layouts/nav-items.ts`

**Interfaces:**
- Consumes: `usePaymentStatus` (Task 25), `APP_ROUTE.{enrollments,invoices,invoiceDetail,paymentReturn}` + `PAYMENT_RETURN_GATEWAY_PARAM` (Task 24), `PAYMENT_METHOD`/`PAYMENT_STATUS` (Task 25), `EnrollmentsPage` (Task 26), `InvoicesPage`/`InvoiceDetailPage` (Task 27).
- Produces:
  `usePaymentReturnController()` → `{ gateway, gatewayTransactionId, payment }`;
  `PaymentReturnPage` (export thêm trong `modules/billing/index.ts`);
  3 route mới trong `RequireAuth` + 1 route public trong `AuthLayout`; 2 mục menu mới.

> **Quy tắc không được phá (spec mục 10):** trang này KHÔNG tự kết luận "thành công" từ query param
> trên URL. Nó chỉ lấy mã giao dịch từ URL (`vnp_TxnRef` với VNPay, `orderId` với MoMo) rồi gọi
> `GET /api/billing/payments/{id}/status` để đọc trạng thái mà IPN đã xác nhận. Query param do người
> dùng kiểm soát, không phải nguồn sự thật.

- [ ] **Step 1: Write the page (consumer) first so typecheck fails**

`frontend/src/modules/billing/pages/payment-return-page.tsx`:

```tsx
import { PAYMENT_STATUS, PAYMENT_STATUS_LABEL } from "@/entities/billing";
import { usePaymentReturnController } from "@/modules/billing/hooks/use-payment-return-controller";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { formatter } from "@/shared/lib/format";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { Skeleton } from "@/shared/ui/skeleton";
import { CheckCircle2, Clock, FileQuestion, XCircle } from "lucide-react";
import { Link } from "react-router-dom";

/**
 * Trang phụ huynh thấy sau khi quay về từ MoMo/VNPay. Public: không bọc trong RequireAuth, không gọi
 * endpoint nào cần quyền. Trạng thái LUÔN đọc lại từ API (IPN là nguồn sự thật), không suy từ query
 * param trên URL (spec mục 10).
 */
export function PaymentReturnPage() {
  const controller = usePaymentReturnController();

  return (
    <GlassPanel className="flex w-full max-w-md flex-col gap-4">
      <h1 className="text-base font-semibold text-mist-100">Kết quả thanh toán học phí</h1>
      <PaymentReturnBody controller={controller} />
      <Link to={APP_ROUTE.dashboard}>
        <GlassButton variant="secondary" size="sm">
          Về trang chủ
        </GlassButton>
      </Link>
    </GlassPanel>
  );
}

function PaymentReturnBody({
  controller,
}: {
  readonly controller: ReturnType<typeof usePaymentReturnController>;
}) {
  if (controller.gatewayTransactionId.length === 0) {
    return (
      <EmptyState
        icon={<FileQuestion size={28} aria-hidden />}
        title="Không đọc được mã giao dịch"
        description="Đường dẫn quay về thiếu mã giao dịch. Vui lòng liên hệ trung tâm để được đối soát."
      />
    );
  }
  if (controller.payment.isPending) {
    return <Skeleton className="h-24" />;
  }
  if (controller.payment.isError) {
    return <ErrorNotice error={controller.payment.error} />;
  }
  if (!controller.payment.data) {
    return (
      <EmptyState
        icon={<FileQuestion size={28} aria-hidden />}
        title="Chưa có dữ liệu giao dịch"
        description="Vui lòng chờ một lát rồi tải lại trang."
      />
    );
  }
  return <PaymentOutcome amount={controller.payment.data.amount} status={controller.payment.data.status} />;
}

const OUTCOME_ICON = {
  [PAYMENT_STATUS.pending]: <Clock size={28} aria-hidden />,
  [PAYMENT_STATUS.success]: <CheckCircle2 size={28} aria-hidden />,
  [PAYMENT_STATUS.failed]: <XCircle size={28} aria-hidden />,
} as const;

const OUTCOME_DESCRIPTION = {
  [PAYMENT_STATUS.pending]:
    "Cổng thanh toán đang xác nhận. Trang sẽ tự cập nhật, bạn không cần thanh toán lại.",
  [PAYMENT_STATUS.success]: "Trung tâm đã ghi nhận khoản thanh toán này vào hoá đơn của bạn.",
  [PAYMENT_STATUS.failed]: "Giao dịch không thành công. Bạn có thể thử lại hoặc liên hệ trung tâm.",
} as const;

function PaymentOutcome({
  amount,
  status,
}: {
  readonly amount: number;
  readonly status: keyof typeof OUTCOME_ICON;
}) {
  return (
    <EmptyState
      icon={OUTCOME_ICON[status]}
      title={`${PAYMENT_STATUS_LABEL[status]} — ${formatter.count(amount)} đ`}
      description={OUTCOME_DESCRIPTION[status]}
    />
  );
}
```

- [ ] **Step 2: Run typecheck to verify it fails**

Run: `cd frontend && npm run typecheck`
Expected: FAIL — không resolve được `@/modules/billing/hooks/use-payment-return-controller`.

- [ ] **Step 3: Write the controller hook**

`frontend/src/modules/billing/hooks/use-payment-return-controller.ts`:

```ts
import { PAYMENT_METHOD, usePaymentStatus } from "@/entities/billing";
import { PAYMENT_RETURN_GATEWAY_PARAM } from "@/shared/constants/app-routes";
import { useParams, useSearchParams } from "react-router-dom";

/** Tên query param mang mã giao dịch của từng cổng - VNPay dùng vnp_TxnRef, MoMo dùng orderId. */
const TRANSACTION_ID_PARAM: Record<string, string> = {
  [PAYMENT_METHOD.vnpay.toLowerCase()]: "vnp_TxnRef",
  [PAYMENT_METHOD.momo.toLowerCase()]: "orderId",
};

/**
 * Chỉ LẤY mã giao dịch từ URL, không đọc trạng thái từ URL. Mọi kết luận thành công/thất bại đến từ
 * {@code usePaymentStatus} (dữ liệu IPN đã xác nhận ở backend) - query param là dữ liệu người dùng
 * kiểm soát được (spec mục 10).
 */
export function usePaymentReturnController() {
  const params = useParams<Record<string, string>>();
  const [searchParams] = useSearchParams();

  const gateway = (params[PAYMENT_RETURN_GATEWAY_PARAM] ?? "").toLowerCase();
  const transactionParam = TRANSACTION_ID_PARAM[gateway];
  const gatewayTransactionId =
    transactionParam === undefined ? "" : (searchParams.get(transactionParam) ?? "");

  const payment = usePaymentStatus(gatewayTransactionId);

  return { gateway, gatewayTransactionId, payment };
}
```

- [ ] **Step 4: Export the page**

`frontend/src/modules/billing/index.ts`:

```ts
export { InvoiceDetailPage } from "@/modules/billing/pages/invoice-detail-page";
export { InvoicesPage } from "@/modules/billing/pages/invoices-page";
export { PaymentReturnPage } from "@/modules/billing/pages/payment-return-page";
```

- [ ] **Step 5: Register the four routes**

Trong `frontend/src/app/router/app-router.tsx`, thêm bốn khai báo `lazy` sau `PayrollRunDetailPage`:

```tsx
const EnrollmentsPage = lazy(() =>
  import("@/modules/enrollment").then((module) => ({ default: module.EnrollmentsPage })),
);
const InvoicesPage = lazy(() =>
  import("@/modules/billing").then((module) => ({ default: module.InvoicesPage })),
);
const InvoiceDetailPage = lazy(() =>
  import("@/modules/billing").then((module) => ({ default: module.InvoiceDetailPage })),
);
const PaymentReturnPage = lazy(() =>
  import("@/modules/billing").then((module) => ({ default: module.PaymentReturnPage })),
);
```

Trong `<Routes>`, thêm route public vào nhánh `AuthLayout` (cùng chỗ `resetPassword` — layout này
không đòi phiên, và KHÔNG bọc `RedirectWhenSignedIn` để kế toán đang đăng nhập cũng mở được link):

```tsx
          <Route path={APP_ROUTE.resetPassword} element={<ResetPasswordPage />} />
          <Route path={APP_ROUTE.paymentReturn} element={<PaymentReturnPage />} />
```

và ba route quản trị vào nhánh `RequireAuth`, ngay sau `payrollRunDetail`:

```tsx
          <Route path={APP_ROUTE.enrollments} element={<EnrollmentsPage />} />
          <Route path={APP_ROUTE.invoices} element={<InvoicesPage />} />
          <Route path={APP_ROUTE.invoiceDetail} element={<InvoiceDetailPage />} />
```

- [ ] **Step 6: Add the two menu items**

Trong `frontend/src/app/layouts/nav-items.ts`, thêm vào mảng `items` của section
"Điều hành & Nghiệp vụ", ngay sau `payrollRuns`:

```ts
      { path: APP_ROUTE.enrollments, label: "Ghi danh", requirement: ACCESS_RULE.readEnrollment },
      { path: APP_ROUTE.invoices, label: "Hoá đơn học phí", requirement: ACCESS_RULE.readInvoice },
```

- [ ] **Step 7: Run typecheck, lint and build**

Run: `cd frontend && npm run typecheck && npm run lint && npm run build`
Expected: PASS cả ba, 0 lỗi.

- [ ] **Step 8: Verify the public route by hand**

Run: `cd frontend && npm run dev` rồi mở (ở cửa sổ ẩn danh, chưa đăng nhập):
`http://localhost:5173/payment/return/vnpay?vnp_TxnRef=order-khong-ton-tai`
Expected: trang hiện ra (KHÔNG bị đẩy về `/login`), và báo lỗi không tìm thấy giao dịch — chứng tỏ
route public và endpoint public đều hoạt động. Dừng dev server sau khi xem.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/modules/billing frontend/src/app
git commit -m "feat(frontend): add public payment return page and register admissions routes"
```

---

## Hoàn tất

- [ ] **Chạy toàn bộ kiểm chứng cuối**

```bash
cd backend && mvn clean verify
cd frontend && npm run typecheck && npm run lint && npm run build
```

Expected: BUILD SUCCESS ở backend (gồm `ModularityTests` và toàn bộ IT), 0 lỗi ở cả ba lệnh frontend.

- [ ] **Mở PR vào `develop`**

```bash
git push -u origin feature/admissions-tuition
gh pr create --base develop --title "feat: admissions and tuition (phase 3)" \
  --body "Implements docs/superpowers/specs/2026-10-03-phan-he-3-tuyen-sinh-hoc-phi-design.md"
```
