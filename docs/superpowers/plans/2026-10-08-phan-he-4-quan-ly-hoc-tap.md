# Phân hệ 4 — Quản lý Học tập (Điểm & Tiến trình) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cho giáo viên nhập điểm nhiều cột theo trọng số cho đúng lớp mình dạy, cho trung tâm xem/xuất Excel bảng điểm và bảng tiến trình học tập tự tính từ lịch lớp.

**Architecture:** `modules.courses` (đã có) nhận thêm entity con `CourseGradeColumn` của `Course` — cấu hình cột điểm là thuộc tính của khóa học, mirror `ClassSchedule` của `Class`. Module mới `modules.academic` sở hữu điểm thật (`GradeEntry`) và việc TÍNH tiến trình; nó đọc danh sách học viên qua `EnrollmentManagement`, tên học viên qua `StudentsManagement` + `IdentityManagement`, và cấu hình lớp/khóa qua `CoursesManagement.getClassInfo`. Quyền "chỉ giáo viên được phân công" KHÔNG phát minh scope RBAC mới: `@PreAuthorize` gác cửa thô, rồi một helper duy nhất `ClassGradeAccessGuard` trong `academic.internal` gọi `AccessManagement.effectivePermissions` + `CoursesManagement.getClassInfo` để so `teacherId`.

**Tech Stack:** Java 21, Spring Boot 3.4.1, Spring Modulith 1.3.1, PostgreSQL 16 + Flyway, Redis, Apache POI 5.4.1 (`poi-ooxml`, dependency MỚI), JUnit 5 + Mockito + Testcontainers; React 19 + Vite + TypeScript + TanStack Query v5 + Zod 3 + Tailwind v4.

**Spec:** `docs/superpowers/specs/2026-10-08-phan-he-4-quan-ly-hoc-tap-design.md`

## Global Constraints

Mọi task đều mang theo các ràng buộc dưới đây; giá trị trích nguyên văn từ spec.

- **KHÔNG điểm danh** — "trung tâm không còn cần tính năng này" (spec mục 1). Không tạo bảng, endpoint, màn hình nào liên quan điểm danh.
- **KHÔNG bài tập/mini-test** — "hệ thống này không phải nơi quản lý bài tập" (spec mục 1).
- **Tiến trình học tập KHÔNG BAO GIỜ được nhập** — "tự động tính từ lịch học, không ai phải nhập" (spec mục 1), "không lưu, tính lúc đọc" (spec mục 5). Không có cột DB nào lưu % tiến trình, không có endpoint ghi tiến trình.
- **Cấu trúc điểm cấu hình theo `Course`, KHÔNG phải theo `Class`** — "mọi lớp cùng khóa dùng chung một cấu trúc điểm" (spec mục 2.2).
- **Tổng trọng số các cột điểm của một Course bắt buộc = 100%** — "chặn lưu nếu khác" (spec mục 2.3), "sai lệch do làm tròn chấp nhận trong khoảng ±0.01" (spec mục 4). Danh sách RỖNG là ngoại lệ hợp lệ duy nhất, nghĩa là "khóa chưa cấu hình cột điểm" (xem Review Focus).
- **Điểm tổng kết là `null` khi thiếu điểm ở bất kỳ cột nào** — "Thiếu một cột → điểm tổng kết là `null` ("chưa đủ điểm"), **không** tính trung bình trên phần đã có (tránh một con số gây hiểu lầm)" (spec mục 6).
- **Kiểm quyền "đúng lớp" nằm ở TẦNG USECASE, không phải một loại scope `@PreAuthorize` mới** — "cơ chế kiểm qua `AccessManagement.effectivePermissions` + `CoursesManagement.getClassInfo`, không phát minh một loại scope RBAC mới" (spec mục 12). `GRADE:*` AccessRules KHÔNG mang marker `@ORGANIZATION` để giáo viên scope `PERSONAL` qua được cửa; scope `ORGANIZATION` bỏ qua hoàn toàn bước so `teacherId` (spec mục 6 bước 3).
- **Học viên KHÔNG tự xem được điểm/tiến trình của mình ở giai đoạn này** — "Ngoài phạm vi, nêu rõ để không bỏ sót ngầm" (spec mục 1). Không seed quyền `GRADE` cho role `STUDENT`, không có endpoint self-view.
- **Xuất Excel phải dùng CHUNG tầng tính toán với API đọc** — "số trên file Excel phải khớp số trả về từ `GetClassGradebook`/`GetClassProgress`, không tính lại theo đường riêng" (spec mục 11.7). Usecase export GỌI usecase đọc rồi chỉ serialize.
- **`modules.enrollment` chỉ được thêm MỘT method ĐỌC vào facade** — "Không có method ghi nào được thêm" (spec mục 3).
- **`Course.standardSessionCount == null` → trả `null` kèm lý do "chưa cấu hình tổng số buổi chuẩn"** — "không suy đoán", không chia cho 0 (spec mục 5).
- **Phép đếm "số buổi đã học" là một hàm THUẦN (không I/O)** — "nhận vào danh sách `ClassSchedule` + hai mốc thời gian, trả về số nguyên — test được độc lập, không cần DB" (spec mục 5).
- **Giao diện ẩn nút theo quyền nhưng nguồn sự thật là lỗi 403 thật từ backend** — "không tự suy luận "tôi là giáo viên lớp này" chỉ từ dữ liệu phía client" (spec mục 10).
- **Không thêm mục menu cấp 1 cho Phân hệ 4** — "vào từ trang chi tiết `Class` đã có (không thêm mục menu cấp 1 riêng, mirror cách `invoiceDetail` không có mục menu)" (spec mục 10).
- Ba mandate của repo (`CLAUDE.md`): không hardcode string (backend gom vào `<Module>Constants` static inner class, frontend gom vào `@/shared/constants/`); frontend tách logic khỏi render (logic ở `hooks/use-*.ts`, `ui/*.tsx` chỉ render); mọi hàm/method/component cyclomatic complexity < 15, component < 200 dòng.
- Mọi lỗi nghiệp vụ là một lớp con của `sealed` exception root của module, mang `errorCode` ổn định + `HttpStatus`, trả về RFC 9457 `ProblemDetail` qua `GlobalExceptionHandler`.
- Frontend KHÔNG có test runner (`package.json` không có vitest/jest). Cổng kiểm tra của mọi task frontend là `npm run lint` + `npm run typecheck` + `npm run build`, không phải unit test.

## Review Focus

Tám mục dưới đây copy nguyên văn từ spec mục 11, mỗi mục đã được gắn vào một task cụ thể có test thật.

1. **Tính tiến trình không lệch khi học viên vào giữa chừng** — hai học viên cùng lớp, `enrolledAt` khác nhau, phải ra % khác nhau dù cùng một `Class`/`Course`. → Task 8 (`ProgressRulesTest.countsOnlyTheSessionsAfterAMidCourseEnrolment`), Task 14 (`GetClassProgressTest.givesTwoDifferentPercentsToTwoStudentsInTheSameClass`).
2. **Rút khỏi lớp đóng băng tiến trình** — tiến trình tại thời điểm rút không được tăng thêm dù thời gian trôi qua sau đó. → Task 8 (`ProgressRulesTest.freezesAtWithdrawalEvenWhenCalledMuchLater`), Task 14 (`GetClassProgressTest.freezesAWithdrawnEnrolmentAtItsWithdrawnAt`).
3. **Điểm tổng kết `null` khi thiếu một cột**, không tính trung bình trên phần đã có. → Task 9 (`GradeRulesTest.returnsNullWhenAnyConfiguredColumnHasNoScore`), Task 13 (`GetClassGradebookTest.leavesFinalScoreNullForAStudentMissingOneColumn`).
4. **Giáo viên KHÔNG được phân công dạy lớp đó bị chặn** `NotAssignedToClassException` dù có quyền `GRADE:UPDATE:PERSONAL` nói chung — kiểm cả chiều ngược lại: giáo viên ĐÚNG lớp thao tác được. → Task 11 (`ClassGradeAccessGuardTest`), Task 16 (`GradeAdminControllerIT.refusesATeacherWhoIsNotAssignedToTheClass` + `...allowsTheAssignedTeacher`).
5. **Admin (scope ORGANIZATION) bỏ qua hoàn toàn kiểm tra phân công**, thao tác được trên bất kỳ lớp nào. → Task 11 (`ClassGradeAccessGuardTest.skipsTheAssignmentCheckForAnOrganizationScopedCaller`), Task 16 (`GradeAdminControllerIT.allowsAnAdminOnAClassTheyDoNotTeach`).
6. **Tổng trọng số cột điểm ≠ 100%** bị chặn lưu, kể cả khi sửa (không chỉ lúc tạo mới). → Task 2 (`GradeColumnRulesTest`), Task 3 (`UpdateCourseTest.rejectsGradeColumnsThatDoNotSumToOneHundred` + `CourseAdminControllerIT.rejectsAPatchWhoseGradeColumnWeightsDoNotSumToOneHundred`).
7. **Xuất Excel dùng chung tầng tính toán với API đọc** — số trên file Excel phải khớp số trả về từ `GetClassGradebook`/`GetClassProgress`, không tính lại theo đường riêng. → Task 19 (`GradeExportControllerIT.exportedGradebookNumbersMatchTheJsonEndpoint`).
8. **`Course.standardSessionCount` chưa cấu hình** → tiến trình trả về rõ ràng "chưa cấu hình", không chia cho 0 hay trả một con số sai. → Task 8 (`ProgressRulesTest.returnsNullWhenTheStandardSessionCountIsNotConfigured`), Task 14 (`GetClassProgressTest.reportsNotConfiguredWhenTheCourseHasNoStandardSessionCount`).

Thêm bốn lớp đầu vào mà spec hàm ý nhưng không nói thẳng, mỗi lớp cũng đã có test trong task sở hữu code:

9. **Khóa học đang tồn tại có ZERO cột điểm** — V23 thêm bảng mới nên mọi `Course` cũ có danh sách rỗng; nếu luật "tổng = 100%" chặn cả danh sách rỗng thì mọi khóa học cũ trở thành không sửa được. Danh sách rỗng phải hợp lệ và có nghĩa "chưa cấu hình". → Task 2 (`GradeColumnRulesTest.acceptsAnEmptyColumnListAsNotYetConfigured`), Task 3 (`UpdateCourseTest.acceptsAnEmptyGradeColumnList`).
10. **Điểm nằm ngoài `[0, maxScore]` của ĐÚNG cột đó** — mỗi cột có thang riêng, nên 9.5 hợp lệ ở cột thang 10 và sai ở cột thang 5. → Task 12 (`RecordGradeTest.rejectsAScoreAboveTheMaxScoreOfThatColumn`, `...rejectsANegativeScore`, `...acceptsExactlyTheMaxScore`).
11. **Cột điểm không thuộc khóa của ghi danh đó** — truyền `courseGradeColumnId` của khóa khác phải bị chặn, không âm thầm ghi một điểm vô nghĩa. → Task 12 (`RecordGradeTest.rejectsAColumnBelongingToAnotherCourse`).
12. **Lớp chưa có lịch học nào** (`Class.schedule` rỗng — `ClassAdminControllerIT.acceptsAnEmptyScheduleOnCreate` cho thấy đây là trạng thái hợp lệ) → số buổi đã học là 0, tiến trình 0%, không chia cho 0 và không ném lỗi. → Task 8 (`ProgressRulesTest.countsZeroSessionsForAClassWithoutAnySchedule`).

---

## Task 1: `CourseGradeColumn` — entity con của `Course`

**Files:**
- Create: `backend/src/main/resources/db/migration/V23__create_course_grade_columns.sql`
- Create: `backend/src/main/java/com/eduerp/modules/courses/internal/model/CourseGradeColumn.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/internal/model/Course.java`
- Test: `backend/src/test/java/com/eduerp/modules/courses/internal/repository/CourseGradeColumnRepositoryIT.java`

**Interfaces:**
- Consumes: `Course` (hiện có, `@Getter @NoArgsConstructor(access = PROTECTED)`), `CourseRepository extends JpaRepository<Course, UUID>`.
- Produces: `Course.getGradeColumns(): List<CourseGradeColumn>`, `Course.addGradeColumn(String name, BigDecimal weightPercent, BigDecimal maxScore, int displayOrder)`, `Course.clearGradeColumns()`; `CourseGradeColumn.getId()/getName()/getWeightPercent()/getMaxScore()/getDisplayOrder()/getCourse()`.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/courses/internal/repository/CourseGradeColumnRepositoryIT.java`:

```java
package com.eduerp.modules.courses.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.model.CourseGradeColumn;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Mirror {@code ClassSchedule}: cột điểm là entity con cascade ALL + orphanRemoval của Course, sửa
 * = thay TOÀN BỘ danh sách (spec mục 4). */
@Testcontainers
@DataJpaTest
class CourseGradeColumnRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    CourseRepository courses;

    private Course newCourseWithTwoColumns(String code) {
        var course = new Course(code, "Tiếng Anh giao tiếp", null, 24, null);
        course.addGradeColumn("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
        course.addGradeColumn("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1);
        return course;
    }

    @Test
    void savesGradeColumnsAlongWithTheirCourse() {
        var saved = courses.saveAndFlush(newCourseWithTwoColumns("GC-SAVE"));

        var found = courses.findById(saved.getId()).orElseThrow();
        assertThat(found.getGradeColumns()).extracting(CourseGradeColumn::getName)
                .containsExactly("Giữa kỳ", "Cuối kỳ");
        assertThat(found.getGradeColumns()).extracting(CourseGradeColumn::getWeightPercent)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("40.00"), new BigDecimal("60.00"));
        assertThat(found.getGradeColumns().get(0).getMaxScore())
                .isEqualByComparingTo(new BigDecimal("10.00"));
    }

    /** {@code getGradeColumns()} trả bản copy bất biến - nơi gọi không được sửa danh sách trực tiếp
     * mà phải đi qua {@code clearGradeColumns}/{@code addGradeColumn}, mirror {@code Class.getSchedule}. */
    @Test
    void exposesAnImmutableCopyOfTheColumnList() {
        var saved = courses.saveAndFlush(newCourseWithTwoColumns("GC-IMMUTABLE"));

        var columns = courses.findById(saved.getId()).orElseThrow().getGradeColumns();

        assertThat(columns).isUnmodifiable();
    }

    /** orphanRemoval: thay toàn bộ danh sách phải XOÁ HẲN dòng cũ, không để lại cột mồ côi. */
    @Test
    void replacingTheListDeletesTheOldColumns() {
        var saved = courses.saveAndFlush(newCourseWithTwoColumns("GC-REPLACE"));
        var reloaded = courses.findById(saved.getId()).orElseThrow();

        reloaded.clearGradeColumns();
        reloaded.addGradeColumn("Chuyên cần", new BigDecimal("100.00"), new BigDecimal("10.00"), 0);
        courses.saveAndFlush(reloaded);

        var afterReplace = courses.findById(saved.getId()).orElseThrow();
        assertThat(afterReplace.getGradeColumns()).extracting(CourseGradeColumn::getName)
                .containsExactly("Chuyên cần");
    }

    /** Khóa học cũ (mọi khóa đã tồn tại trước V23) có danh sách rỗng, không phải null - Review Focus #9. */
    @Test
    void aCourseWithoutAnyColumnHasAnEmptyListNotNull() {
        var saved = courses.saveAndFlush(new Course("GC-EMPTY", "Chưa cấu hình", null, null, null));

        assertThat(courses.findById(saved.getId()).orElseThrow().getGradeColumns()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CourseGradeColumnRepositoryIT`
Expected: FAIL at compile — `cannot find symbol: class CourseGradeColumn`, `cannot find symbol: method addGradeColumn(...)`.

- [ ] **Step 3: Write the migration**

Create `backend/src/main/resources/db/migration/V23__create_course_grade_columns.sql`:

```sql
-- Cấu hình cột điểm thuộc KHÓA HỌC, không thuộc lớp (spec mục 2 quyết định 2): mọi lớp cùng khóa
-- dùng chung một cấu trúc điểm. Mirror class_schedules của V9 về mọi mặt: con cascade của bảng cha,
-- ON DELETE CASCADE, một index theo khóa ngoại.
CREATE TABLE course_grade_columns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    -- NUMERIC(5,2): % trong điểm tổng kết. Ràng buộc "tổng = 100" là luật nghiệp vụ theo từng khóa
    -- học nên không biểu diễn được bằng CHECK trên một dòng - chặn ở GradeColumnRules (Task 2).
    weight_percent NUMERIC(5,2) NOT NULL,
    -- Thang điểm của riêng cột này (ví dụ 10): điểm tổng kết là Σ(score / max_score * weight_percent).
    max_score NUMERIC(5,2) NOT NULL,
    display_order INT NOT NULL
);

CREATE INDEX idx_course_grade_columns_course ON course_grade_columns (course_id);
```

- [ ] **Step 4: Write the entity**

Create `backend/src/main/java/com/eduerp/modules/courses/internal/model/CourseGradeColumn.java`:

```java
package com.eduerp.modules.courses.internal.model;

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
 * Một cột điểm của một khóa học ("Giữa kỳ 40%", "Cuối kỳ 60%"). {@code course} là quan hệ JPA thật -
 * {@code Course} cùng module (rule #3 chỉ cấm xuyên module), mirror {@code ClassSchedule.parentClass}.
 */
@Entity
@Table(name = "course_grade_columns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseGradeColumn {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(nullable = false)
    private String name;

    @Column(name = "weight_percent", nullable = false)
    private BigDecimal weightPercent;

    @Column(name = "max_score", nullable = false)
    private BigDecimal maxScore;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    CourseGradeColumn(Course course, String name, BigDecimal weightPercent, BigDecimal maxScore,
            int displayOrder) {
        this.course = course;
        this.name = name;
        this.weightPercent = weightPercent;
        this.maxScore = maxScore;
        this.displayOrder = displayOrder;
    }
}
```

- [ ] **Step 5: Add the child-list API to `Course`**

In `backend/src/main/java/com/eduerp/modules/courses/internal/model/Course.java`, add these imports next to the existing ones:

```java
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;
```

Then add the collection field immediately after the `active` field:

```java
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    // Cùng lý do với Class.schedule: liệt kê một trang khóa học mà không batch thì mỗi khóa tự bắn
    // một câu SELECT cột điểm riêng (N+1), xem ListCourses.
    @BatchSize(size = 50)
    private final List<CourseGradeColumn> gradeColumns = new ArrayList<>();
```

and these three methods at the end of the class (after the constructor):

```java
    public List<CourseGradeColumn> getGradeColumns() {
        return List.copyOf(gradeColumns);
    }

    public void addGradeColumn(String name, BigDecimal weightPercent, BigDecimal maxScore, int displayOrder) {
        gradeColumns.add(new CourseGradeColumn(this, name, weightPercent, maxScore, displayOrder));
    }

    /** Gọi trước khi thêm lại cột mới khi sửa khóa học - PATCH thay toàn bộ danh sách, không vá từng
     * dòng (spec mục 4, mirror {@code Class.clearSchedule}). */
    public void clearGradeColumns() {
        gradeColumns.clear();
    }
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=CourseGradeColumnRepositoryIT`
Expected: PASS — 4 tests, 0 failures.

- [ ] **Step 7: Confirm nothing else broke**

Run: `cd backend && ./mvnw test -Dtest='CourseRepositoryIT,CoursesManagementIT,ClassAdminControllerIT,CourseAdminControllerIT'`
Expected: PASS — `ddl-auto: validate` chấp nhận entity mới vì V23 đã tạo bảng khớp.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V23__create_course_grade_columns.sql \
        backend/src/main/java/com/eduerp/modules/courses/internal/model/CourseGradeColumn.java \
        backend/src/main/java/com/eduerp/modules/courses/internal/model/Course.java \
        backend/src/test/java/com/eduerp/modules/courses/internal/repository/CourseGradeColumnRepositoryIT.java
git commit -m "feat(courses): add course grade column child entity"
```

---

## Task 2: Luật tổng trọng số = 100% + exception

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/courses/CoursesConstants.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/internal/rules/GradeColumnRules.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/internal/rules/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/CourseGradeColumnWeightsInvalidException.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/CoursesException.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/dto/GradeColumnSlot.java`
- Test: `backend/src/test/java/com/eduerp/modules/courses/internal/rules/GradeColumnRulesTest.java`
- Test: `backend/src/test/java/com/eduerp/modules/courses/CoursesExceptionTest.java`

**Interfaces:**
- Consumes: `CoursesException` (sealed root, `permits CourseNotFoundException, ClassNotFoundException, CourseCodeAlreadyExistsException, ClassCodeAlreadyExistsException`).
- Produces: `CoursesConstants.GradeColumns.REQUIRED_TOTAL_WEIGHT_PERCENT`, `.WEIGHT_TOLERANCE`, `.WEIGHT_SCALE`; `GradeColumnRules.weightsSumToRequiredTotal(List<GradeColumnSlot>): boolean` và `.totalWeight(List<GradeColumnSlot>): BigDecimal`; `dto.GradeColumnSlot(String name, BigDecimal weightPercent, BigDecimal maxScore, int displayOrder)`; `CourseGradeColumnWeightsInvalidException(BigDecimal actualTotal)` với `errorCode` `COURSES_GRADE_COLUMN_WEIGHTS_INVALID`, HTTP 400.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/courses/internal/rules/GradeColumnRulesTest.java`:

```java
package com.eduerp.modules.courses.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.dto.GradeColumnSlot;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure, 100% unit-test được bằng new, không I/O - mirror {@code BillingRules}/{@code PayrollRules}. */
class GradeColumnRulesTest {

    private static GradeColumnSlot slot(String name, String weightPercent, int displayOrder) {
        return new GradeColumnSlot(name, new BigDecimal(weightPercent), new BigDecimal("10.00"), displayOrder);
    }

    /** Review Focus #6, chiều hợp lệ: 40 + 60 = 100 đúng ví dụ của spec mục 2. */
    @Test
    void acceptsWeightsThatSumToExactlyOneHundred() {
        assertThat(GradeColumnRules.weightsSumToRequiredTotal(
                List.of(slot("Giữa kỳ", "40.00", 0), slot("Cuối kỳ", "60.00", 1)))).isTrue();
    }

    /** Review Focus #9: khóa học chưa cấu hình cột điểm nào là trạng thái hợp lệ - mọi Course tồn tại
     * trước V23 đều ở trạng thái này, chặn ở đây là khiến chúng không sửa được nữa. */
    @Test
    void acceptsAnEmptyColumnListAsNotYetConfigured() {
        assertThat(GradeColumnRules.weightsSumToRequiredTotal(List.of())).isTrue();
    }

    /** Review Focus #6, chiều bị chặn: thiếu. */
    @Test
    void rejectsWeightsThatSumBelowOneHundred() {
        assertThat(GradeColumnRules.weightsSumToRequiredTotal(
                List.of(slot("Giữa kỳ", "40.00", 0), slot("Cuối kỳ", "50.00", 1)))).isFalse();
    }

    /** Review Focus #6, chiều bị chặn: vượt. */
    @Test
    void rejectsWeightsThatSumAboveOneHundred() {
        assertThat(GradeColumnRules.weightsSumToRequiredTotal(
                List.of(slot("Giữa kỳ", "40.00", 0), slot("Cuối kỳ", "70.00", 1)))).isFalse();
    }

    /** Spec mục 4: "sai lệch do làm tròn chấp nhận trong khoảng ±0.01" - ba cột 33.33 + 33.33 + 33.34
     * cộng đúng 100.00, còn 33.33 x 3 = 99.99 lệch đúng 0.01 nên vẫn phải qua. */
    @Test
    void acceptsATotalOffByAtMostTheRoundingTolerance() {
        assertThat(GradeColumnRules.weightsSumToRequiredTotal(List.of(
                slot("Đợt 1", "33.33", 0), slot("Đợt 2", "33.33", 1), slot("Đợt 3", "33.33", 2))))
                .as("99.99 lệch đúng 0.01 - trong ngưỡng")
                .isTrue();
        assertThat(GradeColumnRules.weightsSumToRequiredTotal(List.of(
                slot("Đợt 1", "33.33", 0), slot("Đợt 2", "33.33", 1), slot("Đợt 3", "33.32", 2))))
                .as("99.98 lệch 0.02 - ngoài ngưỡng")
                .isFalse();
    }

    /** Thông báo lỗi phải nói ra con số thật người dùng vừa nhập, nên tổng là một hàm riêng dùng được. */
    @Test
    void totalWeightAddsUpEveryColumn() {
        assertThat(GradeColumnRules.totalWeight(
                List.of(slot("Giữa kỳ", "40.00", 0), slot("Cuối kỳ", "55.50", 1))))
                .isEqualByComparingTo(new BigDecimal("95.50"));
    }

    @Test
    void totalWeightOfAnEmptyListIsZero() {
        assertThat(GradeColumnRules.totalWeight(List.of())).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
```

Create `backend/src/test/java/com/eduerp/modules/courses/CoursesExceptionTest.java`:

```java
package com.eduerp.modules.courses;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Mirror {@code EnrollmentExceptionTest}: errorCode là hợp đồng với frontend, đổi là breaking. */
class CoursesExceptionTest {

    @Test
    void courseNotFoundIsNotFound() {
        var ex = new CourseNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("COURSES_COURSE_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void classNotFoundIsNotFound() {
        var ex = new ClassNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("COURSES_CLASS_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Review Focus #6: lỗi phải nói ra tổng thật vừa nhập, nếu không người dùng không biết sai bao nhiêu. */
    @Test
    void gradeColumnWeightsInvalidIsBadRequestAndNamesTheActualTotal() {
        var ex = new CourseGradeColumnWeightsInvalidException(new BigDecimal("95.50"));
        assertThat(ex.getErrorCode()).isEqualTo("COURSES_GRADE_COLUMN_WEIGHTS_INVALID");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("95.50", "100");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest='GradeColumnRulesTest,CoursesExceptionTest'`
Expected: FAIL at compile — `cannot find symbol: class GradeColumnRules`, `cannot find symbol: class GradeColumnSlot`, `cannot find symbol: class CourseGradeColumnWeightsInvalidException`.

- [ ] **Step 3: Add the constants**

In `backend/src/main/java/com/eduerp/modules/courses/CoursesConstants.java`, add the import and the nested class inside `CoursesConstants`, after the `DayOfWeek` enum:

```java
import java.math.BigDecimal;
```

```java
    /** Luật của cấu hình cột điểm theo khóa học (spec mục 2 quyết định 3 + mục 4). */
    public static final class GradeColumns {
        private GradeColumns() {
        }

        /** "Tổng trọng số các cột điểm của một Course bắt buộc = 100%" (spec mục 2.3). */
        public static final BigDecimal REQUIRED_TOTAL_WEIGHT_PERCENT = new BigDecimal("100.00");

        /** "sai lệch do làm tròn chấp nhận trong khoảng ±0.01" (spec mục 4) - ba cột 33.33 phải lưu được. */
        public static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.01");

        /** Khớp cột {@code course_grade_columns.weight_percent}/{@code max_score} NUMERIC(5,2) của V23. */
        public static final int WEIGHT_SCALE = 2;
    }
```

- [ ] **Step 4: Add the request DTO**

Create `backend/src/main/java/com/eduerp/modules/courses/dto/GradeColumnSlot.java`:

```java
package com.eduerp.modules.courses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Một dòng trong cấu hình cột điểm của khóa học khi tạo/sửa - mirror {@link WeeklyScheduleSlot}:
 * không có {@code id} vì PATCH thay TOÀN BỘ danh sách, không vá từng dòng (spec mục 4).
 * {@code maxScore} phải dương vì nó là số bị chia trong công thức điểm tổng kết (spec mục 6).
 */
public record GradeColumnSlot(@NotBlank String name, @NotNull @Positive BigDecimal weightPercent,
        @NotNull @Positive BigDecimal maxScore, @PositiveOrZero int displayOrder) {
}
```

- [ ] **Step 5: Add the pure rule**

Create `backend/src/main/java/com/eduerp/modules/courses/internal/rules/package-info.java`:

```java
package com.eduerp.modules.courses.internal.rules;
```

Create `backend/src/main/java/com/eduerp/modules/courses/internal/rules/GradeColumnRules.java`:

```java
package com.eduerp.modules.courses.internal.rules;

import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.GradeColumnSlot;
import java.math.BigDecimal;
import java.util.List;

/** Pure, 100% unit-test được bằng new, không I/O - mirror {@code BillingRules}. */
public final class GradeColumnRules {

    private GradeColumnRules() {
    }

    public static BigDecimal totalWeight(List<GradeColumnSlot> columns) {
        return columns.stream().map(GradeColumnSlot::weightPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Danh sách RỖNG là hợp lệ và có nghĩa "khóa học chưa cấu hình cột điểm": mọi {@code Course} tồn
     * tại trước V23 ở đúng trạng thái đó, chặn ở đây là khiến chúng không sửa được nữa (Review Focus
     * #9). Với danh sách không rỗng thì tổng phải bằng 100.00 trong sai số ±0.01 (spec mục 2.3 + 4).
     */
    public static boolean weightsSumToRequiredTotal(List<GradeColumnSlot> columns) {
        if (columns.isEmpty()) {
            return true;
        }
        return totalWeight(columns).subtract(CoursesConstants.GradeColumns.REQUIRED_TOTAL_WEIGHT_PERCENT)
                .abs()
                .compareTo(CoursesConstants.GradeColumns.WEIGHT_TOLERANCE) <= 0;
    }
}
```

- [ ] **Step 6: Add the exception and widen the sealed root**

Create `backend/src/main/java/com/eduerp/modules/courses/CourseGradeColumnWeightsInvalidException.java`:

```java
package com.eduerp.modules.courses;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public final class CourseGradeColumnWeightsInvalidException extends CoursesException {

    public CourseGradeColumnWeightsInvalidException(BigDecimal actualTotal) {
        super("COURSES_GRADE_COLUMN_WEIGHTS_INVALID", HttpStatus.BAD_REQUEST,
                "Tổng trọng số các cột điểm phải bằng "
                        + CoursesConstants.GradeColumns.REQUIRED_TOTAL_WEIGHT_PERCENT.toPlainString()
                        + "%, đang là " + actualTotal.toPlainString() + "%");
    }
}
```

In `backend/src/main/java/com/eduerp/modules/courses/CoursesException.java`, replace the `permits` clause:

```java
public sealed class CoursesException extends AppException
        permits CourseNotFoundException, ClassNotFoundException, CourseCodeAlreadyExistsException,
        ClassCodeAlreadyExistsException, CourseGradeColumnWeightsInvalidException {
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest='GradeColumnRulesTest,CoursesExceptionTest'`
Expected: PASS — 10 tests, 0 failures.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/courses/CoursesConstants.java \
        backend/src/main/java/com/eduerp/modules/courses/CoursesException.java \
        backend/src/main/java/com/eduerp/modules/courses/CourseGradeColumnWeightsInvalidException.java \
        backend/src/main/java/com/eduerp/modules/courses/dto/GradeColumnSlot.java \
        backend/src/main/java/com/eduerp/modules/courses/internal/rules/ \
        backend/src/test/java/com/eduerp/modules/courses/internal/rules/GradeColumnRulesTest.java \
        backend/src/test/java/com/eduerp/modules/courses/CoursesExceptionTest.java
git commit -m "feat(courses): enforce grade column weights summing to one hundred percent"
```

---

## Task 3: Tạo/sửa khóa học kèm cột điểm

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/courses/dto/GradeColumnResponse.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/dto/CreateCourseRequest.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/dto/UpdateCourseRequest.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/dto/CourseResponse.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/usecase/CreateCourse.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/usecase/UpdateCourse.java`
- Modify: `backend/src/main/java/com/eduerp/modules/courses/usecase/ListCourses.java`
- Modify: `backend/src/test/java/com/eduerp/modules/courses/web/CourseAdminControllerIT.java` (lines 89, 110, 126, 140 construct the two request records positionally and stop compiling)
- Test: `backend/src/test/java/com/eduerp/modules/courses/usecase/CourseGradeColumnUseCasesTest.java`

**Interfaces:**
- Consumes: `GradeColumnRules.weightsSumToRequiredTotal`/`.totalWeight`, `CourseGradeColumnWeightsInvalidException`, `dto.GradeColumnSlot`, `Course.addGradeColumn`/`.clearGradeColumns`/`.getGradeColumns` (Task 1–2).
- Produces: `dto.GradeColumnResponse(UUID id, String name, BigDecimal weightPercent, BigDecimal maxScore, int displayOrder)`; `CreateCourseRequest(..., List<GradeColumnSlot> gradeColumns)`; `UpdateCourseRequest(..., List<GradeColumnSlot> gradeColumns)`; `CourseResponse(..., List<GradeColumnResponse> gradeColumns)`.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/courses/usecase/CourseGradeColumnUseCasesTest.java`:

```java
package com.eduerp.modules.courses.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.courses.CourseGradeColumnWeightsInvalidException;
import com.eduerp.modules.courses.dto.CreateCourseRequest;
import com.eduerp.modules.courses.dto.GradeColumnSlot;
import com.eduerp.modules.courses.dto.UpdateCourseRequest;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.model.CourseGradeColumn;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Review Focus #6: luật tổng = 100% phải chặn ở CẢ tạo mới VÀ sửa, không chỉ lúc tạo. */
class CourseGradeColumnUseCasesTest {

    private final CourseRepository courses = mock(CourseRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CreateCourse createCourse = new CreateCourse(courses, events);
    private final UpdateCourse updateCourse = new UpdateCourse(courses, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();

    private static final List<GradeColumnSlot> VALID_COLUMNS = List.of(
            new GradeColumnSlot("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0),
            new GradeColumnSlot("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1));

    private static final List<GradeColumnSlot> BROKEN_COLUMNS = List.of(
            new GradeColumnSlot("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0),
            new GradeColumnSlot("Cuối kỳ", new BigDecimal("50.00"), new BigDecimal("10.00"), 1));

    private CreateCourseRequest createRequest(List<GradeColumnSlot> gradeColumns) {
        return new CreateCourseRequest("TA-GT", "Tiếng Anh giao tiếp", null, 24, null, gradeColumns);
    }

    private UpdateCourseRequest updateRequest(List<GradeColumnSlot> gradeColumns) {
        return new UpdateCourseRequest("Tiếng Anh giao tiếp", null, 24, null, true, gradeColumns);
    }

    private void stubNoCodeClash() {
        when(courses.findByCode("TA-GT")).thenReturn(Optional.empty());
        when(courses.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Course existingCourseWithOneColumn() {
        var course = new Course("TA-GT", "Tiếng Anh giao tiếp", null, 24, null);
        course.addGradeColumn("Cũ", new BigDecimal("100.00"), new BigDecimal("10.00"), 0);
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        return course;
    }

    @Test
    void createStoresTheConfiguredGradeColumns() {
        stubNoCodeClash();

        createCourse.execute(actorAccountId, actorBranchId, createRequest(VALID_COLUMNS));

        var saved = org.mockito.ArgumentCaptor.forClass(Course.class);
        org.mockito.Mockito.verify(courses).save(saved.capture());
        assertThat(saved.getValue().getGradeColumns()).extracting(CourseGradeColumn::getName)
                .containsExactly("Giữa kỳ", "Cuối kỳ");
    }

    @Test
    void createRejectsGradeColumnsThatDoNotSumToOneHundred() {
        when(courses.findByCode("TA-GT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createCourse.execute(actorAccountId, actorBranchId, createRequest(BROKEN_COLUMNS)))
                .isInstanceOf(CourseGradeColumnWeightsInvalidException.class)
                .hasMessageContaining("90.00");
        org.mockito.Mockito.verify(courses, org.mockito.Mockito.never()).save(any(Course.class));
    }

    /** Review Focus #6: chặn cả khi SỬA. Trước khi có task này, một khóa hợp lệ có thể bị PATCH thành
     * cấu hình 90% mà không ai cản. */
    @Test
    void updateRejectsGradeColumnsThatDoNotSumToOneHundred() {
        var course = existingCourseWithOneColumn();

        assertThatThrownBy(() -> updateCourse.execute(courseId, actorAccountId, actorBranchId,
                updateRequest(BROKEN_COLUMNS)))
                .isInstanceOf(CourseGradeColumnWeightsInvalidException.class);
        assertThat(course.getGradeColumns()).as("cấu hình cũ không được chạm tới khi PATCH bị chặn")
                .extracting(CourseGradeColumn::getName).containsExactly("Cũ");
    }

    /** Spec mục 4: PATCH thay TOÀN BỘ danh sách, mirror {@code UpdateClass} với lịch học. */
    @Test
    void updateReplacesTheWholeColumnListInsteadOfPatchingRows() {
        var course = existingCourseWithOneColumn();

        updateCourse.execute(courseId, actorAccountId, actorBranchId, updateRequest(VALID_COLUMNS));

        assertThat(course.getGradeColumns()).extracting(CourseGradeColumn::getName)
                .containsExactly("Giữa kỳ", "Cuối kỳ");
    }

    /** Review Focus #9: khóa học cũ chưa cấu hình cột điểm vẫn phải sửa được tên/học phí như trước. */
    @Test
    void updateAcceptsAnEmptyGradeColumnList() {
        var course = existingCourseWithOneColumn();

        assertThatCode(() -> updateCourse.execute(courseId, actorAccountId, actorBranchId,
                updateRequest(List.of()))).doesNotThrowAnyException();
        assertThat(course.getGradeColumns()).isEmpty();
    }

    @Test
    void createAcceptsAnEmptyGradeColumnList() {
        stubNoCodeClash();

        assertThatCode(() -> createCourse.execute(actorAccountId, actorBranchId, createRequest(List.of())))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CourseGradeColumnUseCasesTest`
Expected: FAIL at compile — `constructor CreateCourseRequest cannot be applied to given types` (6 args vs 5), `cannot find symbol: class GradeColumnResponse` is not yet referenced but the request arity mismatch fires first.

- [ ] **Step 3: Add the response DTO and widen the three records**

Create `backend/src/main/java/com/eduerp/modules/courses/dto/GradeColumnResponse.java`:

```java
package com.eduerp.modules.courses.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một cột điểm đã lưu. Khác {@link GradeColumnSlot} ở chỗ có {@code id} - {@code modules.academic}
 * cần id này để gắn một {@code GradeEntry} vào đúng cột (spec mục 6), và frontend cần nó làm key cột
 * trong bảng điểm. Tên field là hợp đồng với Zod schema ở frontend - đổi tên là breaking.
 */
public record GradeColumnResponse(UUID id, String name, BigDecimal weightPercent, BigDecimal maxScore,
        int displayOrder) {
}
```

Replace `backend/src/main/java/com/eduerp/modules/courses/dto/CreateCourseRequest.java` entirely:

```java
package com.eduerp.modules.courses.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

/** {@code gradeColumns} rỗng = chưa cấu hình cột điểm, hợp lệ (mirror {@code CreateClassRequest}
 * nhận lịch học rỗng). Tổng trọng số của danh sách không rỗng phải bằng 100% (spec mục 2.3). */
public record CreateCourseRequest(@NotBlank String code, @NotBlank String name, String description,
        Integer standardSessionCount, @PositiveOrZero BigDecimal tuitionFee,
        @NotNull @Valid List<GradeColumnSlot> gradeColumns) {
}
```

Replace `backend/src/main/java/com/eduerp/modules/courses/dto/UpdateCourseRequest.java` entirely:

```java
package com.eduerp.modules.courses.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

/** Không có {@code code}: mã khóa học bất biến sau khi tạo. {@code gradeColumns} thay TOÀN BỘ danh
 * sách cũ, không vá từng dòng (spec mục 4, mirror {@code UpdateClassRequest.schedule}). */
public record UpdateCourseRequest(@NotBlank String name, String description,
        Integer standardSessionCount, @PositiveOrZero BigDecimal tuitionFee, boolean active,
        @NotNull @Valid List<GradeColumnSlot> gradeColumns) {
}
```

Replace `backend/src/main/java/com/eduerp/modules/courses/dto/CourseResponse.java` entirely:

```java
package com.eduerp.modules.courses.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CourseResponse(UUID id, String code, String name, String description,
        Integer standardSessionCount, BigDecimal tuitionFee, boolean active,
        List<GradeColumnResponse> gradeColumns) {
}
```

- [ ] **Step 4: Wire the two usecases**

Replace the body of `CreateCourse.execute` in `backend/src/main/java/com/eduerp/modules/courses/usecase/CreateCourse.java` and add the two imports:

```java
import com.eduerp.modules.courses.CourseGradeColumnWeightsInvalidException;
import com.eduerp.modules.courses.internal.rules.GradeColumnRules;
```

```java
    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateCourseRequest request) {
        if (courses.findByCode(request.code()).isPresent()) {
            throw new CourseCodeAlreadyExistsException(request.code());
        }
        if (!GradeColumnRules.weightsSumToRequiredTotal(request.gradeColumns())) {
            throw new CourseGradeColumnWeightsInvalidException(
                    GradeColumnRules.totalWeight(request.gradeColumns()));
        }
        var course = new Course(request.code(), request.name(), request.description(),
                request.standardSessionCount(), request.tuitionFee());
        request.gradeColumns().forEach(column -> course.addGradeColumn(column.name(),
                column.weightPercent(), column.maxScore(), column.displayOrder()));
        var saved = courses.save(course);
        events.publishEvent(new CoursesEvents.CourseCreated(saved.getId(), actorAccountId, actorBranchId));
        return saved.getId();
    }
```

Replace the body of `UpdateCourse.execute` in `backend/src/main/java/com/eduerp/modules/courses/usecase/UpdateCourse.java` and add the same two imports:

```java
    @Transactional
    public void execute(UUID courseId, UUID actorAccountId, UUID actorBranchId, UpdateCourseRequest request) {
        var course = courses.findById(courseId).orElseThrow(() -> new CourseNotFoundException(courseId));
        // Kiểm TRƯỚC khi chạm vào entity: clearGradeColumns() rồi mới ném là mất cấu hình cũ dù PATCH
        // bị chặn (CourseGradeColumnUseCasesTest.updateRejects... chốt lại điều này).
        if (!GradeColumnRules.weightsSumToRequiredTotal(request.gradeColumns())) {
            throw new CourseGradeColumnWeightsInvalidException(
                    GradeColumnRules.totalWeight(request.gradeColumns()));
        }
        course.setName(request.name());
        course.setDescription(request.description());
        course.setStandardSessionCount(request.standardSessionCount());
        course.setTuitionFee(request.tuitionFee());
        course.setActive(request.active());
        course.clearGradeColumns();
        request.gradeColumns().forEach(column -> course.addGradeColumn(column.name(),
                column.weightPercent(), column.maxScore(), column.displayOrder()));
        events.publishEvent(new CoursesEvents.CourseUpdated(courseId, actorAccountId, actorBranchId));
    }
```

- [ ] **Step 5: Map the new field in `ListCourses`**

In `backend/src/main/java/com/eduerp/modules/courses/usecase/ListCourses.java`, add the import and replace `toResponse`:

```java
import com.eduerp.modules.courses.dto.GradeColumnResponse;
```

```java
    private static CourseResponse toResponse(Course course) {
        var gradeColumns = course.getGradeColumns().stream()
                .map(column -> new GradeColumnResponse(column.getId(), column.getName(),
                        column.getWeightPercent(), column.getMaxScore(), column.getDisplayOrder()))
                .toList();
        return new CourseResponse(course.getId(), course.getCode(), course.getName(),
                course.getDescription(), course.getStandardSessionCount(), course.getTuitionFee(),
                course.isActive(), gradeColumns);
    }
```

- [ ] **Step 6: Fix the four existing call sites in `CourseAdminControllerIT`**

In `backend/src/test/java/com/eduerp/modules/courses/web/CourseAdminControllerIT.java`, add the import `com.eduerp.modules.courses.dto.GradeColumnSlot`, `java.math.BigDecimal`, `java.util.List` if absent, then update the four constructions:

- line 89: `new CreateCourseRequest("TA-GT", "Tiếng Anh giao tiếp", "Mô tả", 24, null)` → `new CreateCourseRequest("TA-GT", "Tiếng Anh giao tiếp", "Mô tả", 24, null, List.of())`
- line 110: `new UpdateCourseRequest("Toán lớp 9 (mới)", "Cập nhật", 30, null, false)` → `new UpdateCourseRequest("Toán lớp 9 (mới)", "Cập nhật", 30, null, false, List.of())`
- line 126: `new CreateCourseRequest("X01", "", null, null, null)` → `new CreateCourseRequest("X01", "", null, null, null, List.of())`
- line 140: `new CreateCourseRequest("XX01", "Không được phép", null, null, null)` → `new CreateCourseRequest("XX01", "Không được phép", null, null, null, List.of())`

Then append these two tests to the class:

```java
    /** Review Focus #6 end-to-end: cấu hình đúng 40/60 lưu được và đọc lại được qua GET. */
    @Test
    void createsACourseWithGradeColumnsThenReadsThemBack() throws Exception {
        var admin = signIn("course-grade-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var request = new CreateCourseRequest("TA-GC", "Khóa có cột điểm", null, 24, null, List.of(
                new GradeColumnSlot("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0),
                new GradeColumnSlot("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1)));

        var createResult = mockMvc.perform(post("/api/courses/courses")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);
        var courseId = objectMapper.readValue(createResult.getResponse().getContentAsString(), UUID.class);

        var listResult = mockMvc.perform(get("/api/courses/courses?size=100").cookie(admin)).andReturn();
        var items = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items");
        var created = java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .filter(item -> courseId.toString().equals(item.get("id").asText()))
                .findFirst().orElseThrow();
        assertThat(created.get("gradeColumns")).hasSize(2);
        assertThat(created.get("gradeColumns").get(0).get("name").asText()).isEqualTo("Giữa kỳ");
        assertThat(created.get("gradeColumns").get(0).get("weightPercent").asDouble()).isEqualTo(40.0);
    }

    /** Review Focus #6: luật phải chặn ở CẢ đường PATCH, không chỉ POST. */
    @Test
    void rejectsAPatchWhoseGradeColumnWeightsDoNotSumToOneHundred() throws Exception {
        var admin = signIn("course-grade-bad@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var created = mockMvc.perform(post("/api/courses/courses")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCourseRequest("TA-GCB",
                                "Khóa sẽ bị PATCH sai", null, 24, null, List.of()))))
                .andReturn();
        var courseId = objectMapper.readValue(created.getResponse().getContentAsString(), UUID.class);

        var result = mockMvc.perform(patch("/api/courses/courses/" + courseId)
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCourseRequest(
                                "Khóa sẽ bị PATCH sai", null, 24, null, true, List.of(
                                        new GradeColumnSlot("Giữa kỳ", new BigDecimal("40.00"),
                                                new BigDecimal("10.00"), 0),
                                        new GradeColumnSlot("Cuối kỳ", new BigDecimal("50.00"),
                                                new BigDecimal("10.00"), 1))))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString())
                .contains("COURSES_GRADE_COLUMN_WEIGHTS_INVALID");
    }
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest='CourseGradeColumnUseCasesTest,CourseAdminControllerIT'`
Expected: PASS — 6 unit tests + the existing controller tests plus 2 new ones, 0 failures.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/courses/dto/ \
        backend/src/main/java/com/eduerp/modules/courses/usecase/CreateCourse.java \
        backend/src/main/java/com/eduerp/modules/courses/usecase/UpdateCourse.java \
        backend/src/main/java/com/eduerp/modules/courses/usecase/ListCourses.java \
        backend/src/test/java/com/eduerp/modules/courses/usecase/CourseGradeColumnUseCasesTest.java \
        backend/src/test/java/com/eduerp/modules/courses/web/CourseAdminControllerIT.java
git commit -m "feat(courses): configure grade columns when creating and updating a course"
```

---

## Task 4: Mở rộng facade `CoursesManagement`

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/courses/CoursesManagement.java`
- Modify: `backend/src/test/java/com/eduerp/modules/enrollment/usecase/CreateEnrollmentTest.java:56-57,98-99` (xây `ClassInfoResponse` theo thứ tự tham số nên đổi arity là đứt biên dịch)
- Modify: `backend/src/test/java/com/eduerp/modules/courses/CoursesManagementIT.java`
- Không cần sửa: `backend/src/main/java/com/eduerp/modules/enrollment/usecase/CreateEnrollment.java:47` chỉ ĐỌC field theo tên (`classInfo.courseId()`, `.branchId()`, `.maxSeats()`, `.active()`) nên vẫn biên dịch; vẫn phải chạy lại test của nó ở Step 6.

Danh sách call site đầy đủ, lấy từ `grep -rn "ClassInfoResponse\|getClassInfo" backend/src/main/java backend/src/test/java`:
`CoursesManagement.java:20,46,47` (định nghĩa), `CoursesManagement.java:13` (javadoc), `enrollment/package-info.java:4` (javadoc), `CreateEnrollment.java:47` (đọc field), `CreateEnrollmentTest.java:56,57,89,98,99` (xây record), `CoursesManagementIT.java:55,61,71,72` (đọc field).

**Interfaces:**
- Consumes: `dto.WeeklyScheduleSlot(CoursesConstants.DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime)` (hiện có), `dto.GradeColumnResponse` (Task 3), `Class.getTeacherId()/getSchedule()`, `Course.getStandardSessionCount()/getGradeColumns()`.
- Produces: `CoursesManagement.ClassInfoResponse(UUID classId, UUID courseId, UUID branchId, UUID teacherId, int maxSeats, boolean active, Integer standardSessionCount, List<WeeklyScheduleSlot> schedule)`; `CoursesManagement.getGradeColumns(UUID courseId): List<GradeColumnResponse>`.

> Ghi chú thiết kế: spec mục 4 gọi kiểu lịch là `ClassScheduleSlot(dayOfWeek, startTime, endTime)`. Dự án ĐÃ CÓ `courses.dto.WeeklyScheduleSlot` với đúng ba field đó, đã mang `@NamedInterface("dto")` nên module khác tham chiếu được, và `ClassResponse` đã dùng nó. Tạo thêm một record y hệt là hai nguồn sự thật cho cùng một khái niệm, nên facade dùng lại `WeeklyScheduleSlot`. Facade đọc type từ `dto` của chính module là pattern đã có: `AuditManagement` import `audit.dto.RecentAuditAction`, `PayrollManagement` import `payroll.dto.ContractResponse`.

- [ ] **Step 1: Write the failing test**

In `backend/src/test/java/com/eduerp/modules/courses/CoursesManagementIT.java`, add the imports:

```java
import com.eduerp.modules.courses.CoursesConstants;
import java.time.LocalTime;
```

Replace `getClassInfoReturnsTheSnapshotEnrollmentNeeds` and append three new tests:

```java
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

    /**
     * Ba field mới của spec mục 4. {@code modules.academic} dựa vào ĐÚNG một lần gọi facade này để
     * có đủ dữ liệu cho cả hai việc: kiểm "giáo viên này có dạy lớp này không" ({@code teacherId}) và
     * đếm số buổi đã học ({@code schedule} + {@code standardSessionCount}).
     */
    @Test
    void getClassInfoExposesTeacherScheduleAndStandardSessionCount() {
        var course = courses.save(new Course("FACADE-C4", "Khoá facade 4", null, 24, null));
        var branch = branches.save(new Branch("FCD-B4", "Chi nhánh facade 4", null));
        var teacher = accounts.save(new Account("facade-teacher4@eduerp.local", "hash", "GV 4", null));
        var cls = new Class(course, "FACADE-K4", branch.getId(), teacher.getId(), 12);
        cls.addSchedule(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0));
        cls.addSchedule(CoursesConstants.DayOfWeek.WED, LocalTime.of(18, 0), LocalTime.of(20, 0));
        var saved = classes.save(cls);

        var info = coursesManagement.getClassInfo(saved.getId()).orElseThrow();

        assertThat(info.teacherId()).isEqualTo(teacher.getId());
        assertThat(info.standardSessionCount()).isEqualTo(24);
        assertThat(info.schedule()).hasSize(2);
        assertThat(info.schedule().get(0).dayOfWeek()).isEqualTo(CoursesConstants.DayOfWeek.MON);
        assertThat(info.schedule().get(0).startTime()).isEqualTo(LocalTime.of(18, 0));
    }

    /** Review Focus #8 + #12 ở tầng facade: hai trạng thái "chưa cấu hình" phải phân biệt được với
     * "không tìm thấy lớp" - Optional có giá trị, hai field bên trong rỗng/null. */
    @Test
    void getClassInfoReportsAnUnconfiguredSessionCountAndAnEmptySchedule() {
        var course = courses.save(new Course("FACADE-C5", "Khoá facade 5", null, null, null));
        var branch = branches.save(new Branch("FCD-B5", "Chi nhánh facade 5", null));
        var teacher = accounts.save(new Account("facade-teacher5@eduerp.local", "hash", "GV 5", null));
        var cls = classes.save(new Class(course, "FACADE-K5", branch.getId(), teacher.getId(), 12));

        var info = coursesManagement.getClassInfo(cls.getId()).orElseThrow();

        assertThat(info.standardSessionCount()).isNull();
        assertThat(info.schedule()).isEmpty();
    }

    @Test
    void getGradeColumnsReturnsTheColumnsInDisplayOrder() {
        var course = new Course("FACADE-C6", "Khoá facade 6", null, 24, null);
        course.addGradeColumn("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1);
        course.addGradeColumn("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
        var saved = courses.save(course);

        var columns = coursesManagement.getGradeColumns(saved.getId());

        assertThat(columns).extracting(com.eduerp.modules.courses.dto.GradeColumnResponse::name)
                .as("@OrderBy(displayOrder ASC) quyết định thứ tự, không phải thứ tự thêm vào")
                .containsExactly("Giữa kỳ", "Cuối kỳ");
    }

    /** Khoá không tồn tại trả danh sách rỗng, không ném - nơi gọi (modules.academic) đã có lỗi riêng
     * cho "không tìm thấy lớp", không cần hai lỗi cho cùng một tình huống. */
    @Test
    void getGradeColumnsIsEmptyForAnUnknownCourse() {
        assertThat(coursesManagement.getGradeColumns(UUID.randomUUID())).isEmpty();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CoursesManagementIT`
Expected: FAIL at compile — `cannot find symbol: method teacherId()`, `cannot find symbol: method getGradeColumns(java.util.UUID)`.

- [ ] **Step 3: Change the facade**

Replace `backend/src/main/java/com/eduerp/modules/courses/CoursesManagement.java` entirely:

```java
package com.eduerp.modules.courses;

import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module courses — type DUY NHẤT mà module khác được phép gọi (rule #1).
 * {@code modules.enrollment} đọc {@link ClassInfoResponse} để chốt snapshot lúc ghi danh,
 * {@code modules.billing} đọc {@link CourseTuitionResponse} để chặn tổng các đợt vượt học phí,
 * {@code modules.academic} đọc {@link ClassInfoResponse} (kiểm giáo viên được phân công + đếm số
 * buổi đã học) và {@link #getGradeColumns(UUID)} (cấu trúc điểm của khóa).
 */
@Service
public class CoursesManagement {

    /**
     * Ảnh chụp những gì module khác cần biết về một lớp - không lộ entity Class ra ngoài.
     *
     * <p>{@code teacherId}, {@code standardSessionCount} và {@code schedule} thêm ở Phân hệ 4 (spec
     * mục 4): gộp cả ba vào đây để {@code modules.academic} chỉ cần MỘT lần gọi facade là đủ dữ liệu
     * tính tiến trình, không phải gọi thêm {@link #getCourseTuition(UUID)} cho mục đích không liên
     * quan tới học phí. {@code schedule} dùng lại {@link WeeklyScheduleSlot} của {@code dto} thay vì
     * định nghĩa một record y hệt - một khái niệm, một kiểu.
     */
    public record ClassInfoResponse(UUID classId, UUID courseId, UUID branchId, UUID teacherId, int maxSeats,
            boolean active, Integer standardSessionCount, List<WeeklyScheduleSlot> schedule) {
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
        return classes.findById(classId).map(CoursesManagement::toClassInfo);
    }

    @Transactional(readOnly = true)
    public Optional<CourseTuitionResponse> getCourseTuition(UUID courseId) {
        return courses.findById(courseId)
                .map(course -> new CourseTuitionResponse(course.getId(), course.getTuitionFee(), course.isActive()));
    }

    /**
     * Cấu trúc điểm của một khóa học, theo {@code displayOrder} (spec mục 4). Khóa không tồn tại trả
     * danh sách RỖNG chứ không ném: nơi gọi đã có lỗi riêng cho "không tìm thấy", và "khóa chưa cấu
     * hình cột điểm" cũng là danh sách rỗng - hai tình huống dẫn tới cùng một kết luận nghiệp vụ
     * ("chưa có cột điểm nào để nhập").
     */
    @Transactional(readOnly = true)
    public List<GradeColumnResponse> getGradeColumns(UUID courseId) {
        return courses.findById(courseId)
                .map(course -> course.getGradeColumns().stream()
                        .map(column -> new GradeColumnResponse(column.getId(), column.getName(),
                                column.getWeightPercent(), column.getMaxScore(), column.getDisplayOrder()))
                        .toList())
                .orElseGet(List::of);
    }

    private static ClassInfoResponse toClassInfo(Class cls) {
        var schedule = cls.getSchedule().stream()
                .map(slot -> new WeeklyScheduleSlot(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()))
                .toList();
        return new ClassInfoResponse(cls.getId(), cls.getCourse().getId(), cls.getBranchId(),
                cls.getTeacherId(), cls.getMaxSeats(), cls.isActive(),
                cls.getCourse().getStandardSessionCount(), schedule);
    }
}
```

- [ ] **Step 4: Fix the two positional constructions in `CreateEnrollmentTest`**

In `backend/src/test/java/com/eduerp/modules/enrollment/usecase/CreateEnrollmentTest.java`, add a field and the two imports, then update both constructions.

Add imports:

```java
import java.util.List;
```

Add the field next to the existing ones:

```java
    private final UUID classTeacherId = UUID.randomUUID();
```

Replace `stubActiveClass()` (lines 55-58):

```java
    private void stubActiveClass() {
        when(courses.getClassInfo(classId)).thenReturn(Optional.of(
                new CoursesManagement.ClassInfoResponse(classId, courseId, classBranchId, classTeacherId,
                        MAX_SEATS, true, 24, List.of())));
    }
```

Replace the construction inside `rejectsADeactivatedClass()` (lines 98-99):

```java
        when(courses.getClassInfo(classId)).thenReturn(Optional.of(
                new CoursesManagement.ClassInfoResponse(classId, courseId, classBranchId, classTeacherId,
                        MAX_SEATS, false, 24, List.of())));
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=CoursesManagementIT`
Expected: PASS — 8 tests, 0 failures.

- [ ] **Step 6: Verify the facade-change ripple hit nothing else**

Run: `cd backend && ./mvnw test -Dtest='CreateEnrollmentTest,EnrollmentAdminControllerIT,InvoiceAdminControllerIT,ComboAdminControllerIT'`
Expected: PASS — `CreateEnrollment` chỉ đọc field theo tên nên nghiệp vụ ghi danh không đổi hành vi.

Run: `cd backend && ./mvnw -q clean compile test-compile`
Expected: BUILD SUCCESS — không còn call site nào của `ClassInfoResponse` chưa cập nhật.

- [ ] **Step 7: Update the two javadoc references**

In `backend/src/main/java/com/eduerp/modules/enrollment/package-info.java:4`, append to the existing sentence about `CoursesManagement.getClassInfo`: `(bản ghi này mang thêm teacherId/schedule/standardSessionCount từ Phân hệ 4 - enrollment không dùng, chỉ modules.academic dùng)`.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/courses/CoursesManagement.java \
        backend/src/main/java/com/eduerp/modules/enrollment/package-info.java \
        backend/src/test/java/com/eduerp/modules/courses/CoursesManagementIT.java \
        backend/src/test/java/com/eduerp/modules/enrollment/usecase/CreateEnrollmentTest.java
git commit -m "feat(courses): expose teacher, schedule, session count and grade columns on the facade"
```

---

## Task 5: `EnrollmentManagement.listActiveEnrollmentsByClass`

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/enrollment/internal/repository/EnrollmentRepository.java`
- Modify: `backend/src/main/java/com/eduerp/modules/enrollment/EnrollmentManagement.java`
- Test: `backend/src/test/java/com/eduerp/modules/enrollment/EnrollmentManagementIT.java`

**Interfaces:**
- Consumes: `Enrollment.getId()/getStudentProfileId()/getClassId()/getCourseId()/getBranchId()/getStatus()/getEnrolledAt()/getWithdrawnAt()`, `EnrollmentConstants.EnrollmentStatus`.
- Produces: `EnrollmentManagement.ClassRosterEntry(UUID enrollmentId, UUID studentProfileId, UUID courseId, EnrollmentConstants.EnrollmentStatus status, Instant enrolledAt, Instant withdrawnAt)`; `EnrollmentManagement.listActiveEnrollmentsByClass(UUID classId): List<ClassRosterEntry>`.

> Spec mục 3 cho phép thêm ĐÚNG MỘT method ĐỌC vào facade này và "Không có method ghi nào được thêm". Method dưới đây là `@Transactional(readOnly = true)`, không ghi gì.
>
> Tên method theo spec là `listActiveEnrollmentsByClass`, nhưng nó phải trả về CẢ ghi danh `WITHDRAWN`: spec mục 5 yêu cầu "Rút khỏi lớp (`WITHDRAWN`) ĐÓNG BĂNG tiến trình tại `withdrawnAt`", nghĩa là học viên đã rút vẫn phải xuất hiện trong bảng tiến trình với con số đã đóng băng — lọc họ ra là làm hỏng Review Focus #2. "Active" ở đây là "đang/đã thực sự học lớp này", tức mọi `Enrollment` của lớp, phân biệt bằng `status` trong từng dòng để nơi gọi tự quyết định. Javadoc phải nói rõ điều này.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/enrollment/EnrollmentManagementIT.java`:

```java
package com.eduerp.modules.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.redis.testcontainers.RedisContainer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Hợp đồng ra ngoài module mà {@code modules.academic} sẽ gọi - test qua context thật, mirror
 * {@code CoursesManagementIT}. */
@Testcontainers
@SpringBootTest
class EnrollmentManagementIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @Autowired
    EnrollmentManagement enrollmentManagement;

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
        branchId = branches.save(new Branch("ROS-" + shortId(), "Chi nhánh sổ điểm", null)).getId();
        actorId = accounts.save(new Account("ros-actor-" + shortId() + "@eduerp.local", "hash", "Actor", null))
                .getId();
        var course = courses.save(new Course("ROS-C-" + shortId(), "Khoá sổ điểm", null, 24, null));
        courseId = course.getId();
        var teacherId = accounts.save(new Account("ros-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        classId = classes.save(new Class(course, "ROS-K-" + shortId(), branchId, teacherId, 20)).getId();
    }

    private UUID newStudentProfileId() {
        var account = accounts.save(new Account("ros-hv-" + shortId() + "@eduerp.local", "hash", "HV", null));
        return profiles.save(new StudentProfile(account.getId(), null, null, null)).getId();
    }

    private Enrollment saveEnrollment(UUID studentProfileId) {
        return enrollments.saveAndFlush(
                new Enrollment(studentProfileId, classId, courseId, branchId, actorId));
    }

    @Test
    void listsEveryEnrolmentOfTheClassWithTheDatesProgressNeeds() {
        var studentProfileId = newStudentProfileId();
        var enrollment = saveEnrollment(studentProfileId);

        var roster = enrollmentManagement.listActiveEnrollmentsByClass(classId);

        assertThat(roster).hasSize(1);
        var entry = roster.get(0);
        assertThat(entry.enrollmentId()).isEqualTo(enrollment.getId());
        assertThat(entry.studentProfileId()).isEqualTo(studentProfileId);
        assertThat(entry.courseId()).isEqualTo(courseId);
        assertThat(entry.status()).isEqualTo(EnrollmentConstants.EnrollmentStatus.ACTIVE);
        assertThat(entry.enrolledAt()).isNotNull();
        assertThat(entry.withdrawnAt()).isNull();
    }

    /**
     * Review Focus #2: học viên đã rút PHẢI còn trong danh sách, kèm {@code withdrawnAt}, vì tiến
     * trình của họ bị đóng băng tại mốc đó chứ không biến mất khỏi báo cáo.
     */
    @Test
    void keepsWithdrawnEnrolmentsSoProgressCanFreezeThemAtTheirWithdrawnAt() {
        var withdrawn = saveEnrollment(newStudentProfileId());
        withdrawn.withdraw();
        enrollments.saveAndFlush(withdrawn);

        var roster = enrollmentManagement.listActiveEnrollmentsByClass(classId);

        assertThat(roster).hasSize(1);
        assertThat(roster.get(0).status()).isEqualTo(EnrollmentConstants.EnrollmentStatus.WITHDRAWN);
        assertThat(roster.get(0).withdrawnAt()).isNotNull();
    }

    @Test
    void doesNotLeakEnrolmentsOfAnotherClass() {
        saveEnrollment(newStudentProfileId());

        assertThat(enrollmentManagement.listActiveEnrollmentsByClass(UUID.randomUUID())).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=EnrollmentManagementIT`
Expected: FAIL at compile — `cannot find symbol: method listActiveEnrollmentsByClass(java.util.UUID)`.

- [ ] **Step 3: Add the repository finder**

In `backend/src/main/java/com/eduerp/modules/enrollment/internal/repository/EnrollmentRepository.java`, add the import `java.util.List` and this method above `search`:

```java
    /**
     * Toàn bộ ghi danh của một lớp, kể cả WITHDRAWN/COMPLETED - {@code modules.academic} cần cả
     * những dòng đó để đóng băng tiến trình tại {@code withdrawnAt} (spec mục 5). Sắp theo
     * {@code enrolledAt} để bảng điểm/bảng tiến trình có thứ tự ổn định giữa hai lần tải.
     */
    List<Enrollment> findAllByClassIdOrderByEnrolledAtAsc(UUID classId);
```

- [ ] **Step 4: Add the facade method**

In `backend/src/main/java/com/eduerp/modules/enrollment/EnrollmentManagement.java`, add the imports:

```java
import java.time.Instant;
import java.util.List;
```

Add the record next to `EnrollmentSummaryResponse`:

```java
    /**
     * Một dòng trong danh sách học viên của một lớp. {@code status}/{@code withdrawnAt} đi kèm vì
     * {@code modules.academic} dùng chính hai field này để đóng băng tiến trình của người đã rút
     * (spec mục 5) - lọc sẵn ở đây là tước mất khả năng đó của nơi gọi.
     */
    public record ClassRosterEntry(UUID enrollmentId, UUID studentProfileId, UUID courseId,
            EnrollmentConstants.EnrollmentStatus status, Instant enrolledAt, Instant withdrawnAt) {
    }
```

Add the method after `getEnrollment`:

```java
    /**
     * Danh sách học viên của một lớp để {@code modules.academic} dựng bảng điểm/bảng tiến trình
     * (spec mục 3). CHỈ ĐỌC - spec nói rõ "Không có method ghi nào được thêm".
     *
     * <p>Trả về MỌI ghi danh của lớp, không chỉ {@code ACTIVE}: người đã rút vẫn phải có mặt trong
     * báo cáo với tiến trình đóng băng tại {@code withdrawnAt} (Review Focus #2). Nơi gọi phân biệt
     * bằng {@link ClassRosterEntry#status()}.
     */
    @Transactional(readOnly = true)
    public List<ClassRosterEntry> listActiveEnrollmentsByClass(UUID classId) {
        return enrollments.findAllByClassIdOrderByEnrolledAtAsc(classId).stream()
                .map(enrollment -> new ClassRosterEntry(enrollment.getId(), enrollment.getStudentProfileId(),
                        enrollment.getCourseId(), enrollment.getStatus(), enrollment.getEnrolledAt(),
                        enrollment.getWithdrawnAt()))
                .toList();
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest='EnrollmentManagementIT,EnrollmentRepositoryIT'`
Expected: PASS — 3 facade tests + các test repository hiện có, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/enrollment/EnrollmentManagement.java \
        backend/src/main/java/com/eduerp/modules/enrollment/internal/repository/EnrollmentRepository.java \
        backend/src/test/java/com/eduerp/modules/enrollment/EnrollmentManagementIT.java
git commit -m "feat(enrollment): add a read-only class roster method to the facade"
```

---

## Task 6: `StudentsManagement.summariesOf`

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/students/StudentsManagement.java`
- Modify: `backend/src/test/java/com/eduerp/modules/students/StudentsManagementIT.java`

**Interfaces:**
- Consumes: `StudentProfileRepository extends JpaRepository<StudentProfile, UUID>` (`findAllById`), `StudentsManagement.StudentSummaryResponse(UUID studentProfileId, UUID accountId, boolean active)` (hiện có).
- Produces: `StudentsManagement.summariesOf(Collection<UUID> studentProfileIds): Map<UUID, StudentSummaryResponse>`.

> Bảng điểm phải in TÊN học viên, không phải UUID. Đường lấy tên đã có tiền lệ nguyên vẹn trong
> `ListStudentProfiles`: `StudentProfile.getAccountId()` → `IdentityManagement.summariesOf(accountIds)` →
> `AccountBasicInfo.fullName()`. Thiếu một bước batch ở `students` thì `modules.academic` phải gọi
> `getProfile` cho từng học viên (N+1 xuyên facade), nên thêm đúng một method đọc theo lô, mirror
> `IdentityManagement.summariesOf`/`AccessManagement.rolesFor` về cả tên và hình dạng trả về.

- [ ] **Step 1: Write the failing test**

Append to `backend/src/test/java/com/eduerp/modules/students/StudentsManagementIT.java` (thêm các import `java.util.List`, `java.util.UUID` nếu chưa có):

```java
    /** Mirror {@code IdentityManagement.summariesOf}: nạp theo lô để bảng điểm không bắn N+1 câu
     * truy vấn xuyên facade khi in tên 30 học viên của một lớp (rule #3). */
    @Test
    void summariesOfLoadsManyProfilesInOneCall() {
        var first = newActiveStudentProfileId();
        var second = newActiveStudentProfileId();

        var summaries = studentsManagement.summariesOf(List.of(first, second));

        assertThat(summaries).containsOnlyKeys(first, second);
        assertThat(summaries.get(first).studentProfileId()).isEqualTo(first);
        assertThat(summaries.get(first).accountId()).isNotNull();
        assertThat(summaries.get(first).active()).isTrue();
    }

    /** Id không tồn tại không làm cả lô đổ - chỉ vắng mặt trong map, nơi gọi tự xử lý tên trống. */
    @Test
    void summariesOfSkipsUnknownProfileIdsInsteadOfFailing() {
        var known = newActiveStudentProfileId();

        var summaries = studentsManagement.summariesOf(List.of(known, UUID.randomUUID()));

        assertThat(summaries).containsOnlyKeys(known);
    }

    @Test
    void summariesOfAnEmptyCollectionIsAnEmptyMap() {
        assertThat(studentsManagement.summariesOf(List.of())).isEmpty();
    }
```

Nếu `StudentsManagementIT` chưa có helper dựng hồ sơ học viên, thêm helper này vào class (dùng đúng các repository/field mà file đó đã `@Autowired`; nếu tên field khác thì giữ tên có sẵn):

```java
    private UUID newActiveStudentProfileId() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var account = accounts.save(new com.eduerp.modules.identity.internal.model.Account(
                "stu-sum-" + suffix + "@eduerp.local", "hash", "Học viên " + suffix, null));
        return profiles.save(new com.eduerp.modules.students.internal.model.StudentProfile(
                account.getId(), null, null, null)).getId();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=StudentsManagementIT`
Expected: FAIL at compile — `cannot find symbol: method summariesOf(java.util.List<java.util.UUID>)`.

- [ ] **Step 3: Add the facade method**

In `backend/src/main/java/com/eduerp/modules/students/StudentsManagement.java`, add the imports:

```java
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
```

Add the method after `getProfile`, and extend the class javadoc with one sentence naming the new caller:

```java
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
```

Add the import for the entity used in the method reference:

```java
import com.eduerp.modules.students.internal.model.StudentProfile;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=StudentsManagementIT`
Expected: PASS — 3 tests mới + các test hiện có, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/students/StudentsManagement.java \
        backend/src/test/java/com/eduerp/modules/students/StudentsManagementIT.java
git commit -m "feat(students): add a batch profile summary lookup to the facade"
```

---

## Task 7: Khung module `modules.academic`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/academic/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/AcademicConstants.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/AcademicException.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/ClassNotFoundException.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/GradeColumnNotInCourseException.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/EnrollmentNotFoundException.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/InvalidScoreException.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/NotAssignedToClassException.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/AcademicEvents.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/internal/package-info.java`
- Test: `backend/src/test/java/com/eduerp/modules/academic/AcademicExceptionTest.java`

**Interfaces:**
- Consumes: `com.eduerp.core.exception.AppException` (`AppException(String errorCode, HttpStatus status, String message)`).
- Produces: `AcademicConstants.Limits.PROGRESS_PERCENT_CAP`, `AcademicConstants.Scoring.*`, `AcademicConstants.Schedules.CENTER_ZONE_ID`, `AcademicConstants.Excel.*`; `sealed AcademicException` với năm lớp con (`ClassNotFoundException`, `EnrollmentNotFoundException`, `GradeColumnNotInCourseException`, `InvalidScoreException`, `NotAssignedToClassException`); `AcademicEvents.GradeRecorded(UUID enrollmentId, UUID courseGradeColumnId, UUID actorAccountId, UUID actorBranchId)`.

> Không có `GradeColumnNotFoundException` riêng: nơi duy nhất tra cột điểm (`RecordGrade`, Task 12)
> chỉ tìm trong danh sách cột của ĐÚNG khóa thuộc lượt ghi danh đó, nên "id không tồn tại" và "id của
> khóa khác" là cùng một kết luận với người gọi — `GradeColumnNotInCourseException` nói ra cả hai id
> nên đủ thông tin. Thêm một exception thứ hai không ai ném được là code chết.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/academic/AcademicExceptionTest.java`:

```java
package com.eduerp.modules.academic;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Mirror {@code BillingExceptionTest}/{@code EnrollmentExceptionTest}: errorCode là hợp đồng với
 * frontend (ApiError.errorCode), đổi là breaking. */
class AcademicExceptionTest {

    @Test
    void classNotFoundIsNotFound() {
        var ex = new ClassNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ACADEMIC_CLASS_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void enrollmentNotFoundIsBadRequest() {
        var ex = new EnrollmentNotFoundException(UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ACADEMIC_ENROLLMENT_NOT_FOUND");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** Review Focus #11: một lỗi duy nhất cho cả "cột không tồn tại" và "cột của khóa khác" - vì
     * nơi gọi chỉ tra trong danh sách cột của đúng khóa đó, hai tình huống không phân biệt được và
     * cũng không cần phân biệt; thông báo nói ra cả hai id là đủ để người nhập hiểu. */
    @Test
    void gradeColumnNotInCourseIsConflictAndNamesBothIds() {
        var columnId = UUID.randomUUID();
        var courseId = UUID.randomUUID();
        var ex = new GradeColumnNotInCourseException(columnId, courseId);
        assertThat(ex.getErrorCode()).isEqualTo("ACADEMIC_GRADE_COLUMN_NOT_IN_COURSE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains(columnId.toString(), courseId.toString());
    }

    /** Review Focus #10: thông báo phải nói ra thang điểm của ĐÚNG cột đó, nếu không người nhập
     * không biết 9.5 sai ở đâu khi cột này thang 5. */
    @Test
    void invalidScoreIsBadRequestAndNamesTheAllowedRange() {
        var ex = new InvalidScoreException(new BigDecimal("9.50"), new BigDecimal("5.00"));
        assertThat(ex.getErrorCode()).isEqualTo("ACADEMIC_INVALID_SCORE");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("9.50", "5.00");
    }

    /** Review Focus #4: 403, không phải 404 - giáo viên có tồn tại và lớp có tồn tại, chỉ là không
     * phải lớp của họ. 404 ở đây sẽ che mất lý do thật. */
    @Test
    void notAssignedToClassIsForbidden() {
        var ex = new NotAssignedToClassException(UUID.randomUUID(), UUID.randomUUID());
        assertThat(ex.getErrorCode()).isEqualTo("ACADEMIC_NOT_ASSIGNED_TO_CLASS");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=AcademicExceptionTest`
Expected: FAIL at compile — `package com.eduerp.modules.academic does not exist`.

- [ ] **Step 3: Write the two package-info files**

Create `backend/src/main/java/com/eduerp/modules/academic/package-info.java`:

```java
/**
 * Module Academic — sở hữu ĐIỂM thật ({@code GradeEntry}) và việc TÍNH tiến trình học tập. Đây là
 * nơi duy nhất biết "một học viên trong một lớp đang ở đâu" về mặt học thuật, tách khỏi
 * {@code courses} (danh mục), {@code enrollment} (ai học lớp nào) và {@code billing} (ai nợ bao
 * nhiêu) — tránh God Module (spec mục 3).
 *
 * <p>Phụ thuộc một chiều: {@code access} ({@code AccessConstants} cho {@code @PreAuthorize} và
 * {@code AccessManagement.effectivePermissions} để biết scope THẬT của caller), {@code courses}
 * ({@code CoursesManagement.getClassInfo} lấy {@code teacherId}/lịch học/số buổi chuẩn,
 * {@code .getGradeColumns} lấy cấu trúc điểm), {@code enrollment}
 * ({@code EnrollmentManagement.listActiveEnrollmentsByClass}), {@code students} +
 * {@code identity} (tên học viên in trên bảng điểm). KHÔNG module nào đọc ngược từ
 * {@code academic} ở phase này; {@code audit} chỉ lắng nghe {@code AcademicEvents} bằng
 * {@code @ApplicationModuleListener}, không có cạnh phụ thuộc ngược.
 *
 * <p>KHÔNG LƯU tiến trình học tập: không có cột DB nào giữ %, con số được tính lúc đọc từ
 * {@code Enrollment.enrolledAt}/{@code withdrawnAt} đối chiếu lịch tuần của lớp (spec mục 5).
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — API của module: constants, exception, events. Không có facade
 *       {@code AcademicManagement} vì chưa module nào cần đọc điểm (rule #1 — chỉ tạo facade khi
 *       có nơi gọi thật).</li>
 *   <li>{@code dto} — hợp đồng vào/ra qua HTTP, {@code @NamedInterface("dto")}.</li>
 *   <li>{@code usecase} — một class = một use case (rule #7).</li>
 *   <li>{@code web} — controller mỏng (rule #8).</li>
 *   <li>{@code internal} — chi tiết cài đặt, Spring Modulith che khỏi mọi module khác.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Academic (Grades & Progress)")
package com.eduerp.modules.academic;
```

Create `backend/src/main/java/com/eduerp/modules/academic/internal/package-info.java`:

```java
/**
 * Chi tiết cài đặt của module academic. Spring Modulith che toàn bộ cây này khỏi mọi module khác.
 */
package com.eduerp.modules.academic.internal;
```

- [ ] **Step 4: Write the constants**

Create `backend/src/main/java/com/eduerp/modules/academic/AcademicConstants.java`:

```java
package com.eduerp.modules.academic;

import java.math.BigDecimal;

/**
 * Hằng số dùng chung của module academic, nằm ở base package (không phải {@code internal}) vì
 * {@code dto} và {@code internal} cùng tier, cả hai cần tham chiếu - mirror
 * {@code CoursesConstants}/{@code BillingConstants}.
 */
public final class AcademicConstants {

    private AcademicConstants() {
    }

    public static final class Limits {
        private Limits() {
        }

        /** Trần của công thức tiến trình: {@code min(đã học, chuẩn) / chuẩn * 100} (spec mục 5) -
         * học bù quá số buổi chuẩn vẫn dừng ở 100%, không ra 112%. */
        public static final int PROGRESS_PERCENT_CAP = 100;
    }

    public static final class Scoring {
        private Scoring() {
        }

        /** Khớp {@code grade_entries.score} và {@code course_grade_columns.max_score} NUMERIC(5,2). */
        public static final int SCORE_SCALE = 2;

        /** Chia {@code score / maxScore} trước khi nhân trọng số nên cần dư chữ số thập phân, nếu
         * không 8/10 thành 0 (BigDecimal chia theo scale của số bị chia) - mirror
         * {@code BillingRules.DISCOUNT_MATH_SCALE}. */
        public static final int WEIGHTED_MATH_SCALE = 6;

        /** Điểm thấp nhất nhận được ở mọi cột. Trần là {@code maxScore} của riêng từng cột, không
         * phải một hằng dùng chung (spec mục 6). */
        public static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    }

    public static final class Schedules {
        private Schedules() {
        }

        /**
         * Múi giờ dùng để quy một {@code Instant} ({@code Enrollment.enrolledAt}) về một ngày/giờ
         * địa phương so được với {@code ClassSchedule.startTime} — cột đó là {@code TIME} không mang
         * múi giờ, nên phép đếm buổi học BẮT BUỘC phải chọn một múi. Trung tâm vận hành ở Việt Nam
         * nên lịch "18:00 thứ Hai" là 18:00 giờ Việt Nam; không được dùng múi giờ của máy chạy
         * server, vì khi đó cùng một dữ liệu sẽ cho hai con số khác nhau trên hai môi trường.
         */
        public static final String CENTER_ZONE_ID = "Asia/Ho_Chi_Minh";
    }

    /** Nhãn hiển thị trong file Excel xuất ra (spec mục 7) - người dùng đọc trực tiếp các chuỗi này. */
    public static final class Excel {
        private Excel() {
        }

        public static final String CONTENT_TYPE =
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        public static final String GRADEBOOK_SHEET_NAME = "Bảng điểm";
        public static final String PROGRESS_SHEET_NAME = "Tiến trình học tập";
        public static final String GRADEBOOK_FILE_NAME = "bang-diem.xlsx";
        public static final String PROGRESS_FILE_NAME = "tien-trinh-hoc-tap.xlsx";
        public static final String HEADER_STUDENT_NAME = "Học viên";
        public static final String HEADER_FINAL_SCORE = "Điểm tổng kết";
        public static final String HEADER_ENROLLED_AT = "Ngày ghi danh";
        public static final String HEADER_STATUS = "Trạng thái";
        public static final String HEADER_ELAPSED_SESSIONS = "Số buổi đã học";
        public static final String HEADER_STANDARD_SESSIONS = "Tổng số buổi chuẩn";
        public static final String HEADER_PROGRESS_PERCENT = "Tiến trình (%)";
        /** Ô trống không được in "null": với điểm tổng kết nó có nghĩa "chưa đủ điểm" (spec mục 6),
         * với tiến trình nó có nghĩa "chưa cấu hình tổng số buổi chuẩn" (spec mục 5). */
        public static final String CELL_NOT_ENOUGH_SCORES = "Chưa đủ điểm";
        public static final String CELL_SESSION_COUNT_NOT_CONFIGURED = "Chưa cấu hình tổng số buổi chuẩn";
    }

    /** Không cần cache namespace nào ở phase này - điểm đọc trực tiếp từ DB, không qua Redis. */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }
    }
}
```

- [ ] **Step 5: Write the sealed exception hierarchy**

Create `backend/src/main/java/com/eduerp/modules/academic/AcademicException.java`:

```java
package com.eduerp.modules.academic;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module academic phát ra - mirror {@code BillingException}. */
public sealed class AcademicException extends AppException
        permits ClassNotFoundException, EnrollmentNotFoundException, GradeColumnNotInCourseException,
        InvalidScoreException, NotAssignedToClassException {

    protected AcademicException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
```

Create `backend/src/main/java/com/eduerp/modules/academic/ClassNotFoundException.java`:

```java
package com.eduerp.modules.academic;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ClassNotFoundException extends AcademicException {

    public ClassNotFoundException(UUID classId) {
        super("ACADEMIC_CLASS_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy lớp học " + classId);
    }
}
```

Create `backend/src/main/java/com/eduerp/modules/academic/EnrollmentNotFoundException.java`:

```java
package com.eduerp.modules.academic;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class EnrollmentNotFoundException extends AcademicException {

    public EnrollmentNotFoundException(UUID enrollmentId) {
        super("ACADEMIC_ENROLLMENT_NOT_FOUND", HttpStatus.BAD_REQUEST,
                "Không tìm thấy lượt ghi danh " + enrollmentId);
    }
}
```

Create `backend/src/main/java/com/eduerp/modules/academic/GradeColumnNotInCourseException.java`:

```java
package com.eduerp.modules.academic;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Review Focus #11: cột điểm không nằm trong cấu hình của khóa thuộc lượt ghi danh đang nhập - ghi
 * vào đây là tạo ra một điểm không bao giờ được tính vào điểm tổng kết của ai, nên chặn thẳng thay vì
 * lưu im lặng. Bao luôn trường hợp id không tồn tại: {@code RecordGrade} chỉ tra trong danh sách cột
 * của đúng khóa đó nên hai tình huống không phân biệt được, và thông báo nói ra cả hai id là đủ.
 */
public final class GradeColumnNotInCourseException extends AcademicException {

    public GradeColumnNotInCourseException(UUID courseGradeColumnId, UUID courseId) {
        super("ACADEMIC_GRADE_COLUMN_NOT_IN_COURSE", HttpStatus.CONFLICT,
                "Cột điểm " + courseGradeColumnId + " không thuộc khóa học " + courseId);
    }
}
```

Create `backend/src/main/java/com/eduerp/modules/academic/InvalidScoreException.java`:

```java
package com.eduerp.modules.academic;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

/** Review Focus #10: thang điểm là của RIÊNG từng cột, nên lỗi phải nói ra thang của đúng cột đó. */
public final class InvalidScoreException extends AcademicException {

    public InvalidScoreException(BigDecimal score, BigDecimal maxScore) {
        super("ACADEMIC_INVALID_SCORE", HttpStatus.BAD_REQUEST,
                "Điểm " + score.toPlainString() + " phải nằm trong khoảng ["
                        + AcademicConstants.Scoring.MIN_SCORE.toPlainString() + ", "
                        + maxScore.toPlainString() + "] của cột điểm này");
    }
}
```

Create `backend/src/main/java/com/eduerp/modules/academic/NotAssignedToClassException.java`:

```java
package com.eduerp.modules.academic;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Review Focus #4: caller có quyền {@code GRADE:*} ở scope {@code PERSONAL}/{@code BRANCH} nhưng
 * KHÔNG phải giáo viên được phân công dạy lớp này (spec mục 6 bước 4). 403 chứ không 404: cả người
 * và lớp đều tồn tại, chỉ là không phải lớp của họ - trả 404 sẽ che mất lý do thật.
 */
public final class NotAssignedToClassException extends AcademicException {

    public NotAssignedToClassException(UUID actorAccountId, UUID classId) {
        super("ACADEMIC_NOT_ASSIGNED_TO_CLASS", HttpStatus.FORBIDDEN,
                "Tài khoản " + actorAccountId + " không được phân công dạy lớp " + classId);
    }
}
```

- [ ] **Step 6: Write the events**

Create `backend/src/main/java/com/eduerp/modules/academic/AcademicEvents.java`:

```java
package com.eduerp.modules.academic;

import java.util.UUID;

/** Sự kiện nghiệp vụ của module academic. {@code modules.audit} lắng nghe, academic không biết audit
 * tồn tại (spec mục 9). */
public final class AcademicEvents {

    private AcademicEvents() {
    }

    /** Nhập hoặc sửa điểm một cột của một lượt ghi danh (spec mục 9). */
    public record GradeRecorded(UUID enrollmentId, UUID courseGradeColumnId, UUID actorAccountId,
            UUID actorBranchId) {
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=AcademicExceptionTest`
Expected: PASS — 5 tests, 0 failures.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/academic/ \
        backend/src/test/java/com/eduerp/modules/academic/AcademicExceptionTest.java
git commit -m "feat(academic): scaffold the academic module with constants, exceptions and events"
```

---

## Task 8: `ProgressRules` — đếm buổi đã học (hàm thuần, không DB)

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/academic/internal/rules/ProgressRules.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/internal/rules/package-info.java`
- Test: `backend/src/test/java/com/eduerp/modules/academic/internal/rules/ProgressRulesTest.java`

**Interfaces:**
- Consumes: `com.eduerp.modules.courses.dto.WeeklyScheduleSlot(CoursesConstants.DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime)`, `CoursesConstants.DayOfWeek`, `AcademicConstants.Schedules.CENTER_ZONE_ID`, `AcademicConstants.Limits.PROGRESS_PERCENT_CAP`.
- Produces: `ProgressRules.countElapsedSessions(List<WeeklyScheduleSlot> schedule, Instant from, Instant to, ZoneId zone): int`; `ProgressRules.progressPercent(int elapsedSessions, Integer standardSessionCount): Integer`; `ProgressRules.centerZone(): ZoneId`.

> Spec mục 5 yêu cầu phép đếm này là một hàm THUẦN "nhận vào danh sách `ClassSchedule` + hai mốc thời
> gian, trả về số nguyên — test được độc lập, không cần DB". Vì vậy mốc kết thúc (`to`) được TRUYỀN
> VÀO, không đọc `Instant.now()` bên trong — nơi gọi quyết định đó là `withdrawnAt` hay "bây giờ"
> (mirror cách `VnPayPaymentGatewayClient` nhận `Clock` qua constructor để test chốt được giờ).

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/academic/internal/rules/ProgressRulesTest.java`:

```java
package com.eduerp.modules.academic.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hàm thuần, không I/O, không DB (spec mục 5). Mọi mốc thời gian trong test được viết theo giờ địa
 * phương của trung tâm rồi quy về {@code Instant}, đúng cách usecase sẽ làm.
 */
class ProgressRulesTest {

    private static final ZoneId ZONE = ProgressRules.centerZone();

    /** Lịch mẫu: Thứ Hai và Thứ Tư, 18:00-20:00 - hai buổi mỗi tuần. */
    private static final List<WeeklyScheduleSlot> MON_WED_EVENING = List.of(
            new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0)),
            new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.WED, LocalTime.of(18, 0), LocalTime.of(20, 0)));

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZONE).toInstant();
    }

    /** 2026-01-05 là Thứ Hai; tuần đó có buổi 05/01 (T2) và 07/01 (T4). */
    @Test
    void countsOneSessionPerScheduledSlotThatHasAlreadyStarted() {
        var count = ProgressRules.countElapsedSessions(MON_WED_EVENING,
                at(2026, 1, 5, 8, 0), at(2026, 1, 7, 23, 0), ZONE);

        assertThat(count).isEqualTo(2);
    }

    /** Buổi học được tính tại GIỜ BẮT ĐẦU: 18:00 cùng ngày nhưng mốc kết thúc là 17:59 thì buổi đó
     * chưa diễn ra, không được tính. */
    @Test
    void doesNotCountASessionThatHasNotStartedYet() {
        assertThat(ProgressRules.countElapsedSessions(MON_WED_EVENING,
                at(2026, 1, 5, 8, 0), at(2026, 1, 5, 17, 59), ZONE)).isZero();
        assertThat(ProgressRules.countElapsedSessions(MON_WED_EVENING,
                at(2026, 1, 5, 8, 0), at(2026, 1, 5, 18, 0), ZONE))
                .as("đúng 18:00 là đã bắt đầu - tính")
                .isEqualTo(1);
    }

    /**
     * Review Focus #1: hai học viên cùng lớp, {@code enrolledAt} khác nhau phải ra số buổi khác
     * nhau. Học viên A vào từ 05/01, học viên B vào giữa chừng 14/01 (Thứ Tư) - cùng mốc kết thúc
     * 21/01 (Thứ Tư) thì A học 7 buổi (05,07,12,14,19,21 → 6; xem khẳng định bên dưới) và B ít hơn.
     */
    @Test
    void countsOnlyTheSessionsAfterAMidCourseEnrolment() {
        var end = at(2026, 1, 21, 23, 0);

        var fromTheStart = ProgressRules.countElapsedSessions(MON_WED_EVENING,
                at(2026, 1, 5, 8, 0), end, ZONE);
        var fromMidCourse = ProgressRules.countElapsedSessions(MON_WED_EVENING,
                at(2026, 1, 14, 8, 0), end, ZONE);

        assertThat(fromTheStart)
                .as("05,07,12,14,19,21 tháng 1 = 6 buổi")
                .isEqualTo(6);
        assertThat(fromMidCourse)
                .as("14,19,21 tháng 1 = 3 buổi")
                .isEqualTo(3);
        assertThat(fromMidCourse).isLessThan(fromTheStart);
    }

    /**
     * Review Focus #2: mốc kết thúc là {@code withdrawnAt}, nên gọi lại sau bao lâu cũng ra đúng con
     * số đã đóng băng. Hai lần gọi dưới đây chỉ khác nhau ở chỗ lần thứ hai "được gọi muộn hơn" -
     * nhưng vì {@code to} vẫn là mốc rút, kết quả phải y nguyên.
     */
    @Test
    void freezesAtWithdrawalEvenWhenCalledMuchLater() {
        var enrolledAt = at(2026, 1, 5, 8, 0);
        var withdrawnAt = at(2026, 1, 13, 9, 0);

        var atWithdrawal = ProgressRules.countElapsedSessions(MON_WED_EVENING, enrolledAt, withdrawnAt, ZONE);
        var threeMonthsLater = ProgressRules.countElapsedSessions(MON_WED_EVENING, enrolledAt, withdrawnAt, ZONE);

        assertThat(atWithdrawal).as("05,07,12 tháng 1 = 3 buổi").isEqualTo(3);
        assertThat(threeMonthsLater).isEqualTo(atWithdrawal);
        assertThat(ProgressRules.countElapsedSessions(MON_WED_EVENING, enrolledAt,
                at(2026, 4, 13, 9, 0), ZONE))
                .as("nếu nơi gọi truyền sai mốc (dùng now thay vì withdrawnAt) thì số phải tăng - "
                        + "khẳng định này chốt rằng chính mốc to là thứ đóng băng tiến trình")
                .isGreaterThan(atWithdrawal);
    }

    /** Review Focus #12: lớp chưa có lịch học nào (trạng thái hợp lệ, xem
     * {@code ClassAdminControllerIT.acceptsAnEmptyScheduleOnCreate}) → 0 buổi, không ném. */
    @Test
    void countsZeroSessionsForAClassWithoutAnySchedule() {
        assertThat(ProgressRules.countElapsedSessions(List.of(),
                at(2026, 1, 5, 8, 0), at(2026, 6, 5, 8, 0), ZONE)).isZero();
    }

    /** Mốc kết thúc trước mốc bắt đầu (dữ liệu lệch, đồng hồ nhảy) → 0, không ra số âm. */
    @Test
    void countsZeroWhenTheEndMarkIsBeforeTheStartMark() {
        assertThat(ProgressRules.countElapsedSessions(MON_WED_EVENING,
                at(2026, 1, 20, 8, 0), at(2026, 1, 5, 8, 0), ZONE)).isZero();
    }

    /** Review Focus #8: chưa cấu hình tổng số buổi chuẩn → null, KHÔNG chia cho 0. */
    @Test
    void returnsNullWhenTheStandardSessionCountIsNotConfigured() {
        assertThat(ProgressRules.progressPercent(6, null)).isNull();
    }

    /** Số buổi chuẩn 0 hoặc âm là dữ liệu vô nghĩa - cũng là "chưa cấu hình", vẫn không chia cho 0. */
    @Test
    void returnsNullWhenTheStandardSessionCountIsZeroOrNegative() {
        assertThat(ProgressRules.progressPercent(6, 0)).isNull();
        assertThat(ProgressRules.progressPercent(6, -4)).isNull();
    }

    @Test
    void roundsThePercentToTheNearestWholeNumber() {
        assertThat(ProgressRules.progressPercent(6, 24)).isEqualTo(25);
        assertThat(ProgressRules.progressPercent(1, 3))
                .as("1/3 = 33.33 → 33")
                .isEqualTo(33);
        assertThat(ProgressRules.progressPercent(2, 3))
                .as("2/3 = 66.67 → 67")
                .isEqualTo(67);
    }

    /** Ranh giới trần của spec mục 5: {@code min(đã học, chuẩn)} nên học bù vượt số buổi chuẩn vẫn
     * dừng đúng ở 100, không ra 112. */
    @Test
    void capsThePercentAtOneHundred() {
        assertThat(ProgressRules.progressPercent(24, 24)).isEqualTo(100);
        assertThat(ProgressRules.progressPercent(27, 24)).isEqualTo(100);
    }

    @Test
    void reportsZeroPercentBeforeTheFirstSession() {
        assertThat(ProgressRules.progressPercent(0, 24)).isZero();
    }

    /** Múi giờ là quyết định tường minh của module, không phải múi giờ của máy chạy server - cùng dữ
     * liệu phải cho cùng con số trên mọi môi trường. */
    @Test
    void centerZoneIsAnExplicitConfiguredZone() {
        assertThat(ProgressRules.centerZone()).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=ProgressRulesTest`
Expected: FAIL at compile — `cannot find symbol: class ProgressRules`.

- [ ] **Step 3: Write the pure rule**

Create `backend/src/main/java/com/eduerp/modules/academic/internal/rules/package-info.java`:

```java
package com.eduerp.modules.academic.internal.rules;
```

Create `backend/src/main/java/com/eduerp/modules/academic/internal/rules/ProgressRules.java`:

```java
package com.eduerp.modules.academic.internal.rules;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Pure, 100% unit-test được bằng new, không I/O - mirror {@code BillingRules}/{@code PayrollRules}.
 * Spec mục 5 yêu cầu đúng điều này: "Phép đếm "số buổi đã học" là một hàm THUẦN (không I/O), nhận
 * vào danh sách {@code ClassSchedule} + hai mốc thời gian, trả về số nguyên".
 *
 * <p>Mốc kết thúc được TRUYỀN VÀO chứ không đọc {@code Instant.now()} bên trong: nơi gọi quyết định
 * đó là {@code Enrollment.withdrawnAt} (đã rút - đóng băng) hay thời điểm hiện tại. Đọc đồng hồ ở
 * đây là biến hàm thành không thuần và làm Review Focus #2 không test được.
 */
public final class ProgressRules {

    /**
     * Ánh xạ tường minh thay vì dựa vào thứ tự khai báo trùng nhau của hai enum: {@code ordinal()}
     * trùng nhau hôm nay là ngẫu nhiên, thêm một giá trị vào giữa một bên là lệch toàn bộ phép đếm
     * mà không có lỗi biên dịch nào.
     */
    private static final Map<DayOfWeek, CoursesConstants.DayOfWeek> DAY_OF_WEEK_BY_JAVA_TIME = Map.of(
            DayOfWeek.MONDAY, CoursesConstants.DayOfWeek.MON,
            DayOfWeek.TUESDAY, CoursesConstants.DayOfWeek.TUE,
            DayOfWeek.WEDNESDAY, CoursesConstants.DayOfWeek.WED,
            DayOfWeek.THURSDAY, CoursesConstants.DayOfWeek.THU,
            DayOfWeek.FRIDAY, CoursesConstants.DayOfWeek.FRI,
            DayOfWeek.SATURDAY, CoursesConstants.DayOfWeek.SAT,
            DayOfWeek.SUNDAY, CoursesConstants.DayOfWeek.SUN);

    private static final BigDecimal ONE_HUNDRED =
            BigDecimal.valueOf(AcademicConstants.Limits.PROGRESS_PERCENT_CAP);

    private ProgressRules() {
    }

    public static ZoneId centerZone() {
        return ZoneId.of(AcademicConstants.Schedules.CENTER_ZONE_ID);
    }

    /**
     * Số lần lịch tuần của lớp rơi vào khoảng {@code [from, to]}, tính tại GIỜ BẮT ĐẦU của buổi học
     * (spec mục 5: "đếm số lần lịch tuần của Class (ClassSchedule: dayOfWeek + startTime) rơi vào
     * khoảng [mốc_bắt_đầu, mốc_kết_thúc]"). Lịch rỗng hoặc {@code to} trước {@code from} đều cho 0,
     * không ném (Review Focus #12).
     */
    public static int countElapsedSessions(List<WeeklyScheduleSlot> schedule, Instant from, Instant to,
            ZoneId zone) {
        if (schedule.isEmpty() || to.isBefore(from)) {
            return 0;
        }
        var firstDay = LocalDate.ofInstant(from, zone);
        var lastDay = LocalDate.ofInstant(to, zone);
        return (int) firstDay.datesUntil(lastDay.plusDays(1))
                .flatMap(day -> schedule.stream()
                        .filter(slot -> slot.dayOfWeek() == DAY_OF_WEEK_BY_JAVA_TIME.get(day.getDayOfWeek()))
                        .map(slot -> day.atTime(slot.startTime()).atZone(zone).toInstant()))
                .filter(sessionStart -> !sessionStart.isBefore(from) && !sessionStart.isAfter(to))
                .count();
    }

    /**
     * {@code round(min(đã học, chuẩn) / chuẩn * 100)} (spec mục 5). Trả {@code null} khi
     * {@code standardSessionCount} chưa cấu hình (null, 0 hoặc âm): "không tính được, trả null kèm
     * lý do "chưa cấu hình tổng số buổi chuẩn" — không suy đoán" (spec mục 5, Review Focus #8).
     */
    public static Integer progressPercent(int elapsedSessions, Integer standardSessionCount) {
        if (standardSessionCount == null || standardSessionCount <= 0) {
            return null;
        }
        var capped = Math.min(elapsedSessions, standardSessionCount);
        return BigDecimal.valueOf(capped)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(standardSessionCount), 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=ProgressRulesTest`
Expected: PASS — 12 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/academic/internal/rules/ \
        backend/src/test/java/com/eduerp/modules/academic/internal/rules/ProgressRulesTest.java
git commit -m "feat(academic): add pure progress rules counting elapsed weekly sessions"
```

---

## Task 9: `GradeRules` — điểm tổng kết theo trọng số

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/academic/internal/rules/GradeRules.java`
- Test: `backend/src/test/java/com/eduerp/modules/academic/internal/rules/GradeRulesTest.java`

**Interfaces:**
- Consumes: `com.eduerp.modules.courses.dto.GradeColumnResponse(UUID id, String name, BigDecimal weightPercent, BigDecimal maxScore, int displayOrder)` (Task 3), `AcademicConstants.Scoring.*`.
- Produces: `GradeRules.finalScore(List<GradeColumnResponse> columns, Map<UUID, BigDecimal> scoresByColumnId): BigDecimal` (null = chưa đủ điểm); `GradeRules.isScoreWithinRange(BigDecimal score, BigDecimal maxScore): boolean`.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/academic/internal/rules/GradeRulesTest.java`:

```java
package com.eduerp.modules.academic.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.dto.GradeColumnResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure, không I/O. Thang điểm tổng kết là 0-100: Σ(score / maxScore * weightPercent) với Σ weight
 * = 100 (spec mục 6). */
class GradeRulesTest {

    private final UUID midtermId = UUID.randomUUID();
    private final UUID finalId = UUID.randomUUID();

    private final GradeColumnResponse midterm = new GradeColumnResponse(midtermId, "Giữa kỳ",
            new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
    private final GradeColumnResponse finalExam = new GradeColumnResponse(finalId, "Cuối kỳ",
            new BigDecimal("60.00"), new BigDecimal("10.00"), 1);

    private List<GradeColumnResponse> bothColumns() {
        return List.of(midterm, finalExam);
    }

    /** Ví dụ của spec mục 2: Giữa kỳ 40% + Cuối kỳ 60%, thang 10. 8/10*40 + 9/10*60 = 32 + 54 = 86. */
    @Test
    void sumsEveryColumnWeightedByItsOwnMaxScore() {
        var score = GradeRules.finalScore(bothColumns(), Map.of(
                midtermId, new BigDecimal("8.00"), finalId, new BigDecimal("9.00")));

        assertThat(score).isEqualByComparingTo(new BigDecimal("86.00"));
    }

    /** Hai cột có thang KHÁC NHAU vẫn phải ra đúng: 4/5*40 + 9/10*60 = 32 + 54 = 86. */
    @Test
    void handlesColumnsWithDifferentMaxScores() {
        var midtermOutOfFive = new GradeColumnResponse(midtermId, "Giữa kỳ",
                new BigDecimal("40.00"), new BigDecimal("5.00"), 0);

        var score = GradeRules.finalScore(List.of(midtermOutOfFive, finalExam), Map.of(
                midtermId, new BigDecimal("4.00"), finalId, new BigDecimal("9.00")));

        assertThat(score).isEqualByComparingTo(new BigDecimal("86.00"));
    }

    /**
     * Review Focus #3: thiếu điểm ở MỘT cột → null, KHÔNG tính trung bình trên phần đã có. Nếu hàm
     * này trả 32.00 ở đây thì người xem sẽ tưởng học viên được 32/100 trong khi thực tế chỉ là chưa
     * nhập điểm cuối kỳ (spec mục 6: "tránh một con số gây hiểu lầm").
     */
    @Test
    void returnsNullWhenAnyConfiguredColumnHasNoScore() {
        var score = GradeRules.finalScore(bothColumns(), Map.of(midtermId, new BigDecimal("8.00")));

        assertThat(score).isNull();
    }

    @Test
    void returnsNullWhenNoColumnHasAScore() {
        assertThat(GradeRules.finalScore(bothColumns(), Map.of())).isNull();
    }

    /** Review Focus #9: khóa chưa cấu hình cột điểm nào → không có điểm tổng kết để nói, trả null
     * thay vì 0 (0 sẽ bị đọc là "trượt"). */
    @Test
    void returnsNullWhenTheCourseHasNoGradeColumnsConfigured() {
        assertThat(GradeRules.finalScore(List.of(), Map.of())).isNull();
    }

    /** Điểm của một cột đã bị xoá khỏi cấu hình không được kéo điểm tổng kết lên: chỉ các cột ĐANG
     * cấu hình được tính, cột lạ trong map bị bỏ qua. */
    @Test
    void ignoresScoresOfColumnsThatAreNoLongerConfigured() {
        var removedColumnId = UUID.randomUUID();

        var score = GradeRules.finalScore(bothColumns(), Map.of(
                midtermId, new BigDecimal("8.00"), finalId, new BigDecimal("9.00"),
                removedColumnId, new BigDecimal("10.00")));

        assertThat(score).isEqualByComparingTo(new BigDecimal("86.00"));
    }

    @Test
    void roundsToTheScoreScaleOfTheDatabaseColumn() {
        var threeColumnsId = UUID.randomUUID();
        var oneThird = new GradeColumnResponse(threeColumnsId, "Đợt 1",
                new BigDecimal("33.33"), new BigDecimal("3.00"), 0);

        var score = GradeRules.finalScore(List.of(oneThird), Map.of(
                threeColumnsId, new BigDecimal("1.00")));

        assertThat(score.scale()).isEqualTo(2);
        assertThat(score).isEqualByComparingTo(new BigDecimal("11.11"));
    }

    /** Review Focus #10: biên của khoảng hợp lệ cho một cột, dùng lại ở RecordGrade. */
    @Test
    void acceptsScoresOnBothEndsOfTheRangeAndRejectsOutsideIt() {
        var maxScore = new BigDecimal("10.00");

        assertThat(GradeRules.isScoreWithinRange(new BigDecimal("0.00"), maxScore)).isTrue();
        assertThat(GradeRules.isScoreWithinRange(new BigDecimal("10.00"), maxScore)).isTrue();
        assertThat(GradeRules.isScoreWithinRange(new BigDecimal("5.50"), maxScore)).isTrue();
        assertThat(GradeRules.isScoreWithinRange(new BigDecimal("-0.01"), maxScore)).isFalse();
        assertThat(GradeRules.isScoreWithinRange(new BigDecimal("10.01"), maxScore)).isFalse();
    }

    /** Thang của RIÊNG cột quyết định: 9.5 hợp lệ ở thang 10 và sai ở thang 5. */
    @Test
    void judgesTheRangeAgainstTheMaxScoreOfThatColumnOnly() {
        assertThat(GradeRules.isScoreWithinRange(new BigDecimal("9.50"), new BigDecimal("10.00"))).isTrue();
        assertThat(GradeRules.isScoreWithinRange(new BigDecimal("9.50"), new BigDecimal("5.00"))).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=GradeRulesTest`
Expected: FAIL at compile — `cannot find symbol: class GradeRules`.

- [ ] **Step 3: Write the pure rule**

Create `backend/src/main/java/com/eduerp/modules/academic/internal/rules/GradeRules.java`:

```java
package com.eduerp.modules.academic.internal.rules;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure, 100% unit-test được bằng new, không I/O - mirror {@code BillingRules}. */
public final class GradeRules {

    private GradeRules() {
    }

    /**
     * Điểm tổng kết theo spec mục 6: {@code Σ (score / maxScore * weightPercent)} qua mọi cột, CHỈ
     * KHI đủ điểm ở MỌI cột đã cấu hình cho khóa đó. Thang kết quả là 0-100 vì Σ {@code weightPercent}
     * bắt buộc bằng 100 ({@code GradeColumnRules} chặn ở phía courses).
     *
     * <p>Trả {@code null} khi thiếu điểm ở bất kỳ cột nào, hoặc khi khóa chưa cấu hình cột nào.
     * KHÔNG tính trung bình trên phần đã có: "tránh một con số gây hiểu lầm" (spec mục 6, Review
     * Focus #3). Điểm của cột đã bị xoá khỏi cấu hình bị bỏ qua - chỉ các cột ĐANG cấu hình được tính.
     */
    public static BigDecimal finalScore(List<GradeColumnResponse> columns,
            Map<UUID, BigDecimal> scoresByColumnId) {
        if (columns.isEmpty() || !hasEveryColumnScored(columns, scoresByColumnId)) {
            return null;
        }
        return columns.stream()
                .map(column -> weightedPart(column, scoresByColumnId.get(column.id())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(AcademicConstants.Scoring.SCORE_SCALE, RoundingMode.HALF_UP);
    }

    /** Khoảng hợp lệ của một điểm là {@code [0, maxScore]} của ĐÚNG cột đó (Review Focus #10). */
    public static boolean isScoreWithinRange(BigDecimal score, BigDecimal maxScore) {
        return score.compareTo(AcademicConstants.Scoring.MIN_SCORE) >= 0
                && score.compareTo(maxScore) <= 0;
    }

    private static boolean hasEveryColumnScored(List<GradeColumnResponse> columns,
            Map<UUID, BigDecimal> scoresByColumnId) {
        return columns.stream().allMatch(column -> scoresByColumnId.get(column.id()) != null);
    }

    private static BigDecimal weightedPart(GradeColumnResponse column, BigDecimal score) {
        return score.divide(column.maxScore(), AcademicConstants.Scoring.WEIGHTED_MATH_SCALE,
                        RoundingMode.HALF_UP)
                .multiply(column.weightPercent());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GradeRulesTest`
Expected: PASS — 10 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/academic/internal/rules/GradeRules.java \
        backend/src/test/java/com/eduerp/modules/academic/internal/rules/GradeRulesTest.java
git commit -m "feat(academic): add pure grade rules computing the weighted final score"
```

---

## Task 10: `GradeEntry` — entity điểm + migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V24__create_grade_entries.sql`
- Create: `backend/src/main/java/com/eduerp/modules/academic/internal/model/GradeEntry.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/internal/model/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/internal/repository/GradeEntryRepository.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/internal/repository/package-info.java`
- Test: `backend/src/test/java/com/eduerp/modules/academic/internal/repository/GradeEntryRepositoryIT.java`

**Interfaces:**
- Produces: `GradeEntry(UUID enrollmentId, UUID courseGradeColumnId, BigDecimal score, UUID enteredByAccountId)` + `getId()/getEnrollmentId()/getCourseGradeColumnId()/getScore()/getEnteredByAccountId()/getEnteredAt()/getUpdatedAt()` + `changeScore(BigDecimal score, UUID enteredByAccountId)`; `GradeEntryRepository.findByEnrollmentIdAndCourseGradeColumnId(UUID, UUID): Optional<GradeEntry>`, `.findAllByEnrollmentIdIn(Collection<UUID>): List<GradeEntry>`.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/academic/internal/repository/GradeEntryRepositoryIT.java`:

```java
package com.eduerp.modules.academic.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.modules.academic.internal.model.GradeEntry;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Mọi cột UUID của grade_entries đều có FK thật (enrollments, course_grade_columns, accounts) -
 * phải dựng dữ liệu thật qua repository của từng module, không dùng UUID ngẫu nhiên (mirror
 * {@code EnrollmentRepositoryIT}). */
@Testcontainers
@DataJpaTest
class GradeEntryRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    GradeEntryRepository gradeEntries;

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

    private UUID teacherId;
    private UUID midtermColumnId;
    private UUID finalColumnId;
    private UUID enrollmentId;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @BeforeEach
    void seedReferences() {
        var branchId = branches.save(new Branch("GE-" + shortId(), "Chi nhánh điểm", null)).getId();
        teacherId = accounts.save(new Account("ge-gv-" + shortId() + "@eduerp.local", "hash", "GV", null))
                .getId();
        var course = new Course("GE-C-" + shortId(), "Khoá có điểm", null, 24, null);
        course.addGradeColumn("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
        course.addGradeColumn("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1);
        var savedCourse = courses.saveAndFlush(course);
        midtermColumnId = savedCourse.getGradeColumns().get(0).getId();
        finalColumnId = savedCourse.getGradeColumns().get(1).getId();
        var cls = classes.save(new Class(savedCourse, "GE-K-" + shortId(), branchId, teacherId, 20));
        var studentAccount = accounts.save(
                new Account("ge-hv-" + shortId() + "@eduerp.local", "hash", "HV", null));
        var studentProfileId = profiles.save(
                new StudentProfile(studentAccount.getId(), null, null, null)).getId();
        enrollmentId = enrollments.saveAndFlush(new Enrollment(studentProfileId, cls.getId(),
                savedCourse.getId(), branchId, teacherId)).getId();
    }

    @Test
    void savesAGradeEntryWithItsAuditTrail() {
        var saved = gradeEntries.saveAndFlush(
                new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("8.50"), teacherId));

        var found = gradeEntries.findById(saved.getId()).orElseThrow();
        assertThat(found.getScore()).isEqualByComparingTo(new BigDecimal("8.50"));
        assertThat(found.getEnrollmentId()).isEqualTo(enrollmentId);
        assertThat(found.getCourseGradeColumnId()).isEqualTo(midtermColumnId);
        assertThat(found.getEnteredByAccountId()).isEqualTo(teacherId);
        assertThat(found.getEnteredAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    /** Spec mục 6: UNIQUE (enrollment_id, course_grade_column_id) - một điểm cho một cột của một
     * lượt ghi danh. Nhập lại là SỬA, không phải thêm dòng thứ hai. */
    @Test
    void rejectsASecondEntryForTheSameEnrolmentAndColumnAtDatabaseLevel() {
        gradeEntries.saveAndFlush(new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("8.50"),
                teacherId));

        assertThatThrownBy(() -> gradeEntries.saveAndFlush(
                new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("9.00"), teacherId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsTwoEntriesForTwoDifferentColumnsOfTheSameEnrolment() {
        gradeEntries.saveAndFlush(new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("8.50"),
                teacherId));
        gradeEntries.saveAndFlush(new GradeEntry(enrollmentId, finalColumnId, new BigDecimal("9.00"),
                teacherId));

        assertThat(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentId))).hasSize(2);
    }

    @Test
    void findsAnExistingEntryByEnrolmentAndColumnSoRecordingCanUpsert() {
        gradeEntries.saveAndFlush(new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("8.50"),
                teacherId));

        var found = gradeEntries.findByEnrollmentIdAndCourseGradeColumnId(enrollmentId, midtermColumnId);

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getScore()).isEqualByComparingTo(new BigDecimal("8.50"));
        assertThat(gradeEntries.findByEnrollmentIdAndCourseGradeColumnId(enrollmentId, finalColumnId))
                .isEmpty();
    }

    /** {@code changeScore} phải đẩy {@code updatedAt} về sau {@code enteredAt} - nhật ký "ai sửa
     * điểm lúc nào" là lý do duy nhất hai cột này tồn tại. */
    @Test
    void changeScoreUpdatesTheScoreAndTheUpdatedAtMark() {
        var saved = gradeEntries.saveAndFlush(
                new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("8.50"), teacherId));
        var enteredAt = saved.getEnteredAt();
        var otherTeacherId = accounts.save(
                new Account("ge-gv2-" + shortId() + "@eduerp.local", "hash", "GV2", null)).getId();

        saved.changeScore(new BigDecimal("9.25"), otherTeacherId);
        gradeEntries.saveAndFlush(saved);

        var reloaded = gradeEntries.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getScore()).isEqualByComparingTo(new BigDecimal("9.25"));
        assertThat(reloaded.getEnteredByAccountId()).isEqualTo(otherTeacherId);
        assertThat(reloaded.getEnteredAt()).isEqualTo(enteredAt);
        assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(enteredAt);
    }

    /** Nạp theo lô cho cả lớp: bảng điểm 30 học viên không được bắn 30 câu SELECT (rule #3). */
    @Test
    void loadsEntriesForManyEnrolmentsInOneCall() {
        gradeEntries.saveAndFlush(new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("8.50"),
                teacherId));

        assertThat(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentId, UUID.randomUUID())))
                .hasSize(1);
        assertThat(gradeEntries.findAllByEnrollmentIdIn(List.of())).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=GradeEntryRepositoryIT`
Expected: FAIL at compile — `cannot find symbol: class GradeEntry`, `cannot find symbol: class GradeEntryRepository`.

- [ ] **Step 3: Write the migration**

Create `backend/src/main/resources/db/migration/V24__create_grade_entries.sql`:

```sql
-- Điểm thật thuộc modules.academic (spec mục 3): courses giữ CẤU HÌNH cột điểm, academic giữ ĐIỂM.
-- enrollment_id/course_grade_column_id/entered_by_account_id có FK mức DB để toàn vẹn dữ liệu,
-- nhưng KHÔNG có @ManyToOne tương ứng trong Java (rule #3: không quan hệ JPA xuyên module) -
-- mirror enrollments của V17.
CREATE TABLE grade_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES enrollments(id),
    course_grade_column_id UUID NOT NULL REFERENCES course_grade_columns(id),
    -- NUMERIC(5,2) khớp course_grade_columns.max_score: điểm phải cùng thang với cột nó thuộc về.
    -- Khoảng hợp lệ [0, max_score] là luật theo từng cột nên không biểu diễn được bằng CHECK ở đây,
    -- chặn ở GradeRules.isScoreWithinRange (Task 9) và RecordGrade (Task 12).
    score NUMERIC(5,2) NOT NULL,
    entered_by_account_id UUID NOT NULL REFERENCES accounts(id),
    entered_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    -- Spec mục 6: một điểm cho một cột của một lượt ghi danh. UNIQUE ĐẦY ĐỦ (không PARTIAL như V17):
    -- không có trạng thái nào khiến một dòng điểm "ngừng tính", nhập lại là SỬA dòng cũ. Đây cũng là
    -- lớp chặn cuối cho race hai request cùng nhập điểm một cột.
    UNIQUE (enrollment_id, course_grade_column_id)
);

CREATE INDEX idx_grade_entries_enrollment ON grade_entries (enrollment_id);
CREATE INDEX idx_grade_entries_column ON grade_entries (course_grade_column_id);
```

- [ ] **Step 4: Write the entity and repository**

Create `backend/src/main/java/com/eduerp/modules/academic/internal/model/package-info.java`:

```java
package com.eduerp.modules.academic.internal.model;
```

Create `backend/src/main/java/com/eduerp/modules/academic/internal/model/GradeEntry.java`:

```java
package com.eduerp.modules.academic.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * Điểm của một học viên ở một cột điểm của khóa. {@code enrollmentId}/{@code courseGradeColumnId}/
 * {@code enteredByAccountId} là UUID trần - {@code enrollments}/{@code courses}/{@code identity}
 * thuộc module khác nên không có {@code @ManyToOne} (rule #3).
 *
 * <p>{@code enteredAt} bất biến, {@code updatedAt} đổi mỗi lần sửa: nhật ký "ai nhập/sửa điểm lúc
 * nào" là lý do duy nhất hai cột này tồn tại.
 */
@Entity
@Table(name = "grade_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradeEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(name = "course_grade_column_id", nullable = false, updatable = false)
    private UUID courseGradeColumnId;

    @Column(nullable = false)
    private BigDecimal score;

    @Column(name = "entered_by_account_id", nullable = false)
    private UUID enteredByAccountId;

    @Column(name = "entered_at", nullable = false, updatable = false)
    private Instant enteredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GradeEntry(UUID enrollmentId, UUID courseGradeColumnId, BigDecimal score,
            UUID enteredByAccountId) {
        this.enrollmentId = enrollmentId;
        this.courseGradeColumnId = courseGradeColumnId;
        this.score = score;
        this.enteredByAccountId = enteredByAccountId;
        this.enteredAt = Instant.now();
        this.updatedAt = this.enteredAt;
    }

    /** Nhập lại điểm một cột là SỬA dòng cũ (UNIQUE của V24), nên {@code enteredAt} giữ nguyên và
     * {@code enteredByAccountId} chuyển sang người sửa gần nhất. */
    public void changeScore(BigDecimal score, UUID enteredByAccountId) {
        this.score = score;
        this.enteredByAccountId = enteredByAccountId;
        this.updatedAt = Instant.now();
    }
}
```

Create `backend/src/main/java/com/eduerp/modules/academic/internal/repository/package-info.java`:

```java
package com.eduerp.modules.academic.internal.repository;
```

Create `backend/src/main/java/com/eduerp/modules/academic/internal/repository/GradeEntryRepository.java`:

```java
package com.eduerp.modules.academic.internal.repository;

import com.eduerp.modules.academic.internal.model.GradeEntry;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeEntryRepository extends JpaRepository<GradeEntry, UUID> {

    /** {@code RecordGrade} upsert theo cặp này - UNIQUE của V24 bảo đảm tối đa một dòng. */
    Optional<GradeEntry> findByEnrollmentIdAndCourseGradeColumnId(UUID enrollmentId,
            UUID courseGradeColumnId);

    /** Một câu IN cho CẢ lớp: bảng điểm 30 học viên không được bắn 30 câu SELECT (rule #3). */
    List<GradeEntry> findAllByEnrollmentIdIn(Collection<UUID> enrollmentIds);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GradeEntryRepositoryIT`
Expected: PASS — 6 tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V24__create_grade_entries.sql \
        backend/src/main/java/com/eduerp/modules/academic/internal/model/ \
        backend/src/main/java/com/eduerp/modules/academic/internal/repository/ \
        backend/src/test/java/com/eduerp/modules/academic/internal/repository/GradeEntryRepositoryIT.java
git commit -m "feat(academic): add the grade entry entity, migration and repository"
```

---

## Task 11: `ClassGradeAccessGuard` — kiểm "đúng lớp" dùng chung cho cả năm usecase

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/academic/internal/rules/GradeAccessRules.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/internal/access/ClassGradeAccessGuard.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/internal/access/package-info.java`
- Test: `backend/src/test/java/com/eduerp/modules/academic/internal/rules/GradeAccessRulesTest.java`
- Test: `backend/src/test/java/com/eduerp/modules/academic/internal/access/ClassGradeAccessGuardTest.java`

**Interfaces:**
- Consumes: `AccessManagement.effectivePermissions(UUID accountId): Set<EffectivePermission>`, `EffectivePermission(String resource, String action, AccessConstants.PermissionScope scope)`, `AccessConstants.Resources.GRADE` (Task 15 — nhưng hằng này phải có TRƯỚC, nên Step 3 của task này thêm nó), `AccessConstants.Actions.READ/UPDATE`, `CoursesManagement.getClassInfo(UUID): Optional<ClassInfoResponse>` (Task 4), `academic.ClassNotFoundException`, `academic.NotAssignedToClassException`.
- Produces: `GradeAccessRules.allowsEveryClass(Set<EffectivePermission> permissions, String action): boolean`; `ClassGradeAccessGuard.requireAccessToClass(UUID actorAccountId, UUID classId, String action): CoursesManagement.ClassInfoResponse`.

> ĐÂY là mảnh dùng chung mà cả năm usecase (`RecordGrade`, `GetClassGradebook`, `GetClassProgress`,
> `ExportClassGradebook`, `ExportClassProgress`) gọi — không copy-paste logic này ở đâu khác. Guard
> TRẢ VỀ `ClassInfoResponse` chứ không trả `void`: nơi gọi cần chính bản ghi đó (lịch học, số buổi
> chuẩn, `courseId`) nên gọi `getClassInfo` lần thứ hai là vừa chậm vừa mở ra khả năng hai lần đọc
> lệch nhau.

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/java/com/eduerp/modules/academic/internal/rules/GradeAccessRulesTest.java`:

```java
package com.eduerp.modules.academic.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.EffectivePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Pure, không I/O: tách phần "scope này có phải toàn quyền tổ chức không" khỏi phần gọi facade để
 * test được không cần mock. */
class GradeAccessRulesTest {

    private static EffectivePermission grade(String action, AccessConstants.PermissionScope scope) {
        return new EffectivePermission(AccessConstants.Resources.GRADE, action, scope);
    }

    /** Spec mục 6 bước 3: scope ORGANIZATION → bỏ qua hoàn toàn bước kiểm phân công (Review Focus #5). */
    @Test
    void anOrganizationScopedGradePermissionAllowsEveryClass() {
        var permissions = Set.of(grade(AccessConstants.Actions.UPDATE,
                AccessConstants.PermissionScope.ORGANIZATION));

        assertThat(GradeAccessRules.allowsEveryClass(permissions, AccessConstants.Actions.UPDATE)).isTrue();
    }

    /** Spec mục 6 bước 4: PERSONAL và BRANCH đều phải đi tiếp qua bước so teacherId. */
    @Test
    void personalAndBranchScopesDoNotAllowEveryClass() {
        assertThat(GradeAccessRules.allowsEveryClass(
                Set.of(grade(AccessConstants.Actions.UPDATE, AccessConstants.PermissionScope.PERSONAL)),
                AccessConstants.Actions.UPDATE)).isFalse();
        assertThat(GradeAccessRules.allowsEveryClass(
                Set.of(grade(AccessConstants.Actions.UPDATE, AccessConstants.PermissionScope.BRANCH)),
                AccessConstants.Actions.UPDATE)).isFalse();
    }

    /** Quyền ở resource/action KHÁC không được dùng để mở cửa: READ:ORGANIZATION không cho phép
     * UPDATE trên mọi lớp. */
    @Test
    void doesNotMixUpActions() {
        var readOnlyAdmin = Set.of(grade(AccessConstants.Actions.READ,
                AccessConstants.PermissionScope.ORGANIZATION));

        assertThat(GradeAccessRules.allowsEveryClass(readOnlyAdmin, AccessConstants.Actions.READ)).isTrue();
        assertThat(GradeAccessRules.allowsEveryClass(readOnlyAdmin, AccessConstants.Actions.UPDATE)).isFalse();
    }

    @Test
    void doesNotMixUpResources() {
        var otherResource = Set.of(new EffectivePermission(AccessConstants.Resources.ENROLLMENT,
                AccessConstants.Actions.UPDATE, AccessConstants.PermissionScope.ORGANIZATION));

        assertThat(GradeAccessRules.allowsEveryClass(otherResource, AccessConstants.Actions.UPDATE)).isFalse();
    }

    @Test
    void anEmptyPermissionSetAllowsNothing() {
        assertThat(GradeAccessRules.allowsEveryClass(Set.of(), AccessConstants.Actions.UPDATE)).isFalse();
    }
}
```

Create `backend/src/test/java/com/eduerp/modules/academic/internal/access/ClassGradeAccessGuardTest.java`:

```java
package com.eduerp.modules.academic.internal.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.academic.ClassNotFoundException;
import com.eduerp.modules.academic.NotAssignedToClassException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.access.EffectivePermission;
import com.eduerp.modules.courses.CoursesManagement;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Review Focus #4 + #5. Cơ chế này là mới trong dự án: RBAC hiện tại chỉ có {@code @PreAuthorize}
 * thô ("có quyền ở scope X hay không"), không biết "X của ai" - nên phần "của ai" nằm ở đây, trong
 * usecase layer, chứ không phát minh một loại scope RBAC mới (spec mục 6 + mục 12).
 */
class ClassGradeAccessGuardTest {

    private final AccessManagement access = mock(AccessManagement.class);
    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final ClassGradeAccessGuard guard = new ClassGradeAccessGuard(access, courses);

    private final UUID classId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID assignedTeacherId = UUID.randomUUID();

    private void stubClassTaughtByAssignedTeacher() {
        when(courses.getClassInfo(classId)).thenReturn(Optional.of(
                new CoursesManagement.ClassInfoResponse(classId, courseId, branchId, assignedTeacherId,
                        20, true, 24, List.of())));
    }

    private void stubCallerPermissions(UUID accountId, AccessConstants.PermissionScope scope) {
        when(access.effectivePermissions(accountId)).thenReturn(Set.of(
                new EffectivePermission(AccessConstants.Resources.GRADE, AccessConstants.Actions.UPDATE, scope),
                new EffectivePermission(AccessConstants.Resources.GRADE, AccessConstants.Actions.READ, scope)));
    }

    /** Review Focus #4, chiều hợp lệ: đúng giáo viên của lớp thao tác được, và nhận lại chính bản ghi
     * lớp để không phải gọi facade lần hai. */
    @Test
    void allowsTheTeacherAssignedToTheClassAndReturnsTheClassInfo() {
        stubClassTaughtByAssignedTeacher();
        stubCallerPermissions(assignedTeacherId, AccessConstants.PermissionScope.PERSONAL);

        var classInfo = guard.requireAccessToClass(assignedTeacherId, classId,
                AccessConstants.Actions.UPDATE);

        assertThat(classInfo.classId()).isEqualTo(classId);
        assertThat(classInfo.courseId()).isEqualTo(courseId);
        assertThat(classInfo.teacherId()).isEqualTo(assignedTeacherId);
        verify(courses).getClassInfo(classId);
    }

    /**
     * Review Focus #4, chiều bị chặn: giáo viên khác CÓ quyền {@code GRADE:UPDATE:PERSONAL} nói
     * chung nhưng không dạy lớp này → 403 {@code NotAssignedToClassException}. Đây là lỗ hổng mà
     * {@code @PreAuthorize} một mình không bao giờ bắt được.
     */
    @Test
    void refusesATeacherWhoHoldsThePermissionButIsNotAssignedToThisClass() {
        var otherTeacherId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        stubCallerPermissions(otherTeacherId, AccessConstants.PermissionScope.PERSONAL);

        assertThatThrownBy(() -> guard.requireAccessToClass(otherTeacherId, classId,
                AccessConstants.Actions.UPDATE))
                .isInstanceOf(NotAssignedToClassException.class)
                .hasMessageContaining(classId.toString());
    }

    /** Spec mục 6 bước 4 nói rõ BRANCH cũng phải qua bước so teacherId, không chỉ PERSONAL. */
    @Test
    void refusesABranchScopedCallerWhoIsNotTheAssignedTeacher() {
        var branchManagerId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        stubCallerPermissions(branchManagerId, AccessConstants.PermissionScope.BRANCH);

        assertThatThrownBy(() -> guard.requireAccessToClass(branchManagerId, classId,
                AccessConstants.Actions.UPDATE))
                .isInstanceOf(NotAssignedToClassException.class);
    }

    /** Review Focus #5: admin scope ORGANIZATION thao tác được trên lớp mình không dạy. */
    @Test
    void skipsTheAssignmentCheckForAnOrganizationScopedCaller() {
        var adminId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        stubCallerPermissions(adminId, AccessConstants.PermissionScope.ORGANIZATION);

        var classInfo = guard.requireAccessToClass(adminId, classId, AccessConstants.Actions.UPDATE);

        assertThat(classInfo.teacherId())
                .as("admin vẫn nhận đúng bản ghi lớp, chỉ là không bị so với chính mình")
                .isEqualTo(assignedTeacherId);
    }

    /** Lớp không tồn tại là 404, kiểm TRƯỚC mọi chuyện về quyền - không gọi tới access làm gì. */
    @Test
    void reportsAnUnknownClassAsNotFoundWithoutAskingForPermissions() {
        when(courses.getClassInfo(classId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireAccessToClass(UUID.randomUUID(), classId,
                AccessConstants.Actions.READ))
                .isInstanceOf(ClassNotFoundException.class);
        verify(access, never()).effectivePermissions(org.mockito.ArgumentMatchers.any());
    }

    /** Caller không có quyền GRADE nào (cấu hình RBAC lệch, hoặc quyền bị thu hồi giữa phiên) vẫn
     * phải là 403 có nghĩa, không phải NPE thành 500. */
    @Test
    void refusesACallerWithoutAnyGradePermission() {
        var strangerId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        when(access.effectivePermissions(strangerId)).thenReturn(Set.of());

        assertThatThrownBy(() -> guard.requireAccessToClass(strangerId, classId,
                AccessConstants.Actions.READ))
                .isInstanceOf(NotAssignedToClassException.class);
    }

    /** Hai action tách biệt: quyền READ ở ORGANIZATION không mở cửa UPDATE trên lớp người khác. */
    @Test
    void checksTheActionItWasAskedAbout() {
        var readOnlyAdminId = UUID.randomUUID();
        stubClassTaughtByAssignedTeacher();
        when(access.effectivePermissions(readOnlyAdminId)).thenReturn(Set.of(
                new EffectivePermission(AccessConstants.Resources.GRADE, AccessConstants.Actions.READ,
                        AccessConstants.PermissionScope.ORGANIZATION)));

        assertThat(guard.requireAccessToClass(readOnlyAdminId, classId, AccessConstants.Actions.READ)
                .classId()).isEqualTo(classId);
        assertThatThrownBy(() -> guard.requireAccessToClass(readOnlyAdminId, classId,
                AccessConstants.Actions.UPDATE))
                .isInstanceOf(NotAssignedToClassException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest='GradeAccessRulesTest,ClassGradeAccessGuardTest'`
Expected: FAIL at compile — `cannot find symbol: variable GRADE` (chưa có `AccessConstants.Resources.GRADE`), `cannot find symbol: class GradeAccessRules`, `cannot find symbol: class ClassGradeAccessGuard`.

- [ ] **Step 3: Add `Resources.GRADE`**

In `backend/src/main/java/com/eduerp/modules/access/AccessConstants.java`, add one line to `Resources`, after `INVOICE`:

```java
        public static final String GRADE = "GRADE";
```

(Ba hằng `AccessRules.*_GRADE` và seed RBAC thuộc Task 15 — ở đây chỉ cần tên tài nguyên để guard so khớp.)

- [ ] **Step 4: Write the pure rule**

Create `backend/src/main/java/com/eduerp/modules/academic/internal/rules/GradeAccessRules.java`:

```java
package com.eduerp.modules.academic.internal.rules;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.EffectivePermission;
import java.util.Set;

/** Pure, không I/O - phần quyết định của bước 3 trong spec mục 6, tách khỏi phần gọi facade. */
public final class GradeAccessRules {

    private GradeAccessRules() {
    }

    /**
     * Caller có quyền {@code GRADE:<action>} ở scope {@code ORGANIZATION} hay không. Chỉ scope này
     * được bỏ qua bước so {@code teacherId} (spec mục 6 bước 3); {@code PERSONAL} và {@code BRANCH}
     * đều phải đi tiếp (spec mục 6 bước 4).
     */
    public static boolean allowsEveryClass(Set<EffectivePermission> permissions, String action) {
        return permissions.stream().anyMatch(permission ->
                AccessConstants.Resources.GRADE.equals(permission.resource())
                        && action.equals(permission.action())
                        && permission.scope() == AccessConstants.PermissionScope.ORGANIZATION);
    }
}
```

- [ ] **Step 5: Write the guard**

Create `backend/src/main/java/com/eduerp/modules/academic/internal/access/package-info.java`:

```java
package com.eduerp.modules.academic.internal.access;
```

Create `backend/src/main/java/com/eduerp/modules/academic/internal/access/ClassGradeAccessGuard.java`:

```java
package com.eduerp.modules.academic.internal.access;

import com.eduerp.modules.academic.ClassNotFoundException;
import com.eduerp.modules.academic.NotAssignedToClassException;
import com.eduerp.modules.academic.internal.rules.GradeAccessRules;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.CoursesManagement;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Nơi DUY NHẤT cài đặt luật "chỉ giáo viên được phân công dạy lớp đó (+ admin) mới thao tác được"
 * (spec mục 6). Cả năm usecase của module gọi method này - {@code RecordGrade},
 * {@code GetClassGradebook}, {@code GetClassProgress}, {@code ExportClassGradebook},
 * {@code ExportClassProgress} - để không có đường tắt nào bỏ qua kiểm tra (spec mục 7: "không có
 * đường tắt tải Excel bỏ qua kiểm tra quyền").
 *
 * <p>Vì sao luật này KHÔNG nằm trong {@code @PreAuthorize}: RBAC hiện tại chỉ trả lời "có quyền ở
 * scope X hay không", không biết "X của ai" (spec mục 6). Phát minh một loại scope mới sẽ phải sửa
 * {@code ScopedPermissionEvaluator} và toàn bộ bảng quyền; cách của spec là gác cửa thô bằng
 * {@code @PreAuthorize} rồi hỏi lại scope THẬT ở đây qua
 * {@link AccessManagement#effectivePermissions(UUID)}.
 */
@Component
public class ClassGradeAccessGuard {

    private final AccessManagement access;
    private final CoursesManagement courses;

    ClassGradeAccessGuard(AccessManagement access, CoursesManagement courses) {
        this.access = access;
        this.courses = courses;
    }

    /**
     * Xác nhận {@code actorAccountId} được phép làm {@code action} ({@code AccessConstants.Actions
     * .READ}/{@code .UPDATE}) với lớp {@code classId}, rồi TRẢ VỀ bản ghi lớp đã đọc.
     *
     * <p>Trả về {@code ClassInfoResponse} thay vì {@code void} là có chủ đích: nơi gọi cần chính bản
     * ghi đó (lịch học + số buổi chuẩn để tính tiến trình, {@code courseId} để lấy cột điểm), nên gọi
     * {@code getClassInfo} lần thứ hai là vừa thêm một câu truy vấn vừa mở ra khả năng hai lần đọc
     * lệch nhau giữa lúc kiểm quyền và lúc tính số.
     *
     * @throws ClassNotFoundException lớp không tồn tại (404) - kiểm trước mọi chuyện về quyền
     * @throws NotAssignedToClassException có quyền ở scope hẹp nhưng không phải giáo viên của lớp (403)
     */
    public CoursesManagement.ClassInfoResponse requireAccessToClass(UUID actorAccountId, UUID classId,
            String action) {
        var classInfo = courses.getClassInfo(classId)
                .orElseThrow(() -> new ClassNotFoundException(classId));
        if (GradeAccessRules.allowsEveryClass(access.effectivePermissions(actorAccountId), action)) {
            return classInfo;
        }
        if (!actorAccountId.equals(classInfo.teacherId())) {
            throw new NotAssignedToClassException(actorAccountId, classId);
        }
        return classInfo;
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest='GradeAccessRulesTest,ClassGradeAccessGuardTest'`
Expected: PASS — 5 + 7 = 12 tests, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/access/AccessConstants.java \
        backend/src/main/java/com/eduerp/modules/academic/internal/rules/GradeAccessRules.java \
        backend/src/main/java/com/eduerp/modules/academic/internal/access/ \
        backend/src/test/java/com/eduerp/modules/academic/internal/rules/GradeAccessRulesTest.java \
        backend/src/test/java/com/eduerp/modules/academic/internal/access/ClassGradeAccessGuardTest.java
git commit -m "feat(academic): enforce class assignment for grade access inside the usecase layer"
```

---

## Task 12: `academic.dto` + usecase `RecordGrade`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/academic/dto/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/dto/RecordGradeRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/dto/StudentGradesRow.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/dto/GradebookResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/dto/StudentProgressRow.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/dto/ClassProgressResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/dto/ExportedFileResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/usecase/RecordGrade.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/usecase/package-info.java`
- Test: `backend/src/test/java/com/eduerp/modules/academic/usecase/RecordGradeTest.java`

**Interfaces:**
- Consumes: `ClassGradeAccessGuard.requireAccessToClass` (Task 11), `EnrollmentManagement.listActiveEnrollmentsByClass` + `ClassRosterEntry` (Task 5), `CoursesManagement.getGradeColumns` (Task 4), `GradeRules.isScoreWithinRange` (Task 9), `GradeEntryRepository` + `GradeEntry` (Task 10), `AcademicEvents.GradeRecorded` (Task 7).
- Produces: `dto.RecordGradeRequest(UUID classId, UUID enrollmentId, UUID courseGradeColumnId, BigDecimal score)`; `dto.StudentGradesRow(UUID enrollmentId, UUID studentProfileId, String studentName, EnrollmentConstants.EnrollmentStatus status, Map<UUID, BigDecimal> scoresByColumnId, BigDecimal finalScore)`; `dto.GradebookResponse(UUID classId, UUID courseId, List<GradeColumnResponse> columns, List<StudentGradesRow> rows)`; `dto.StudentProgressRow(UUID enrollmentId, UUID studentProfileId, String studentName, EnrollmentConstants.EnrollmentStatus status, Instant enrolledAt, Instant withdrawnAt, int elapsedSessions, Integer standardSessionCount, Integer progressPercent)`; `dto.ClassProgressResponse(UUID classId, UUID courseId, Integer standardSessionCount, List<StudentProgressRow> rows)`; `dto.ExportedFileResponse(String fileName, byte[] content)`; `RecordGrade.execute(UUID actorAccountId, UUID actorBranchId, RecordGradeRequest request): void`.

> `RecordGradeRequest` mang `classId` vì `EnrollmentManagement.EnrollmentSummaryResponse` KHÔNG có
> `classId` (nó chỉ có `enrollmentId/studentProfileId/courseId/branchId/status`), và spec mục 3 chỉ
> cho thêm MỘT method đọc vào facade đó. Dùng chính method mới `listActiveEnrollmentsByClass(classId)`
> để đồng thời (a) xác nhận lượt ghi danh thuộc đúng lớp đang nhập điểm và (b) lấy `courseId` để tra
> cột điểm — một câu truy vấn, không phải sửa thêm record nào của module enrollment.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/academic/usecase/RecordGradeTest.java`:

```java
package com.eduerp.modules.academic.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.academic.AcademicEvents;
import com.eduerp.modules.academic.EnrollmentNotFoundException;
import com.eduerp.modules.academic.GradeColumnNotInCourseException;
import com.eduerp.modules.academic.InvalidScoreException;
import com.eduerp.modules.academic.NotAssignedToClassException;
import com.eduerp.modules.academic.dto.RecordGradeRequest;
import com.eduerp.modules.academic.internal.access.ClassGradeAccessGuard;
import com.eduerp.modules.academic.internal.model.GradeEntry;
import com.eduerp.modules.academic.internal.repository.GradeEntryRepository;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RecordGradeTest {

    private final GradeEntryRepository gradeEntries = mock(GradeEntryRepository.class);
    private final EnrollmentManagement enrollments = mock(EnrollmentManagement.class);
    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final ClassGradeAccessGuard guard = mock(ClassGradeAccessGuard.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RecordGrade useCase = new RecordGrade(gradeEntries, enrollments, courses, guard, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();
    private final UUID midtermColumnId = UUID.randomUUID();

    private RecordGradeRequest request(String score) {
        return new RecordGradeRequest(classId, enrollmentId, midtermColumnId, new BigDecimal(score));
    }

    private void stubAccessGranted() {
        when(guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.UPDATE))
                .thenReturn(new CoursesManagement.ClassInfoResponse(classId, courseId, actorBranchId,
                        actorAccountId, 20, true, 24, List.of()));
    }

    private void stubRosterContainsTheEnrolment() {
        when(enrollments.listActiveEnrollmentsByClass(classId)).thenReturn(List.of(
                new EnrollmentManagement.ClassRosterEntry(enrollmentId, UUID.randomUUID(), courseId,
                        EnrollmentConstants.EnrollmentStatus.ACTIVE, Instant.now(), null)));
    }

    private void stubColumnOutOf(String maxScore) {
        when(courses.getGradeColumns(courseId)).thenReturn(List.of(new GradeColumnResponse(
                midtermColumnId, "Giữa kỳ", new BigDecimal("40.00"), new BigDecimal(maxScore), 0)));
    }

    private void stubNoExistingEntry() {
        when(gradeEntries.findByEnrollmentIdAndCourseGradeColumnId(enrollmentId, midtermColumnId))
                .thenReturn(Optional.empty());
    }

    private void stubHappyPath() {
        stubAccessGranted();
        stubRosterContainsTheEnrolment();
        stubColumnOutOf("10.00");
        stubNoExistingEntry();
    }

    @Test
    void savesANewEntryAndPublishesGradeRecorded() {
        stubHappyPath();

        useCase.execute(actorAccountId, actorBranchId, request("8.50"));

        var saved = ArgumentCaptor.forClass(GradeEntry.class);
        verify(gradeEntries).save(saved.capture());
        assertThat(saved.getValue().getEnrollmentId()).isEqualTo(enrollmentId);
        assertThat(saved.getValue().getCourseGradeColumnId()).isEqualTo(midtermColumnId);
        assertThat(saved.getValue().getScore()).isEqualByComparingTo(new BigDecimal("8.50"));
        assertThat(saved.getValue().getEnteredByAccountId()).isEqualTo(actorAccountId);
        verify(events).publishEvent(any(AcademicEvents.GradeRecorded.class));
    }

    /** Spec mục 6: UNIQUE (enrollment, column) nên nhập lại là SỬA dòng cũ, không tạo dòng thứ hai. */
    @Test
    void updatesTheExistingEntryInsteadOfInsertingASecondRow() {
        stubAccessGranted();
        stubRosterContainsTheEnrolment();
        stubColumnOutOf("10.00");
        var existing = new GradeEntry(enrollmentId, midtermColumnId, new BigDecimal("7.00"),
                UUID.randomUUID());
        when(gradeEntries.findByEnrollmentIdAndCourseGradeColumnId(enrollmentId, midtermColumnId))
                .thenReturn(Optional.of(existing));

        useCase.execute(actorAccountId, actorBranchId, request("9.00"));

        assertThat(existing.getScore()).isEqualByComparingTo(new BigDecimal("9.00"));
        assertThat(existing.getEnteredByAccountId()).isEqualTo(actorAccountId);
        verify(gradeEntries, never()).save(any(GradeEntry.class));
        verify(events).publishEvent(any(AcademicEvents.GradeRecorded.class));
    }

    /** Review Focus #4: guard chặn TRƯỚC khi chạm vào dữ liệu nào - không được lưu nửa vời rồi mới
     * phát hiện sai quyền. */
    @Test
    void doesNotTouchAnythingWhenTheGuardRefusesTheCaller() {
        when(guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.UPDATE))
                .thenThrow(new NotAssignedToClassException(actorAccountId, classId));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("8.50")))
                .isInstanceOf(NotAssignedToClassException.class);
        verify(gradeEntries, never()).save(any(GradeEntry.class));
        verify(events, never()).publishEvent(any());
    }

    /** Lượt ghi danh không thuộc lớp đang nhập điểm: chặn, không âm thầm ghi điểm cho lớp khác. */
    @Test
    void rejectsAnEnrolmentThatDoesNotBelongToThisClass() {
        stubAccessGranted();
        when(enrollments.listActiveEnrollmentsByClass(classId)).thenReturn(List.of(
                new EnrollmentManagement.ClassRosterEntry(UUID.randomUUID(), UUID.randomUUID(), courseId,
                        EnrollmentConstants.EnrollmentStatus.ACTIVE, Instant.now(), null)));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("8.50")))
                .isInstanceOf(EnrollmentNotFoundException.class);
        verify(gradeEntries, never()).save(any(GradeEntry.class));
    }

    /** Review Focus #11: cột điểm thuộc khóa khác (hoặc không tồn tại) bị chặn - một điểm gắn vào cột
     * ngoài khóa sẽ không bao giờ được tính vào điểm tổng kết của ai. */
    @Test
    void rejectsAColumnBelongingToAnotherCourse() {
        stubAccessGranted();
        stubRosterContainsTheEnrolment();
        when(courses.getGradeColumns(courseId)).thenReturn(List.of(new GradeColumnResponse(
                UUID.randomUUID(), "Cột của khóa khác", new BigDecimal("100.00"),
                new BigDecimal("10.00"), 0)));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("8.50")))
                .isInstanceOf(GradeColumnNotInCourseException.class);
        verify(gradeEntries, never()).save(any(GradeEntry.class));
    }

    /** Review Focus #9: khóa chưa cấu hình cột điểm nào thì không có gì để nhập - cùng lỗi với cột lạ. */
    @Test
    void rejectsRecordingWhenTheCourseHasNoGradeColumnsConfigured() {
        stubAccessGranted();
        stubRosterContainsTheEnrolment();
        when(courses.getGradeColumns(courseId)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("8.50")))
                .isInstanceOf(GradeColumnNotInCourseException.class);
    }

    /** Review Focus #10: thang của ĐÚNG cột đó quyết định. 9.50 hợp lệ ở thang 10... */
    @Test
    void acceptsAScoreWithinTheColumnRange() {
        stubHappyPath();

        assertThatCode(() -> useCase.execute(actorAccountId, actorBranchId, request("9.50")))
                .doesNotThrowAnyException();
    }

    /** ...và sai ở thang 5, dù vẫn là cùng một con số. */
    @Test
    void rejectsAScoreAboveTheMaxScoreOfThatColumn() {
        stubAccessGranted();
        stubRosterContainsTheEnrolment();
        stubColumnOutOf("5.00");

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("9.50")))
                .isInstanceOf(InvalidScoreException.class)
                .hasMessageContaining("5.00");
        verify(gradeEntries, never()).save(any(GradeEntry.class));
    }

    @Test
    void rejectsANegativeScore() {
        stubAccessGranted();
        stubRosterContainsTheEnrolment();
        stubColumnOutOf("10.00");

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request("-0.01")))
                .isInstanceOf(InvalidScoreException.class);
    }

    /** Hai ranh giới hợp lệ: đúng 0 và đúng maxScore phải nhận được, chặn ở đây là chặn sai. */
    @Test
    void acceptsExactlyZeroAndExactlyTheMaxScore() {
        stubHappyPath();

        assertThatCode(() -> useCase.execute(actorAccountId, actorBranchId, request("0.00")))
                .doesNotThrowAnyException();
        assertThatCode(() -> useCase.execute(actorAccountId, actorBranchId, request("10.00")))
                .doesNotThrowAnyException();
    }

    /** Học viên đã rút vẫn sửa được điểm đã có: trung tâm cần chốt lại bảng điểm cuối khóa cho người
     * học dở, và spec không nói rút khỏi lớp thì khoá luôn việc nhập điểm. */
    @Test
    void stillRecordsGradesForAWithdrawnEnrolment() {
        stubAccessGranted();
        stubColumnOutOf("10.00");
        stubNoExistingEntry();
        when(enrollments.listActiveEnrollmentsByClass(classId)).thenReturn(List.of(
                new EnrollmentManagement.ClassRosterEntry(enrollmentId, UUID.randomUUID(), courseId,
                        EnrollmentConstants.EnrollmentStatus.WITHDRAWN, Instant.now(), Instant.now())));

        assertThatCode(() -> useCase.execute(actorAccountId, actorBranchId, request("8.00")))
                .doesNotThrowAnyException();
    }

    /** Sự kiện phải mang đủ bốn field spec mục 9 yêu cầu, nếu không dòng audit mất actor/chi nhánh. */
    @Test
    void publishesAnEventCarryingTheEnrolmentColumnActorAndBranch() {
        stubHappyPath();

        useCase.execute(actorAccountId, actorBranchId, request("8.50"));

        var published = ArgumentCaptor.forClass(AcademicEvents.GradeRecorded.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue().enrollmentId()).isEqualTo(enrollmentId);
        assertThat(published.getValue().courseGradeColumnId()).isEqualTo(midtermColumnId);
        assertThat(published.getValue().actorAccountId()).isEqualTo(actorAccountId);
        assertThat(published.getValue().actorBranchId()).isEqualTo(actorBranchId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=RecordGradeTest`
Expected: FAIL at compile — `cannot find symbol: class RecordGradeRequest`, `cannot find symbol: class RecordGrade`.

- [ ] **Step 3: Write the dto package**

Create `backend/src/main/java/com/eduerp/modules/academic/dto/package-info.java`:

```java
/**
 * Hợp đồng vào/ra của module academic. Controller nhận/trả các record này nên package phải được
 * expose bằng {@code @NamedInterface}, nếu không {@code ApplicationModules.verify()} sẽ fail.
 */
@org.springframework.modulith.NamedInterface("dto")
package com.eduerp.modules.academic.dto;
```

Create `backend/src/main/java/com/eduerp/modules/academic/dto/RecordGradeRequest.java`:

```java
package com.eduerp.modules.academic.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code classId} có trong body vì mọi kiểm tra quyền của module này đều theo LỚP (spec mục 6), và
 * {@code EnrollmentManagement.EnrollmentSummaryResponse} không mang {@code classId} - xem javadoc
 * {@code RecordGrade}. Trần của {@code score} là {@code maxScore} của riêng cột nên không đặt được
 * bằng annotation ở đây, chặn trong usecase (Review Focus #10).
 */
public record RecordGradeRequest(@NotNull UUID classId, @NotNull UUID enrollmentId,
        @NotNull UUID courseGradeColumnId, @NotNull @PositiveOrZero BigDecimal score) {
}
```

Create `backend/src/main/java/com/eduerp/modules/academic/dto/StudentGradesRow.java`:

```java
package com.eduerp.modules.academic.dto;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Một dòng bảng điểm. {@code scoresByColumnId} khuyết cột nào nghĩa là cột đó CHƯA có điểm, và khi
 * đó {@code finalScore} là {@code null} = "chưa đủ điểm" (spec mục 6) - KHÔNG phải 0. Tên field là
 * hợp đồng với Zod schema ở frontend ({@code entities/academic}) - đổi tên là breaking.
 */
public record StudentGradesRow(UUID enrollmentId, UUID studentProfileId, String studentName,
        EnrollmentConstants.EnrollmentStatus status, Map<UUID, BigDecimal> scoresByColumnId,
        BigDecimal finalScore) {
}
```

Create `backend/src/main/java/com/eduerp/modules/academic/dto/GradebookResponse.java`:

```java
package com.eduerp.modules.academic.dto;

import com.eduerp.modules.courses.dto.GradeColumnResponse;
import java.util.List;
import java.util.UUID;

/** {@code columns} theo {@code displayOrder} - cả giao diện và file Excel xuất ra đều lấy thứ tự cột
 * từ đây, không tự sắp lại. */
public record GradebookResponse(UUID classId, UUID courseId, List<GradeColumnResponse> columns,
        List<StudentGradesRow> rows) {
}
```

Create `backend/src/main/java/com/eduerp/modules/academic/dto/StudentProgressRow.java`:

```java
package com.eduerp.modules.academic.dto;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng bảng tiến trình. {@code progressPercent} là {@code null} khi
 * {@code standardSessionCount} chưa cấu hình - "chưa cấu hình tổng số buổi chuẩn", không phải 0%
 * (spec mục 5, Review Focus #8). {@code elapsedSessions} vẫn có số thật để người xem hiểu con số
 * null đến từ việc thiếu cấu hình chứ không phải thiếu dữ liệu học.
 */
public record StudentProgressRow(UUID enrollmentId, UUID studentProfileId, String studentName,
        EnrollmentConstants.EnrollmentStatus status, Instant enrolledAt, Instant withdrawnAt,
        int elapsedSessions, Integer standardSessionCount, Integer progressPercent) {
}
```

Create `backend/src/main/java/com/eduerp/modules/academic/dto/ClassProgressResponse.java`:

```java
package com.eduerp.modules.academic.dto;

import java.util.List;
import java.util.UUID;

/** {@code standardSessionCount} null = khóa chưa cấu hình tổng số buổi chuẩn, khi đó mọi
 * {@code rows[].progressPercent} cũng null (spec mục 5). */
public record ClassProgressResponse(UUID classId, UUID courseId, Integer standardSessionCount,
        List<StudentProgressRow> rows) {
}
```

Create `backend/src/main/java/com/eduerp/modules/academic/dto/ExportedFileResponse.java`:

```java
package com.eduerp.modules.academic.dto;

/** File đã sinh xong trong bộ nhớ, chờ controller gắn {@code Content-Disposition} - mirror cách
 * {@code DownloadContractFile} trả về cặp tên file + nội dung. */
public record ExportedFileResponse(String fileName, byte[] content) {
}
```

- [ ] **Step 4: Write the usecase**

Create `backend/src/main/java/com/eduerp/modules/academic/usecase/package-info.java`:

```java
/** Một class = một use case (rule #7). Mọi use case ở đây bắt đầu bằng một lần gọi
 * {@code ClassGradeAccessGuard.requireAccessToClass} - không có đường nào vào dữ liệu điểm mà bỏ
 * qua bước đó (spec mục 6 + 7). */
package com.eduerp.modules.academic.usecase;
```

Create `backend/src/main/java/com/eduerp/modules/academic/usecase/RecordGrade.java`:

```java
package com.eduerp.modules.academic.usecase;

import com.eduerp.modules.academic.AcademicEvents;
import com.eduerp.modules.academic.EnrollmentNotFoundException;
import com.eduerp.modules.academic.GradeColumnNotInCourseException;
import com.eduerp.modules.academic.InvalidScoreException;
import com.eduerp.modules.academic.dto.RecordGradeRequest;
import com.eduerp.modules.academic.internal.access.ClassGradeAccessGuard;
import com.eduerp.modules.academic.internal.model.GradeEntry;
import com.eduerp.modules.academic.internal.repository.GradeEntryRepository;
import com.eduerp.modules.academic.internal.rules.GradeRules;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhập hoặc sửa điểm một cột cho một lượt ghi danh (spec mục 6). Upsert vì UNIQUE
 * {@code (enrollment_id, course_grade_column_id)} của V24: nhập lại là sửa dòng cũ.
 *
 * <p>Lượt ghi danh được xác nhận qua {@code listActiveEnrollmentsByClass(classId)} chứ không qua
 * {@code getEnrollment(enrollmentId)}: record của method thứ hai không mang {@code classId} nên
 * không kiểm được "lượt ghi danh này có thuộc lớp đang nhập điểm không", và spec mục 3 chỉ cho thêm
 * MỘT method đọc vào facade enrollment. Một câu truy vấn trả về đúng danh sách lớp (tối đa
 * {@code maxSeats} dòng) vừa kiểm được điều đó vừa cho luôn {@code courseId} để tra cột điểm.
 */
@Service
public class RecordGrade {

    private final GradeEntryRepository gradeEntries;
    private final EnrollmentManagement enrollments;
    private final CoursesManagement courses;
    private final ClassGradeAccessGuard guard;
    private final ApplicationEventPublisher events;

    RecordGrade(GradeEntryRepository gradeEntries, EnrollmentManagement enrollments,
            CoursesManagement courses, ClassGradeAccessGuard guard, ApplicationEventPublisher events) {
        this.gradeEntries = gradeEntries;
        this.enrollments = enrollments;
        this.courses = courses;
        this.guard = guard;
        this.events = events;
    }

    @Transactional
    public void execute(UUID actorAccountId, UUID actorBranchId, RecordGradeRequest request) {
        guard.requireAccessToClass(actorAccountId, request.classId(), AccessConstants.Actions.UPDATE);
        var rosterEntry = enrollments.listActiveEnrollmentsByClass(request.classId()).stream()
                .filter(entry -> entry.enrollmentId().equals(request.enrollmentId()))
                .findFirst()
                .orElseThrow(() -> new EnrollmentNotFoundException(request.enrollmentId()));
        var column = findColumn(rosterEntry.courseId(), request.courseGradeColumnId());
        if (!GradeRules.isScoreWithinRange(request.score(), column.maxScore())) {
            throw new InvalidScoreException(request.score(), column.maxScore());
        }
        upsert(request, actorAccountId);
        events.publishEvent(new AcademicEvents.GradeRecorded(request.enrollmentId(),
                request.courseGradeColumnId(), actorAccountId, actorBranchId));
    }

    private GradeColumnResponse findColumn(UUID courseId, UUID courseGradeColumnId) {
        return courses.getGradeColumns(courseId).stream()
                .filter(column -> column.id().equals(courseGradeColumnId))
                .findFirst()
                .orElseThrow(() -> new GradeColumnNotInCourseException(courseGradeColumnId, courseId));
    }

    private void upsert(RecordGradeRequest request, UUID actorAccountId) {
        gradeEntries.findByEnrollmentIdAndCourseGradeColumnId(request.enrollmentId(),
                        request.courseGradeColumnId())
                .ifPresentOrElse(entry -> entry.changeScore(request.score(), actorAccountId),
                        () -> gradeEntries.save(new GradeEntry(request.enrollmentId(),
                                request.courseGradeColumnId(), request.score(), actorAccountId)));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=RecordGradeTest`
Expected: PASS — 11 tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/academic/dto/ \
        backend/src/main/java/com/eduerp/modules/academic/usecase/ \
        backend/src/test/java/com/eduerp/modules/academic/usecase/RecordGradeTest.java
git commit -m "feat(academic): record and update a weighted grade for an enrolment"
```

---

## Task 13: `GetClassGradebook` + `StudentNameResolver`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/academic/internal/StudentNameResolver.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/usecase/GetClassGradebook.java`
- Test: `backend/src/test/java/com/eduerp/modules/academic/usecase/GetClassGradebookTest.java`

**Interfaces:**
- Consumes: `StudentsManagement.summariesOf(Collection<UUID>): Map<UUID, StudentSummaryResponse>` (Task 6), `IdentityManagement.summariesOf(Collection<UUID>): Map<UUID, AccountBasicInfo>` (hiện có, `AccountBasicInfo(UUID id, String email, String fullName)`), `GradeEntryRepository.findAllByEnrollmentIdIn` (Task 10), `GradeRules.finalScore` (Task 9), `ClassGradeAccessGuard` (Task 11), `dto.GradebookResponse`/`dto.StudentGradesRow` (Task 12).
- Produces: `StudentNameResolver.namesOf(Collection<UUID> studentProfileIds): Map<UUID, String>`; `GetClassGradebook.execute(UUID actorAccountId, UUID classId): GradebookResponse`.

> `StudentNameResolver` tồn tại để Task 13 và Task 14 không copy-paste cùng một chuỗi hai lần gọi
> facade (`students` → `identity`). Đường này đúng tiền lệ `ListStudentProfiles` đã làm.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/academic/usecase/GetClassGradebookTest.java`:

```java
package com.eduerp.modules.academic.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.academic.NotAssignedToClassException;
import com.eduerp.modules.academic.dto.StudentGradesRow;
import com.eduerp.modules.academic.internal.StudentNameResolver;
import com.eduerp.modules.academic.internal.access.ClassGradeAccessGuard;
import com.eduerp.modules.academic.internal.model.GradeEntry;
import com.eduerp.modules.academic.internal.repository.GradeEntryRepository;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetClassGradebookTest {

    private final GradeEntryRepository gradeEntries = mock(GradeEntryRepository.class);
    private final EnrollmentManagement enrollments = mock(EnrollmentManagement.class);
    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final ClassGradeAccessGuard guard = mock(ClassGradeAccessGuard.class);
    private final StudentNameResolver names = mock(StudentNameResolver.class);
    private final GetClassGradebook useCase =
            new GetClassGradebook(gradeEntries, enrollments, courses, guard, names);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID midtermId = UUID.randomUUID();
    private final UUID finalId = UUID.randomUUID();
    private final UUID enrollmentA = UUID.randomUUID();
    private final UUID enrollmentB = UUID.randomUUID();
    private final UUID studentA = UUID.randomUUID();
    private final UUID studentB = UUID.randomUUID();

    private void stubAccessGranted() {
        when(guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ))
                .thenReturn(new CoursesManagement.ClassInfoResponse(classId, courseId, UUID.randomUUID(),
                        actorAccountId, 20, true, 24, List.of()));
    }

    private void stubTwoColumns() {
        when(courses.getGradeColumns(courseId)).thenReturn(List.of(
                new GradeColumnResponse(midtermId, "Giữa kỳ", new BigDecimal("40.00"),
                        new BigDecimal("10.00"), 0),
                new GradeColumnResponse(finalId, "Cuối kỳ", new BigDecimal("60.00"),
                        new BigDecimal("10.00"), 1)));
    }

    private void stubTwoStudents() {
        when(enrollments.listActiveEnrollmentsByClass(classId)).thenReturn(List.of(
                new EnrollmentManagement.ClassRosterEntry(enrollmentA, studentA, courseId,
                        EnrollmentConstants.EnrollmentStatus.ACTIVE, Instant.now(), null),
                new EnrollmentManagement.ClassRosterEntry(enrollmentB, studentB, courseId,
                        EnrollmentConstants.EnrollmentStatus.ACTIVE, Instant.now(), null)));
        when(names.namesOf(List.of(studentA, studentB)))
                .thenReturn(Map.of(studentA, "Nguyễn Văn A", studentB, "Trần Thị B"));
    }

    private static GradeEntry entry(UUID enrollmentId, UUID columnId, String score) {
        return new GradeEntry(enrollmentId, columnId, new BigDecimal(score), UUID.randomUUID());
    }

    private StudentGradesRow rowOf(UUID enrollmentId, List<StudentGradesRow> rows) {
        return rows.stream().filter(row -> row.enrollmentId().equals(enrollmentId)).findFirst()
                .orElseThrow();
    }

    @Test
    void buildsTheGradebookWithColumnsScoresAndNames() {
        stubAccessGranted();
        stubTwoColumns();
        stubTwoStudents();
        when(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentA, enrollmentB))).thenReturn(List.of(
                entry(enrollmentA, midtermId, "8.00"), entry(enrollmentA, finalId, "9.00"),
                entry(enrollmentB, midtermId, "6.00"), entry(enrollmentB, finalId, "7.00")));

        var gradebook = useCase.execute(actorAccountId, classId);

        assertThat(gradebook.classId()).isEqualTo(classId);
        assertThat(gradebook.courseId()).isEqualTo(courseId);
        assertThat(gradebook.columns()).extracting(GradeColumnResponse::name)
                .containsExactly("Giữa kỳ", "Cuối kỳ");
        assertThat(gradebook.rows()).hasSize(2);
        var rowA = rowOf(enrollmentA, gradebook.rows());
        assertThat(rowA.studentName()).isEqualTo("Nguyễn Văn A");
        assertThat(rowA.scoresByColumnId().get(midtermId)).isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(rowA.finalScore())
                .as("8/10*40 + 9/10*60 = 86")
                .isEqualByComparingTo(new BigDecimal("86.00"));
        assertThat(rowOf(enrollmentB, gradebook.rows()).finalScore())
                .as("6/10*40 + 7/10*60 = 66")
                .isEqualByComparingTo(new BigDecimal("66.00"));
    }

    /**
     * Review Focus #3: thiếu một cột → {@code finalScore} null, KHÔNG phải 32.00 (giá trị mà một
     * phép "trung bình trên phần đã có" sẽ trả ra và người xem sẽ đọc là điểm trượt).
     */
    @Test
    void leavesFinalScoreNullForAStudentMissingOneColumn() {
        stubAccessGranted();
        stubTwoColumns();
        stubTwoStudents();
        when(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentA, enrollmentB))).thenReturn(List.of(
                entry(enrollmentA, midtermId, "8.00"),
                entry(enrollmentB, midtermId, "6.00"), entry(enrollmentB, finalId, "7.00")));

        var gradebook = useCase.execute(actorAccountId, classId);

        var rowA = rowOf(enrollmentA, gradebook.rows());
        assertThat(rowA.finalScore()).isNull();
        assertThat(rowA.scoresByColumnId())
                .as("điểm đã nhập vẫn hiện ra - chỉ điểm TỔNG KẾT là chưa có")
                .containsOnlyKeys(midtermId);
        assertThat(rowOf(enrollmentB, gradebook.rows()).finalScore()).isNotNull();
    }

    @Test
    void leavesEveryFinalScoreNullWhenNoGradeHasBeenEnteredYet() {
        stubAccessGranted();
        stubTwoColumns();
        stubTwoStudents();
        when(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentA, enrollmentB)))
                .thenReturn(List.of());

        var gradebook = useCase.execute(actorAccountId, classId);

        assertThat(gradebook.rows()).allSatisfy(row -> {
            assertThat(row.finalScore()).isNull();
            assertThat(row.scoresByColumnId()).isEmpty();
        });
    }

    /** Review Focus #9: khóa chưa cấu hình cột điểm nào - bảng vẫn dựng được (danh sách học viên có
     * nghĩa), chỉ là không có cột nào và không có điểm tổng kết nào. */
    @Test
    void returnsTheRosterWithNoColumnsWhenTheCourseHasNoneConfigured() {
        stubAccessGranted();
        stubTwoStudents();
        when(courses.getGradeColumns(courseId)).thenReturn(List.of());
        when(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentA, enrollmentB)))
                .thenReturn(List.of());

        var gradebook = useCase.execute(actorAccountId, classId);

        assertThat(gradebook.columns()).isEmpty();
        assertThat(gradebook.rows()).hasSize(2);
        assertThat(gradebook.rows()).allSatisfy(row -> assertThat(row.finalScore()).isNull());
    }

    /** Lớp chưa có học viên nào: bảng rỗng là câu trả lời hợp lệ, không ném. */
    @Test
    void returnsAnEmptyRowListForAClassWithoutAnyEnrolment() {
        stubAccessGranted();
        stubTwoColumns();
        when(enrollments.listActiveEnrollmentsByClass(classId)).thenReturn(List.of());
        when(names.namesOf(List.of())).thenReturn(Map.of());
        when(gradeEntries.findAllByEnrollmentIdIn(List.of())).thenReturn(List.of());

        var gradebook = useCase.execute(actorAccountId, classId);

        assertThat(gradebook.rows()).isEmpty();
        assertThat(gradebook.columns()).hasSize(2);
    }

    /** Hồ sơ học viên bị xoá/không tra được tên: dòng vẫn còn với tên trống, không làm đổ cả bảng. */
    @Test
    void keepsTheRowWhenAStudentNameCannotBeResolved() {
        stubAccessGranted();
        stubTwoColumns();
        when(enrollments.listActiveEnrollmentsByClass(classId)).thenReturn(List.of(
                new EnrollmentManagement.ClassRosterEntry(enrollmentA, studentA, courseId,
                        EnrollmentConstants.EnrollmentStatus.ACTIVE, Instant.now(), null)));
        when(names.namesOf(List.of(studentA))).thenReturn(Map.of());
        when(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentA))).thenReturn(List.of());

        var gradebook = useCase.execute(actorAccountId, classId);

        assertThat(gradebook.rows()).hasSize(1);
        assertThat(gradebook.rows().get(0).studentName()).isNull();
    }

    /** Review Focus #4: đường đọc cũng đi qua guard - không có endpoint xem bảng điểm nào bỏ qua
     * kiểm tra "đúng lớp". */
    @Test
    void refusesACallerTheGuardRejects() {
        when(guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ))
                .thenThrow(new NotAssignedToClassException(actorAccountId, classId));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, classId))
                .isInstanceOf(NotAssignedToClassException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=GetClassGradebookTest`
Expected: FAIL at compile — `cannot find symbol: class StudentNameResolver`, `cannot find symbol: class GetClassGradebook`.

- [ ] **Step 3: Write the name resolver**

Create `backend/src/main/java/com/eduerp/modules/academic/internal/StudentNameResolver.java`:

```java
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
```

- [ ] **Step 4: Write the usecase**

Create `backend/src/main/java/com/eduerp/modules/academic/usecase/GetClassGradebook.java`:

```java
package com.eduerp.modules.academic.usecase;

import com.eduerp.modules.academic.dto.GradebookResponse;
import com.eduerp.modules.academic.dto.StudentGradesRow;
import com.eduerp.modules.academic.internal.StudentNameResolver;
import com.eduerp.modules.academic.internal.access.ClassGradeAccessGuard;
import com.eduerp.modules.academic.internal.model.GradeEntry;
import com.eduerp.modules.academic.internal.repository.GradeEntryRepository;
import com.eduerp.modules.academic.internal.rules.GradeRules;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bảng điểm của một lớp (spec mục 6). Đây là TẦNG TÍNH TOÁN DUY NHẤT cho bảng điểm:
 * {@code ExportClassGradebook} gọi lại chính usecase này rồi serialize, nên số trên web và số trong
 * file Excel không thể lệch nhau (spec mục 11.7).
 *
 * <p>Bốn lần đọc cố định, không tăng theo số học viên (rule #3): một lần guard/lớp, một lần danh
 * sách ghi danh, một lần cột điểm, một lần điểm theo lô - cộng hai lần nạp tên theo lô bên trong
 * {@link StudentNameResolver}.
 */
@Service
public class GetClassGradebook {

    private final GradeEntryRepository gradeEntries;
    private final EnrollmentManagement enrollments;
    private final CoursesManagement courses;
    private final ClassGradeAccessGuard guard;
    private final StudentNameResolver studentNames;

    GetClassGradebook(GradeEntryRepository gradeEntries, EnrollmentManagement enrollments,
            CoursesManagement courses, ClassGradeAccessGuard guard, StudentNameResolver studentNames) {
        this.gradeEntries = gradeEntries;
        this.enrollments = enrollments;
        this.courses = courses;
        this.guard = guard;
        this.studentNames = studentNames;
    }

    @Transactional(readOnly = true)
    public GradebookResponse execute(UUID actorAccountId, UUID classId) {
        var classInfo = guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ);
        var roster = enrollments.listActiveEnrollmentsByClass(classId);
        var columns = courses.getGradeColumns(classInfo.courseId());
        var scores = scoresByEnrollment(roster.stream()
                .map(EnrollmentManagement.ClassRosterEntry::enrollmentId).toList());
        var names = studentNames.namesOf(roster.stream()
                .map(EnrollmentManagement.ClassRosterEntry::studentProfileId).toList());

        var rows = roster.stream()
                .map(entry -> toRow(entry, columns, scores, names))
                .toList();
        return new GradebookResponse(classId, classInfo.courseId(), columns, rows);
    }

    private Map<UUID, Map<UUID, BigDecimal>> scoresByEnrollment(List<UUID> enrollmentIds) {
        return gradeEntries.findAllByEnrollmentIdIn(enrollmentIds).stream()
                .collect(Collectors.groupingBy(GradeEntry::getEnrollmentId,
                        Collectors.toMap(GradeEntry::getCourseGradeColumnId, GradeEntry::getScore)));
    }

    private static StudentGradesRow toRow(EnrollmentManagement.ClassRosterEntry entry,
            List<GradeColumnResponse> columns, Map<UUID, Map<UUID, BigDecimal>> scores,
            Map<UUID, String> names) {
        var rowScores = scores.getOrDefault(entry.enrollmentId(), Map.of());
        return new StudentGradesRow(entry.enrollmentId(), entry.studentProfileId(),
                names.get(entry.studentProfileId()), entry.status(), rowScores,
                GradeRules.finalScore(columns, rowScores));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GetClassGradebookTest`
Expected: PASS — 7 tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/academic/internal/StudentNameResolver.java \
        backend/src/main/java/com/eduerp/modules/academic/usecase/GetClassGradebook.java \
        backend/src/test/java/com/eduerp/modules/academic/usecase/GetClassGradebookTest.java
git commit -m "feat(academic): build the class gradebook with weighted final scores"
```

---

## Task 14: `GetClassProgress`

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/academic/usecase/GetClassProgress.java`
- Test: `backend/src/test/java/com/eduerp/modules/academic/usecase/GetClassProgressTest.java`

**Interfaces:**
- Consumes: `ProgressRules.countElapsedSessions`/`.progressPercent`/`.centerZone` (Task 8), `ClassGradeAccessGuard` (Task 11), `StudentNameResolver.namesOf` (Task 13), `EnrollmentManagement.listActiveEnrollmentsByClass` (Task 5), `dto.ClassProgressResponse`/`dto.StudentProgressRow` (Task 12), `EnrollmentConstants.EnrollmentStatus.WITHDRAWN`.
- Produces: `GetClassProgress.execute(UUID actorAccountId, UUID classId): ClassProgressResponse`; package-private constructor `GetClassProgress(..., Clock clock)` dùng trong test.

> Usecase này là nơi DUY NHẤT đọc đồng hồ: `ProgressRules` thuần nhận mốc kết thúc từ ngoài. Lấy
> `Clock` qua constructor (public ctor mặc định `Clock.systemUTC()`, ctor package-private cho test)
> theo đúng tiền lệ `VnPayPaymentGatewayClient` — không chốt được đồng hồ thì Review Focus #2 không
> test được ở tầng usecase.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/academic/usecase/GetClassProgressTest.java`:

```java
package com.eduerp.modules.academic.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.academic.NotAssignedToClassException;
import com.eduerp.modules.academic.dto.StudentProgressRow;
import com.eduerp.modules.academic.internal.StudentNameResolver;
import com.eduerp.modules.academic.internal.access.ClassGradeAccessGuard;
import com.eduerp.modules.academic.internal.rules.ProgressRules;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetClassProgressTest {

    private static final ZoneId ZONE = ProgressRules.centerZone();

    private static final List<WeeklyScheduleSlot> MON_WED_EVENING = List.of(
            new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0)),
            new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.WED, LocalTime.of(18, 0), LocalTime.of(20, 0)));

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZONE).toInstant();
    }

    private final EnrollmentManagement enrollments = mock(EnrollmentManagement.class);
    private final ClassGradeAccessGuard guard = mock(ClassGradeAccessGuard.class);
    private final StudentNameResolver names = mock(StudentNameResolver.class);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID enrollmentA = UUID.randomUUID();
    private final UUID enrollmentB = UUID.randomUUID();
    private final UUID studentA = UUID.randomUUID();
    private final UUID studentB = UUID.randomUUID();

    /** Đồng hồ chốt ở 2026-01-21 23:00 giờ trung tâm - mọi con số dưới đây tính tới mốc đó. */
    private GetClassProgress useCaseAt(Instant now) {
        return new GetClassProgress(enrollments, guard, names, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void stubClass(Integer standardSessionCount, List<WeeklyScheduleSlot> schedule) {
        when(guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ))
                .thenReturn(new CoursesManagement.ClassInfoResponse(classId, courseId, UUID.randomUUID(),
                        actorAccountId, 20, true, standardSessionCount, schedule));
    }

    private void stubRoster(List<EnrollmentManagement.ClassRosterEntry> roster) {
        when(enrollments.listActiveEnrollmentsByClass(classId)).thenReturn(roster);
        when(names.namesOf(roster.stream()
                .map(EnrollmentManagement.ClassRosterEntry::studentProfileId).toList()))
                .thenReturn(Map.of(studentA, "Nguyễn Văn A", studentB, "Trần Thị B"));
    }

    private static EnrollmentManagement.ClassRosterEntry active(UUID enrollmentId, UUID studentProfileId,
            UUID courseId, Instant enrolledAt) {
        return new EnrollmentManagement.ClassRosterEntry(enrollmentId, studentProfileId, courseId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE, enrolledAt, null);
    }

    private StudentProgressRow rowOf(UUID enrollmentId, List<StudentProgressRow> rows) {
        return rows.stream().filter(row -> row.enrollmentId().equals(enrollmentId)).findFirst()
                .orElseThrow();
    }

    /**
     * Review Focus #1: hai học viên CÙNG lớp, cùng {@code Class}/{@code Course}, {@code enrolledAt}
     * khác nhau PHẢI ra % khác nhau. Lớp 24 buổi, lịch T2+T4: A vào 05/01 học 6 buổi (25%),
     * B vào 14/01 học 3 buổi (13%).
     */
    @Test
    void givesTwoDifferentPercentsToTwoStudentsInTheSameClass() {
        stubClass(24, MON_WED_EVENING);
        stubRoster(List.of(
                active(enrollmentA, studentA, courseId, at(2026, 1, 5, 8, 0)),
                active(enrollmentB, studentB, courseId, at(2026, 1, 14, 8, 0))));

        var progress = useCaseAt(at(2026, 1, 21, 23, 0)).execute(actorAccountId, classId);

        var rowA = rowOf(enrollmentA, progress.rows());
        var rowB = rowOf(enrollmentB, progress.rows());
        assertThat(rowA.elapsedSessions()).isEqualTo(6);
        assertThat(rowA.progressPercent()).as("6/24 = 25%").isEqualTo(25);
        assertThat(rowB.elapsedSessions()).isEqualTo(3);
        assertThat(rowB.progressPercent()).as("3/24 = 12.5 → 13%").isEqualTo(13);
        assertThat(rowA.progressPercent()).isNotEqualTo(rowB.progressPercent());
        assertThat(rowA.studentName()).isEqualTo("Nguyễn Văn A");
    }

    /**
     * Review Focus #2: ghi danh WITHDRAWN đóng băng tại {@code withdrawnAt}. Cùng một dữ liệu, đọc
     * ở hai thời điểm cách nhau ba tháng phải ra CÙNG con số - nếu usecase dùng "bây giờ" làm mốc
     * kết thúc cho người đã rút thì khẳng định thứ hai sẽ đỏ.
     */
    @Test
    void freezesAWithdrawnEnrolmentAtItsWithdrawnAt() {
        stubClass(24, MON_WED_EVENING);
        stubRoster(List.of(new EnrollmentManagement.ClassRosterEntry(enrollmentA, studentA, courseId,
                EnrollmentConstants.EnrollmentStatus.WITHDRAWN, at(2026, 1, 5, 8, 0),
                at(2026, 1, 13, 9, 0))));

        var readSoon = useCaseAt(at(2026, 1, 21, 23, 0)).execute(actorAccountId, classId);
        var readMuchLater = useCaseAt(at(2026, 4, 21, 23, 0)).execute(actorAccountId, classId);

        assertThat(rowOf(enrollmentA, readSoon.rows()).elapsedSessions())
                .as("05,07,12 tháng 1 = 3 buổi tính tới mốc rút 13/01")
                .isEqualTo(3);
        assertThat(rowOf(enrollmentA, readMuchLater.rows()).elapsedSessions())
                .as("ba tháng sau vẫn đúng 3 buổi - tiến trình đã đóng băng")
                .isEqualTo(3);
        assertThat(rowOf(enrollmentA, readMuchLater.rows()).progressPercent())
                .isEqualTo(rowOf(enrollmentA, readSoon.rows()).progressPercent());
        assertThat(rowOf(enrollmentA, readMuchLater.rows()).withdrawnAt()).isNotNull();
    }

    /** COMPLETED không cần mốc riêng: công thức có trần {@code min(...)} nên tự dừng ở 100%
     * (spec mục 5). */
    @Test
    void capsACompletedEnrolmentAtOneHundredPercentWithoutASpecialMark() {
        stubClass(4, MON_WED_EVENING);
        stubRoster(List.of(new EnrollmentManagement.ClassRosterEntry(enrollmentA, studentA, courseId,
                EnrollmentConstants.EnrollmentStatus.COMPLETED, at(2026, 1, 5, 8, 0), null)));

        var progress = useCaseAt(at(2026, 3, 1, 23, 0)).execute(actorAccountId, classId);

        assertThat(rowOf(enrollmentA, progress.rows()).progressPercent()).isEqualTo(100);
    }

    /**
     * Review Focus #8: {@code standardSessionCount} chưa cấu hình → {@code progressPercent} null ở
     * cả mức lớp và từng dòng, KHÔNG chia cho 0 và không trả một con số sai. {@code elapsedSessions}
     * vẫn có số thật để người xem hiểu null đến từ thiếu cấu hình, không phải thiếu dữ liệu học.
     */
    @Test
    void reportsNotConfiguredWhenTheCourseHasNoStandardSessionCount() {
        stubClass(null, MON_WED_EVENING);
        stubRoster(List.of(active(enrollmentA, studentA, courseId, at(2026, 1, 5, 8, 0))));

        var progress = useCaseAt(at(2026, 1, 21, 23, 0)).execute(actorAccountId, classId);

        assertThat(progress.standardSessionCount()).isNull();
        var rowA = rowOf(enrollmentA, progress.rows());
        assertThat(rowA.progressPercent()).isNull();
        assertThat(rowA.standardSessionCount()).isNull();
        assertThat(rowA.elapsedSessions()).isEqualTo(6);
    }

    /** Review Focus #12: lớp chưa có lịch học nào → 0 buổi, 0%, không ném. */
    @Test
    void reportsZeroForAClassWithoutAnySchedule() {
        stubClass(24, List.of());
        stubRoster(List.of(active(enrollmentA, studentA, courseId, at(2026, 1, 5, 8, 0))));

        var progress = useCaseAt(at(2026, 1, 21, 23, 0)).execute(actorAccountId, classId);

        assertThat(rowOf(enrollmentA, progress.rows()).elapsedSessions()).isZero();
        assertThat(rowOf(enrollmentA, progress.rows()).progressPercent()).isZero();
    }

    @Test
    void returnsAnEmptyRowListForAClassWithoutAnyEnrolment() {
        stubClass(24, MON_WED_EVENING);
        when(enrollments.listActiveEnrollmentsByClass(classId)).thenReturn(List.of());
        when(names.namesOf(List.of())).thenReturn(Map.of());

        var progress = useCaseAt(at(2026, 1, 21, 23, 0)).execute(actorAccountId, classId);

        assertThat(progress.rows()).isEmpty();
        assertThat(progress.classId()).isEqualTo(classId);
        assertThat(progress.standardSessionCount()).isEqualTo(24);
    }

    /** Review Focus #4: đường đọc tiến trình cũng đi qua guard. */
    @Test
    void refusesACallerTheGuardRejects() {
        when(guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ))
                .thenThrow(new NotAssignedToClassException(actorAccountId, classId));

        assertThatThrownBy(() -> useCaseAt(Instant.now()).execute(actorAccountId, classId))
                .isInstanceOf(NotAssignedToClassException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=GetClassProgressTest`
Expected: FAIL at compile — `cannot find symbol: class GetClassProgress`.

- [ ] **Step 3: Write the usecase**

Create `backend/src/main/java/com/eduerp/modules/academic/usecase/GetClassProgress.java`:

```java
package com.eduerp.modules.academic.usecase;

import com.eduerp.modules.academic.dto.ClassProgressResponse;
import com.eduerp.modules.academic.dto.StudentProgressRow;
import com.eduerp.modules.academic.internal.StudentNameResolver;
import com.eduerp.modules.academic.internal.access.ClassGradeAccessGuard;
import com.eduerp.modules.academic.internal.rules.ProgressRules;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tiến trình học tập của một lớp (spec mục 5). KHÔNG LƯU gì: con số được tính lúc đọc từ
 * {@code enrolledAt}/{@code withdrawnAt} của từng lượt ghi danh đối chiếu lịch tuần của lớp - không
 * ai nhập tiến trình, và không có cột DB nào giữ nó.
 *
 * <p>Đây là TẦNG TÍNH TOÁN DUY NHẤT cho tiến trình: {@code ExportClassProgress} gọi lại chính
 * usecase này rồi serialize, nên số trên web và số trong file Excel không thể lệch nhau (spec mục
 * 11.7).
 *
 * <p>Đồng hồ nhận qua constructor theo tiền lệ {@code VnPayPaymentGatewayClient}: mốc kết thúc của
 * một ghi danh còn học là "bây giờ", nên không chốt được đồng hồ thì không test được rằng ghi danh
 * đã rút KHÔNG dùng mốc đó (Review Focus #2).
 */
@Service
public class GetClassProgress {

    private final EnrollmentManagement enrollments;
    private final ClassGradeAccessGuard guard;
    private final StudentNameResolver studentNames;
    private final Clock clock;

    GetClassProgress(EnrollmentManagement enrollments, ClassGradeAccessGuard guard,
            StudentNameResolver studentNames) {
        this(enrollments, guard, studentNames, Clock.systemUTC());
    }

    /** Constructor cho test: tiến trình phụ thuộc "bây giờ" nên phải chốt được Clock. */
    GetClassProgress(EnrollmentManagement enrollments, ClassGradeAccessGuard guard,
            StudentNameResolver studentNames, Clock clock) {
        this.enrollments = enrollments;
        this.guard = guard;
        this.studentNames = studentNames;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ClassProgressResponse execute(UUID actorAccountId, UUID classId) {
        var classInfo = guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ);
        var roster = enrollments.listActiveEnrollmentsByClass(classId);
        var names = studentNames.namesOf(roster.stream()
                .map(EnrollmentManagement.ClassRosterEntry::studentProfileId).toList());
        var now = clock.instant();

        var rows = roster.stream()
                .map(entry -> toRow(entry, classInfo, names, now))
                .toList();
        return new ClassProgressResponse(classId, classInfo.courseId(),
                classInfo.standardSessionCount(), rows);
    }

    private static StudentProgressRow toRow(EnrollmentManagement.ClassRosterEntry entry,
            CoursesManagement.ClassInfoResponse classInfo, Map<UUID, String> names, Instant now) {
        var elapsedSessions = ProgressRules.countElapsedSessions(classInfo.schedule(),
                entry.enrolledAt(), endMarkOf(entry, now), ProgressRules.centerZone());
        return new StudentProgressRow(entry.enrollmentId(), entry.studentProfileId(),
                names.get(entry.studentProfileId()), entry.status(), entry.enrolledAt(),
                entry.withdrawnAt(), elapsedSessions, classInfo.standardSessionCount(),
                ProgressRules.progressPercent(elapsedSessions, classInfo.standardSessionCount()));
    }

    /**
     * Spec mục 5: "mốc_kết_thúc = Enrollment.withdrawnAt nếu status == WITHDRAWN, ngược lại là thời
     * điểm hiện tại". {@code withdrawnAt} null trên một bản ghi WITHDRAWN là dữ liệu lệch (V17 cho
     * phép null vì ghi danh ACTIVE chưa có mốc rút), nên vẫn rơi về "bây giờ" thay vì ném NPE.
     */
    private static Instant endMarkOf(EnrollmentManagement.ClassRosterEntry entry, Instant now) {
        if (entry.status() == EnrollmentConstants.EnrollmentStatus.WITHDRAWN
                && entry.withdrawnAt() != null) {
            return entry.withdrawnAt();
        }
        return now;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GetClassProgressTest`
Expected: PASS — 7 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/academic/usecase/GetClassProgress.java \
        backend/src/test/java/com/eduerp/modules/academic/usecase/GetClassProgressTest.java
git commit -m "feat(academic): compute class learning progress from the weekly schedule"
```

---

## Task 15: RBAC cho resource `GRADE` — hằng + seed migration

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/access/AccessConstants.java` (thêm ba hằng vào `AccessRules`; `Resources.GRADE` đã thêm ở Task 11)
- Create: `backend/src/main/resources/db/migration/V25__seed_academic_rbac.sql`
- Modify: `backend/src/test/java/com/eduerp/modules/access/AccessConstantsTest.java`
- Test: `backend/src/test/java/com/eduerp/modules/access/internal/repository/AcademicRbacSeedIT.java`

**Interfaces:**
- Consumes: `AccessConstants.Resources.GRADE` (Task 11), `AccessConstants.Actions.CREATE/READ/UPDATE`, `AccessConstants.RoleCodes.TEACHER/ADMIN/STUDENT`, `RoleRepository.findByCode` + `Role.getPermissionGroups()` + `PermissionGroupItem.getPermission()/getScope()` (dùng y như `EnrollmentBillingRbacSeedIT` đã dùng).
- Produces: `AccessConstants.AccessRules.CREATE_GRADE`/`.READ_GRADE`/`.UPDATE_GRADE` — ba biểu thức `@PreAuthorize` KHÔNG có marker `@ORGANIZATION`; permission group mới `11111111-0000-0000-0000-000000000003` gắn vào role `TEACHER`.

> **Phát hiện khi đọc migration thật, quyết định cấu trúc seed:** V5 tạo group
> `11111111-0000-0000-0000-000000000002` ("Tự phục vụ cơ bản", scope `PERSONAL`) và `role_permission_groups`
> gắn group đó cho **CẢ `TEACHER` VÀ `STUDENT`**:
> ```sql
> INSERT INTO role_permission_groups (role_id, permission_group_id) VALUES
>     ('22222222-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001'),
>     ('22222222-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000002'),  -- TEACHER
>     ('22222222-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000002');  -- STUDENT
> ```
> Vì vậy **KHÔNG được** thêm `GRADE:READ:PERSONAL`/`GRADE:UPDATE:PERSONAL` vào group `...0002` như
> cách `ACCOUNT:UPDATE:PERSONAL` đã được cấp: làm thế là cấp quyền xem/sửa điểm cho luôn role
> `STUDENT`, trái Global Constraint "Học viên KHÔNG tự xem được điểm/tiến trình của mình ở giai đoạn
> này" (spec mục 1). V25 phải tạo một group MỚI chỉ gắn cho `TEACHER`. Phần admin thì mirror đúng
> V10/V13/V19: thêm dòng `ORGANIZATION` vào group `...0001`.

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/java/com/eduerp/modules/access/internal/repository/AcademicRbacSeedIT.java`:

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

/**
 * Mirror {@code EnrollmentBillingRbacSeedIT}. Đây là lần ĐẦU TIÊN trong dự án một role khác ADMIN
 * được seed quyền cho một resource hoàn toàn mới (spec mục 8), nên test này chốt ba điều cùng lúc:
 * admin có GRADE ở ORGANIZATION, TEACHER có GRADE:READ/UPDATE ở PERSONAL, và STUDENT KHÔNG có gì.
 */
@Testcontainers
@DataJpaTest
class AcademicRbacSeedIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    RoleRepository roles;

    private List<String> permissionKeysOf(String roleCode) {
        return roles.findByCode(roleCode).orElseThrow()
                .getPermissionGroups().stream()
                .flatMap(group -> group.getItems().stream())
                .map(item -> item.getPermission().getResource() + ":" + item.getPermission().getAction() + ":"
                        + item.getScope().name())
                .toList();
    }

    private static String gradeKey(String action, String scope) {
        return AccessConstants.Resources.GRADE + ":" + action + ":" + scope;
    }

    /** Review Focus #5: admin phải có GRADE ở ORGANIZATION, nếu không guard sẽ coi admin như giáo
     * viên lạ và chặn họ ở mọi lớp họ không dạy. */
    @Test
    void adminHasOrganizationScopedGradePermissions() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.ADMIN)).contains(
                gradeKey(AccessConstants.Actions.CREATE, AccessConstants.ScopeNames.ORGANIZATION),
                gradeKey(AccessConstants.Actions.READ, AccessConstants.ScopeNames.ORGANIZATION),
                gradeKey(AccessConstants.Actions.UPDATE, AccessConstants.ScopeNames.ORGANIZATION));
    }

    /** Spec mục 6: TEACHER được GRADE:READ:PERSONAL + GRADE:UPDATE:PERSONAL - đủ để qua
     * {@code @PreAuthorize}, còn "đúng lớp hay không" do guard quyết định. */
    @Test
    void teacherHasPersonalScopedGradeReadAndUpdate() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.TEACHER)).contains(
                gradeKey(AccessConstants.Actions.READ, AccessConstants.PermissionScope.PERSONAL.name()),
                gradeKey(AccessConstants.Actions.UPDATE, AccessConstants.PermissionScope.PERSONAL.name()));
    }

    /** Giáo viên KHÔNG được tạo cấu hình điểm ở cấp tổ chức - CREATE chỉ dành cho admin (spec mục 8). */
    @Test
    void teacherDoesNotGetAnyOrganizationScopedGradePermission() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.TEACHER)).noneMatch(key ->
                key.startsWith(AccessConstants.Resources.GRADE + ":")
                        && key.endsWith(":" + AccessConstants.ScopeNames.ORGANIZATION));
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.TEACHER)).noneMatch(key ->
                key.startsWith(AccessConstants.Resources.GRADE + ":" + AccessConstants.Actions.CREATE));
    }

    /**
     * Global Constraint của spec mục 1: "học viên KHÔNG tự xem được điểm/tiến trình của mình qua hệ
     * thống ở giai đoạn này". V5 gắn group "Tự phục vụ cơ bản" cho CẢ TEACHER và STUDENT, nên nếu
     * V25 nhét GRADE vào group đó thì học viên cũng được cấp - test này biến sai sót đó thành build đỏ.
     */
    @Test
    void studentGetsNoGradePermissionAtAll() {
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.STUDENT)).noneMatch(key ->
                key.startsWith(AccessConstants.Resources.GRADE + ":"));
    }

    /** Quyền tự phục vụ sẵn có của TEACHER/STUDENT không được mất đi khi thêm group mới. */
    @Test
    void keepsTheExistingSelfServicePermissionsOfBothRoles() {
        var personalAccountUpdate = AccessConstants.Resources.ACCOUNT + ":"
                + AccessConstants.Actions.UPDATE + ":" + AccessConstants.PermissionScope.PERSONAL.name();
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.TEACHER)).contains(personalAccountUpdate);
        assertThat(permissionKeysOf(AccessConstants.RoleCodes.STUDENT)).contains(personalAccountUpdate);
    }
}
```

Append to `backend/src/test/java/com/eduerp/modules/access/AccessConstantsTest.java`:

```java
    /**
     * Spec mục 6 bước 1: ba rule của GRADE KHÔNG mang marker {@code @ORGANIZATION}, khác MỌI rule
     * khác trong hệ thống. Thêm marker vào đây là chặn giáo viên ngay ở cửa
     * {@code @PreAuthorize} - họ chỉ có scope PERSONAL - và toàn bộ Phân hệ 4 trở thành chỉ admin
     * dùng được. Việc kiểm "lớp này có phải lớp của anh không" nằm ở
     * {@code ClassGradeAccessGuard}, không ở annotation.
     */
    @Test
    void gradeRulesDeliberatelyOmitTheMinimumOrganizationScopeMarker() {
        assertThat(AccessConstants.AccessRules.READ_GRADE)
                .contains("GRADE", "READ")
                .doesNotContain("@" + AccessConstants.ScopeNames.ORGANIZATION);
        assertThat(AccessConstants.AccessRules.UPDATE_GRADE)
                .contains("GRADE", "UPDATE")
                .doesNotContain("@" + AccessConstants.ScopeNames.ORGANIZATION);
        assertThat(AccessConstants.AccessRules.CREATE_GRADE)
                .contains("GRADE", "CREATE")
                .doesNotContain("@" + AccessConstants.ScopeNames.ORGANIZATION);
    }

    /** Resource GRADE phải là một tên tài nguyên thật, dùng chung giữa seed migration và guard. */
    @Test
    void gradeIsAKnownResourceName() {
        assertThat(AccessConstants.Resources.GRADE).isEqualTo("GRADE");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest='AccessConstantsTest,AcademicRbacSeedIT'`
Expected: FAIL at compile — `cannot find symbol: variable READ_GRADE`.

- [ ] **Step 3: Add the three AccessRules constants**

In `backend/src/main/java/com/eduerp/modules/access/AccessConstants.java`, add to `AccessRules` after `UPDATE_INVOICE`:

```java
        /**
         * Ba rule DUY NHẤT trong hệ thống KHÔNG có {@link #MINIMUM_SCOPE}, và đó là chủ đích (spec
         * mục 6 bước 1): giáo viên chỉ được cấp {@code GRADE:*:PERSONAL}, thêm marker
         * {@code @ORGANIZATION} vào đây là chặn họ ngay ở cửa và Phân hệ 4 chỉ còn admin dùng được.
         * Câu hỏi "lớp này có phải lớp của anh không" mà annotation không trả lời được nằm ở
         * {@code modules.academic.internal.access.ClassGradeAccessGuard}.
         */
        public static final String READ_GRADE = CHECK_PREFIX + Resources.GRADE + CHECK_SEPARATOR
                + Actions.READ + CHECK_SUFFIX;
        public static final String UPDATE_GRADE = CHECK_PREFIX + Resources.GRADE + CHECK_SEPARATOR
                + Actions.UPDATE + CHECK_SUFFIX;
        /**
         * Chưa có endpoint nào gác bằng rule này ở Phân hệ 4: nhập điểm là upsert nên đi theo
         * {@link #UPDATE_GRADE}. Hằng vẫn tồn tại vì spec mục 8 yêu cầu seed {@code (GRADE, CREATE)}
         * cho nhóm admin - có hằng thì quyền đã seed luôn có một tên biên dịch được đối chiếu
         * ({@code AcademicRbacSeedIT}), thay vì chỉ là một chuỗi rời trong file SQL.
         */
        public static final String CREATE_GRADE = CHECK_PREFIX + Resources.GRADE + CHECK_SEPARATOR
                + Actions.CREATE + CHECK_SUFFIX;
```

- [ ] **Step 4: Write the seed migration**

Create `backend/src/main/resources/db/migration/V25__seed_academic_rbac.sql`:

```sql
INSERT INTO permissions (id, resource, action) VALUES
    (gen_random_uuid(), 'GRADE', 'CREATE'),
    (gen_random_uuid(), 'GRADE', 'READ'),
    (gen_random_uuid(), 'GRADE', 'UPDATE');

-- V5 đã seed "Toàn quyền hệ thống" xong và đã chạy rồi - không sửa lại được, nên permission mới
-- phải tự thêm dòng gán vào đúng group đó, giống cách V10/V13/V14/V19 đã làm.
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000001', p.id, 'ORGANIZATION'
FROM permissions p WHERE p.resource = 'GRADE';

-- Đây là lần đầu một role khác ADMIN được cấp quyền cho một resource mới (spec mục 8), và nó KHÔNG
-- đi qua group "Tự phục vụ cơ bản" ('...0002') như ACCOUNT:READ/UPDATE:PERSONAL của V5: V5 gắn group
-- đó cho CẢ role TEACHER ('...0002') VÀ role STUDENT ('...0003') trong role_permission_groups, nên
-- nhét GRADE vào đó là cấp luôn cho học viên - trái spec mục 1 ("học viên KHÔNG tự xem được
-- điểm/tiến trình của mình qua hệ thống ở giai đoạn này"). Vì vậy: group RIÊNG, chỉ gắn cho TEACHER.
-- AcademicRbacSeedIT.studentGetsNoGradePermissionAtAll chốt lại điều này.
INSERT INTO permission_groups (id, name, description) VALUES
    ('11111111-0000-0000-0000-000000000003', 'Giáo viên nhập điểm',
     'Xem và nhập điểm cho lớp mình được phân công dạy, scope PERSONAL');

-- Chỉ READ + UPDATE: nhập điểm là upsert (UNIQUE enrollment+column của V24) nên đi theo UPDATE, và
-- CREATE ở đây sẽ là một quyền không endpoint nào dùng. CREATE chỉ nằm ở nhóm admin bên trên.
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000003', p.id, 'PERSONAL'
FROM permissions p WHERE p.resource = 'GRADE' AND p.action IN ('READ', 'UPDATE');

INSERT INTO role_permission_groups (role_id, permission_group_id) VALUES
    ('22222222-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000003');
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest='AccessConstantsTest,AcademicRbacSeedIT,DefaultRbacSeedIT,EnrollmentBillingRbacSeedIT'`
Expected: PASS — 2 test mới của `AccessConstantsTest` + 5 test của `AcademicRbacSeedIT` + các seed test cũ không đổi, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/access/AccessConstants.java \
        backend/src/main/resources/db/migration/V25__seed_academic_rbac.sql \
        backend/src/test/java/com/eduerp/modules/access/AccessConstantsTest.java \
        backend/src/test/java/com/eduerp/modules/access/internal/repository/AcademicRbacSeedIT.java
git commit -m "feat(access): seed grade permissions for admins and assigned teachers"
```

---

## Task 16: `GradeAdminController` — ba endpoint JSON

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/academic/web/GradeAdminController.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/web/package-info.java`
- Test: `backend/src/test/java/com/eduerp/modules/academic/web/GradeAdminControllerIT.java`

**Interfaces:**
- Consumes: `RecordGrade.execute` (Task 12), `GetClassGradebook.execute` (Task 13), `GetClassProgress.execute` (Task 14), `AccessConstants.AccessRules.READ_GRADE/UPDATE_GRADE` (Task 15), `shared.AccountPrincipal(UUID accountId, UUID homeBranchId)`.
- Produces: `POST /api/academic/grades`, `GET /api/academic/classes/{classId}/gradebook`, `GET /api/academic/classes/{classId}/progress`.

> `POST` (không `PUT`) cho việc nhập điểm: `apiClient` của frontend chỉ phơi `get`/`post`/`patch`/
> `postForm`/`patchForm`/`getBlob`, không có `put`. Nghiệp vụ là upsert idempotent theo khóa
> `(enrollmentId, courseGradeColumnId)`, và cả hệ thống này chưa dùng `PUT` ở đâu.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/academic/web/GradeAdminControllerIT.java`:

```java
package com.eduerp.modules.academic.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.academic.dto.RecordGradeRequest;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
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
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * Review Focus #4 + #5 end-to-end qua HTTP thật, với quyền thật đọc từ Redis: đây là chỗ duy nhất
 * chứng minh rằng {@code @PreAuthorize} KHÔNG chặn giáo viên scope PERSONAL (cửa mở) nhưng guard
 * trong usecase VẪN chặn giáo viên sai lớp (403). Mirror {@code ClassAdminControllerIT} về cách
 * đăng nhập bằng cookie.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class GradeAdminControllerIT {

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
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    @Autowired
    BranchRepository branches;

    @Autowired
    EnrollmentRepository enrollments;

    @Autowired
    StudentProfileRepository profiles;

    @Autowired
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    private UUID assignedTeacherId;
    private UUID classId;
    private UUID enrollmentId;
    private UUID midtermColumnId;
    private UUID finalColumnId;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private UUID newAccount(String emailPrefix, String fullName) {
        return accounts.save(new Account(emailPrefix + "-" + shortId() + "@eduerp.local",
                passwordEncoder.encode(PASSWORD), fullName, null)).getId();
    }

    private Cookie signIn(UUID accountId, String roleCode) throws Exception {
        var account = accounts.findById(accountId).orElseThrow();
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(accountId, roleCode);
        access.evictPermissionCache(accountId);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(account.getEmail(), PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    @BeforeEach
    void seedAClassWithOneStudentAndTwoGradeColumns() {
        var branchId = branches.save(new Branch("GB-" + shortId(), "Chi nhánh sổ điểm", null)).getId();
        assignedTeacherId = newAccount("gb-gv", "Cô Lan");
        var course = new Course("GB-C-" + shortId(), "Khoá có sổ điểm", null, 24, null);
        course.addGradeColumn("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
        course.addGradeColumn("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1);
        var savedCourse = courses.saveAndFlush(course);
        midtermColumnId = savedCourse.getGradeColumns().get(0).getId();
        finalColumnId = savedCourse.getGradeColumns().get(1).getId();
        var cls = new Class(savedCourse, "GB-K-" + shortId(), branchId, assignedTeacherId, 20);
        cls.addSchedule(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0));
        classId = classes.save(cls).getId();
        var studentProfileId = profiles.save(
                new StudentProfile(newAccount("gb-hv", "Nguyễn Văn A"), null, null, null)).getId();
        enrollmentId = enrollments.saveAndFlush(new Enrollment(studentProfileId, classId,
                savedCourse.getId(), branchId, assignedTeacherId)).getId();
    }

    private int recordGrade(Cookie cookie, UUID columnId, String score) throws Exception {
        return mockMvc.perform(post("/api/academic/grades")
                        .cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecordGradeRequest(classId,
                                enrollmentId, columnId, new BigDecimal(score)))))
                .andReturn().getResponse().getStatus();
    }

    /** Review Focus #4, chiều hợp lệ: giáo viên ĐÚNG lớp, chỉ có GRADE:*:PERSONAL, nhập điểm được và
     * đọc được bảng điểm. */
    @Test
    void allowsTheAssignedTeacherToRecordAndReadGrades() throws Exception {
        var teacher = signIn(assignedTeacherId, AccessConstants.RoleCodes.TEACHER);

        assertThat(recordGrade(teacher, midtermColumnId, "8.00")).isEqualTo(200);
        assertThat(recordGrade(teacher, finalColumnId, "9.00")).isEqualTo(200);

        var result = mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook")
                .cookie(teacher)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("columns")).hasSize(2);
        assertThat(body.get("rows")).hasSize(1);
        assertThat(body.get("rows").get(0).get("studentName").asText()).isEqualTo("Nguyễn Văn A");
        assertThat(body.get("rows").get(0).get("finalScore").asDouble())
                .as("8/10*40 + 9/10*60 = 86")
                .isEqualTo(86.0);
    }

    /**
     * Review Focus #4, chiều bị chặn và là khẳng định quan trọng nhất của cả Phân hệ 4: giáo viên
     * này có ĐÚNG cùng bộ quyền {@code GRADE:READ/UPDATE:PERSONAL} như người trên (cùng role
     * TEACHER), nên {@code @PreAuthorize} cho qua - chỉ guard trong usecase chặn lại.
     */
    @Test
    void refusesATeacherWhoIsNotAssignedToTheClass() throws Exception {
        var outsider = signIn(newAccount("gb-gv-outsider", "Thầy Nam"),
                AccessConstants.RoleCodes.TEACHER);

        assertThat(recordGrade(outsider, midtermColumnId, "8.00")).isEqualTo(403);

        var gradebook = mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook")
                .cookie(outsider)).andReturn();
        assertThat(gradebook.getResponse().getStatus()).isEqualTo(403);
        assertThat(gradebook.getResponse().getContentAsString())
                .contains("ACADEMIC_NOT_ASSIGNED_TO_CLASS");

        var progress = mockMvc.perform(get("/api/academic/classes/" + classId + "/progress")
                .cookie(outsider)).andReturn();
        assertThat(progress.getResponse().getStatus()).isEqualTo(403);
    }

    /** Review Focus #5: admin scope ORGANIZATION thao tác được trên lớp mình không dạy. */
    @Test
    void allowsAnAdminOnAClassTheyDoNotTeach() throws Exception {
        var admin = signIn(newAccount("gb-admin", "Quản trị"), AccessConstants.RoleCodes.ADMIN);

        assertThat(recordGrade(admin, midtermColumnId, "7.50")).isEqualTo(200);
        assertThat(mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook")
                .cookie(admin)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(get("/api/academic/classes/" + classId + "/progress")
                .cookie(admin)).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    /** Global Constraint: học viên không có quyền GRADE nào nên bị chặn ngay ở cửa
     * {@code @PreAuthorize} (403), không cần tới guard. */
    @Test
    void refusesAStudentEntirely() throws Exception {
        var student = signIn(newAccount("gb-student", "Học viên tò mò"),
                AccessConstants.RoleCodes.STUDENT);

        assertThat(recordGrade(student, midtermColumnId, "10.00")).isEqualTo(403);
        assertThat(mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook")
                .cookie(student)).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    /** Review Focus #3 qua HTTP: thiếu cột cuối kỳ thì finalScore là null trong JSON, không phải 32. */
    @Test
    void returnsANullFinalScoreWhenOneColumnHasNoScore() throws Exception {
        var teacher = signIn(assignedTeacherId, AccessConstants.RoleCodes.TEACHER);
        assertThat(recordGrade(teacher, midtermColumnId, "8.00")).isEqualTo(200);

        var result = mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook")
                .cookie(teacher)).andReturn();

        var row = objectMapper.readTree(result.getResponse().getContentAsString()).get("rows").get(0);
        assertThat(row.get("finalScore").isNull()).isTrue();
        assertThat(row.get("scoresByColumnId").size()).isEqualTo(1);
    }

    /** Review Focus #10 qua HTTP: điểm vượt thang của cột là 400 có errorCode, không phải 500. */
    @Test
    void rejectsAScoreAboveTheColumnMaxWithAProblemDetail() throws Exception {
        var teacher = signIn(assignedTeacherId, AccessConstants.RoleCodes.TEACHER);

        var result = mockMvc.perform(post("/api/academic/grades")
                        .cookie(teacher).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecordGradeRequest(classId,
                                enrollmentId, midtermColumnId, new BigDecimal("10.50")))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("ACADEMIC_INVALID_SCORE");
    }

    /** Lớp không tồn tại là 404, phân biệt rõ với 403 "không phải lớp của anh". */
    @Test
    void reportsAnUnknownClassAsNotFoundForAnAdmin() throws Exception {
        var admin = signIn(newAccount("gb-admin-404", "Quản trị"), AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(get("/api/academic/classes/" + UUID.randomUUID() + "/gradebook")
                .cookie(admin)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("ACADEMIC_CLASS_NOT_FOUND");
    }

    /** Review Focus #1 + #8 qua HTTP: bảng tiến trình trả số thật và nói rõ tổng số buổi chuẩn. */
    @Test
    void returnsTheProgressTableWithElapsedSessionsAndStandardCount() throws Exception {
        var teacher = signIn(assignedTeacherId, AccessConstants.RoleCodes.TEACHER);

        var result = mockMvc.perform(get("/api/academic/classes/" + classId + "/progress")
                .cookie(teacher)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("standardSessionCount").asInt()).isEqualTo(24);
        assertThat(body.get("rows")).hasSize(1);
        var row = body.get("rows").get(0);
        assertThat(row.get("studentName").asText()).isEqualTo("Nguyễn Văn A");
        assertThat(row.get("elapsedSessions").isInt()).isTrue();
        assertThat(row.get("progressPercent").asInt()).isBetween(0, 100);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=GradeAdminControllerIT`
Expected: FAIL — 404 trên mọi endpoint (`/api/academic/...` chưa tồn tại), `allowsTheAssignedTeacherToRecordAndReadGrades` đỏ ở khẳng định `isEqualTo(200)`.

- [ ] **Step 3: Write the controller**

Create `backend/src/main/java/com/eduerp/modules/academic/web/package-info.java`:

```java
/** Adapter HTTP của module academic: controller mỏng (rule #8 — không chứa nghiệp vụ). */
package com.eduerp.modules.academic.web;
```

Create `backend/src/main/java/com/eduerp/modules/academic/web/GradeAdminController.java`:

```java
package com.eduerp.modules.academic.web;

import com.eduerp.modules.academic.dto.ClassProgressResponse;
import com.eduerp.modules.academic.dto.GradebookResponse;
import com.eduerp.modules.academic.dto.RecordGradeRequest;
import com.eduerp.modules.academic.usecase.GetClassGradebook;
import com.eduerp.modules.academic.usecase.GetClassProgress;
import com.eduerp.modules.academic.usecase.RecordGrade;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.shared.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sổ điểm và tiến trình học tập theo lớp. Ba {@code @PreAuthorize} ở đây KHÔNG đòi scope
 * {@code ORGANIZATION} (khác mọi controller khác trong dự án) vì giáo viên chỉ có
 * {@code GRADE:*:PERSONAL} - xem javadoc {@code AccessConstants.AccessRules.READ_GRADE}. Việc kiểm
 * "lớp này có phải lớp của anh không" nằm trong từng usecase qua
 * {@code ClassGradeAccessGuard}, nên không có endpoint nào ở đây bỏ qua được bước đó.
 */
@RestController
@RequestMapping("/api/academic")
class GradeAdminController {

    private final RecordGrade recordGrade;
    private final GetClassGradebook getClassGradebook;
    private final GetClassProgress getClassProgress;

    GradeAdminController(RecordGrade recordGrade, GetClassGradebook getClassGradebook,
            GetClassProgress getClassProgress) {
        this.recordGrade = recordGrade;
        this.getClassGradebook = getClassGradebook;
        this.getClassProgress = getClassProgress;
    }

    /** POST (không PUT): upsert idempotent theo {@code (enrollmentId, courseGradeColumnId)}, và
     * {@code apiClient} ở frontend không phơi method PUT nào. */
    @PostMapping("/grades")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_GRADE)
    void record(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody RecordGradeRequest request) {
        recordGrade.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @GetMapping("/classes/{classId}/gradebook")
    @PreAuthorize(AccessConstants.AccessRules.READ_GRADE)
    GradebookResponse gradebook(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID classId) {
        return getClassGradebook.execute(principal.accountId(), classId);
    }

    @GetMapping("/classes/{classId}/progress")
    @PreAuthorize(AccessConstants.AccessRules.READ_GRADE)
    ClassProgressResponse progress(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID classId) {
        return getClassProgress.execute(principal.accountId(), classId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GradeAdminControllerIT`
Expected: PASS — 8 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/academic/web/ \
        backend/src/test/java/com/eduerp/modules/academic/web/GradeAdminControllerIT.java
git commit -m "feat(academic): expose gradebook, progress and grade recording endpoints"
```

---

## Task 17: Audit cho `GradeRecorded`

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/audit/AuditConstants.java`
- Modify: `backend/src/main/java/com/eduerp/modules/audit/internal/listener/AuditEventListeners.java`
- Modify: `backend/src/test/java/com/eduerp/modules/audit/internal/listener/AuditEventListenersTest.java`
- Test: `backend/src/test/java/com/eduerp/modules/audit/AcademicEventAuditingIT.java`

**Interfaces:**
- Consumes: `AcademicEvents.GradeRecorded(UUID enrollmentId, UUID courseGradeColumnId, UUID actorAccountId, UUID actorBranchId)` (Task 7), `AuditLog(UUID actorAccountId, String action, String entityType, String entityId, UUID branchId)` (hiện có), `AuditManagement.recentActions(String entityType, String action, int limit)`.
- Produces: `AuditConstants.Actions.GRADE_RECORD`, `AuditConstants.EntityTypes.GRADE_ENTRY`, `AuditEventListeners.on(AcademicEvents.GradeRecorded)`.

- [ ] **Step 1: Write the failing tests**

Append to `backend/src/test/java/com/eduerp/modules/audit/internal/listener/AuditEventListenersTest.java` (thêm import `com.eduerp.modules.academic.AcademicEvents`):

```java
    /**
     * Spec mục 9. {@code entityId} là {@code enrollmentId} chứ không phải id của dòng
     * {@code GradeEntry}: sự kiện không mang id đó (upsert nên nó chưa chắc tồn tại lúc publish), và
     * câu hỏi mà người đọc nhật ký đặt ra luôn là "điểm của lượt ghi danh nào vừa bị sửa".
     * {@code courseGradeColumnId} có trong sự kiện nhưng {@code audit_logs} chỉ có một cột
     * {@code entity_id} - không nhồi hai id vào một cột.
     */
    @Test
    void logsAGradeRecordAgainstTheEnrolmentItBelongsTo() {
        var enrollmentId = UUID.randomUUID();

        listeners.on(new AcademicEvents.GradeRecorded(enrollmentId, UUID.randomUUID(), actorAccountId,
                actorBranchId));

        var log = captureSavedLog();
        assertThat(log.getAction()).isEqualTo(AuditConstants.Actions.GRADE_RECORD);
        assertThat(log.getEntityType()).isEqualTo(AuditConstants.EntityTypes.GRADE_ENTRY);
        assertThat(log.getEntityId()).isEqualTo(enrollmentId.toString());
        assertThat(log.getActorAccountId()).isEqualTo(actorAccountId);
        assertThat(log.getBranchId()).isEqualTo(actorBranchId);
    }
```

Create `backend/src/test/java/com/eduerp/modules/audit/AcademicEventAuditingIT.java`:

```java
package com.eduerp.modules.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.academic.AcademicEvents;
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
 * {@code EnrollmentBillingEventAuditingIT}.
 */
@Testcontainers
@SpringBootTest
class AcademicEventAuditingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

    @TestConfiguration
    static class TransactionalPublisherConfig {
        @Bean
        TransactionalPublisher academicPublisher(ApplicationEventPublisher events) {
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

    @Test
    void auditsGradeRecorded() {
        var enrollmentId = UUID.randomUUID();

        events.publish(new AcademicEvents.GradeRecorded(enrollmentId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID()));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var entityIds = audit.recentActions(AuditConstants.EntityTypes.GRADE_ENTRY,
                            AuditConstants.Actions.GRADE_RECORD, 50).stream()
                    .map(action -> action.entityId()).toList();
            assertThat(entityIds).contains(enrollmentId.toString());
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest='AuditEventListenersTest,AcademicEventAuditingIT'`
Expected: FAIL at compile — `cannot find symbol: variable GRADE_RECORD`, `cannot find symbol: variable GRADE_ENTRY`, `no suitable method found for on(AcademicEvents.GradeRecorded)`.

- [ ] **Step 3: Add the audit vocabulary**

In `backend/src/main/java/com/eduerp/modules/audit/AuditConstants.java`, add to `Actions` after `COMBO_CANCEL`:

```java
        public static final String GRADE_RECORD = "GRADE_RECORD";
```

and to `EntityTypes` after `COMBO`:

```java
        public static final String GRADE_ENTRY = "GRADE_ENTRY";
```

- [ ] **Step 4: Add the listener**

In `backend/src/main/java/com/eduerp/modules/audit/internal/listener/AuditEventListeners.java`, add the import `com.eduerp.modules.academic.AcademicEvents` and this method at the end of the class:

```java
    /**
     * Spec mục 9. Điểm là một con số có hệ quả với học viên nên mọi lần nhập/sửa phải có dấu vết -
     * mirror 1:1 các listener của enrollment/billing. {@code entityId} là {@code enrollmentId}:
     * {@code audit_logs} chỉ có một cột {@code entity_id}, và câu hỏi người đọc nhật ký đặt ra là
     * "điểm của lượt ghi danh nào vừa bị sửa", không phải "dòng grade_entries nào".
     */
    @ApplicationModuleListener
    void on(AcademicEvents.GradeRecorded event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.GRADE_RECORD,
                AuditConstants.EntityTypes.GRADE_ENTRY, event.enrollmentId().toString(),
                event.actorBranchId()));
    }
```

- [ ] **Step 5: Update the audit module's package-info**

In `backend/src/main/java/com/eduerp/modules/audit/package-info.java`, add `{@code AcademicEvents}` to the list of event types the module listens to (sentence that currently reads `({@code IdentityEvents}, {@code AccessEvents}, ...)`), so the dependency is documented where every other one is.

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest='AuditEventListenersTest,AcademicEventAuditingIT'`
Expected: PASS — 1 test mới của listener + 1 IT, cộng các test cũ, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/audit/ \
        backend/src/test/java/com/eduerp/modules/audit/internal/listener/AuditEventListenersTest.java \
        backend/src/test/java/com/eduerp/modules/audit/AcademicEventAuditingIT.java
git commit -m "feat(audit): log every grade record against its enrolment"
```

---

## Task 18: Apache POI + `AcademicWorkbooks`

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/eduerp/modules/academic/internal/excel/AcademicWorkbooks.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/internal/excel/package-info.java`
- Test: `backend/src/test/java/com/eduerp/modules/academic/internal/excel/AcademicWorkbooksTest.java`

**Interfaces:**
- Consumes: `dto.GradebookResponse`/`dto.StudentGradesRow`/`dto.ClassProgressResponse`/`dto.StudentProgressRow` (Task 12), `courses.dto.GradeColumnResponse` (Task 3), `AcademicConstants.Excel.*` (Task 7).
- Produces: `AcademicWorkbooks.gradebook(GradebookResponse gradebook): byte[]`; `AcademicWorkbooks.progress(ClassProgressResponse progress): byte[]`.

> **Phiên bản POI: `5.4.1`.** Đây là bản vá cuối của dòng 5.4 (phát hành 2025-04-01), đã có hơn một
> năm sử dụng thực tế, chạy Java 21 bình thường. Transitive của nó (`commons-compress` 1.27.1,
> `commons-io` 2.18.0, `commons-collections4` 4.4, `log4j-api` qua log4j-bom 2.24.3) sát với những gì
> BOM Spring Boot 3.4.1 đang quản; dòng 5.5.x nhảy `commons-collections4` lên 4.5.0 và `commons-io`
> lên 2.21.0, một thay đổi transitive lớn hơn mức cần thiết để ghi một file `.xlsx` phẳng. POI không
> nằm trong `dependencyManagement` của Spring Boot nên version phải ghi tường minh, đúng cách
> `springdoc`, `jjwt`, `awssdk s3` đã làm trong `pom.xml` này.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/eduerp/modules/academic/internal/excel/AcademicWorkbooksTest.java`:

```java
package com.eduerp.modules.academic.internal.excel;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.academic.dto.ClassProgressResponse;
import com.eduerp.modules.academic.dto.GradebookResponse;
import com.eduerp.modules.academic.dto.StudentGradesRow;
import com.eduerp.modules.academic.dto.StudentProgressRow;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** Đọc lại chính file vừa sinh bằng POI: khẳng định trên nội dung thật, không phải trên độ dài byte. */
class AcademicWorkbooksTest {

    private final UUID midtermId = UUID.randomUUID();
    private final UUID finalId = UUID.randomUUID();

    private final List<GradeColumnResponse> columns = List.of(
            new GradeColumnResponse(midtermId, "Giữa kỳ", new BigDecimal("40.00"),
                    new BigDecimal("10.00"), 0),
            new GradeColumnResponse(finalId, "Cuối kỳ", new BigDecimal("60.00"),
                    new BigDecimal("10.00"), 1));

    /** Mỗi helper tự mở rồi đóng workbook và chỉ trả về GIÁ TRỊ đã đọc: trả về một {@code Sheet}
     * sau khi {@code XSSFWorkbook} đã đóng là đọc trên object đã giải phóng. */
    private static List<String> cellTextsOf(byte[] content, int rowIndex) throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Row row = workbook.getSheetAt(0).getRow(rowIndex);
            var texts = new java.util.ArrayList<String>();
            for (int cell = 0; cell < row.getLastCellNum(); cell++) {
                var current = row.getCell(cell);
                texts.add(current == null ? "" : switch (current.getCellType()) {
                    case STRING -> current.getStringCellValue();
                    case NUMERIC -> new BigDecimal(String.valueOf(current.getNumericCellValue()))
                            .stripTrailingZeros().toPlainString();
                    default -> "";
                });
            }
            return texts;
        }
    }

    private static String sheetNameOf(byte[] content) throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            return workbook.getSheetName(0);
        }
    }

    private GradebookResponse gradebookWith(StudentGradesRow... rows) {
        return new GradebookResponse(UUID.randomUUID(), UUID.randomUUID(), columns, List.of(rows));
    }

    @Test
    void writesTheGradebookHeaderFromTheConfiguredColumnsInOrder() throws Exception {
        var content = AcademicWorkbooks.gradebook(gradebookWith());

        assertThat(sheetNameOf(content)).isEqualTo(AcademicConstants.Excel.GRADEBOOK_SHEET_NAME);
        assertThat(cellTextsOf(content, 0)).containsExactly(
                AcademicConstants.Excel.HEADER_STUDENT_NAME, "Giữa kỳ", "Cuối kỳ",
                AcademicConstants.Excel.HEADER_FINAL_SCORE);
    }

    @Test
    void writesOneRowPerStudentWithScoresUnderTheMatchingColumn() throws Exception {
        var content = AcademicWorkbooks.gradebook(gradebookWith(new StudentGradesRow(
                UUID.randomUUID(), UUID.randomUUID(), "Nguyễn Văn A",
                EnrollmentConstants.EnrollmentStatus.ACTIVE,
                Map.of(midtermId, new BigDecimal("8.00"), finalId, new BigDecimal("9.00")),
                new BigDecimal("86.00"))));

        assertThat(cellTextsOf(content, 1))
                .containsExactly("Nguyễn Văn A", "8", "9", "86");
    }

    /** Review Focus #3 trong file Excel: ô điểm tổng kết KHÔNG được in "null" hay 0 - nó phải nói
     * thành chữ rằng chưa đủ điểm, và ô cột thiếu điểm để trống. */
    @Test
    void writesAReadableLabelInsteadOfNullForAMissingFinalScore() throws Exception {
        var content = AcademicWorkbooks.gradebook(gradebookWith(new StudentGradesRow(
                UUID.randomUUID(), UUID.randomUUID(), "Trần Thị B",
                EnrollmentConstants.EnrollmentStatus.ACTIVE,
                Map.of(midtermId, new BigDecimal("8.00")), null)));

        assertThat(cellTextsOf(content, 1)).containsExactly("Trần Thị B", "8", "",
                AcademicConstants.Excel.CELL_NOT_ENOUGH_SCORES);
    }

    /** Học viên không tra được tên vẫn phải có một dòng, ô tên để trống chứ không in "null". */
    @Test
    void writesAnEmptyNameCellWhenTheStudentNameIsUnknown() throws Exception {
        var content = AcademicWorkbooks.gradebook(gradebookWith(new StudentGradesRow(
                UUID.randomUUID(), UUID.randomUUID(), null,
                EnrollmentConstants.EnrollmentStatus.ACTIVE, Map.of(), null)));

        assertThat(cellTextsOf(content, 1).get(0)).isEmpty();
    }

    /** Review Focus #9: khóa chưa cấu hình cột điểm - file vẫn mở được, chỉ có hai cột khung. */
    @Test
    void writesAGradebookWithoutAnyConfiguredColumn() throws Exception {
        var content = AcademicWorkbooks.gradebook(new GradebookResponse(UUID.randomUUID(),
                UUID.randomUUID(), List.of(), List.of()));

        assertThat(cellTextsOf(content, 0)).containsExactly(
                AcademicConstants.Excel.HEADER_STUDENT_NAME,
                AcademicConstants.Excel.HEADER_FINAL_SCORE);
    }

    @Test
    void writesTheProgressHeaderAndRow() throws Exception {
        var content = AcademicWorkbooks.progress(new ClassProgressResponse(UUID.randomUUID(),
                UUID.randomUUID(), 24, List.of(new StudentProgressRow(UUID.randomUUID(),
                        UUID.randomUUID(), "Nguyễn Văn A",
                        EnrollmentConstants.EnrollmentStatus.ACTIVE,
                        Instant.parse("2026-01-05T01:00:00Z"), null, 6, 24, 25))));

        assertThat(sheetNameOf(content)).isEqualTo(AcademicConstants.Excel.PROGRESS_SHEET_NAME);
        assertThat(cellTextsOf(content, 0)).containsExactly(
                AcademicConstants.Excel.HEADER_STUDENT_NAME, AcademicConstants.Excel.HEADER_STATUS,
                AcademicConstants.Excel.HEADER_ENROLLED_AT,
                AcademicConstants.Excel.HEADER_ELAPSED_SESSIONS,
                AcademicConstants.Excel.HEADER_STANDARD_SESSIONS,
                AcademicConstants.Excel.HEADER_PROGRESS_PERCENT);
        var row = cellTextsOf(content, 1);
        assertThat(row.get(0)).isEqualTo("Nguyễn Văn A");
        assertThat(row.get(1)).isEqualTo(EnrollmentConstants.EnrollmentStatus.ACTIVE.name());
        assertThat(row.get(3)).isEqualTo("6");
        assertThat(row.get(4)).isEqualTo("24");
        assertThat(row.get(5)).isEqualTo("25");
    }

    /** Review Focus #8 trong file Excel: chưa cấu hình tổng số buổi chuẩn thì ô % phải nói thành chữ,
     * không in 0 (người đọc sẽ hiểu sai là học viên chưa học buổi nào). */
    @Test
    void writesAReadableLabelInsteadOfZeroWhenTheSessionCountIsNotConfigured() throws Exception {
        var content = AcademicWorkbooks.progress(new ClassProgressResponse(UUID.randomUUID(),
                UUID.randomUUID(), null, List.of(new StudentProgressRow(UUID.randomUUID(),
                        UUID.randomUUID(), "Nguyễn Văn A",
                        EnrollmentConstants.EnrollmentStatus.ACTIVE,
                        Instant.parse("2026-01-05T01:00:00Z"), null, 6, null, null))));

        var row = cellTextsOf(content, 1);
        assertThat(row.get(3)).as("số buổi đã học vẫn là số thật").isEqualTo("6");
        assertThat(row.get(4)).isEmpty();
        assertThat(row.get(5)).isEqualTo(AcademicConstants.Excel.CELL_SESSION_COUNT_NOT_CONFIGURED);
    }

    @Test
    void writesAProgressSheetForAClassWithoutAnyStudent() throws Exception {
        var content = AcademicWorkbooks.progress(new ClassProgressResponse(UUID.randomUUID(),
                UUID.randomUUID(), 24, List.of()));

        assertThat(content).isNotEmpty();
        assertThat(cellTextsOf(content, 0)).hasSize(6);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=AcademicWorkbooksTest`
Expected: FAIL at compile — `package org.apache.poi.xssf.usermodel does not exist`.

- [ ] **Step 3: Add the dependency**

In `backend/pom.xml`, add a property next to `java.version`/`spring-modulith.version`:

```xml
    <poi.version>5.4.1</poi.version>
```

and this dependency block right after the `software.amazon.awssdk:s3` block (giữ đúng thụt lề 4 khoảng trắng của file):

```xml
    <dependency>
      <groupId>org.apache.poi</groupId>
      <artifactId>poi-ooxml</artifactId>
      <version>${poi.version}</version>
    </dependency>
```

- [ ] **Step 4: Write the workbook writer**

Create `backend/src/main/java/com/eduerp/modules/academic/internal/excel/package-info.java`:

```java
package com.eduerp.modules.academic.internal.excel;
```

Create `backend/src/main/java/com/eduerp/modules/academic/internal/excel/AcademicWorkbooks.java`:

```java
package com.eduerp.modules.academic.internal.excel;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.academic.dto.ClassProgressResponse;
import com.eduerp.modules.academic.dto.GradebookResponse;
import com.eduerp.modules.academic.dto.StudentGradesRow;
import com.eduerp.modules.academic.dto.StudentProgressRow;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Serialize hai báo cáo của spec mục 7 thành {@code .xlsx}. Chỉ BIẾN ĐỔI ĐỊNH DẠNG: mọi con số đi
 * vào đây đã được {@code GetClassGradebook}/{@code GetClassProgress} tính xong, class này không
 * cộng, chia hay làm tròn gì - đó là cách duy nhất bảo đảm số trên web khớp số trong file
 * (spec mục 11.7).
 *
 * <p>Ô trống không in "null": {@code finalScore} null nghĩa là "chưa đủ điểm" và
 * {@code progressPercent} null nghĩa là "chưa cấu hình tổng số buổi chuẩn" - hai câu đó phải đọc
 * được bằng tiếng Việt trong file, không phải suy từ một ô rỗng.
 */
public final class AcademicWorkbooks {

    private AcademicWorkbooks() {
    }

    /** Bảng điểm: Học viên | &lt;từng cột điểm theo displayOrder&gt; | Điểm tổng kết. */
    public static byte[] gradebook(GradebookResponse gradebook) {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet(AcademicConstants.Excel.GRADEBOOK_SHEET_NAME);
            writeGradebookHeader(sheet, gradebook.columns());
            for (int index = 0; index < gradebook.rows().size(); index++) {
                writeGradebookRow(sheet.createRow(index + 1), gradebook.rows().get(index),
                        gradebook.columns());
            }
            autoSize(sheet, gradebook.columns().size() + 2);
            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Bảng tiến trình: Học viên | Trạng thái | Ngày ghi danh | Số buổi đã học | Tổng số buổi chuẩn | %. */
    public static byte[] progress(ClassProgressResponse progress) {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet(AcademicConstants.Excel.PROGRESS_SHEET_NAME);
            writeRowOfTexts(sheet.createRow(0), List.of(
                    AcademicConstants.Excel.HEADER_STUDENT_NAME,
                    AcademicConstants.Excel.HEADER_STATUS,
                    AcademicConstants.Excel.HEADER_ENROLLED_AT,
                    AcademicConstants.Excel.HEADER_ELAPSED_SESSIONS,
                    AcademicConstants.Excel.HEADER_STANDARD_SESSIONS,
                    AcademicConstants.Excel.HEADER_PROGRESS_PERCENT));
            for (int index = 0; index < progress.rows().size(); index++) {
                writeProgressRow(sheet.createRow(index + 1), progress.rows().get(index));
            }
            autoSize(sheet, 6);
            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeGradebookHeader(Sheet sheet, List<GradeColumnResponse> columns) {
        var headers = new java.util.ArrayList<String>();
        headers.add(AcademicConstants.Excel.HEADER_STUDENT_NAME);
        columns.forEach(column -> headers.add(column.name()));
        headers.add(AcademicConstants.Excel.HEADER_FINAL_SCORE);
        writeRowOfTexts(sheet.createRow(0), headers);
    }

    private static void writeGradebookRow(Row row, StudentGradesRow data,
            List<GradeColumnResponse> columns) {
        setText(row, 0, data.studentName());
        for (int index = 0; index < columns.size(); index++) {
            setNumberOrBlank(row, index + 1, data.scoresByColumnId().get(columns.get(index).id()));
        }
        int finalScoreAt = columns.size() + 1;
        if (data.finalScore() == null) {
            setText(row, finalScoreAt, AcademicConstants.Excel.CELL_NOT_ENOUGH_SCORES);
            return;
        }
        setNumberOrBlank(row, finalScoreAt, data.finalScore());
    }

    private static void writeProgressRow(Row row, StudentProgressRow data) {
        setText(row, 0, data.studentName());
        setText(row, 1, data.status().name());
        setText(row, 2, data.enrolledAt().toString());
        setNumberOrBlank(row, 3, BigDecimal.valueOf(data.elapsedSessions()));
        setNumberOrBlank(row, 4, data.standardSessionCount() == null ? null
                : BigDecimal.valueOf(data.standardSessionCount()));
        if (data.progressPercent() == null) {
            setText(row, 5, AcademicConstants.Excel.CELL_SESSION_COUNT_NOT_CONFIGURED);
            return;
        }
        setNumberOrBlank(row, 5, BigDecimal.valueOf(data.progressPercent()));
    }

    private static void writeRowOfTexts(Row row, List<String> texts) {
        for (int index = 0; index < texts.size(); index++) {
            setText(row, index, texts.get(index));
        }
    }

    /** {@code null} thành ô chuỗi RỖNG, không bao giờ thành chữ "null". */
    private static void setText(Row row, int at, String value) {
        row.createCell(at).setCellValue(value == null ? "" : value);
    }

    private static void setNumberOrBlank(Row row, int at, BigDecimal value) {
        var cell = row.createCell(at);
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        cell.setCellValue(value.doubleValue());
    }

    private static void autoSize(Sheet sheet, int columnCount) {
        for (int index = 0; index < columnCount; index++) {
            sheet.autoSizeColumn(index);
        }
    }

    private static byte[] toBytes(Workbook workbook) throws IOException {
        try (var out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=AcademicWorkbooksTest`
Expected: PASS — 8 tests, 0 failures.

- [ ] **Step 6: Confirm the new dependency did not disturb the BOM**

Run: `cd backend && ./mvnw -q dependency:tree -Dincludes=org.apache.poi:poi-ooxml,commons-io:commons-io,org.apache.logging.log4j:log4j-api`
Expected: `poi-ooxml:5.4.1` có mặt; `log4j-api` giữ version do BOM Spring Boot 3.4.1 quản (POI chạy bình thường với bản đó).

Run: `cd backend && ./mvnw -q clean compile test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/pom.xml \
        backend/src/main/java/com/eduerp/modules/academic/internal/excel/ \
        backend/src/test/java/com/eduerp/modules/academic/internal/excel/AcademicWorkbooksTest.java
git commit -m "feat(academic): render the gradebook and progress reports as xlsx workbooks"
```

---

## Task 19: Hai usecase export + hai endpoint tải file

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/academic/usecase/ExportClassGradebook.java`
- Create: `backend/src/main/java/com/eduerp/modules/academic/usecase/ExportClassProgress.java`
- Modify: `backend/src/main/java/com/eduerp/modules/academic/web/GradeAdminController.java`
- Test: `backend/src/test/java/com/eduerp/modules/academic/usecase/ExportUseCasesTest.java`
- Test: `backend/src/test/java/com/eduerp/modules/academic/web/GradeExportControllerIT.java`

**Interfaces:**
- Consumes: `GetClassGradebook.execute` (Task 13), `GetClassProgress.execute` (Task 14), `AcademicWorkbooks.gradebook`/`.progress` (Task 18), `AcademicConstants.Excel.GRADEBOOK_FILE_NAME`/`.PROGRESS_FILE_NAME`/`.CONTENT_TYPE` (Task 7), `dto.ExportedFileResponse` (Task 12).
- Produces: `ExportClassGradebook.execute(UUID actorAccountId, UUID classId): ExportedFileResponse`; `ExportClassProgress.execute(UUID actorAccountId, UUID classId): ExportedFileResponse`; `GET /api/academic/classes/{classId}/gradebook/export`, `GET /api/academic/classes/{classId}/progress/export`.

> Hai usecase này KHÔNG tự đọc dữ liệu: chúng gọi thẳng usecase đọc rồi serialize. Đó là cách thực
> thi spec mục 11.7 ở mức kiến trúc, không phải ở mức "nhớ đừng tính lại" — và kéo theo cả việc kiểm
> quyền, vì guard nằm trong usecase đọc (spec mục 7: "không có đường tắt tải Excel bỏ qua kiểm tra
> quyền").

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/java/com/eduerp/modules/academic/usecase/ExportUseCasesTest.java`:

```java
package com.eduerp.modules.academic.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.academic.NotAssignedToClassException;
import com.eduerp.modules.academic.dto.ClassProgressResponse;
import com.eduerp.modules.academic.dto.GradebookResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Review Focus #7 ở mức đơn vị: export PHẢI đi qua chính usecase đọc, không có đường dữ liệu riêng.
 * Hai khẳng định {@code verify(...)} dưới đây là thứ chặn một lần refactor tương lai âm thầm thay
 * {@code GetClassGradebook} bằng một query riêng "cho nhanh".
 */
class ExportUseCasesTest {

    private final GetClassGradebook getClassGradebook = mock(GetClassGradebook.class);
    private final GetClassProgress getClassProgress = mock(GetClassProgress.class);
    private final ExportClassGradebook exportGradebook = new ExportClassGradebook(getClassGradebook);
    private final ExportClassProgress exportProgress = new ExportClassProgress(getClassProgress);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();

    @Test
    void gradebookExportDelegatesToTheReadUseCaseAndNamesTheFile() {
        when(getClassGradebook.execute(actorAccountId, classId)).thenReturn(
                new GradebookResponse(classId, UUID.randomUUID(), List.of(), List.of()));

        var exported = exportGradebook.execute(actorAccountId, classId);

        verify(getClassGradebook).execute(actorAccountId, classId);
        assertThat(exported.fileName()).isEqualTo(AcademicConstants.Excel.GRADEBOOK_FILE_NAME);
        assertThat(exported.content()).isNotEmpty();
    }

    @Test
    void progressExportDelegatesToTheReadUseCaseAndNamesTheFile() {
        when(getClassProgress.execute(actorAccountId, classId)).thenReturn(
                new ClassProgressResponse(classId, UUID.randomUUID(), 24, List.of()));

        var exported = exportProgress.execute(actorAccountId, classId);

        verify(getClassProgress).execute(actorAccountId, classId);
        assertThat(exported.fileName()).isEqualTo(AcademicConstants.Excel.PROGRESS_FILE_NAME);
        assertThat(exported.content()).isNotEmpty();
    }

    /** Spec mục 7: "không có đường tắt tải Excel bỏ qua kiểm tra quyền" - lỗi của guard (nằm trong
     * usecase đọc) phải nổi nguyên vẹn ra ngoài, không bị bắt rồi trả file rỗng. */
    @Test
    void gradebookExportPropagatesTheAccessRefusalOfTheReadUseCase() {
        when(getClassGradebook.execute(actorAccountId, classId))
                .thenThrow(new NotAssignedToClassException(actorAccountId, classId));

        assertThatThrownBy(() -> exportGradebook.execute(actorAccountId, classId))
                .isInstanceOf(NotAssignedToClassException.class);
    }

    @Test
    void progressExportPropagatesTheAccessRefusalOfTheReadUseCase() {
        when(getClassProgress.execute(actorAccountId, classId))
                .thenThrow(new NotAssignedToClassException(actorAccountId, classId));

        assertThatThrownBy(() -> exportProgress.execute(actorAccountId, classId))
                .isInstanceOf(NotAssignedToClassException.class);
    }
}
```

Create `backend/src/test/java/com/eduerp/modules/academic/web/GradeExportControllerIT.java`:

```java
package com.eduerp.modules.academic.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.academic.dto.RecordGradeRequest;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
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
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Review Focus #7 end-to-end: con số trong file {@code .xlsx} tải về phải KHỚP con số endpoint JSON
 * trả ra, đọc bằng POI từ chính byte của HTTP response. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class GradeExportControllerIT {

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
    CourseRepository courses;

    @Autowired
    ClassRepository classes;

    @Autowired
    BranchRepository branches;

    @Autowired
    EnrollmentRepository enrollments;

    @Autowired
    StudentProfileRepository profiles;

    @Autowired
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    private UUID assignedTeacherId;
    private UUID classId;
    private UUID enrollmentId;
    private UUID midtermColumnId;
    private UUID finalColumnId;

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private UUID newAccount(String emailPrefix, String fullName) {
        return accounts.save(new Account(emailPrefix + "-" + shortId() + "@eduerp.local",
                passwordEncoder.encode(PASSWORD), fullName, null)).getId();
    }

    private Cookie signIn(UUID accountId, String roleCode) throws Exception {
        var account = accounts.findById(accountId).orElseThrow();
        account.recordFirstLogin();
        accounts.save(account);
        access.assignRole(accountId, roleCode);
        access.evictPermissionCache(accountId);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(account.getEmail(), PASSWORD))))
                .andReturn();
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    @BeforeEach
    void seedAFullyGradedClass() {
        var branchId = branches.save(new Branch("GX-" + shortId(), "Chi nhánh xuất Excel", null)).getId();
        assignedTeacherId = newAccount("gx-gv", "Cô Lan");
        var course = new Course("GX-C-" + shortId(), "Khoá xuất Excel", null, 24, null);
        course.addGradeColumn("Giữa kỳ", new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
        course.addGradeColumn("Cuối kỳ", new BigDecimal("60.00"), new BigDecimal("10.00"), 1);
        var savedCourse = courses.saveAndFlush(course);
        midtermColumnId = savedCourse.getGradeColumns().get(0).getId();
        finalColumnId = savedCourse.getGradeColumns().get(1).getId();
        var cls = new Class(savedCourse, "GX-K-" + shortId(), branchId, assignedTeacherId, 20);
        cls.addSchedule(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0));
        classId = classes.save(cls).getId();
        var studentProfileId = profiles.save(
                new StudentProfile(newAccount("gx-hv", "Nguyễn Văn A"), null, null, null)).getId();
        enrollmentId = enrollments.saveAndFlush(new Enrollment(studentProfileId, classId,
                savedCourse.getId(), branchId, assignedTeacherId)).getId();
    }

    private void recordGrade(Cookie cookie, UUID columnId, String score) throws Exception {
        var status = mockMvc.perform(post("/api/academic/grades")
                        .cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RecordGradeRequest(classId,
                                enrollmentId, columnId, new BigDecimal(score)))))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(200);
    }

    private static double numericCell(byte[] content, int rowIndex, int cellIndex) throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            return workbook.getSheetAt(0).getRow(rowIndex).getCell(cellIndex).getNumericCellValue();
        }
    }

    /**
     * Review Focus #7: điểm tổng kết trong file Excel phải bằng ĐÚNG số JSON trả về. Nếu ai đó thêm
     * một đường tính riêng cho Excel thì hai con số này tách nhau và test đỏ.
     */
    @Test
    void exportedGradebookNumbersMatchTheJsonEndpoint() throws Exception {
        var teacher = signIn(assignedTeacherId, AccessConstants.RoleCodes.TEACHER);
        recordGrade(teacher, midtermColumnId, "8.00");
        recordGrade(teacher, finalColumnId, "9.00");

        var json = mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook")
                .cookie(teacher)).andReturn();
        var jsonFinalScore = objectMapper.readTree(json.getResponse().getContentAsString())
                .get("rows").get(0).get("finalScore").asDouble();

        var export = mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook/export")
                .cookie(teacher)).andReturn();

        assertThat(export.getResponse().getStatus()).isEqualTo(200);
        assertThat(export.getResponse().getContentType())
                .isEqualTo(AcademicConstants.Excel.CONTENT_TYPE);
        assertThat(export.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains(AcademicConstants.Excel.GRADEBOOK_FILE_NAME);
        var content = export.getResponse().getContentAsByteArray();
        assertThat(numericCell(content, 1, 3))
                .as("ô Điểm tổng kết của dòng đầu, khớp đúng số JSON")
                .isEqualTo(jsonFinalScore);
        assertThat(jsonFinalScore).isEqualTo(86.0);
    }

    /** Review Focus #7, bảng thứ hai: số buổi đã học và % trong Excel khớp JSON. */
    @Test
    void exportedProgressNumbersMatchTheJsonEndpoint() throws Exception {
        var teacher = signIn(assignedTeacherId, AccessConstants.RoleCodes.TEACHER);

        var json = mockMvc.perform(get("/api/academic/classes/" + classId + "/progress")
                .cookie(teacher)).andReturn();
        var row = objectMapper.readTree(json.getResponse().getContentAsString()).get("rows").get(0);
        var jsonElapsed = row.get("elapsedSessions").asDouble();
        var jsonPercent = row.get("progressPercent").asDouble();

        var export = mockMvc.perform(get("/api/academic/classes/" + classId + "/progress/export")
                .cookie(teacher)).andReturn();

        assertThat(export.getResponse().getStatus()).isEqualTo(200);
        assertThat(export.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains(AcademicConstants.Excel.PROGRESS_FILE_NAME);
        var content = export.getResponse().getContentAsByteArray();
        assertThat(numericCell(content, 1, 3)).isEqualTo(jsonElapsed);
        assertThat(numericCell(content, 1, 5)).isEqualTo(jsonPercent);
    }

    /** Spec mục 7 + Review Focus #4: hai endpoint tải file cũng bị chặn 403 với giáo viên sai lớp -
     * tải Excel không phải cửa sau. */
    @Test
    void refusesBothExportsForATeacherNotAssignedToTheClass() throws Exception {
        var outsider = signIn(newAccount("gx-gv-outsider", "Thầy Nam"),
                AccessConstants.RoleCodes.TEACHER);

        assertThat(mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook/export")
                .cookie(outsider)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(get("/api/academic/classes/" + classId + "/progress/export")
                .cookie(outsider)).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    /** Review Focus #5: admin tải được Excel của lớp mình không dạy. */
    @Test
    void allowsAnAdminToExportAClassTheyDoNotTeach() throws Exception {
        var admin = signIn(newAccount("gx-admin", "Quản trị"), AccessConstants.RoleCodes.ADMIN);

        assertThat(mockMvc.perform(get("/api/academic/classes/" + classId + "/gradebook/export")
                .cookie(admin)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(get("/api/academic/classes/" + classId + "/progress/export")
                .cookie(admin)).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest='ExportUseCasesTest,GradeExportControllerIT'`
Expected: FAIL at compile — `cannot find symbol: class ExportClassGradebook`, `cannot find symbol: class ExportClassProgress`.

- [ ] **Step 3: Write the two export usecases**

Create `backend/src/main/java/com/eduerp/modules/academic/usecase/ExportClassGradebook.java`:

```java
package com.eduerp.modules.academic.usecase;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.academic.dto.ExportedFileResponse;
import com.eduerp.modules.academic.internal.excel.AcademicWorkbooks;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bảng điểm dưới dạng {@code .xlsx} (spec mục 7). KHÔNG đọc dữ liệu riêng: gọi thẳng
 * {@link GetClassGradebook} rồi serialize, nên (a) số trên web và số trong file không thể lệch nhau
 * (spec mục 11.7) và (b) việc kiểm quyền "chỉ giáo viên được phân công" đi kèm miễn phí vì guard
 * nằm trong usecase đọc - không có đường tắt tải Excel bỏ qua kiểm tra quyền (spec mục 7).
 */
@Service
public class ExportClassGradebook {

    private final GetClassGradebook getClassGradebook;

    ExportClassGradebook(GetClassGradebook getClassGradebook) {
        this.getClassGradebook = getClassGradebook;
    }

    @Transactional(readOnly = true)
    public ExportedFileResponse execute(UUID actorAccountId, UUID classId) {
        var gradebook = getClassGradebook.execute(actorAccountId, classId);
        return new ExportedFileResponse(AcademicConstants.Excel.GRADEBOOK_FILE_NAME,
                AcademicWorkbooks.gradebook(gradebook));
    }
}
```

Create `backend/src/main/java/com/eduerp/modules/academic/usecase/ExportClassProgress.java`:

```java
package com.eduerp.modules.academic.usecase;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.academic.dto.ExportedFileResponse;
import com.eduerp.modules.academic.internal.excel.AcademicWorkbooks;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bảng tiến trình học tập dưới dạng {@code .xlsx} (spec mục 7) - cùng lý do uỷ quyền hoàn toàn cho
 * usecase đọc như {@link ExportClassGradebook}. */
@Service
public class ExportClassProgress {

    private final GetClassProgress getClassProgress;

    ExportClassProgress(GetClassProgress getClassProgress) {
        this.getClassProgress = getClassProgress;
    }

    @Transactional(readOnly = true)
    public ExportedFileResponse execute(UUID actorAccountId, UUID classId) {
        var progress = getClassProgress.execute(actorAccountId, classId);
        return new ExportedFileResponse(AcademicConstants.Excel.PROGRESS_FILE_NAME,
                AcademicWorkbooks.progress(progress));
    }
}
```

- [ ] **Step 4: Add the two download endpoints**

In `backend/src/main/java/com/eduerp/modules/academic/web/GradeAdminController.java`, add the imports:

```java
import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.academic.dto.ExportedFileResponse;
import com.eduerp.modules.academic.usecase.ExportClassGradebook;
import com.eduerp.modules.academic.usecase.ExportClassProgress;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
```

Add the two fields and extend the constructor:

```java
    private final ExportClassGradebook exportClassGradebook;
    private final ExportClassProgress exportClassProgress;

    GradeAdminController(RecordGrade recordGrade, GetClassGradebook getClassGradebook,
            GetClassProgress getClassProgress, ExportClassGradebook exportClassGradebook,
            ExportClassProgress exportClassProgress) {
        this.recordGrade = recordGrade;
        this.getClassGradebook = getClassGradebook;
        this.getClassProgress = getClassProgress;
        this.exportClassGradebook = exportClassGradebook;
        this.exportClassProgress = exportClassProgress;
    }
```

Add the two endpoints and the shared response builder at the end of the class:

```java
    @GetMapping("/classes/{classId}/gradebook/export")
    @PreAuthorize(AccessConstants.AccessRules.READ_GRADE)
    ResponseEntity<byte[]> exportGradebook(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID classId) {
        return toDownload(exportClassGradebook.execute(principal.accountId(), classId));
    }

    @GetMapping("/classes/{classId}/progress/export")
    @PreAuthorize(AccessConstants.AccessRules.READ_GRADE)
    ResponseEntity<byte[]> exportProgress(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID classId) {
        return toDownload(exportClassProgress.execute(principal.accountId(), classId));
    }

    /** Không có {@code Content-Disposition} kèm tên file thì trình duyệt lưu file không có phần mở
     * rộng - mirror {@code ContractAdminController.downloadFile}. */
    private static ResponseEntity<byte[]> toDownload(ExportedFileResponse exported) {
        var disposition = ContentDisposition.attachment()
                .filename(exported.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(AcademicConstants.Excel.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(exported.content());
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest='ExportUseCasesTest,GradeExportControllerIT,GradeAdminControllerIT'`
Expected: PASS — 4 + 4 + 8 = 16 tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/academic/usecase/ExportClassGradebook.java \
        backend/src/main/java/com/eduerp/modules/academic/usecase/ExportClassProgress.java \
        backend/src/main/java/com/eduerp/modules/academic/web/GradeAdminController.java \
        backend/src/test/java/com/eduerp/modules/academic/usecase/ExportUseCasesTest.java \
        backend/src/test/java/com/eduerp/modules/academic/web/GradeExportControllerIT.java
git commit -m "feat(academic): download the gradebook and progress reports as excel files"
```

---

## Task 20: `ModularityTests` nhận module thứ 19

**Files:**
- Modify: `backend/src/test/java/com/eduerp/ModularityTests.java:46-51`

**Interfaces:**
- Consumes: `@ApplicationModule(displayName = "Academic (Grades & Progress)")` trên `modules/academic/package-info.java` (Task 7).
- Produces: không có type mới — task này chỉ mở rộng danh sách module được chốt.

> Danh sách hiện tại chốt **18** module (`core`, `shared`, 11 `modules.*`, 5 `integrations.*`).
> `modules.academic` là module thứ **19**. Đây là điểm khác biệt lớn nhất so với các phân hệ trước:
> chúng không thêm module nào nên file này không phải sửa.

- [ ] **Step 1: Run the test to watch it fail for the right reason**

Run: `cd backend && ./mvnw test -Dtest=ModularityTests`
Expected: FAIL ở `everyDomainAndIntegrationPackageIsADetectedModule` với thông điệp kiểu
`Expecting actual: [... "modules.academic" ...] to contain exactly in any order: [...]` và dòng
`but could not find the following elements: []` / `the following elements were unexpected: ["modules.academic"]`.

Đây là thất bại ĐÚNG lý do: `verifiesModularStructure` phải VẪN XANH. Nếu `verifiesModularStructure`
cũng đỏ thì có vi phạm ranh giới thật (ví dụ `academic.internal` bị module khác chọc vào, hoặc
`academic` đọc `internal` của module khác) — sửa vi phạm đó trước, đừng sửa danh sách.

- [ ] **Step 2: Add the module to the pinned list**

In `backend/src/test/java/com/eduerp/ModularityTests.java`, replace the assertion.

Trước:

```java
        assertThat(detected).containsExactlyInAnyOrder("core", "shared", "modules.identity", "modules.access",
                "modules.organization", "modules.audit", "modules.dashboard", "modules.courses",
                "modules.teachers", "modules.students", "modules.payroll", "modules.enrollment",
                "modules.billing", "integrations.cache", "integrations.mail", "integrations.notification",
                "integrations.payment", "integrations.storage");
```

Sau:

```java
        assertThat(detected).containsExactlyInAnyOrder("core", "shared", "modules.identity", "modules.access",
                "modules.organization", "modules.audit", "modules.dashboard", "modules.courses",
                "modules.teachers", "modules.students", "modules.payroll", "modules.enrollment",
                "modules.billing", "modules.academic", "integrations.cache", "integrations.mail",
                "integrations.notification", "integrations.payment", "integrations.storage");
```

- [ ] **Step 3: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=ModularityTests`
Expected: PASS — 2 tests; danh sách giờ gồm 19 tên.

- [ ] **Step 4: Run the whole backend suite**

Run: `cd backend && ./mvnw clean test`
Expected: BUILD SUCCESS, 0 failures, 0 errors.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/eduerp/ModularityTests.java
git commit -m "test: pin the academic module in the detected module list"
```

---

> **Ghi chú chung cho mọi task frontend (21-27):** dự án này KHÔNG có test runner (`package.json`
> không có vitest/jest, `find src -name "*.test.ts*"` trả về rỗng). Cổng kiểm tra của từng task
> frontend vì vậy là `npm run lint` (ESLint + `boundaries` + `complexity: 15` + `max-lines: 600`),
> `npm run typecheck` và `npm run build` — chạy TRƯỚC khi viết code để thấy lỗi mong đợi, rồi chạy
> lại để thấy xanh. Không bịa ra test runner mới trong phân hệ này.

## Task 21: Hằng số frontend cho resource `GRADE`

**Files:**
- Modify: `frontend/src/shared/constants/permissions.ts`
- Modify: `frontend/src/shared/constants/api-routes.ts`
- Modify: `frontend/src/shared/constants/app-routes.ts`

**Interfaces:**
- Consumes: `RESOURCE`, `ACTION`, `ACCESS_RULE` (hiện có), `PermissionRequirement` của `@/entities/permission` (scope optional, mặc định `PERSONAL`).
- Produces: `RESOURCE.grade`; `RESOURCE_LABEL[RESOURCE.grade]`; `ACCESS_RULE.readGrade`/`.updateGrade`; `API_ROUTE.academic.grades`/`.classGradebook(classId)`/`.classGradebookExport(classId)`/`.classProgress(classId)`/`.classProgressExport(classId)`; `APP_ROUTE.classGradebook`/`.classProgress`; `buildClassGradebookPath(classId)`/`buildClassProgressPath(classId)`.

> `ACCESS_RULE.readGrade`/`.updateGrade` KHÔNG ghi `scope`. Đây là hai entry duy nhất như vậy trong
> file, và nó cố ý: `PermissionSet.allows` mặc định `PERSONAL`, khớp đúng
> `ScopedPermissionEvaluator` khi biểu thức `@PreAuthorize` không có marker `@ORGANIZATION` — mà ba
> rule `*_GRADE` ở backend chính là như thế (Task 15). Ghi `scope: organization` ở đây sẽ ẩn toàn bộ
> màn hình sổ điểm khỏi giáo viên dù API cho họ vào.

- [ ] **Step 1: Watch the typecheck fail for the right reason**

Append a temporary assertion file `frontend/src/shared/constants/grade-access-check.ts` to prove the
constants are missing (file này sẽ bị xoá ở Step 4):

```ts
import { ACCESS_RULE, RESOURCE } from "@/shared/constants/permissions";

export const GRADE_RESOURCE_EXISTS: string = RESOURCE.grade;
export const GRADE_READ_RULE = ACCESS_RULE.readGrade;
export const GRADE_UPDATE_RULE = ACCESS_RULE.updateGrade;
```

Run: `cd frontend && npm run typecheck`
Expected: FAIL — `Property 'grade' does not exist on type ...`, `Property 'readGrade' does not exist on type ...`, `Property 'updateGrade' does not exist on type ...`.

- [ ] **Step 2: Add the permission constants**

In `frontend/src/shared/constants/permissions.ts`:

Add to `RESOURCE` after `invoice`:

```ts
  grade: "GRADE",
```

Add to `RESOURCE_LABEL` after the `invoice` entry:

```ts
  [RESOURCE.grade]: "Điểm & Tiến trình học tập",
```

Add to `ACCESS_RULE` after `updateInvoice`:

```ts
  /**
   * Hai entry DUY NHẤT trong file không ghi scope, và đó là chủ đích: ba rule `*_GRADE` ở backend
   * không có marker `@ORGANIZATION` (AccessConstants.AccessRules.READ_GRADE), nên câu hỏi đúng là
   * "có quyền GRADE ở bất kỳ scope nào không". Ghi `organization` ở đây sẽ ẩn màn hình sổ điểm khỏi
   * giáo viên — những người duy nhất thực sự nhập điểm.
   *
   * Giao diện chỉ dùng hai rule này để quyết định HIỂN THỊ. Nguồn sự thật cho "lớp này có phải lớp
   * của tôi không" là lỗi 403 ACADEMIC_NOT_ASSIGNED_TO_CLASS từ backend (spec mục 10) — frontend
   * không có dữ liệu để tự kết luận điều đó và không được thử.
   */
  readGrade: { resource: RESOURCE.grade, action: ACTION.read },
  updateGrade: { resource: RESOURCE.grade, action: ACTION.update },
```

- [ ] **Step 3: Add the API and app routes**

In `frontend/src/shared/constants/api-routes.ts`, add a new group after `billing`:

```ts
  academic: {
    /** POST upsert điểm một cột - classId/enrollmentId/courseGradeColumnId nằm trong body. */
    grades: "/api/academic/grades",
    classGradebook: (classId: string) => `/api/academic/classes/${classId}/gradebook`,
    classGradebookExport: (classId: string) => `/api/academic/classes/${classId}/gradebook/export`,
    classProgress: (classId: string) => `/api/academic/classes/${classId}/progress`,
    classProgressExport: (classId: string) => `/api/academic/classes/${classId}/progress/export`,
  },
```

In `frontend/src/shared/constants/app-routes.ts`, add two routes to `APP_ROUTE` after `comboDiscountTiers`:

```ts
  classGradebook: "/admin/academic/classes/:classId/gradebook",
  classProgress: "/admin/academic/classes/:classId/progress",
```

and two builders at the end of the file:

```ts
/** Link thật tới sổ điểm của một lớp - APP_ROUTE.classGradebook chỉ là route template. */
export function buildClassGradebookPath(classId: string): string {
  return `/admin/academic/classes/${classId}/gradebook`;
}

/** Link thật tới bảng tiến trình học tập của một lớp. */
export function buildClassProgressPath(classId: string): string {
  return `/admin/academic/classes/${classId}/progress`;
}
```

- [ ] **Step 4: Delete the temporary check file and verify**

```bash
rm frontend/src/shared/constants/grade-access-check.ts
```

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: cả hai PASS, 0 errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/shared/constants/permissions.ts \
        frontend/src/shared/constants/api-routes.ts \
        frontend/src/shared/constants/app-routes.ts
git commit -m "feat(frontend): add grade permission, api and app route constants"
```

---

## Task 22: `gradeColumnSchema` ở `shared` + cột điểm trong `entities/course`

**Files:**
- Modify: `frontend/src/shared/api/schemas.ts`
- Modify: `frontend/src/entities/course/model/course-schema.ts`
- Modify: `frontend/src/entities/course/index.ts`

**Interfaces:**
- Consumes: `z` (zod 3), `courseSummarySchema`/`CreateCoursePayload`/`UpdateCoursePayload` (hiện có).
- Produces: `shared/api/schemas.ts`: `gradeColumnSchema`, `type GradeColumn`, `gradeColumnSlotSchema`, `type GradeColumnSlot`; `entities/course`: `courseSummarySchema.gradeColumns: GradeColumn[]`, `CreateCoursePayload.gradeColumns: readonly GradeColumnSlot[]`, `UpdateCoursePayload.gradeColumns: readonly GradeColumnSlot[]`.

> **Nơi đặt `gradeColumnSchema`, và tại sao khác chữ trong spec mục 10.** Spec viết
> "`entities/academic` (mới): schema `gradeColumnSchema`, `gradebookSchema`, `classProgressSchema`".
> Nhưng cột điểm xuất hiện ở HAI chỗ: `CourseResponse.gradeColumns` (màn hình sửa khóa học, thuộc
> `entities/course`) và `GradebookResponse.columns` (sổ điểm, thuộc `entities/academic`). ESLint
> `boundaries` cấm entity import entity (`{ from: entities, allow: [cùng entity, shared] }`), nên
> `entities/academic` và `entities/course` KHÔNG thể dùng chung một schema đặt trong một trong hai
> chỗ đó, và AGENTS.md cấm cả định nghĩa trùng lẫn file proxy re-export. Chỗ còn lại duy nhất là
> `shared` — và đó chính là tiền lệ đã được ghi lại trong file này cho `grantedPermissionSchema`:
> *"Đặt ở shared vì cả entity account (đọc phiên) lẫn entity permission (suy luận quyền) đều cần, mà
> hai entity thì không được import nhau."* `entities/academic` (Task 23) import trực tiếp từ
> `@/shared/api/schemas`, không re-export lại.

- [ ] **Step 1: Watch the typecheck fail for the right reason**

Create a temporary check file `frontend/src/entities/course/grade-column-check.ts` (xoá ở Step 4):

```ts
import { courseSummarySchema } from "@/entities/course/model/course-schema";
import { gradeColumnSchema } from "@/shared/api/schemas";

export const COLUMN_SHAPE = gradeColumnSchema.shape.weightPercent;
export const COURSE_HAS_COLUMNS = courseSummarySchema.shape.gradeColumns;
```

Run: `cd frontend && npm run typecheck`
Expected: FAIL — `Module '"@/shared/api/schemas"' has no exported member 'gradeColumnSchema'` và `Property 'gradeColumns' does not exist on type ...`.

- [ ] **Step 2: Add the two shared schemas**

Append to `frontend/src/shared/api/schemas.ts`:

```ts
/**
 * Một cột điểm đã lưu của khóa học, khớp `courses.dto.GradeColumnResponse` ở backend.
 *
 * Đặt ở shared cùng lý do với {@link grantedPermissionSchema}: cả entity course (màn hình sửa khóa
 * học cấu hình cột điểm) lẫn entity academic (sổ điểm hiển thị cột điểm) đều cần, mà hai entity thì
 * không được import nhau. `weightPercent`/`maxScore` là số (backend trả NUMERIC(5,2) dưới dạng số
 * JSON, không phải chuỗi).
 */
export const gradeColumnSchema = z.object({
  id: z.string().uuid(),
  name: z.string(),
  weightPercent: z.number(),
  maxScore: z.number(),
  displayOrder: z.number().int(),
});

export type GradeColumn = z.infer<typeof gradeColumnSchema>;

/**
 * Một dòng cột điểm khi GỬI LÊN, khớp `courses.dto.GradeColumnSlot`. Không có `id`: PATCH thay toàn
 * bộ danh sách, không vá từng dòng (mirror `weeklyScheduleSlotSchema` của entity class).
 */
export const gradeColumnSlotSchema = z.object({
  name: z.string(),
  weightPercent: z.number(),
  maxScore: z.number(),
  displayOrder: z.number().int(),
});

export type GradeColumnSlot = z.infer<typeof gradeColumnSlotSchema>;

/** Tổng trọng số bắt buộc, khớp `CoursesConstants.GradeColumns.REQUIRED_TOTAL_WEIGHT_PERCENT`. */
export const REQUIRED_TOTAL_WEIGHT_PERCENT = 100;

/** Sai số làm tròn chấp nhận được, khớp `CoursesConstants.GradeColumns.WEIGHT_TOLERANCE`. */
export const WEIGHT_TOLERANCE = 0.01;
```

- [ ] **Step 3: Widen the course schema and payloads**

In `frontend/src/entities/course/model/course-schema.ts`, add the import and the field:

```ts
import { gradeColumnSchema, type GradeColumnSlot } from "@/shared/api/schemas";
```

Add to `courseSummarySchema`, after `active`:

```ts
  /** Rỗng = khóa học chưa cấu hình cột điểm (hợp lệ). Tổng weightPercent của danh sách không rỗng
   * luôn là 100 vì backend chặn lưu nếu khác. */
  gradeColumns: z.array(gradeColumnSchema),
```

Add to both payload interfaces:

```ts
export interface CreateCoursePayload {
  readonly code: string;
  readonly name: string;
  readonly description: string | null;
  readonly standardSessionCount: number | null;
  readonly tuitionFee: number | null;
  readonly gradeColumns: readonly GradeColumnSlot[];
}

export interface UpdateCoursePayload {
  readonly name: string;
  readonly description: string | null;
  readonly standardSessionCount: number | null;
  readonly tuitionFee: number | null;
  readonly active: boolean;
  readonly gradeColumns: readonly GradeColumnSlot[];
}
```

`frontend/src/entities/course/index.ts` KHÔNG thay đổi. Nó đã export đúng bốn tên mà Task 24 cần
(`courseSummarySchema`, `CourseSummary`, `CreateCoursePayload`, `UpdateCoursePayload`), và hai tên
mới `gradeColumnSchema`/`GradeColumnSlot` thuộc `shared` — nơi gọi import trực tiếp từ
`@/shared/api/schemas`. Re-export chúng qua barrel của entity chính là file proxy mà AGENTS.md cấm.

- [ ] **Step 4: Delete the temporary check file and verify**

```bash
rm frontend/src/entities/course/grade-column-check.ts
```

Run: `cd frontend && npm run typecheck`
Expected: FAIL — và đây là thất bại MONG ĐỢI: `modules/courses/ui/create-course-dialog.tsx` và
`edit-course-dialog.tsx` gọi `mutateAsync(values)` với object thiếu `gradeColumns`, nên TypeScript
báo `Property 'gradeColumns' is missing`. Task 24 sửa hai chỗ đó. KHÔNG commit ở trạng thái này —
chuyển sang Task 23 rồi Task 24, và commit của task này đi kèm commit Task 24 nếu `typecheck` chưa
xanh được một mình.

Run: `cd frontend && npx tsc --noEmit src/shared/api/schemas.ts --strict --skipLibCheck --moduleResolution bundler --module esnext --target es2022 --jsx react-jsx --baseUrl src --paths '{"@/*":["./*"]}'`
Expected: PASS cho riêng file schema vừa thêm — xác nhận hai schema mới tự hợp lệ trước khi đi tiếp.

- [ ] **Step 5: Commit the schema layer**

```bash
git add frontend/src/shared/api/schemas.ts \
        frontend/src/entities/course/model/course-schema.ts
git commit -m "feat(frontend): add grade column schemas and attach them to the course entity"
```

---

## Task 23: `entities/academic` — schema, query keys, fetcher, hooks

**Files:**
- Create: `frontend/src/entities/academic/model/academic-schema.ts`
- Create: `frontend/src/entities/academic/api/academic-keys.ts`
- Create: `frontend/src/entities/academic/api/academic-api.ts`
- Create: `frontend/src/entities/academic/api/use-gradebook.ts`
- Create: `frontend/src/entities/academic/api/use-class-progress.ts`
- Create: `frontend/src/entities/academic/index.ts`

**Interfaces:**
- Consumes: `apiClient.get`/`.post`/`.getBlob` (`@/shared/api/api-client`), `gradeColumnSchema` (Task 22), `API_ROUTE.academic.*` (Task 21), `QUERY_STALE_TIME_MS.list`, `QUERY_RETRY_COUNT`.
- Produces: `gradebookSchema`, `type Gradebook`, `studentGradesRowSchema`, `type StudentGradesRow`, `classProgressSchema`, `type ClassProgress`, `studentProgressRowSchema`, `type StudentProgressRow`, `ENROLLMENT_STATUS`, `type RecordGradePayload`; `academicKeys.all/gradebook(classId)/progress(classId)`; `academicApi.getGradebook`/`.getClassProgress`/`.recordGrade`/`.downloadGradebook`/`.downloadClassProgress`; `useGradebook(classId)`, `useClassProgress(classId)`.

> `apiClient` ĐÃ hỗ trợ tải file không-JSON: `getBlob(path)` trả `{ blob, fileName }`, tự đọc tên từ
> `Content-Disposition` và vẫn đi qua lượt refresh-rồi-thử-lại khi access token hết hạn. Kèm
> `downloadBlob(blob, fileName)` ở `@/shared/lib/download-blob`. Tiền lệ đầy đủ:
> `entities/payroll/api/payroll-api.ts#downloadContractFile` + `modules/payroll/hooks/use-contracts-page-controller.ts#onDownload`.
> Không cần thêm method nào vào `apiClient`.

- [ ] **Step 1: Write the schema**

Create `frontend/src/entities/academic/model/academic-schema.ts`:

```ts
import { gradeColumnSchema } from "@/shared/api/schemas";
import { z } from "zod";

/** Bản sao của EnrollmentConstants.EnrollmentStatus ở backend. */
export const ENROLLMENT_STATUS = {
  active: "ACTIVE",
  withdrawn: "WITHDRAWN",
  completed: "COMPLETED",
} as const;

export type EnrollmentStatus = (typeof ENROLLMENT_STATUS)[keyof typeof ENROLLMENT_STATUS];

export const ENROLLMENT_STATUS_LABEL: Record<EnrollmentStatus, string> = {
  [ENROLLMENT_STATUS.active]: "Đang học",
  [ENROLLMENT_STATUS.withdrawn]: "Đã rút",
  [ENROLLMENT_STATUS.completed]: "Đã hoàn thành",
};

const enrollmentStatusSchema = z.enum([
  ENROLLMENT_STATUS.active,
  ENROLLMENT_STATUS.withdrawn,
  ENROLLMENT_STATUS.completed,
]);

/**
 * Một dòng bảng điểm. `scoresByColumnId` là map có khoá là id cột điểm (JSON luôn trả khoá dạng
 * chuỗi). Cột khuyết khoá nghĩa là CHƯA có điểm, và khi đó `finalScore` là null = "chưa đủ điểm"
 * (backend GradeRules) — KHÔNG phải 0, giao diện không được hiển thị 0 ở đây.
 */
export const studentGradesRowSchema = z.object({
  enrollmentId: z.string().uuid(),
  studentProfileId: z.string().uuid(),
  studentName: z.string().nullable(),
  status: enrollmentStatusSchema,
  scoresByColumnId: z.record(z.string(), z.number()),
  finalScore: z.number().nullable(),
});

export type StudentGradesRow = z.infer<typeof studentGradesRowSchema>;

export const gradebookSchema = z.object({
  classId: z.string().uuid(),
  courseId: z.string().uuid(),
  /** Thứ tự cột do backend quyết định theo displayOrder - không sắp lại ở frontend. */
  columns: z.array(gradeColumnSchema),
  rows: z.array(studentGradesRowSchema),
});

export type Gradebook = z.infer<typeof gradebookSchema>;

/**
 * Một dòng bảng tiến trình. `progressPercent` null = khóa chưa cấu hình tổng số buổi chuẩn, không
 * phải 0% — `elapsedSessions` vẫn là số thật để người xem phân biệt hai tình huống.
 */
export const studentProgressRowSchema = z.object({
  enrollmentId: z.string().uuid(),
  studentProfileId: z.string().uuid(),
  studentName: z.string().nullable(),
  status: enrollmentStatusSchema,
  enrolledAt: z.string(),
  withdrawnAt: z.string().nullable(),
  elapsedSessions: z.number().int(),
  standardSessionCount: z.number().int().nullable(),
  progressPercent: z.number().int().nullable(),
});

export type StudentProgressRow = z.infer<typeof studentProgressRowSchema>;

export const classProgressSchema = z.object({
  classId: z.string().uuid(),
  courseId: z.string().uuid(),
  standardSessionCount: z.number().int().nullable(),
  rows: z.array(studentProgressRowSchema),
});

export type ClassProgress = z.infer<typeof classProgressSchema>;

/** Khớp `academic.dto.RecordGradeRequest`. Upsert: gửi lại cùng cặp (enrollmentId, columnId) là SỬA. */
export interface RecordGradePayload {
  readonly classId: string;
  readonly enrollmentId: string;
  readonly courseGradeColumnId: string;
  readonly score: number;
}
```

- [ ] **Step 2: Write the query keys and fetchers**

Create `frontend/src/entities/academic/api/academic-keys.ts`:

```ts
export const academicKeys = {
  all: ["academic"] as const,
  gradebooks: () => [...academicKeys.all, "gradebook"] as const,
  gradebook: (classId: string) => [...academicKeys.gradebooks(), classId] as const,
  progresses: () => [...academicKeys.all, "progress"] as const,
  progress: (classId: string) => [...academicKeys.progresses(), classId] as const,
} as const;
```

Create `frontend/src/entities/academic/api/academic-api.ts`:

```ts
import {
  classProgressSchema,
  gradebookSchema,
  type RecordGradePayload,
} from "@/entities/academic/model/academic-schema";
import { apiClient } from "@/shared/api/api-client";
import { API_ROUTE } from "@/shared/constants/api-routes";

export const academicApi = {
  async getGradebook(classId: string) {
    return gradebookSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.academic.classGradebook(classId)),
    );
  },

  async getClassProgress(classId: string) {
    return classProgressSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.academic.classProgress(classId)),
    );
  },

  async recordGrade(payload: RecordGradePayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.academic.grades, payload);
  },

  /** getBlob đi qua lượt tự refresh khi access token hết hạn và đọc luôn tên file từ
   * Content-Disposition - cùng đường mà tải file hợp đồng đã dùng. */
  async downloadGradebook(classId: string): Promise<{ blob: Blob; fileName: string | null }> {
    return apiClient.getBlob(API_ROUTE.academic.classGradebookExport(classId));
  },

  async downloadClassProgress(classId: string): Promise<{ blob: Blob; fileName: string | null }> {
    return apiClient.getBlob(API_ROUTE.academic.classProgressExport(classId));
  },
} as const;
```

- [ ] **Step 3: Write the two read hooks**

Create `frontend/src/entities/academic/api/use-gradebook.ts`:

```ts
import { academicApi } from "@/entities/academic/api/academic-api";
import { academicKeys } from "@/entities/academic/api/academic-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function useGradebook(classId: string) {
  return useQuery({
    queryKey: academicKeys.gradebook(classId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => academicApi.getGradebook(classId),
  });
}
```

Create `frontend/src/entities/academic/api/use-class-progress.ts`:

```ts
import { academicApi } from "@/entities/academic/api/academic-api";
import { academicKeys } from "@/entities/academic/api/academic-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

/** Tiến trình được TÍNH lúc đọc ở backend (không lưu), nên staleTime ngắn như mọi danh sách khác là
 * đúng: mỗi lần làm mới là một con số mới theo thời gian thực. */
export function useClassProgress(classId: string) {
  return useQuery({
    queryKey: academicKeys.progress(classId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    queryFn: () => academicApi.getClassProgress(classId),
  });
}
```

- [ ] **Step 4: Write the barrel**

Create `frontend/src/entities/academic/index.ts`:

```ts
export { academicApi } from "@/entities/academic/api/academic-api";
export { academicKeys } from "@/entities/academic/api/academic-keys";
export { useClassProgress } from "@/entities/academic/api/use-class-progress";
export { useGradebook } from "@/entities/academic/api/use-gradebook";
export {
  classProgressSchema,
  ENROLLMENT_STATUS,
  ENROLLMENT_STATUS_LABEL,
  gradebookSchema,
  studentGradesRowSchema,
  studentProgressRowSchema,
  type ClassProgress,
  type EnrollmentStatus,
  type Gradebook,
  type RecordGradePayload,
  type StudentGradesRow,
  type StudentProgressRow,
} from "@/entities/academic/model/academic-schema";
```

- [ ] **Step 5: Verify lint boundaries accept the new slice**

Run: `cd frontend && npm run lint`
Expected: PASS cho `src/entities/academic/**` — không có lỗi `boundaries/dependencies` (slice chỉ
import `@/shared/**` và chính nó) và không có `boundaries/no-unknown-files`.

Run: `cd frontend && npm run typecheck`
Expected: vẫn FAIL ở hai dialog khóa học từ Task 22 (`Property 'gradeColumns' is missing`), KHÔNG có
lỗi mới nào trong `src/entities/academic/**`. Task 24 đóng nốt.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/entities/academic/
git commit -m "feat(frontend): add the academic entity slice for gradebook and progress"
```

---

## Task 24: Cấu hình cột điểm trong màn hình khóa học

**Files:**
- Modify: `frontend/src/modules/courses/model/courses-forms.ts`
- Create: `frontend/src/modules/courses/ui/grade-column-editor.tsx`
- Modify: `frontend/src/modules/courses/ui/create-course-dialog.tsx`
- Modify: `frontend/src/modules/courses/ui/edit-course-dialog.tsx`
- Modify: `frontend/src/modules/courses/ui/courses-table.tsx`

**Interfaces:**
- Consumes: `REQUIRED_TOTAL_WEIGHT_PERCENT`, `WEIGHT_TOLERANCE`, `type GradeColumnSlot` (Task 22), `useZodForm`, `GlassInput`/`GlassButton`/`FormField`, `CourseSummary.gradeColumns` (Task 22).
- Produces: `createCourseFormSchema`/`updateCourseFormSchema` có thêm field `gradeColumns`; `GradeColumnEditor` với props `{ columns, onChange }`; `toGradeColumnPayload(columns)` trong `courses-forms.ts`.

> Màn hình này mirror đúng cách sửa lịch học của Class: một editor danh sách thêm/xoá dòng, PATCH
> thay toàn bộ. Khác một điểm bắt buộc theo spec mục 10: hiển thị TỔNG TRỌNG SỐ real-time và chặn
> submit nếu ≠ 100%.

- [ ] **Step 1: Watch the typecheck fail for the right reason**

Run: `cd frontend && npm run typecheck`
Expected: FAIL (thừa hưởng từ Task 22) — `src/modules/courses/ui/create-course-dialog.tsx` và
`edit-course-dialog.tsx`: `Argument of type '{ code: string; ... }' is not assignable to parameter of
type 'CreateCoursePayload'. Property 'gradeColumns' is missing`.

- [ ] **Step 2: Extend the two form schemas**

In `frontend/src/modules/courses/model/courses-forms.ts`, add the imports:

```ts
import {
  REQUIRED_TOTAL_WEIGHT_PERCENT,
  WEIGHT_TOLERANCE,
  type GradeColumnSlot,
} from "@/shared/api/schemas";
```

Add this block above `createCourseFormSchema`:

```ts
const gradeColumnFormSchema = z.object({
  name: z.string().min(1, "Nhập tên cột điểm"),
  weightPercent: z
    .string()
    .min(1, "Nhập trọng số")
    .transform((value) => Number(value))
    .refine((value) => value > 0, "Trọng số phải lớn hơn 0"),
  maxScore: z
    .string()
    .min(1, "Nhập thang điểm")
    .transform((value) => Number(value))
    .refine((value) => value > 0, "Thang điểm phải lớn hơn 0"),
});

/**
 * Spec mục 2.3: tổng trọng số bắt buộc = 100% (sai số ±0.01). Danh sách RỖNG hợp lệ và có nghĩa
 * "khóa chưa cấu hình cột điểm" - mọi khóa học tạo trước phân hệ này đang ở trạng thái đó, chặn ở
 * đây là khiến chúng không sửa nổi. Backend chặn lần nữa bằng
 * COURSES_GRADE_COLUMN_WEIGHTS_INVALID; luật ở đây chỉ để người dùng thấy lỗi trước khi gửi.
 */
const gradeColumnsFormSchema = z
  .array(gradeColumnFormSchema)
  .superRefine((columns, ctx) => {
    if (columns.length === 0) {
      return;
    }
    const total = columns.reduce((sum, column) => sum + column.weightPercent, 0);
    if (Math.abs(total - REQUIRED_TOTAL_WEIGHT_PERCENT) > WEIGHT_TOLERANCE) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: `Tổng trọng số phải bằng ${REQUIRED_TOTAL_WEIGHT_PERCENT}%, đang là ${total}%`,
      });
    }
  });

/** `displayOrder` là vị trí trong danh sách - người dùng sắp cột bằng cách thêm/xoá theo thứ tự,
 * không phải gõ một con số thứ tự riêng. */
export function toGradeColumnPayload(
  columns: readonly { readonly name: string; readonly weightPercent: number; readonly maxScore: number }[],
): GradeColumnSlot[] {
  return columns.map((column, index) => ({
    name: column.name,
    weightPercent: column.weightPercent,
    maxScore: column.maxScore,
    displayOrder: index,
  }));
}
```

Add `gradeColumns: gradeColumnsFormSchema,` as the last field of BOTH `createCourseFormSchema` and
`updateCourseFormSchema`.

- [ ] **Step 3: Write the editor component**

Create `frontend/src/modules/courses/ui/grade-column-editor.tsx`:

```tsx
import { REQUIRED_TOTAL_WEIGHT_PERCENT, WEIGHT_TOLERANCE } from "@/shared/api/schemas";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { Plus, X } from "lucide-react";

/** Giá trị ô vẫn là chuỗi: GlassInput là input thường, Zod mới đổi sang số khi submit. */
export interface GradeColumnRow {
  readonly name: string;
  readonly weightPercent: string;
  readonly maxScore: string;
}

export interface GradeColumnEditorProps {
  readonly columns: readonly GradeColumnRow[];
  // Mutable, không readonly: nơi gọi truyền thẳng vào form.setValue("gradeColumns", ...), mà kiểu
  // trường đó do Zod suy ra từ schema (z.array(...) luôn là mảng mutable).
  readonly onChange: (columns: GradeColumnRow[]) => void;
}

const DEFAULT_MAX_SCORE = "10";

function totalWeightOf(columns: readonly GradeColumnRow[]): number {
  return columns.reduce((sum, column) => sum + Number(column.weightPercent || "0"), 0);
}

export function GradeColumnEditor({ columns, onChange }: GradeColumnEditorProps) {
  const total = totalWeightOf(columns);
  const isBalanced =
    columns.length === 0 || Math.abs(total - REQUIRED_TOTAL_WEIGHT_PERCENT) <= WEIGHT_TOLERANCE;

  const addColumn = () => {
    onChange([...columns, { name: "", weightPercent: "", maxScore: DEFAULT_MAX_SCORE }]);
  };

  const updateColumn = (index: number, patch: Partial<GradeColumnRow>) => {
    onChange(columns.map((column, i) => (i === index ? { ...column, ...patch } : column)));
  };

  const removeColumn = (index: number) => {
    onChange(columns.filter((_, i) => i !== index));
  };

  return (
    <div className="flex flex-col gap-2">
      {columns.map((column, index) => (
        <div key={index} className="flex items-center gap-2">
          <GlassInput
            aria-label="Tên cột điểm"
            placeholder="Giữa kỳ"
            value={column.name}
            onChange={(event) => updateColumn(index, { name: event.target.value })}
            className="min-w-0 flex-1"
          />
          <GlassInput
            aria-label="Trọng số (%)"
            type="number"
            min={0}
            step={0.01}
            placeholder="40"
            value={column.weightPercent}
            onChange={(event) => updateColumn(index, { weightPercent: event.target.value })}
            className="w-24"
          />
          <span className="text-xs text-mist-500">%</span>
          <GlassInput
            aria-label="Thang điểm"
            type="number"
            min={0}
            step={0.01}
            value={column.maxScore}
            onChange={(event) => updateColumn(index, { maxScore: event.target.value })}
            className="w-24"
          />
          <GlassButton
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => removeColumn(index)}
            aria-label="Xoá cột điểm"
          >
            <X size={14} aria-hidden />
          </GlassButton>
        </div>
      ))}

      <div className="flex items-center justify-between gap-2">
        <GlassButton
          type="button"
          variant="secondary"
          size="sm"
          onClick={addColumn}
          icon={<Plus size={14} aria-hidden />}
        >
          Thêm cột điểm
        </GlassButton>
        <GradeColumnTotal count={columns.length} total={total} isBalanced={isBalanced} />
      </div>
    </div>
  );
}

/** Tách ra để component chính không rẽ nhánh trong JSX (Mandate #3). */
function GradeColumnTotal({
  count,
  total,
  isBalanced,
}: {
  readonly count: number;
  readonly total: number;
  readonly isBalanced: boolean;
}) {
  if (count === 0) {
    return <p className="text-xs text-mist-500">Chưa cấu hình cột điểm nào.</p>;
  }
  return (
    <p className={isBalanced ? "text-xs text-mist-400" : "text-xs text-rose-300"}>
      Tổng trọng số: {total}% / {REQUIRED_TOTAL_WEIGHT_PERCENT}%
    </p>
  );
}
```

- [ ] **Step 4: Wire the editor into both dialogs**

In `frontend/src/modules/courses/ui/create-course-dialog.tsx`, add the imports:

```tsx
import { toGradeColumnPayload } from "@/modules/courses/model/courses-forms";
import { GradeColumnEditor } from "@/modules/courses/ui/grade-column-editor";
```

(giữ import `createCourseFormSchema` đang có, chỉ thêm `toGradeColumnPayload` vào cùng câu import từ
`@/modules/courses/model/courses-forms`.)

Replace the `useZodForm` call:

```tsx
  const form = useZodForm({
    schema: createCourseFormSchema,
    initialValues: {
      code: "",
      name: "",
      description: "",
      standardSessionCount: "",
      tuitionFee: "",
      gradeColumns: [],
    },
    onSubmit: async (values) => {
      await createCourse.mutateAsync({
        ...values,
        gradeColumns: toGradeColumnPayload(values.gradeColumns),
      });
      form.reset();
      onClose();
    },
  });
```

Add this `FormField` after the tuition-fee field, before `</form>`:

```tsx
        <FormField
          label="Cột điểm"
          htmlFor="course-grade-columns"
          hint="Không bắt buộc. Nếu có, tổng trọng số phải bằng 100%."
          error={form.fieldErrors.gradeColumns}
        >
          <div id="course-grade-columns">
            <GradeColumnEditor
              columns={form.values.gradeColumns}
              onChange={(columns) => form.setValue("gradeColumns", columns)}
            />
          </div>
        </FormField>
```

In `frontend/src/modules/courses/ui/edit-course-dialog.tsx`, add the same two imports, then replace
the `useZodForm` call:

```tsx
  const form = useZodForm({
    schema: updateCourseFormSchema,
    initialValues: {
      name: course.name,
      description: course.description ?? "",
      standardSessionCount: course.standardSessionCount?.toString() ?? "",
      tuitionFee: course.tuitionFee?.toString() ?? "",
      active: course.active,
      gradeColumns: course.gradeColumns.map((column) => ({
        name: column.name,
        weightPercent: column.weightPercent.toString(),
        maxScore: column.maxScore.toString(),
      })),
    },
    onSubmit: async (values) => {
      await updateCourse.mutateAsync({
        ...values,
        gradeColumns: toGradeColumnPayload(values.gradeColumns),
      });
      onClose();
    },
  });
```

and add the same `FormField` block (đổi `htmlFor`/`id` thành `edit-course-grade-columns`) after the
tuition-fee field, before the `CheckableRow`.

- [ ] **Step 5: Show the column count in the course list**

In `frontend/src/modules/courses/ui/courses-table.tsx`, add one cell so người dùng thấy khóa nào đã
cấu hình cột điểm mà không phải mở từng dialog. Thêm vào mỗi dòng, cạnh ô số buổi chuẩn (giữ nguyên
lớp Tailwind của các ô text khác trong file đó, và cập nhật `grid-cols-*` của dòng thêm đúng một cột):

```tsx
          <p className="truncate text-xs text-mist-400">
            {course.gradeColumns.length === 0
              ? "Chưa có cột điểm"
              : `${course.gradeColumns.length} cột điểm`}
          </p>
```

- [ ] **Step 6: Verify**

Run: `cd frontend && npm run typecheck`
Expected: PASS — 0 errors. Lỗi `Property 'gradeColumns' is missing` của Task 22 đã hết.

Run: `cd frontend && npm run lint`
Expected: PASS — 0 errors (kiểm `complexity` ≤ 15 cho `GradeColumnEditor` và `boundaries` cho import
từ `@/shared/api/schemas`).

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/modules/courses/model/courses-forms.ts \
        frontend/src/modules/courses/ui/grade-column-editor.tsx \
        frontend/src/modules/courses/ui/create-course-dialog.tsx \
        frontend/src/modules/courses/ui/edit-course-dialog.tsx \
        frontend/src/modules/courses/ui/courses-table.tsx
git commit -m "feat(frontend): configure course grade columns with a live weight total"
```

---

## Task 25: Trang "Sổ điểm" theo lớp

**Files:**
- Create: `frontend/src/modules/academic/api/use-academic-mutations.ts`
- Create: `frontend/src/modules/academic/model/academic-forms.ts`
- Create: `frontend/src/modules/academic/hooks/use-gradebook-controller.ts`
- Create: `frontend/src/modules/academic/ui/gradebook-table.tsx`
- Create: `frontend/src/modules/academic/ui/grade-score-dialog.tsx`
- Create: `frontend/src/modules/academic/pages/gradebook-page.tsx`

**Interfaces:**
- Consumes: `useGradebook`, `academicApi`, `academicKeys`, `type Gradebook`, `type StudentGradesRow`, `ENROLLMENT_STATUS_LABEL`, `type RecordGradePayload` (Task 23), `type GradeColumn` (Task 22), `ACCESS_RULE.readGrade`/`.updateGrade` (Task 21), `downloadBlob`, `useZodForm`, `Can`/`RequirePermission`.
- Produces: `useRecordGrade(classId)`; `recordGradeFormSchema`; `useGradebookController(classId)` trả `{ gradebook, editing, startEditing, stopEditing, onDownload, isDownloading }`; `GradebookTable`; `GradeScoreDialog`; `GradebookPage`.

- [ ] **Step 1: Write the mutation hook**

Create `frontend/src/modules/academic/api/use-academic-mutations.ts`:

```ts
import { academicApi, academicKeys, type RecordGradePayload } from "@/entities/academic";
import { useMutation, useQueryClient } from "@tanstack/react-query";

/**
 * Nhập/sửa điểm xong phải bỏ cache CẢ sổ điểm của lớp: điểm tổng kết của dòng vừa sửa do backend
 * tính (null khi còn thiếu cột), nên không thể cập nhật lạc quan ở client mà không có nguy cơ hiện
 * một con số sai trong khoảnh khắc.
 */
export function useRecordGrade(classId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: RecordGradePayload) => academicApi.recordGrade(payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: academicKeys.gradebook(classId) });
    },
  });
}
```

- [ ] **Step 2: Write the form schema**

Create `frontend/src/modules/academic/model/academic-forms.ts`:

```ts
import { z } from "zod";

/**
 * Trần của điểm là `maxScore` của RIÊNG cột đang nhập, nên nó được truyền vào lúc dựng schema thay
 * vì là một hằng trong file. Backend kiểm lại bằng ACADEMIC_INVALID_SCORE - luật ở đây chỉ để người
 * nhập thấy lỗi trước khi gửi.
 */
export function createRecordGradeFormSchema(maxScore: number) {
  return z.object({
    score: z
      .string()
      .min(1, "Nhập điểm")
      .transform((value) => Number(value))
      .refine((value) => Number.isFinite(value), "Điểm phải là một số")
      .refine((value) => value >= 0, "Điểm không được âm")
      .refine((value) => value <= maxScore, `Điểm không được vượt thang ${maxScore}`),
  });
}
```

- [ ] **Step 3: Write the controller hook**

Create `frontend/src/modules/academic/hooks/use-gradebook-controller.ts`:

```ts
import { academicApi, useGradebook, type StudentGradesRow } from "@/entities/academic";
import { downloadBlob } from "@/shared/lib/download-blob";
import type { GradeColumn } from "@/shared/api/schemas";
import { useCallback, useState } from "react";

export interface EditingCell {
  readonly row: StudentGradesRow;
  readonly column: GradeColumn;
}

const FALLBACK_FILE_NAME = "bang-diem.xlsx";

/** Toàn bộ state/query/mutation của trang Sổ điểm - ui/* chỉ render (Mandate #2). */
export function useGradebookController(classId: string) {
  const [editing, setEditing] = useState<EditingCell | null>(null);
  const [isDownloading, setIsDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<unknown>(null);
  const gradebook = useGradebook(classId);

  const startEditing = useCallback((row: StudentGradesRow, column: GradeColumn) => {
    setEditing({ row, column });
  }, []);

  const stopEditing = useCallback(() => setEditing(null), []);

  const onDownload = useCallback(() => {
    setIsDownloading(true);
    setDownloadError(null);
    void academicApi
      .downloadGradebook(classId)
      .then(({ blob, fileName }) => {
        downloadBlob(blob, fileName ?? FALLBACK_FILE_NAME);
      })
      .catch((error: unknown) => {
        // 403 ACADEMIC_NOT_ASSIGNED_TO_CLASS là nguồn sự thật về quyền (spec mục 10) - hiện nguyên
        // lỗi của backend thay vì im lặng.
        setDownloadError(error);
      })
      .finally(() => {
        setIsDownloading(false);
      });
  }, [classId]);

  return {
    gradebook,
    editing,
    startEditing,
    stopEditing,
    onDownload,
    isDownloading,
    downloadError,
  };
}
```

- [ ] **Step 4: Write the table**

Create `frontend/src/modules/academic/ui/gradebook-table.tsx`:

```tsx
import { ENROLLMENT_STATUS_LABEL, type StudentGradesRow } from "@/entities/academic";
import type { GradeColumn } from "@/shared/api/schemas";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";

export interface GradebookTableProps {
  readonly columns: readonly GradeColumn[];
  readonly rows: readonly StudentGradesRow[];
  readonly canEdit: boolean;
  readonly onEditScore: (row: StudentGradesRow, column: GradeColumn) => void;
}

/** Điểm tổng kết null = "chưa đủ điểm" (backend GradeRules), KHÔNG được hiển thị 0. */
function FinalScoreCell({ finalScore }: { readonly finalScore: number | null }) {
  if (finalScore === null) {
    return <Badge tone="neutral">Chưa đủ điểm</Badge>;
  }
  return <span className="text-sm text-mist-100">{finalScore}</span>;
}

function ScoreCell({
  row,
  column,
  canEdit,
  onEditScore,
}: {
  readonly row: StudentGradesRow;
  readonly column: GradeColumn;
  readonly canEdit: boolean;
  readonly onEditScore: (row: StudentGradesRow, column: GradeColumn) => void;
}) {
  const score = row.scoresByColumnId[column.id];
  const label = score === undefined ? "—" : String(score);

  if (!canEdit) {
    return <span className="text-sm text-mist-100">{label}</span>;
  }
  return (
    <GlassButton
      variant="ghost"
      size="sm"
      onClick={() => onEditScore(row, column)}
      aria-label={`Sửa điểm ${column.name} của ${row.studentName ?? "học viên"}`}
    >
      {label}
    </GlassButton>
  );
}

export function GradebookTable({ columns, rows, canEdit, onEditScore }: GradebookTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] text-left">
        <thead>
          <tr className="text-xs text-mist-500">
            <th scope="col" className="p-2 font-normal">Học viên</th>
            <th scope="col" className="p-2 font-normal">Trạng thái</th>
            {columns.map((column) => (
              <th key={column.id} scope="col" className="p-2 font-normal">
                {column.name} ({column.weightPercent}% · thang {column.maxScore})
              </th>
            ))}
            <th scope="col" className="p-2 font-normal">Điểm tổng kết</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.enrollmentId} className="glass rounded-2xl">
              <td className="p-2 text-sm text-mist-100">{row.studentName ?? "—"}</td>
              <td className="p-2 text-xs text-mist-400">{ENROLLMENT_STATUS_LABEL[row.status]}</td>
              {columns.map((column) => (
                <td key={column.id} className="p-2">
                  <ScoreCell row={row} column={column} canEdit={canEdit} onEditScore={onEditScore} />
                </td>
              ))}
              <td className="p-2">
                <FinalScoreCell finalScore={row.finalScore} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 5: Write the score dialog**

Create `frontend/src/modules/academic/ui/grade-score-dialog.tsx`:

```tsx
import type { StudentGradesRow } from "@/entities/academic";
import { useRecordGrade } from "@/modules/academic/api/use-academic-mutations";
import { createRecordGradeFormSchema } from "@/modules/academic/model/academic-forms";
import type { GradeColumn } from "@/shared/api/schemas";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { useMemo } from "react";

export interface GradeScoreDialogProps {
  readonly classId: string;
  readonly row: StudentGradesRow;
  readonly column: GradeColumn;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function GradeScoreDialog({ classId, row, column, open, onClose }: GradeScoreDialogProps) {
  const recordGrade = useRecordGrade(classId);
  const schema = useMemo(() => createRecordGradeFormSchema(column.maxScore), [column.maxScore]);
  const currentScore = row.scoresByColumnId[column.id];

  const form = useZodForm({
    schema,
    initialValues: { score: currentScore === undefined ? "" : String(currentScore) },
    onSubmit: async (values) => {
      await recordGrade.mutateAsync({
        classId,
        enrollmentId: row.enrollmentId,
        courseGradeColumnId: column.id,
        score: values.score,
      });
      onClose();
    },
  });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title={`Điểm ${column.name}`}
      description={`${row.studentName ?? "Học viên"} · thang điểm ${column.maxScore} · trọng số ${column.weightPercent}%`}
    >
      <form id="record-grade-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Điểm" htmlFor="record-grade-score" error={form.fieldErrors.score}>
          <GlassInput
            id="record-grade-score"
            autoFocus
            type="number"
            min={0}
            max={column.maxScore}
            step={0.01}
            value={form.values.score}
            invalid={form.fieldErrors.score !== undefined}
            onChange={(event) => form.setValue("score", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="record-grade-form" loading={form.isSubmitting}>
          Lưu điểm
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

- [ ] **Step 6: Write the page**

Create `frontend/src/modules/academic/pages/gradebook-page.tsx`:

```tsx
import { RequirePermission, usePermissions } from "@/entities/permission";
import { useGradebookController } from "@/modules/academic/hooks/use-gradebook-controller";
import { GradebookTable } from "@/modules/academic/ui/gradebook-table";
import { GradeScoreDialog } from "@/modules/academic/ui/grade-score-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { buildClassProgressPath } from "@/shared/constants/app-routes";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { Download, LineChart, Users } from "lucide-react";
import { Link, useParams } from "react-router-dom";

export function GradebookPage() {
  const { classId = "" } = useParams<{ classId: string }>();
  const controller = useGradebookController(classId);
  const permissions = usePermissions();
  const canEdit = permissions.allows(ACCESS_RULE.updateGrade);

  return (
    <RequirePermission {...ACCESS_RULE.readGrade}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Sổ điểm"
          description="Điểm từng cột theo trọng số đã cấu hình ở khóa học. Điểm tổng kết chỉ hiện khi đủ điểm mọi cột."
          actions={
            <div className="flex gap-2">
              <Link to={buildClassProgressPath(classId)}>
                <GlassButton variant="secondary" size="sm" icon={<LineChart size={14} aria-hidden />}>
                  Tiến trình học tập
                </GlassButton>
              </Link>
              {/* Không bọc thêm <Can readGrade>: cả trang đã nằm trong RequirePermission cùng rule
                  đó, bọc lại là một lớp kiểm tra chết. */}
              <GlassButton
                size="sm"
                loading={controller.isDownloading}
                onClick={controller.onDownload}
                icon={<Download size={14} aria-hidden />}
              >
                Tải Excel
              </GlassButton>
            </div>
          }
        />

        {/* Nguồn sự thật về quyền là lỗi thật từ backend: 403 ACADEMIC_NOT_ASSIGNED_TO_CLASS hiện ở
            đây, giao diện không tự kết luận "tôi là giáo viên lớp này" (spec mục 10). */}
        {controller.gradebook.isError ? <ErrorNotice error={controller.gradebook.error} /> : null}
        {controller.downloadError ? <ErrorNotice error={controller.downloadError} /> : null}
        {controller.gradebook.isPending ? <Skeleton className="h-40" /> : null}

        {controller.gradebook.data ? (
          <GlassPanel className="flex flex-col gap-4">
            <GradebookBody controller={controller} canEdit={canEdit} />
          </GlassPanel>
        ) : null}

        {controller.editing === null ? null : (
          <GradeScoreDialog
            key={`${controller.editing.row.enrollmentId}-${controller.editing.column.id}`}
            classId={classId}
            row={controller.editing.row}
            column={controller.editing.column}
            open
            onClose={controller.stopEditing}
          />
        )}
      </div>
    </RequirePermission>
  );
}

/** Tách ra để trang không rẽ nhánh trong JSX (Mandate #3). */
function GradebookBody({
  controller,
  canEdit,
}: {
  readonly controller: ReturnType<typeof useGradebookController>;
  readonly canEdit: boolean;
}) {
  const gradebook = controller.gradebook.data;
  if (!gradebook) {
    return null;
  }
  if (gradebook.columns.length === 0) {
    return (
      <EmptyState
        icon={<Users size={28} aria-hidden />}
        title="Khóa học chưa cấu hình cột điểm"
        description="Mở màn hình Khóa học và thêm các cột điểm (tổng trọng số 100%) trước khi nhập điểm."
      />
    );
  }
  if (gradebook.rows.length === 0) {
    return (
      <EmptyState
        icon={<Users size={28} aria-hidden />}
        title="Lớp chưa có học viên nào"
        description="Ghi danh học viên vào lớp này rồi quay lại để nhập điểm."
      />
    );
  }
  return (
    <GradebookTable
      columns={gradebook.columns}
      rows={gradebook.rows}
      canEdit={canEdit}
      onEditScore={controller.startEditing}
    />
  );
}
```

- [ ] **Step 7: Verify**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: cả hai PASS, 0 errors. `boundaries` chấp nhận vì mọi import của `modules/academic` chỉ đi
tới chính nó, `@/entities/*` và `@/shared/*`.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/modules/academic/api/ \
        frontend/src/modules/academic/model/ \
        frontend/src/modules/academic/hooks/use-gradebook-controller.ts \
        frontend/src/modules/academic/ui/ \
        frontend/src/modules/academic/pages/gradebook-page.tsx
git commit -m "feat(frontend): add the class gradebook page with score entry and excel export"
```

---

## Task 26: Trang "Tiến trình học tập" + route + đường vào từ danh sách lớp

**Files:**
- Create: `frontend/src/modules/academic/hooks/use-class-progress-controller.ts`
- Create: `frontend/src/modules/academic/ui/class-progress-table.tsx`
- Create: `frontend/src/modules/academic/pages/class-progress-page.tsx`
- Create: `frontend/src/modules/academic/index.ts`
- Modify: `frontend/src/app/router/app-router.tsx`
- Modify: `frontend/src/modules/courses/ui/classes-table.tsx`

**Interfaces:**
- Consumes: `useClassProgress`, `academicApi.downloadClassProgress`, `type StudentProgressRow`, `ENROLLMENT_STATUS_LABEL` (Task 23), `APP_ROUTE.classGradebook`/`.classProgress`, `buildClassGradebookPath`/`buildClassProgressPath` (Task 21), `ACCESS_RULE.readGrade`, `formatter.dateTime`, `downloadBlob`.
- Produces: `useClassProgressController(classId)` trả `{ progress, onDownload, isDownloading, downloadError }`; `ClassProgressTable`; `ClassProgressPage`; `modules/academic/index.ts` export `GradebookPage` + `ClassProgressPage`; hai `<Route>` mới; hai link mới trên mỗi dòng của `ClassesTable`.

> Không thêm mục menu cấp 1 (spec mục 10): hai trang này vào từ danh sách lớp, mirror cách
> `invoiceDetail`/`comboDetail` không có mục trong `NAV_SECTIONS`. Vì vậy `app/layouts/nav-items.ts`
> KHÔNG bị sửa trong task này — cố ý, không phải bỏ sót.

- [ ] **Step 1: Write the controller hook**

Create `frontend/src/modules/academic/hooks/use-class-progress-controller.ts`:

```ts
import { academicApi, useClassProgress } from "@/entities/academic";
import { downloadBlob } from "@/shared/lib/download-blob";
import { useCallback, useState } from "react";

const FALLBACK_FILE_NAME = "tien-trinh-hoc-tap.xlsx";

/** Toàn bộ state/query của trang Tiến trình - ui/* chỉ render (Mandate #2). Không có mutation nào:
 * tiến trình được TÍNH ở backend, không ai nhập (spec mục 1). */
export function useClassProgressController(classId: string) {
  const [isDownloading, setIsDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<unknown>(null);
  const progress = useClassProgress(classId);

  const onDownload = useCallback(() => {
    setIsDownloading(true);
    setDownloadError(null);
    void academicApi
      .downloadClassProgress(classId)
      .then(({ blob, fileName }) => {
        downloadBlob(blob, fileName ?? FALLBACK_FILE_NAME);
      })
      .catch((error: unknown) => {
        setDownloadError(error);
      })
      .finally(() => {
        setIsDownloading(false);
      });
  }, [classId]);

  return { progress, onDownload, isDownloading, downloadError };
}
```

- [ ] **Step 2: Write the table**

Create `frontend/src/modules/academic/ui/class-progress-table.tsx`:

```tsx
import { ENROLLMENT_STATUS, ENROLLMENT_STATUS_LABEL, type StudentProgressRow } from "@/entities/academic";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";

export interface ClassProgressTableProps {
  readonly rows: readonly StudentProgressRow[];
}

/**
 * `progressPercent` null = khóa chưa cấu hình tổng số buổi chuẩn (spec mục 5) - phải nói thành chữ,
 * không hiển thị 0% (người xem sẽ hiểu sai là học viên chưa học buổi nào).
 */
function ProgressCell({ row }: { readonly row: StudentProgressRow }) {
  if (row.progressPercent === null) {
    return <Badge tone="neutral">Chưa cấu hình tổng số buổi chuẩn</Badge>;
  }
  return (
    <div className="flex items-center gap-2">
      <div className="h-1.5 w-24 overflow-hidden rounded-full bg-white/10">
        <div className="h-full rounded-full bg-sky-400" style={{ width: `${row.progressPercent}%` }} />
      </div>
      <span className="text-sm text-mist-100">{row.progressPercent}%</span>
    </div>
  );
}

/** Học viên đã rút: tiến trình đóng băng tại mốc rút, nên phải nói rõ mốc đó ngay trên dòng. */
function StatusCell({ row }: { readonly row: StudentProgressRow }) {
  if (row.status !== ENROLLMENT_STATUS.withdrawn || row.withdrawnAt === null) {
    return <span className="text-xs text-mist-400">{ENROLLMENT_STATUS_LABEL[row.status]}</span>;
  }
  return (
    <span className="text-xs text-mist-400">
      {ENROLLMENT_STATUS_LABEL[row.status]} · {formatter.dateTime(row.withdrawnAt)}
    </span>
  );
}

export function ClassProgressTable({ rows }: ClassProgressTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] text-left">
        <thead>
          <tr className="text-xs text-mist-500">
            <th scope="col" className="p-2 font-normal">Học viên</th>
            <th scope="col" className="p-2 font-normal">Trạng thái</th>
            <th scope="col" className="p-2 font-normal">Ngày ghi danh</th>
            <th scope="col" className="p-2 font-normal">Số buổi đã học</th>
            <th scope="col" className="p-2 font-normal">Tiến trình</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.enrollmentId} className="glass rounded-2xl">
              <td className="p-2 text-sm text-mist-100">{row.studentName ?? "—"}</td>
              <td className="p-2">
                <StatusCell row={row} />
              </td>
              <td className="p-2 text-xs text-mist-400">{formatter.dateTime(row.enrolledAt)}</td>
              <td className="p-2 text-sm text-mist-100">
                {row.elapsedSessions}
                {row.standardSessionCount === null ? "" : ` / ${row.standardSessionCount}`}
              </td>
              <td className="p-2">
                <ProgressCell row={row} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 3: Write the page**

Create `frontend/src/modules/academic/pages/class-progress-page.tsx`:

```tsx
import { RequirePermission } from "@/entities/permission";
import { useClassProgressController } from "@/modules/academic/hooks/use-class-progress-controller";
import { ClassProgressTable } from "@/modules/academic/ui/class-progress-table";
import { buildClassGradebookPath } from "@/shared/constants/app-routes";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { BookOpen, Download, Users } from "lucide-react";
import { Link, useParams } from "react-router-dom";

export function ClassProgressPage() {
  const { classId = "" } = useParams<{ classId: string }>();
  const controller = useClassProgressController(classId);

  return (
    <RequirePermission {...ACCESS_RULE.readGrade}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Tiến trình học tập"
          description="Tự tính từ lịch học của lớp và ngày ghi danh của từng học viên — không ai nhập. Học viên đã rút được đóng băng tại thời điểm rút."
          actions={
            <div className="flex gap-2">
              <Link to={buildClassGradebookPath(classId)}>
                <GlassButton variant="secondary" size="sm" icon={<BookOpen size={14} aria-hidden />}>
                  Sổ điểm
                </GlassButton>
              </Link>
              {/* Cùng lý do với trang Sổ điểm: RequirePermission ở ngoài đã gác đúng rule này. */}
              <GlassButton
                size="sm"
                loading={controller.isDownloading}
                onClick={controller.onDownload}
                icon={<Download size={14} aria-hidden />}
              >
                Tải Excel
              </GlassButton>
            </div>
          }
        />

        {controller.progress.isError ? <ErrorNotice error={controller.progress.error} /> : null}
        {controller.downloadError ? <ErrorNotice error={controller.downloadError} /> : null}
        {controller.progress.isPending ? <Skeleton className="h-40" /> : null}

        {controller.progress.data ? (
          <GlassPanel className="flex flex-col gap-4">
            <ProgressBody controller={controller} />
          </GlassPanel>
        ) : null}
      </div>
    </RequirePermission>
  );
}

/** Tách ra để trang không rẽ nhánh trong JSX (Mandate #3). */
function ProgressBody({
  controller,
}: {
  readonly controller: ReturnType<typeof useClassProgressController>;
}) {
  const progress = controller.progress.data;
  if (!progress) {
    return null;
  }
  if (progress.rows.length === 0) {
    return (
      <EmptyState
        icon={<Users size={28} aria-hidden />}
        title="Lớp chưa có học viên nào"
        description="Ghi danh học viên vào lớp này rồi quay lại để xem tiến trình."
      />
    );
  }
  return (
    <>
      <p className="text-xs text-mist-500">
        {progress.standardSessionCount === null
          ? "Khóa học chưa cấu hình tổng số buổi chuẩn nên không tính được phần trăm tiến trình."
          : `Tổng số buổi chuẩn của khóa: ${progress.standardSessionCount}.`}
      </p>
      <ClassProgressTable rows={progress.rows} />
    </>
  );
}
```

- [ ] **Step 4: Write the module barrel**

Create `frontend/src/modules/academic/index.ts`:

```ts
export { ClassProgressPage } from "@/modules/academic/pages/class-progress-page";
export { GradebookPage } from "@/modules/academic/pages/gradebook-page";
```

- [ ] **Step 5: Register the two routes**

In `frontend/src/app/router/app-router.tsx`, add the two lazy imports after `ComboDiscountTiersPage`:

```tsx
const GradebookPage = lazy(() =>
  import("@/modules/academic").then((module) => ({ default: module.GradebookPage })),
);
const ClassProgressPage = lazy(() =>
  import("@/modules/academic").then((module) => ({ default: module.ClassProgressPage })),
);
```

and the two routes inside the `<Route element={<RequireAuth />}>` block, after `comboDiscountTiers`:

```tsx
          <Route path={APP_ROUTE.classGradebook} element={<GradebookPage />} />
          <Route path={APP_ROUTE.classProgress} element={<ClassProgressPage />} />
```

- [ ] **Step 6: Add the two entry links to the class list**

In `frontend/src/modules/courses/ui/classes-table.tsx`, add the imports:

```tsx
import { buildClassGradebookPath, buildClassProgressPath } from "@/shared/constants/app-routes";
import { BookOpen, LineChart, Pencil } from "lucide-react";
import { Link } from "react-router-dom";
```

(gộp `BookOpen`/`LineChart` vào câu import `lucide-react` đang có `Pencil`.)

Add this block right before the existing `<Can {...ACCESS_RULE.updateClass}>` block, and widen the
row's `lg:grid-cols-[...]` by one `auto` column:

```tsx
          <Can {...ACCESS_RULE.readGrade}>
            <div className="flex gap-1 justify-self-start lg:justify-self-end">
              <Link to={buildClassGradebookPath(cls.id)}>
                <GlassButton variant="ghost" size="sm" icon={<BookOpen size={14} aria-hidden />}>
                  Sổ điểm
                </GlassButton>
              </Link>
              <Link to={buildClassProgressPath(cls.id)}>
                <GlassButton variant="ghost" size="sm" icon={<LineChart size={14} aria-hidden />}>
                  Tiến trình
                </GlassButton>
              </Link>
            </div>
          </Can>
```

- [ ] **Step 7: Verify the whole frontend**

Run: `cd frontend && npm run typecheck`
Expected: PASS — 0 errors.

Run: `cd frontend && npm run lint`
Expected: PASS — 0 errors. Đặc biệt không có `boundaries/dependencies` (modules/academic không import
modules/courses và ngược lại; `classes-table.tsx` chỉ dùng `@/shared/constants/app-routes`) và không
có `import/no-cycle`.

Run: `cd frontend && npm run build`
Expected: BUILD SUCCESS, hai chunk mới cho `modules/academic`.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/modules/academic/ \
        frontend/src/app/router/app-router.tsx \
        frontend/src/modules/courses/ui/classes-table.tsx
git commit -m "feat(frontend): add the class progress page and link both academic pages from the class list"
```

---

## Task 27: Kiểm tra toàn bộ + soát lại gợn sóng của thay đổi facade

**Files:**
- Không tạo/sửa file nào. Nếu một bước nào đỏ, sửa tại chỗ rồi chạy lại cả danh sách từ đầu.

**Interfaces:**
- Consumes: toàn bộ 26 task phía trên.
- Produces: không có type mới.

- [ ] **Step 1: Verify module boundaries first**

Run: `cd backend && ./mvnw test -Dtest=ModularityTests`
Expected: PASS — 2 tests. `everyDomainAndIntegrationPackageIsADetectedModule` phải thấy đúng **19**
module (18 cũ + `modules.academic`), và `verifiesModularStructure` xanh nghĩa là `modules.academic`
không chọc vào `internal` của module nào, cũng không module nào chọc vào `academic.internal`.

- [ ] **Step 2: Verify the `ClassInfoResponse` ripple hit every call site**

Run: `cd backend && grep -rn "ClassInfoResponse\|getClassInfo" src/main/java src/test/java`
Expected: mỗi dòng in ra phải thuộc một trong các nhóm sau, và KHÔNG có dòng nào ngoài danh sách:
- `modules/courses/CoursesManagement.java` — nơi định nghĩa record + `getClassInfo` + `toClassInfo` (Task 4)
- `modules/courses/package-info.java` / `modules/enrollment/package-info.java` — javadoc (Task 4 Step 7)
- `modules/enrollment/usecase/CreateEnrollment.java` — đọc field theo tên, không đổi
- `modules/academic/internal/access/ClassGradeAccessGuard.java` — nơi dùng `teacherId` (Task 11)
- `modules/academic/usecase/GetClassProgress.java` — nơi dùng `schedule` + `standardSessionCount` (Task 14)
- `test/.../CoursesManagementIT.java`, `test/.../CreateEnrollmentTest.java`,
  `test/.../ClassGradeAccessGuardTest.java`, `test/.../RecordGradeTest.java`,
  `test/.../GetClassGradebookTest.java`, `test/.../GetClassProgressTest.java` — đều xây record với
  đủ **8** tham số

Run: `cd backend && ./mvnw -q clean compile test-compile`
Expected: BUILD SUCCESS — không còn call site nào dựng `ClassInfoResponse` với 5 tham số cũ.

- [ ] **Step 3: Verify every Review Focus item has a green test**

Run: `cd backend && ./mvnw test -Dtest='ProgressRulesTest,GradeRulesTest,GradeAccessRulesTest,ClassGradeAccessGuardTest,GradeColumnRulesTest'`
Expected: PASS — Review Focus #1, #2, #3, #4, #5, #6, #8, #9, #10, #12 ở tầng thuần/đơn vị.

Run: `cd backend && ./mvnw test -Dtest='RecordGradeTest,GetClassGradebookTest,GetClassProgressTest,ExportUseCasesTest,CourseGradeColumnUseCasesTest'`
Expected: PASS — cùng các mục trên ở tầng usecase, cộng Review Focus #7 và #11.

Run: `cd backend && ./mvnw test -Dtest='GradeAdminControllerIT,GradeExportControllerIT,AcademicRbacSeedIT,CourseAdminControllerIT'`
Expected: PASS — Review Focus #4, #5, #6, #7 end-to-end qua HTTP với quyền thật, và seed RBAC không
lọt quyền sang `STUDENT`.

- [ ] **Step 4: Run the full backend suite**

Run: `cd backend && ./mvnw clean verify`
Expected: BUILD SUCCESS, 0 failures, 0 errors — gồm cả Flyway chạy V23/V24/V25 trên Postgres thật
trong mọi IT, và `ddl-auto: validate` xác nhận hai entity mới khớp schema.

- [ ] **Step 5: Run the full frontend suite**

Run: `cd frontend && npm run lint && npm run typecheck && npm run build`
Expected: cả ba PASS, 0 errors.

- [ ] **Step 6: Confirm nothing out of scope was added**

Run: `cd backend && grep -rni "attendance\|diem_danh\|điểm danh\|assignment\|bai_tap\|bài tập" src/main/java src/main/resources/db/migration`
Expected: không có kết quả nào thuộc Phân hệ 4 (spec mục 1 bỏ hẳn điểm danh và bài tập/mini-test).

Run: `cd backend && grep -rn "progress_percent\|progressPercent" src/main/resources/db/migration`
Expected: KHÔNG có kết quả — tiến trình không được lưu ở bất kỳ cột DB nào, nó được tính lúc đọc
(spec mục 5).

Run: `cd backend && grep -rn "GRADE" src/main/resources/db/migration/V25__seed_academic_rbac.sql | grep -i "0000000000000002"`
Expected: KHÔNG có kết quả — quyền GRADE không được nhét vào group "Tự phục vụ cơ bản" mà role
`STUDENT` cũng đang giữ.

- [ ] **Step 7: Commit anything the verification pass had to fix**

```bash
git status
```

Nếu working tree sạch thì không cần commit. Nếu phải sửa gì ở Step 1-6:

```bash
git add -A
git commit -m "fix: address issues found during full-suite verification"
```

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-10-08-phan-he-4-quan-ly-hoc-tap.md`. Please
review the plan. Does it capture what you want?
