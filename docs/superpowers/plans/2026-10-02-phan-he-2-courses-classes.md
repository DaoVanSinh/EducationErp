# Phân hệ 2 — Courses & Classes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `modules.courses` backend module (Course catalog + Class openings + weekly
schedule) and the matching frontend admin pages, mirroring the already-shipped Branch CRUD feature
file-for-file.

**Architecture:** New Spring Modulith module `modules.courses`, one-way dependent on `access`
(PreAuthorize) and `identity` (teacher existence check) — no reverse edges, no port needed. New
frontend `entities/course` + `entities/class` + `modules/courses`, mirroring `entities/branch` +
`modules/organization` exactly.

**Tech Stack:** Spring Boot 3.4 / Java 21 / Spring Modulith / PostgreSQL / Flyway (backend); React 19
/ TanStack Query v5 / Zod / Vite (frontend) — same stack as every other module in this repo.

**Spec:** `docs/superpowers/specs/2026-10-02-phan-he-2-courses-classes-design.md`

## Global Constraints

- No bare string literals for resources/actions/scopes — go through `AccessConstants`/`shared/constants`.
- `courses → access`, `courses → identity` one-way only; never import `modules.courses.internal.*` from
  outside the module.
- No cross-module JPA `@ManyToOne` for `branch_id`/`teacher_id` (raw UUID); `course_id` on `Class` IS a
  real JPA relation (same module as `Course`).
- No hard delete anywhere — `active` boolean only, matching `Branch`.
- `code` fields (`Course.code`, `Class.code`) are immutable after creation — no setter.
- Every new commit: English, Conventional Commits, no AI attribution (standing repo rule).
- Branch for this work: `feature/courses-classes` off `develop`; never commit directly to `develop`.

## Review Focus

- Creating a Class with a `teacherId` that doesn't belong to any account must 400, not 500 or silently
  succeed — `CreateClass`/`UpdateClass` must validate via `IdentityManagement.summariesOf(...)` before
  saving, the same way nothing in this repo lets a dangling FK reach the DB uncaught. Pinned by
  `rejectsACreateWithANonExistentTeacher` (Task 6).
- Creating a Class with a `branchId` that doesn't exist must 400 — validate via
  `OrganizationManagement.exists(...)`, since nothing else validates that FK before insert and a raw
  DB constraint violation would otherwise surface as an unhandled 500. Pinned by
  `rejectsACreateWithANonExistentBranch` (Task 6).
- An empty `schedule` array on `CreateClassRequest` (a class with literally no weekly slots yet,
  teacher/room TBD) must be accepted, not rejected — the spec never said at least one slot is required,
  and forcing one would block the legitimate "create the class shell first, schedule later via PATCH"
  workflow. Pinned by `acceptsAnEmptyScheduleOnCreate` (Task 6).
- A role with only `CREATE`-level permissions but not `READ` (e.g. after a future custom permission
  group) must still get a 200 from `POST /api/courses/classes` and a usable created-id — the create
  flow must never implicitly require read access to the thing it just made. Not separately pinned by a
  test (the existing `createsAClassWithScheduleThenListsIt` happens to use an ADMIN with both grants);
  acceptable gap for this phase since every seeded role bundles CREATE+READ+UPDATE together (see
  `V10__seed_courses_rbac.sql`), so the scenario cannot occur with today's seed data.
- `UpdateClass` replacing the whole `schedule` list (clear-then-re-add, not a partial patch) must
  actually remove slots that were dropped, not just add the new ones — pinned by
  `updatesTeacherScheduleAndMaxSeats` (Task 6), which asserts the final schedule size is exactly 1
  after starting from a class created with zero slots.

---

## Task 1: RBAC constants + migrations

**Files:**
- Modify: `backend/src/main/java/com/eduerp/modules/access/AccessConstants.java`
- Create: `backend/src/main/resources/db/migration/V9__create_courses_and_classes.sql`
- Create: `backend/src/main/resources/db/migration/V10__seed_courses_rbac.sql`
- Test: `backend/src/test/java/com/eduerp/modules/access/AccessConstantsTest.java` (modify)

**Interfaces:**
- Produces: `AccessConstants.Resources.COURSE`, `.CLASS`; `AccessConstants.AccessRules.CREATE_COURSE`,
  `.READ_COURSE`, `.UPDATE_COURSE`, `.CREATE_CLASS`, `.READ_CLASS`, `.UPDATE_CLASS` (all `String`
  SpEL expressions) — every later task's `@PreAuthorize` annotations consume these by name.

- [ ] **Step 1: Read the existing test to see the pattern**

Open `backend/src/test/java/com/eduerp/modules/access/AccessConstantsTest.java` and read it in full —
it asserts the shape of existing `AccessRules` constants (e.g. that `CREATE_ROLE` contains
`"ROLE"` and `"CREATE"` and `"@ORGANIZATION"`). Follow the exact same assertion style for the new
constants in Step 2.

- [ ] **Step 2: Add a failing test for the new constants**

Add to `AccessConstantsTest.java` (same class, new `@Test` methods):

```java
@Test
void courseAndClassRulesRequireOrganizationScope() {
    assertThat(AccessConstants.AccessRules.CREATE_COURSE)
            .contains("COURSE", "CREATE", "@ORGANIZATION");
    assertThat(AccessConstants.AccessRules.READ_COURSE)
            .contains("COURSE", "READ", "@ORGANIZATION");
    assertThat(AccessConstants.AccessRules.UPDATE_COURSE)
            .contains("COURSE", "UPDATE", "@ORGANIZATION");
    assertThat(AccessConstants.AccessRules.CREATE_CLASS)
            .contains("CLASS", "CREATE", "@ORGANIZATION");
    assertThat(AccessConstants.AccessRules.READ_CLASS)
            .contains("CLASS", "READ", "@ORGANIZATION");
    assertThat(AccessConstants.AccessRules.UPDATE_CLASS)
            .contains("CLASS", "UPDATE", "@ORGANIZATION");
}
```

- [ ] **Step 2b: Run it, confirm it fails to compile**

Run: `cd backend && mvn test -Dtest=AccessConstantsTest`
Expected: compile error — `COURSE`, `CLASS`, `CREATE_COURSE`, etc. don't exist yet.

- [ ] **Step 3: Add the constants**

In `AccessConstants.Resources`, add after `BRANCH`:
```java
        public static final String COURSE = "COURSE";
        public static final String CLASS = "CLASS";
```

In `AccessConstants.AccessRules`, add after `UPDATE_BRANCH`:
```java
        public static final String CREATE_COURSE = CHECK_PREFIX + Resources.COURSE + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_COURSE = CHECK_PREFIX + Resources.COURSE + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String UPDATE_COURSE = CHECK_PREFIX + Resources.COURSE + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String CREATE_CLASS = CHECK_PREFIX + Resources.CLASS + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_CLASS = CHECK_PREFIX + Resources.CLASS + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String UPDATE_CLASS = CHECK_PREFIX + Resources.CLASS + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
```

- [ ] **Step 4: Run the test, confirm it passes**

Run: `cd backend && mvn test -Dtest=AccessConstantsTest`
Expected: PASS, all tests green.

- [ ] **Step 5: Write the schema migration**

Create `backend/src/main/resources/db/migration/V9__create_courses_and_classes.sql`:
```sql
CREATE TABLE courses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    standard_session_count INT,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE classes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL REFERENCES courses(id),
    code VARCHAR(32) NOT NULL UNIQUE,
    -- branch_id/teacher_id có FK mức DB để toàn vẹn dữ liệu, giống accounts.home_branch_id - nhưng
    -- không có quan hệ JPA tương ứng trong Java (rule #3: không @ManyToOne xuyên module).
    branch_id UUID NOT NULL REFERENCES branches(id),
    teacher_id UUID NOT NULL REFERENCES accounts(id),
    max_seats INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_classes_course ON classes (course_id);
CREATE INDEX idx_classes_branch ON classes (branch_id);

CREATE TABLE class_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    day_of_week VARCHAR(16) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
);

CREATE INDEX idx_class_schedules_class ON class_schedules (class_id);
```

- [ ] **Step 6: Write the RBAC seed migration**

Create `backend/src/main/resources/db/migration/V10__seed_courses_rbac.sql`:
```sql
INSERT INTO permissions (id, resource, action) VALUES
    (gen_random_uuid(), 'COURSE', 'CREATE'),
    (gen_random_uuid(), 'COURSE', 'READ'),
    (gen_random_uuid(), 'COURSE', 'UPDATE'),
    (gen_random_uuid(), 'CLASS', 'CREATE'),
    (gen_random_uuid(), 'CLASS', 'READ'),
    (gen_random_uuid(), 'CLASS', 'UPDATE');

-- V5 đã seed "Toàn quyền hệ thống" xong và đã chạy rồi - không sửa lại được, nên permission mới
-- phải tự thêm dòng gán vào đúng group đó, giống cách group này được build ban đầu.
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000001', p.id, 'ORGANIZATION'
FROM permissions p WHERE p.resource IN ('COURSE', 'CLASS');
```

- [ ] **Step 7: Compile and commit**

Run: `cd backend && mvn clean compile test-compile`
Expected: BUILD SUCCESS.

```bash
cd /Users/hoangdieu/PycharmProjects/EducationErp
git checkout develop && git pull --ff-only origin develop
git checkout -b feature/courses-classes
git add backend/src/main/java/com/eduerp/modules/access/AccessConstants.java \
        backend/src/test/java/com/eduerp/modules/access/AccessConstantsTest.java \
        backend/src/main/resources/db/migration/V9__create_courses_and_classes.sql \
        backend/src/main/resources/db/migration/V10__seed_courses_rbac.sql
git commit -m "feat(backend): add COURSE/CLASS RBAC rules and schema migration"
```

---

## Task 2: Domain entities + repositories

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/courses/CoursesConstants.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/internal/model/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/internal/model/Course.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/internal/model/Class.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/internal/model/ClassSchedule.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/internal/repository/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/internal/repository/CourseRepository.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/internal/repository/ClassRepository.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/internal/package-info.java`
- Test: `backend/src/test/java/com/eduerp/modules/courses/internal/repository/CourseRepositoryIT.java`

**Interfaces:**
- Consumes: nothing outside this task.
- Produces: `Course(code, name, description, standardSessionCount)` constructor + getters +
  `setName/setDescription/setStandardSessionCount/setActive`; `Class(course, code, branchId, teacherId,
  maxSeats)` constructor + getters + `setTeacherId/setMaxSeats/setActive` +
  `addSchedule(CoursesConstants.DayOfWeek, LocalTime, LocalTime)`/`clearSchedule()`;
  `CoursesConstants.DayOfWeek` enum (`MON`..`SUN`) — mirrors `IdentityConstants.AccountStatus`:
  lives in the module's base package (not `internal`) specifically so `dto/` can reference it too
  without reaching into `internal` (rule #14 tier discipline — `dto` and `internal.model` are the
  same tier, neither may depend on the other); `CourseRepository.findByCode/findAll(Pageable)`;
  `ClassRepository.findByCode/findAll(Pageable)`. Later tasks (usecase layer) call these directly.

- [ ] **Step 1: Write the failing repository test**

Create `backend/src/test/java/com/eduerp/modules/courses/internal/repository/CourseRepositoryIT.java`:
```java
package com.eduerp.modules.courses.internal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.internal.model.Course;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
class CourseRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    CourseRepository courses;

    @Test
    void savesAndFindsByCode() {
        courses.save(new Course("TA-GT", "Tiếng Anh giao tiếp", "Mô tả", 24));

        var found = courses.findByCode("TA-GT");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Tiếng Anh giao tiếp");
        assertThat(found.get().isActive()).isTrue();
    }
}
```

- [ ] **Step 2: Run it, confirm it fails to compile**

Run: `cd backend && mvn test -Dtest=CourseRepositoryIT`
Expected: compile error — `com.eduerp.modules.courses.*` doesn't exist yet.

- [ ] **Step 3: Create the module package-info files**

`backend/src/main/java/com/eduerp/modules/courses/internal/package-info.java`:
```java
/**
 * Chi tiết cài đặt của module courses. Spring Modulith che toàn bộ cây này khỏi mọi module khác;
 * module khác chỉ được đi qua {@link com.eduerp.modules.courses.CoursesManagement}.
 */
package com.eduerp.modules.courses.internal;
```

`backend/src/main/java/com/eduerp/modules/courses/internal/model/package-info.java`:
```java
package com.eduerp.modules.courses.internal.model;
```

`backend/src/main/java/com/eduerp/modules/courses/internal/repository/package-info.java`:
```java
package com.eduerp.modules.courses.internal.repository;
```

- [ ] **Step 4: Write the Course entity**

`backend/src/main/java/com/eduerp/modules/courses/internal/model/Course.java`:
```java
package com.eduerp.modules.courses.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    @Setter
    private String name;

    @Setter
    private String description;

    @Setter
    private Integer standardSessionCount;

    @Column(nullable = false)
    @Setter
    private boolean active = true;

    public Course(String code, String name, String description, Integer standardSessionCount) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.standardSessionCount = standardSessionCount;
        this.active = true;
    }
}
```

- [ ] **Step 5: Write the DayOfWeek enum (base package) and Class/ClassSchedule entities**

`backend/src/main/java/com/eduerp/modules/courses/CoursesConstants.java`:
```java
package com.eduerp.modules.courses;

/**
 * Hằng số dùng chung của module courses, nằm ở base package (không phải {@code internal}) vì
 * {@code dto} cũng cần tham chiếu - giống hệt {@code IdentityConstants.AccountStatus} vừa dùng được
 * trong entity vừa dùng được trong DTO mà không phá tier discipline (rule #14: {@code dto} và
 * {@code internal.model} cùng một tier, không được phụ thuộc lẫn nhau).
 */
public final class CoursesConstants {

    private CoursesConstants() {
    }

    public enum DayOfWeek {
        MON, TUE, WED, THU, FRI, SAT, SUN
    }
}
```

`backend/src/main/java/com/eduerp/modules/courses/internal/model/Class.java`:
```java
package com.eduerp.modules.courses.internal.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import com.eduerp.modules.courses.CoursesConstants;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * {@code course} là quan hệ JPA thật - {@code Course} cùng module (rule #3 chỉ cấm xuyên module).
 * {@code branchId}/{@code teacherId} là UUID trần vì {@code organization}/{@code identity} là module
 * khác.
 */
@Entity
@Table(name = "classes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Class {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", updatable = false)
    private Course course;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "teacher_id", nullable = false)
    @Setter
    private UUID teacherId;

    @Column(nullable = false)
    @Setter
    private int maxSeats;

    @Column(nullable = false)
    @Setter
    private boolean active = true;

    @OneToMany(mappedBy = "parentClass", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("dayOfWeek ASC, startTime ASC")
    private final List<ClassSchedule> schedule = new ArrayList<>();

    public Class(Course course, String code, UUID branchId, UUID teacherId, int maxSeats) {
        this.course = course;
        this.code = code;
        this.branchId = branchId;
        this.teacherId = teacherId;
        this.maxSeats = maxSeats;
        this.active = true;
    }

    public List<ClassSchedule> getSchedule() {
        return List.copyOf(schedule);
    }

    public void addSchedule(CoursesConstants.DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        schedule.add(new ClassSchedule(this, dayOfWeek, startTime, endTime));
    }

    /** Gọi trước khi thêm lại lịch mới khi sửa lớp - PATCH thay toàn bộ lịch, không vá từng dòng. */
    public void clearSchedule() {
        schedule.clear();
    }
}
```

`backend/src/main/java/com/eduerp/modules/courses/internal/model/ClassSchedule.java`:
```java
package com.eduerp.modules.courses.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.eduerp.modules.courses.CoursesConstants;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "class_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassSchedule {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "class_id")
    private Class parentClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private CoursesConstants.DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    ClassSchedule(Class parentClass, CoursesConstants.DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.parentClass = parentClass;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
```

- [ ] **Step 6: Write the repositories**

`backend/src/main/java/com/eduerp/modules/courses/internal/repository/CourseRepository.java`:
```java
package com.eduerp.modules.courses.internal.repository;

import com.eduerp.modules.courses.internal.model.Course;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    Optional<Course> findByCode(String code);

    Page<Course> findAll(Pageable pageable);
}
```

`backend/src/main/java/com/eduerp/modules/courses/internal/repository/ClassRepository.java`:
```java
package com.eduerp.modules.courses.internal.repository;

import com.eduerp.modules.courses.internal.model.Class;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassRepository extends JpaRepository<Class, UUID> {
    Optional<Class> findByCode(String code);

    Page<Class> findAll(Pageable pageable);
}
```

- [ ] **Step 7: Run the test, confirm it passes**

Run: `cd backend && mvn test -Dtest=CourseRepositoryIT`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/courses/CoursesConstants.java \
        backend/src/main/java/com/eduerp/modules/courses/internal \
        backend/src/test/java/com/eduerp/modules/courses
git commit -m "feat(backend): add Course/Class/ClassSchedule entities and repositories"
```

---

## Task 3: Exceptions, events, DTOs

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/courses/CoursesException.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/CourseNotFoundException.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/ClassNotFoundException.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/CourseCodeAlreadyExistsException.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/ClassCodeAlreadyExistsException.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/CoursesEvents.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/dto/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/dto/CourseResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/dto/CreateCourseRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/dto/UpdateCourseRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/dto/WeeklyScheduleSlot.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/dto/ClassResponse.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/dto/CreateClassRequest.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/dto/UpdateClassRequest.java`

**Interfaces:**
- Consumes: `com.eduerp.core.exception.AppException` (existing base).
- Produces: all the record/exception types later tasks (`usecase/`, `web/`) construct and throw by
  these exact names.

This task is pure scaffolding (records + sealed exception hierarchy) with no independently-testable
runtime behavior — folded into one compile-and-commit step per the Task Right-Sizing guidance (a
reviewer cannot sensibly approve `CourseResponse` while rejecting `ClassResponse`; they're one
deliverable, the DTO layer).

- [ ] **Step 1: Write the exception hierarchy**

`backend/src/main/java/com/eduerp/modules/courses/CoursesException.java`:
```java
package com.eduerp.modules.courses;

import com.eduerp.core.exception.AppException;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module courses phát ra. */
public sealed class CoursesException extends AppException
        permits CourseNotFoundException, ClassNotFoundException, CourseCodeAlreadyExistsException,
        ClassCodeAlreadyExistsException {

    protected CoursesException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }
}
```

`backend/src/main/java/com/eduerp/modules/courses/CourseNotFoundException.java`:
```java
package com.eduerp.modules.courses;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class CourseNotFoundException extends CoursesException {

    public CourseNotFoundException(UUID courseId) {
        super("COURSES_COURSE_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy khóa học " + courseId);
    }
}
```

`backend/src/main/java/com/eduerp/modules/courses/ClassNotFoundException.java`:
```java
package com.eduerp.modules.courses;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ClassNotFoundException extends CoursesException {

    public ClassNotFoundException(UUID classId) {
        super("COURSES_CLASS_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy lớp học " + classId);
    }
}
```

`backend/src/main/java/com/eduerp/modules/courses/CourseCodeAlreadyExistsException.java`:
```java
package com.eduerp.modules.courses;

import org.springframework.http.HttpStatus;

public final class CourseCodeAlreadyExistsException extends CoursesException {

    public CourseCodeAlreadyExistsException(String code) {
        super("COURSES_COURSE_CODE_ALREADY_EXISTS", HttpStatus.CONFLICT, "Mã khóa học " + code + " đã tồn tại");
    }
}
```

`backend/src/main/java/com/eduerp/modules/courses/ClassCodeAlreadyExistsException.java`:
```java
package com.eduerp.modules.courses;

import org.springframework.http.HttpStatus;

public final class ClassCodeAlreadyExistsException extends CoursesException {

    public ClassCodeAlreadyExistsException(String code) {
        super("COURSES_CLASS_CODE_ALREADY_EXISTS", HttpStatus.CONFLICT, "Mã lớp " + code + " đã tồn tại");
    }
}
```

- [ ] **Step 2: Write the events**

`backend/src/main/java/com/eduerp/modules/courses/CoursesEvents.java`:
```java
package com.eduerp.modules.courses;

import java.util.UUID;

public final class CoursesEvents {

    private CoursesEvents() {
    }

    public record CourseCreated(UUID courseId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record CourseUpdated(UUID courseId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record ClassCreated(UUID classId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record ClassUpdated(UUID classId, UUID actorAccountId, UUID actorBranchId) {
    }
}
```

- [ ] **Step 3: Write the dto package**

`backend/src/main/java/com/eduerp/modules/courses/dto/package-info.java`:
```java
/**
 * Hợp đồng vào/ra của module courses. Facade/controller nhận/trả các record này nên package phải
 * được expose bằng {@code @NamedInterface}, nếu không {@code ApplicationModules.verify()} sẽ fail.
 */
@org.springframework.modulith.NamedInterface("dto")
package com.eduerp.modules.courses.dto;
```

`backend/src/main/java/com/eduerp/modules/courses/dto/CourseResponse.java`:
```java
package com.eduerp.modules.courses.dto;

import java.util.UUID;

public record CourseResponse(UUID id, String code, String name, String description,
        Integer standardSessionCount, boolean active) {
}
```

`backend/src/main/java/com/eduerp/modules/courses/dto/CreateCourseRequest.java`:
```java
package com.eduerp.modules.courses.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCourseRequest(@NotBlank String code, @NotBlank String name, String description,
        Integer standardSessionCount) {
}
```

`backend/src/main/java/com/eduerp/modules/courses/dto/UpdateCourseRequest.java`:
```java
package com.eduerp.modules.courses.dto;

import jakarta.validation.constraints.NotBlank;

/** Không có {@code code}: mã khóa học bất biến sau khi tạo. */
public record UpdateCourseRequest(@NotBlank String name, String description,
        Integer standardSessionCount, boolean active) {
}
```

`backend/src/main/java/com/eduerp/modules/courses/dto/WeeklyScheduleSlot.java`:
```java
package com.eduerp.modules.courses.dto;

import com.eduerp.modules.courses.CoursesConstants;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record WeeklyScheduleSlot(@NotNull CoursesConstants.DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime, @NotNull LocalTime endTime) {
}
```

`backend/src/main/java/com/eduerp/modules/courses/dto/ClassResponse.java`:
```java
package com.eduerp.modules.courses.dto;

import java.util.List;
import java.util.UUID;

public record ClassResponse(UUID id, String code, UUID courseId, String courseName, UUID branchId,
        String branchName, UUID teacherId, String teacherName, int maxSeats, boolean active,
        List<WeeklyScheduleSlot> schedule) {
}
```

`backend/src/main/java/com/eduerp/modules/courses/dto/CreateClassRequest.java`:
```java
package com.eduerp.modules.courses.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record CreateClassRequest(@NotNull UUID courseId, @NotBlank String code, @NotNull UUID branchId,
        @NotNull UUID teacherId, @Positive int maxSeats, @Valid List<WeeklyScheduleSlot> schedule) {
}
```

`backend/src/main/java/com/eduerp/modules/courses/dto/UpdateClassRequest.java`:
```java
package com.eduerp.modules.courses.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

/** Không có {@code courseId}/{@code branchId}/{@code code}: bất biến sau khi tạo (xem spec §2.2). */
public record UpdateClassRequest(@NotNull UUID teacherId, @Positive int maxSeats, boolean active,
        @Valid List<WeeklyScheduleSlot> schedule) {
}
```

- [ ] **Step 4: Compile and commit**

Run: `cd backend && mvn clean compile`
Expected: BUILD SUCCESS.

```bash
git add backend/src/main/java/com/eduerp/modules/courses/CoursesException.java \
        backend/src/main/java/com/eduerp/modules/courses/CourseNotFoundException.java \
        backend/src/main/java/com/eduerp/modules/courses/ClassNotFoundException.java \
        backend/src/main/java/com/eduerp/modules/courses/CourseCodeAlreadyExistsException.java \
        backend/src/main/java/com/eduerp/modules/courses/ClassCodeAlreadyExistsException.java \
        backend/src/main/java/com/eduerp/modules/courses/CoursesEvents.java \
        backend/src/main/java/com/eduerp/modules/courses/dto
git commit -m "feat(backend): add courses module exceptions, events, and DTOs"
```

---

## Task 4: Use cases + facade

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/courses/usecase/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/usecase/CreateCourse.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/usecase/UpdateCourse.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/usecase/ListCourses.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/usecase/CreateClass.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/usecase/UpdateClass.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/usecase/ListClasses.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/CoursesManagement.java`

**Interfaces:**
- Consumes: `OrganizationManagement.exists(UUID)` / `.namesOf(Collection<UUID>)` (existing);
  `IdentityManagement.summariesOf(Collection<UUID>)` (existing, returns
  `Map<UUID, AccountBasicInfo>` where `AccountBasicInfo` has `.fullName()`); repositories and
  entities from Task 2; DTOs/exceptions/events from Task 3.
- Produces: `CreateCourse.execute(UUID actorAccountId, UUID actorBranchId, CreateCourseRequest):
  UUID`; `UpdateCourse.execute(UUID courseId, UUID actorAccountId, UUID actorBranchId,
  UpdateCourseRequest): void`; `ListCourses.execute(
  Pageable): PageResponse<CourseResponse>`; `CreateClass.execute(UUID actorAccountId, UUID
  actorBranchId, CreateClassRequest): UUID`; `UpdateClass.execute(UUID classId, UUID actorAccountId,
  UUID actorBranchId, UpdateClassRequest): void`; `ListClasses.execute(Pageable):
  PageResponse<ClassResponse>` — the `web/` controllers in Task 5 call these by these exact
  signatures.

- [ ] **Step 1: package-info**

`backend/src/main/java/com/eduerp/modules/courses/usecase/package-info.java`:
```java
/**
 * Một class = một use case = một method {@code public execute(...)} (rule #7).
 * Method phải {@code public} để proxy AOP của Spring bọc được {@code @Transactional}.
 */
package com.eduerp.modules.courses.usecase;
```

- [ ] **Step 2: CreateCourse / UpdateCourse / ListCourses**

`backend/src/main/java/com/eduerp/modules/courses/usecase/CreateCourse.java`:
```java
package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.CourseCodeAlreadyExistsException;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.dto.CreateCourseRequest;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCourse {

    private final CourseRepository courses;
    private final ApplicationEventPublisher events;

    CreateCourse(CourseRepository courses, ApplicationEventPublisher events) {
        this.courses = courses;
        this.events = events;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateCourseRequest request) {
        if (courses.findByCode(request.code()).isPresent()) {
            throw new CourseCodeAlreadyExistsException(request.code());
        }
        var saved = courses.save(new Course(request.code(), request.name(), request.description(),
                request.standardSessionCount()));
        events.publishEvent(new CoursesEvents.CourseCreated(saved.getId(), actorAccountId, actorBranchId));
        return saved.getId();
    }
}
```

`backend/src/main/java/com/eduerp/modules/courses/usecase/UpdateCourse.java`:
```java
package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.CourseNotFoundException;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.dto.UpdateCourseRequest;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateCourse {

    private final CourseRepository courses;
    private final ApplicationEventPublisher events;

    UpdateCourse(CourseRepository courses, ApplicationEventPublisher events) {
        this.courses = courses;
        this.events = events;
    }

    @Transactional
    public void execute(UUID courseId, UUID actorAccountId, UUID actorBranchId, UpdateCourseRequest request) {
        var course = courses.findById(courseId).orElseThrow(() -> new CourseNotFoundException(courseId));
        course.setName(request.name());
        course.setDescription(request.description());
        course.setStandardSessionCount(request.standardSessionCount());
        course.setActive(request.active());
        events.publishEvent(new CoursesEvents.CourseUpdated(courseId, actorAccountId, actorBranchId));
    }
}
```

`backend/src/main/java/com/eduerp/modules/courses/usecase/ListCourses.java`:
```java
package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.dto.CourseResponse;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.shared.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListCourses {

    private final CourseRepository courses;

    ListCourses(CourseRepository courses) {
        this.courses = courses;
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> execute(Pageable pageable) {
        return PageResponse.of(courses.findAll(pageable).map(ListCourses::toResponse));
    }

    private static CourseResponse toResponse(Course course) {
        return new CourseResponse(course.getId(), course.getCode(), course.getName(),
                course.getDescription(), course.getStandardSessionCount(), course.isActive());
    }
}
```

- [ ] **Step 3: Compile-check courses-only so far**

Run: `cd backend && mvn clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: CreateClass / UpdateClass / ListClasses**

`backend/src/main/java/com/eduerp/modules/courses/usecase/CreateClass.java`:
```java
package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.ClassCodeAlreadyExistsException;
import com.eduerp.modules.courses.CourseNotFoundException;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.dto.CreateClassRequest;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.organization.OrganizationManagement;
import com.eduerp.core.exception.AppValidationException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateClass {

    private final ClassRepository classes;
    private final CourseRepository courses;
    private final OrganizationManagement organization;
    private final IdentityManagement identity;
    private final ApplicationEventPublisher events;

    CreateClass(ClassRepository classes, CourseRepository courses, OrganizationManagement organization,
            IdentityManagement identity, ApplicationEventPublisher events) {
        this.classes = classes;
        this.courses = courses;
        this.organization = organization;
        this.identity = identity;
        this.events = events;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateClassRequest request) {
        if (classes.findByCode(request.code()).isPresent()) {
            throw new ClassCodeAlreadyExistsException(request.code());
        }
        var course = courses.findById(request.courseId())
                .orElseThrow(() -> new CourseNotFoundException(request.courseId()));
        if (!organization.exists(request.branchId())) {
            throw new AppValidationException("branchId", "Chi nhánh không tồn tại");
        }
        if (!identity.summariesOf(List.of(request.teacherId())).containsKey(request.teacherId())) {
            throw new AppValidationException("teacherId", "Giáo viên không tồn tại");
        }

        var newClass = new Class(course, request.code(), request.branchId(), request.teacherId(),
                request.maxSeats());
        request.schedule()
                .forEach(slot -> newClass.addSchedule(slot.dayOfWeek(), slot.startTime(), slot.endTime()));
        var saved = classes.save(newClass);
        events.publishEvent(new CoursesEvents.ClassCreated(saved.getId(), actorAccountId, actorBranchId));
        return saved.getId();
    }
}
```

`backend/src/main/java/com/eduerp/modules/courses/usecase/UpdateClass.java`:
```java
package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.ClassNotFoundException;
import com.eduerp.modules.courses.CoursesEvents;
import com.eduerp.modules.courses.dto.UpdateClassRequest;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.core.exception.AppValidationException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateClass {

    private final ClassRepository classes;
    private final IdentityManagement identity;
    private final ApplicationEventPublisher events;

    UpdateClass(ClassRepository classes, IdentityManagement identity, ApplicationEventPublisher events) {
        this.classes = classes;
        this.identity = identity;
        this.events = events;
    }

    @Transactional
    public void execute(UUID classId, UUID actorAccountId, UUID actorBranchId, UpdateClassRequest request) {
        var existing = classes.findById(classId).orElseThrow(() -> new ClassNotFoundException(classId));
        if (!identity.summariesOf(List.of(request.teacherId())).containsKey(request.teacherId())) {
            throw new AppValidationException("teacherId", "Giáo viên không tồn tại");
        }

        existing.setTeacherId(request.teacherId());
        existing.setMaxSeats(request.maxSeats());
        existing.setActive(request.active());
        existing.clearSchedule();
        request.schedule()
                .forEach(slot -> existing.addSchedule(slot.dayOfWeek(), slot.startTime(), slot.endTime()));

        events.publishEvent(new CoursesEvents.ClassUpdated(classId, actorAccountId, actorBranchId));
    }
}
```

`backend/src/main/java/com/eduerp/modules/courses/usecase/ListClasses.java`:
```java
package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.dto.ClassResponse;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.organization.OrganizationManagement;
import com.eduerp.shared.PageResponse;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListClasses {

    private final ClassRepository classes;
    private final OrganizationManagement organization;
    private final IdentityManagement identity;

    ListClasses(ClassRepository classes, OrganizationManagement organization, IdentityManagement identity) {
        this.classes = classes;
        this.organization = organization;
        this.identity = identity;
    }

    /**
     * Tên khóa học lấy thẳng qua {@code Class.course} (cùng module - rule #3 không áp dụng). Tên chi
     * nhánh/giáo viên phải nạp theo lô qua facade organization/identity, tránh N+1 - đúng pattern
     * {@code ListAccounts}.
     */
    @Transactional(readOnly = true)
    public PageResponse<ClassResponse> execute(Pageable pageable) {
        Page<Class> page = classes.findAll(pageable);
        var branchIds = page.getContent().stream().map(Class::getBranchId).toList();
        var teacherIds = page.getContent().stream().map(Class::getTeacherId).toList();

        var branchNames = organization.namesOf(branchIds);
        var teachers = identity.summariesOf(teacherIds);

        return PageResponse.of(page.map(cls -> toResponse(cls, branchNames, teachers)));
    }

    private static ClassResponse toResponse(Class cls, java.util.Map<java.util.UUID, String> branchNames,
            java.util.Map<java.util.UUID, IdentityManagement.AccountBasicInfo> teachers) {
        var teacher = teachers.get(cls.getTeacherId());
        var slots = cls.getSchedule().stream()
                .map(s -> new WeeklyScheduleSlot(s.getDayOfWeek(), s.getStartTime(), s.getEndTime()))
                .toList();
        return new ClassResponse(cls.getId(), cls.getCode(), cls.getCourse().getId(),
                cls.getCourse().getName(), cls.getBranchId(),
                branchNames.get(cls.getBranchId()), cls.getTeacherId(),
                teacher == null ? null : teacher.fullName(), cls.getMaxSeats(), cls.isActive(), slots);
    }
}
```

- [ ] **Step 5: Add the shared validation exception used above**

Check first whether a generic field-validation `AppException` subtype already exists:
Run: `grep -rln "extends AppException" backend/src/main/java/com/eduerp/core/`

If none is generic/reusable (one that takes a field name + message, not tied to one module's
resource), create `backend/src/main/java/com/eduerp/core/exception/AppValidationException.java` —
this belongs in `core.exception` alongside `AppException`/`GlobalExceptionHandler`, not in `shared`:
it is mechanism (a generic 400-with-field-name shape), not a business concept, and `core/` is exactly
where this repo's "shared mechanism, no business concept" code already lives:
```java
package com.eduerp.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Lỗi 400 chung cho một trường cụ thể không hợp lệ theo nghiệp vụ (không phải lỗi format mà
 * {@code @Valid} đã bắt) - vd một UUID đúng định dạng nhưng không trỏ tới bản ghi nào còn tồn tại ở
 * module khác. Dùng chung thay vì mỗi module tự định nghĩa một lớp "ValidationException" trùng nhau.
 */
public final class AppValidationException extends AppException {

    public AppValidationException(String field, String message) {
        super("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, field + ": " + message);
    }
}
```

If an equivalent already exists, read it and adjust `CreateClass`/`UpdateClass` above (both the
import and the constructor call) to match its actual package and signature instead of adding a
duplicate.

- [ ] **Step 6: Add the facade**

`backend/src/main/java/com/eduerp/modules/courses/CoursesManagement.java`:
```java
package com.eduerp.modules.courses;

import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module courses — type DUY NHẤT mà module khác được phép gọi (rule #1). Chưa có module
 * nào gọi tới ở phase này (Phân hệ 3/4 sẽ cần sau) — hai method exists() là điểm bắt đầu tối thiểu,
 * không suy đoán thêm method nào khác chưa ai cần.
 */
@Service
public class CoursesManagement {

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
}
```

- [ ] **Step 7: Compile**

Run: `cd backend && mvn clean compile`
Expected: BUILD SUCCESS. If `AppValidationException`'s real constructor differs from Step 5's guess,
fix the two call sites in `CreateClass`/`UpdateClass` now.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/courses/usecase \
        backend/src/main/java/com/eduerp/modules/courses/CoursesManagement.java \
        backend/src/main/java/com/eduerp/core/exception/AppValidationException.java
git commit -m "feat(backend): add courses module use cases and facade"
```
(Drop the `AppValidationException.java` path above from `git add` if Step 5 found it already existed.)

---

## Task 5: Controllers + module package-info + audit wiring

**Files:**
- Create: `backend/src/main/java/com/eduerp/modules/courses/web/package-info.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/web/CourseAdminController.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/web/ClassAdminController.java`
- Create: `backend/src/main/java/com/eduerp/modules/courses/package-info.java`
- Modify: `backend/src/main/java/com/eduerp/modules/audit/AuditConstants.java`
- Modify: `backend/src/main/java/com/eduerp/modules/audit/internal/listener/AuditEventListeners.java`

**Interfaces:**
- Consumes: use cases from Task 4; `AccessConstants.AccessRules.*_COURSE`/`*_CLASS` from Task 1;
  `AccountPrincipal` (existing, `com.eduerp.shared.AccountPrincipal`).
- Produces: `GET/POST /api/courses/courses`, `PATCH /api/courses/courses/{id}`,
  `GET/POST /api/courses/classes`, `PATCH /api/courses/classes/{id}` — Task 7's IT tests call these.

- [ ] **Step 1: web package-info + CourseAdminController**

`backend/src/main/java/com/eduerp/modules/courses/web/package-info.java`:
```java
/** Adapter HTTP của module courses: controller mỏng (rule #8 — không chứa nghiệp vụ). */
package com.eduerp.modules.courses.web;
```

`backend/src/main/java/com/eduerp/modules/courses/web/CourseAdminController.java`:
```java
package com.eduerp.modules.courses.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.dto.CourseResponse;
import com.eduerp.modules.courses.dto.CreateCourseRequest;
import com.eduerp.modules.courses.dto.UpdateCourseRequest;
import com.eduerp.modules.courses.usecase.CreateCourse;
import com.eduerp.modules.courses.usecase.ListCourses;
import com.eduerp.modules.courses.usecase.UpdateCourse;
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

/** Quản trị danh mục khóa học — không có endpoint xoá, chỉ vô hiệu hoá qua {@code active}. */
@RestController
@RequestMapping("/api/courses/courses")
class CourseAdminController {

    private final ListCourses listCourses;
    private final CreateCourse createCourse;
    private final UpdateCourse updateCourse;

    CourseAdminController(ListCourses listCourses, CreateCourse createCourse, UpdateCourse updateCourse) {
        this.listCourses = listCourses;
        this.createCourse = createCourse;
        this.updateCourse = updateCourse;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_COURSE)
    PageResponse<CourseResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return listCourses.execute(pageable);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_COURSE)
    UUID create(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody CreateCourseRequest request) {
        return createCourse.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @PatchMapping("/{courseId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_COURSE)
    void update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest request) {
        updateCourse.execute(courseId, principal.accountId(), principal.homeBranchId(), request);
    }
}
```

- [ ] **Step 2: ClassAdminController**

`backend/src/main/java/com/eduerp/modules/courses/web/ClassAdminController.java`:
```java
package com.eduerp.modules.courses.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.dto.ClassResponse;
import com.eduerp.modules.courses.dto.CreateClassRequest;
import com.eduerp.modules.courses.dto.UpdateClassRequest;
import com.eduerp.modules.courses.usecase.CreateClass;
import com.eduerp.modules.courses.usecase.ListClasses;
import com.eduerp.modules.courses.usecase.UpdateClass;
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

@RestController
@RequestMapping("/api/courses/classes")
class ClassAdminController {

    private final ListClasses listClasses;
    private final CreateClass createClass;
    private final UpdateClass updateClass;

    ClassAdminController(ListClasses listClasses, CreateClass createClass, UpdateClass updateClass) {
        this.listClasses = listClasses;
        this.createClass = createClass;
        this.updateClass = updateClass;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_CLASS)
    PageResponse<ClassResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return listClasses.execute(pageable);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_CLASS)
    UUID create(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody CreateClassRequest request) {
        return createClass.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @PatchMapping("/{classId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_CLASS)
    void update(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID classId,
            @Valid @RequestBody UpdateClassRequest request) {
        updateClass.execute(classId, principal.accountId(), principal.homeBranchId(), request);
    }
}
```

- [ ] **Step 3: Module package-info**

`backend/src/main/java/com/eduerp/modules/courses/package-info.java`:
```java
/**
 * Module Courses — sở hữu danh mục Khóa học và các Lớp học mở từ đó. Phụ thuộc một chiều
 * {@code shared}, {@code access} ({@code AccessConstants} cho {@code @PreAuthorize}) và
 * {@code identity} (xác nhận {@code teacherId} là một account có thật qua
 * {@code IdentityManagement.summariesOf}). Không module nào đọc ngược từ {@code courses} ở phase
 * này nên không có nguy cơ vòng phụ thuộc, không cần port/adapter như
 * {@code access.BranchCatalog}.
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — API của module: {@link com.eduerp.modules.courses.CoursesManagement}.</li>
 *   <li>{@code dto} — hợp đồng vào/ra qua HTTP, {@code @NamedInterface("dto")}.</li>
 *   <li>{@code usecase} — một class = một use case (rule #7).</li>
 *   <li>{@code web} — controller mỏng (rule #8).</li>
 *   <li>{@code internal} — chi tiết cài đặt, Spring Modulith che khỏi mọi module khác.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Courses & Classes")
package com.eduerp.modules.courses;
```

- [ ] **Step 4: Audit wiring**

In `backend/src/main/java/com/eduerp/modules/audit/AuditConstants.java`, add to `Actions`:
```java
        public static final String COURSE_CREATE = "COURSE_CREATE";
        public static final String COURSE_UPDATE = "COURSE_UPDATE";
        public static final String CLASS_CREATE = "CLASS_CREATE";
        public static final String CLASS_UPDATE = "CLASS_UPDATE";
```
and to `EntityTypes`:
```java
        public static final String COURSE = "COURSE";
        public static final String CLASS = "CLASS";
```

In `backend/src/main/java/com/eduerp/modules/audit/internal/listener/AuditEventListeners.java`, add
the import `com.eduerp.modules.courses.CoursesEvents;` and three listener methods (same shape as the
existing `OrganizationEvents.BranchCreated`/`BranchUpdated` handlers):
```java
    @ApplicationModuleListener
    void on(CoursesEvents.CourseCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.COURSE_CREATE,
                AuditConstants.EntityTypes.COURSE, event.courseId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(CoursesEvents.CourseUpdated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.COURSE_UPDATE,
                AuditConstants.EntityTypes.COURSE, event.courseId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(CoursesEvents.ClassCreated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.CLASS_CREATE,
                AuditConstants.EntityTypes.CLASS, event.classId().toString(), event.actorBranchId()));
    }

    @ApplicationModuleListener
    void on(CoursesEvents.ClassUpdated event) {
        auditLogs.save(new AuditLog(event.actorAccountId(), AuditConstants.Actions.CLASS_UPDATE,
                AuditConstants.EntityTypes.CLASS, event.classId().toString(), event.actorBranchId()));
    }
```

- [ ] **Step 5: Compile + run ModularityTests**

Run: `cd backend && mvn clean compile && mvn test -Dtest=ModularityTests`
Expected: BUILD SUCCESS, 2 tests green, no cyclic-dependency or boundary violations reported. If a
cycle IS reported here, stop — it means some existing module already depends on `courses` (shouldn't
happen per this plan, but verify before continuing rather than assume).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/eduerp/modules/courses/web \
        backend/src/main/java/com/eduerp/modules/courses/package-info.java \
        backend/src/main/java/com/eduerp/modules/audit
git commit -m "feat(backend): add courses admin controllers and audit wiring"
```

---

## Task 6: Backend integration tests

**Files:**
- Create: `backend/src/test/java/com/eduerp/modules/courses/web/CourseAdminControllerIT.java`
- Create: `backend/src/test/java/com/eduerp/modules/courses/web/ClassAdminControllerIT.java`

**Interfaces:**
- Consumes: everything from Tasks 1–5; mirrors `backend/src/test/java/com/eduerp/modules/organization/web/BranchAdminControllerIT.java` exactly (same `signIn` helper shape, same container setup).

- [ ] **Step 1: CourseAdminControllerIT**

`backend/src/test/java/com/eduerp/modules/courses/web/CourseAdminControllerIT.java`:
```java
package com.eduerp.modules.courses.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.dto.CreateCourseRequest;
import com.eduerp.modules.courses.dto.UpdateCourseRequest;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
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
class CourseAdminControllerIT {

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
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie signIn(String email, String roleCode) throws Exception {
        var account = accounts.save(new Account(email, passwordEncoder.encode(PASSWORD), email, null));
        access.assignRole(account.getId(), roleCode);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    @Test
    void createsThenListsACourse() throws Exception {
        var admin = signIn("course-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var createResult = mockMvc.perform(post("/api/courses/courses")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCourseRequest("TA-GT", "Tiếng Anh giao tiếp", "Mô tả", 24))))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);
        var courseId = objectMapper.readValue(createResult.getResponse().getContentAsString(), UUID.class);

        var listResult = mockMvc.perform(get("/api/courses/courses").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
        var items = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items");
        assertThat(items).anyMatch(node -> courseId.toString().equals(node.get("id").asText()));
    }

    @Test
    void updatesACourse() throws Exception {
        var admin = signIn("course-editor@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var course = courses.save(new com.eduerp.modules.courses.internal.model.Course("TOAN-9",
                "Toán lớp 9", null, null));

        var updateResult = mockMvc.perform(patch("/api/courses/courses/" + course.getId())
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateCourseRequest("Toán lớp 9 (mới)", "Cập nhật", 30, false))))
                .andReturn();

        assertThat(updateResult.getResponse().getStatus()).isEqualTo(200);
        var updated = courses.findById(course.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Toán lớp 9 (mới)");
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void rejectsBlankNameOnCreate() throws Exception {
        var admin = signIn("course-invalid@eduerp.local", AccessConstants.RoleCodes.ADMIN);

        var result = mockMvc.perform(post("/api/courses/courses")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCourseRequest("X01", "", null, null))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void refusesAnAccountWithOnlyPersonalScopePermissions() throws Exception {
        var teacher = signIn("course-outsider@eduerp.local", AccessConstants.RoleCodes.TEACHER);

        var result = mockMvc.perform(post("/api/courses/courses")
                        .cookie(teacher).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCourseRequest("XX01", "Không được phép", null, null))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(courses.findByCode("XX01")).isEmpty();
    }
}
```

- [ ] **Step 2: Run it**

Run: `cd backend && mvn verify -Dit.test=CourseAdminControllerIT`
Expected: 4 tests green. Fix any compile/assertion mismatch against the actual Task 1–5 code before
moving on (this is the first real exercise of the whole chain end-to-end).

- [ ] **Step 3: ClassAdminControllerIT**

`backend/src/test/java/com/eduerp/modules/courses/web/ClassAdminControllerIT.java`:
```java
package com.eduerp.modules.courses.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.dto.CreateClassRequest;
import com.eduerp.modules.courses.dto.UpdateClassRequest;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.courses.internal.model.Course;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.CourseRepository;
import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.dto.LoginRequest;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.repository.AccountRepository;
import com.eduerp.modules.organization.internal.model.Branch;
import com.eduerp.modules.organization.internal.repository.BranchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import jakarta.servlet.http.Cookie;
import java.time.LocalTime;
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
class ClassAdminControllerIT {

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
    AccessManagement access;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie signIn(String email, String roleCode) throws Exception {
        var account = accounts.save(new Account(email, passwordEncoder.encode(PASSWORD), email, null));
        access.assignRole(account.getId(), roleCode);
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getCookie(IdentityConstants.Cookies.ACCESS_TOKEN);
    }

    @Test
    void createsAClassWithScheduleThenListsIt() throws Exception {
        var admin = signIn("class-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var course = courses.save(new Course("TA-GT", "Tiếng Anh giao tiếp", null, null));
        var branch = branches.save(new Branch("CG01", "Chi nhánh Cầu Giấy", null));
        var teacher = accounts.save(new Account("teacher-1@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Cô Lan", null));

        var request = new CreateClassRequest(course.getId(), "TA-GT-K15", branch.getId(), teacher.getId(), 20,
                List.of(new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0))));
        var createResult = mockMvc.perform(post("/api/courses/classes")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(200);
        var classId = objectMapper.readValue(createResult.getResponse().getContentAsString(), UUID.class);

        var listResult = mockMvc.perform(get("/api/courses/classes").cookie(admin)).andReturn();
        assertThat(listResult.getResponse().getStatus()).isEqualTo(200);
        var items = objectMapper.readTree(listResult.getResponse().getContentAsString()).get("items");
        var createdNode = items.findValuesAsText("id").contains(classId.toString());
        assertThat(createdNode).isTrue();
    }

    @Test
    void rejectsACreateWithANonExistentTeacher() throws Exception {
        var admin = signIn("class-bad-teacher@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var course = courses.save(new Course("TA-GT2", "Tiếng Anh giao tiếp 2", null, null));
        var branch = branches.save(new Branch("CG02", "Chi nhánh 2", null));

        var request = new CreateClassRequest(course.getId(), "TA-GT2-K1", branch.getId(), UUID.randomUUID(),
                20, List.of());
        var result = mockMvc.perform(post("/api/courses/classes")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void rejectsACreateWithANonExistentBranch() throws Exception {
        var admin = signIn("class-bad-branch@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var course = courses.save(new Course("TA-GT4", "Tiếng Anh giao tiếp 4", null, null));
        var teacher = accounts.save(new Account("teacher-5@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Cô Mai", null));

        var request = new CreateClassRequest(course.getId(), "TA-GT4-K1", UUID.randomUUID(), teacher.getId(),
                20, List.of());
        var result = mockMvc.perform(post("/api/courses/classes")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    /** Lớp mới mở có thể chưa biết lịch học, thêm sau qua PATCH - schedule rỗng không phải lỗi. */
    @Test
    void acceptsAnEmptyScheduleOnCreate() throws Exception {
        var admin = signIn("class-empty-schedule@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var course = courses.save(new Course("TA-GT5", "Tiếng Anh giao tiếp 5", null, null));
        var branch = branches.save(new Branch("CG05", "Chi nhánh 5", null));
        var teacher = accounts.save(new Account("teacher-6@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Thầy Đức", null));

        var request = new CreateClassRequest(course.getId(), "TA-GT5-K1", branch.getId(), teacher.getId(),
                20, List.of());
        var result = mockMvc.perform(post("/api/courses/classes")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var classId = objectMapper.readValue(result.getResponse().getContentAsString(), UUID.class);
        assertThat(classes.findById(classId).orElseThrow().getSchedule()).isEmpty();
    }

    @Test
    void updatesTeacherScheduleAndMaxSeats() throws Exception {
        var admin = signIn("class-update-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var course = courses.save(new Course("TA-GT3", "Tiếng Anh giao tiếp 3", null, null));
        var branch = branches.save(new Branch("CG03", "Chi nhánh 3", null));
        var teacher1 = accounts.save(new Account("teacher-2@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Thầy Nam", null));
        var teacher2 = accounts.save(new Account("teacher-3@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Cô Hoa", null));
        var classId = classes.save(new com.eduerp.modules.courses.internal.model.Class(course, "TA-GT3-K1",
                branch.getId(), teacher1.getId(), 15)).getId();

        var updateRequest = new UpdateClassRequest(teacher2.getId(), 25, true,
                List.of(new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.TUE, LocalTime.of(19, 0), LocalTime.of(21, 0))));
        var result = mockMvc.perform(patch("/api/courses/classes/" + classId)
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var updated = classes.findById(classId).orElseThrow();
        assertThat(updated.getTeacherId()).isEqualTo(teacher2.getId());
        assertThat(updated.getMaxSeats()).isEqualTo(25);
        assertThat(updated.getSchedule()).hasSize(1);
    }

    @Test
    void rejectsUpdatingANonExistentClass() throws Exception {
        var admin = signIn("class-missing-admin@eduerp.local", AccessConstants.RoleCodes.ADMIN);
        var teacher = accounts.save(new Account("teacher-4@eduerp.local",
                passwordEncoder.encode(PASSWORD), "Thầy Khoa", null));

        var result = mockMvc.perform(patch("/api/courses/classes/" + UUID.randomUUID())
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateClassRequest(teacher.getId(), 10, true, List.of()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }
}
```

- [ ] **Step 4: Run it**

Run: `cd backend && mvn verify -Dit.test=ClassAdminControllerIT`
Expected: 6 tests green.

- [ ] **Step 5: Full backend verify**

Run: `cd backend && mvn verify -Dit.test='!AccountSelfServiceControllerIT'`
Expected: BUILD SUCCESS, every test green (the excluded class is a pre-existing, already-confirmed
unrelated flake — see prior session notes; do not spend time on it here).

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/java/com/eduerp/modules/courses
git commit -m "test(backend): add CourseAdminControllerIT and ClassAdminControllerIT"
```

---

## Task 7: Frontend entities

**Files:**
- Create: `frontend/src/entities/course/model/course-schema.ts`
- Create: `frontend/src/entities/course/api/course-keys.ts`
- Create: `frontend/src/entities/course/api/course-api.ts`
- Create: `frontend/src/entities/course/api/use-courses.ts`
- Create: `frontend/src/entities/course/index.ts`
- Create: `frontend/src/entities/class/model/class-schema.ts`
- Create: `frontend/src/entities/class/api/class-keys.ts`
- Create: `frontend/src/entities/class/api/class-api.ts`
- Create: `frontend/src/entities/class/api/use-classes.ts`
- Create: `frontend/src/entities/class/index.ts`
- Modify: `frontend/src/shared/constants/api-routes.ts`

**Interfaces:**
- Consumes: `apiClient` (existing, `@/shared/api/api-client` — `get`/`post`/`patch`, no `signal` param
  per the earlier fix), `pageResponseSchema` (existing, `@/shared/api/schemas`).
- Produces: `useCourses(page, size)`, `useClasses(page, size)`, `courseApi`, `classApi`,
  `CourseSummary`, `ClassSummary`, `CreateCoursePayload`, `UpdateCoursePayload`,
  `CreateClassPayload`, `UpdateClassPayload`, `WeeklyScheduleSlot` type — Task 9/10 import these.

- [ ] **Step 1: API routes**

In `frontend/src/shared/constants/api-routes.ts`, add after the `organization` block:
```ts
  courses: {
    courses: "/api/courses/courses",
    course: (courseId: string) => `/api/courses/courses/${courseId}`,
    classes: "/api/courses/classes",
    class: (classId: string) => `/api/courses/classes/${classId}`,
  },
```

- [ ] **Step 2: entities/course**

`frontend/src/entities/course/model/course-schema.ts`:
```ts
import { z } from "zod";

export const courseSummarySchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  standardSessionCount: z.number().int().nullable(),
  active: z.boolean(),
});

export type CourseSummary = z.infer<typeof courseSummarySchema>;

export interface CreateCoursePayload {
  readonly code: string;
  readonly name: string;
  readonly description: string | null;
  readonly standardSessionCount: number | null;
}

export interface UpdateCoursePayload {
  readonly name: string;
  readonly description: string | null;
  readonly standardSessionCount: number | null;
  readonly active: boolean;
}
```

`frontend/src/entities/course/api/course-keys.ts`:
```ts
export const courseKeys = {
  all: ["course"] as const,
  lists: () => [...courseKeys.all, "list"] as const,
  list: (page: number, size: number) => [...courseKeys.lists(), { page, size }] as const,
} as const;
```

`frontend/src/entities/course/api/course-api.ts`:
```ts
import {
  courseSummarySchema,
  type CreateCoursePayload,
  type UpdateCoursePayload,
} from "@/entities/course/model/course-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const coursePageSchema = pageResponseSchema(courseSummarySchema);
const createdCourseIdSchema = z.string().uuid();

export const courseApi = {
  async listCourses(page: number, size: number) {
    return coursePageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.courses.courses, { page, size }),
    );
  },

  async createCourse(payload: CreateCoursePayload): Promise<string> {
    return createdCourseIdSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.courses.courses, payload),
    );
  },

  async updateCourse(courseId: string, payload: UpdateCoursePayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.courses.course(courseId), payload);
  },
} as const;
```

`frontend/src/entities/course/api/use-courses.ts`:
```ts
import { courseApi } from "@/entities/course/api/course-api";
import { courseKeys } from "@/entities/course/api/course-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useCourses(page: number, size: number) {
  return useQuery({
    queryKey: courseKeys.list(page, size),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => courseApi.listCourses(page, size),
  });
}
```

`frontend/src/entities/course/index.ts`:
```ts
export { courseApi } from "@/entities/course/api/course-api";
export { courseKeys } from "@/entities/course/api/course-keys";
export { useCourses } from "@/entities/course/api/use-courses";
export {
  courseSummarySchema,
  type CourseSummary,
  type CreateCoursePayload,
  type UpdateCoursePayload,
} from "@/entities/course/model/course-schema";
```

- [ ] **Step 3: Compile-check so far**

Run: `cd frontend && npm run typecheck`
Expected: PASS (nothing imports these new files yet, so no errors possible beyond internal
consistency within the 5 files just written — fix any typo now before duplicating the pattern).

- [ ] **Step 4: entities/class**

`frontend/src/entities/class/model/class-schema.ts`:
```ts
import { z } from "zod";

export const DAY_OF_WEEK = {
  mon: "MON",
  tue: "TUE",
  wed: "WED",
  thu: "THU",
  fri: "FRI",
  sat: "SAT",
  sun: "SUN",
} as const;

export type DayOfWeek = (typeof DAY_OF_WEEK)[keyof typeof DAY_OF_WEEK];

export const DAY_OF_WEEK_LABEL: Record<DayOfWeek, string> = {
  [DAY_OF_WEEK.mon]: "Thứ 2",
  [DAY_OF_WEEK.tue]: "Thứ 3",
  [DAY_OF_WEEK.wed]: "Thứ 4",
  [DAY_OF_WEEK.thu]: "Thứ 5",
  [DAY_OF_WEEK.fri]: "Thứ 6",
  [DAY_OF_WEEK.sat]: "Thứ 7",
  [DAY_OF_WEEK.sun]: "Chủ nhật",
};

export const weeklyScheduleSlotSchema = z.object({
  dayOfWeek: z.enum([
    DAY_OF_WEEK.mon,
    DAY_OF_WEEK.tue,
    DAY_OF_WEEK.wed,
    DAY_OF_WEEK.thu,
    DAY_OF_WEEK.fri,
    DAY_OF_WEEK.sat,
    DAY_OF_WEEK.sun,
  ]),
  startTime: z.string(),
  endTime: z.string(),
});

export type WeeklyScheduleSlot = z.infer<typeof weeklyScheduleSlotSchema>;

export const classSummarySchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  courseId: z.string().uuid(),
  courseName: z.string(),
  branchId: z.string().uuid(),
  branchName: z.string().nullable(),
  teacherId: z.string().uuid(),
  teacherName: z.string().nullable(),
  maxSeats: z.number().int(),
  active: z.boolean(),
  schedule: z.array(weeklyScheduleSlotSchema),
});

export type ClassSummary = z.infer<typeof classSummarySchema>;

export interface CreateClassPayload {
  readonly courseId: string;
  readonly code: string;
  readonly branchId: string;
  readonly teacherId: string;
  readonly maxSeats: number;
  readonly schedule: readonly WeeklyScheduleSlot[];
}

export interface UpdateClassPayload {
  readonly teacherId: string;
  readonly maxSeats: number;
  readonly active: boolean;
  readonly schedule: readonly WeeklyScheduleSlot[];
}
```

Note: backend's `LocalTime` serializes as `"HH:mm:ss"` by Jackson's default — `startTime`/`endTime`
stay as plain `z.string()` (not parsed into a Date) since the UI only ever needs to display/edit them
as `HH:mm` text, matching how the rest of this codebase keeps wire-format strings as strings (e.g.
`Session.branchId` is a bare UUID string, never parsed into a richer type).

`frontend/src/entities/class/api/class-keys.ts`:
```ts
export const classKeys = {
  all: ["class"] as const,
  lists: () => [...classKeys.all, "list"] as const,
  list: (page: number, size: number) => [...classKeys.lists(), { page, size }] as const,
} as const;
```

`frontend/src/entities/class/api/class-api.ts`:
```ts
import {
  classSummarySchema,
  type CreateClassPayload,
  type UpdateClassPayload,
} from "@/entities/class/model/class-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const classPageSchema = pageResponseSchema(classSummarySchema);
const createdClassIdSchema = z.string().uuid();

export const classApi = {
  async listClasses(page: number, size: number) {
    return classPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.courses.classes, { page, size }),
    );
  },

  async createClass(payload: CreateClassPayload): Promise<string> {
    return createdClassIdSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.courses.classes, payload),
    );
  },

  async updateClass(classId: string, payload: UpdateClassPayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.courses.class(classId), payload);
  },
} as const;
```

`frontend/src/entities/class/api/use-classes.ts`:
```ts
import { classApi } from "@/entities/class/api/class-api";
import { classKeys } from "@/entities/class/api/class-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useClasses(page: number, size: number) {
  return useQuery({
    queryKey: classKeys.list(page, size),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => classApi.listClasses(page, size),
  });
}
```

`frontend/src/entities/class/index.ts`:
```ts
export { classApi } from "@/entities/class/api/class-api";
export { classKeys } from "@/entities/class/api/class-keys";
export { useClasses } from "@/entities/class/api/use-classes";
export {
  classSummarySchema,
  DAY_OF_WEEK,
  DAY_OF_WEEK_LABEL,
  weeklyScheduleSlotSchema,
  type ClassSummary,
  type CreateClassPayload,
  type DayOfWeek,
  type UpdateClassPayload,
  type WeeklyScheduleSlot,
} from "@/entities/class/model/class-schema";
```

- [ ] **Step 5: Typecheck + commit**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: both clean.

```bash
git add frontend/src/entities/course frontend/src/entities/class frontend/src/shared/constants/api-routes.ts
git commit -m "feat(frontend): add course and class entity layers"
```

---

## Task 8: Permissions, routes, nav wiring

**Files:**
- Modify: `frontend/src/shared/constants/permissions.ts`
- Modify: `frontend/src/shared/constants/app-routes.ts`
- Modify: `frontend/src/app/layouts/nav-items.ts`
- Modify: `frontend/src/app/ui/app-nav.tsx`

**Interfaces:**
- Produces: `ACCESS_RULE.readCourse/createCourse/updateCourse/readClass/createClass/updateClass`,
  `APP_ROUTE.courses`/`.classes` — Task 9/10's pages and Task 5's... (already-done backend) consume
  these names.

- [ ] **Step 1: ACCESS_RULE**

In `frontend/src/shared/constants/permissions.ts`, add `course`/`class` to `RESOURCE`:
```ts
  course: "COURSE",
  class: "CLASS",
```
and to `RESOURCE_LABEL`:
```ts
  [RESOURCE.course]: "Khóa học",
  [RESOURCE.class]: "Lớp học",
```
and to `ACCESS_RULE` (after `updateBranch`):
```ts
  readCourse: { resource: RESOURCE.course, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createCourse: {
    resource: RESOURCE.course,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateCourse: {
    resource: RESOURCE.course,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  readClass: { resource: RESOURCE.class, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createClass: {
    resource: RESOURCE.class,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateClass: {
    resource: RESOURCE.class,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
```

- [ ] **Step 2: Routes + nav**

In `frontend/src/shared/constants/app-routes.ts`, add to `APP_ROUTE`:
```ts
  courses: "/admin/courses",
  classes: "/admin/classes",
```

In `frontend/src/app/layouts/nav-items.ts`, add to the "Điều hành & Nghiệp vụ" section, after the
`branches` entry:
```ts
      { path: APP_ROUTE.courses, label: "Khóa học", requirement: ACCESS_RULE.readCourse },
      { path: APP_ROUTE.classes, label: "Lớp học", requirement: ACCESS_RULE.readClass },
```

In `frontend/src/app/ui/app-nav.tsx`, add two icons to `NAV_ICON` (import `BookOpen`, `CalendarDays`
from `lucide-react` alongside the existing icon imports):
```ts
  [APP_ROUTE.courses]: <BookOpen size={18} aria-hidden />,
  [APP_ROUTE.classes]: <CalendarDays size={18} aria-hidden />,
```

- [ ] **Step 3: Typecheck + commit**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: both clean (routes/nav reference `ACCESS_RULE`/`APP_ROUTE` entries that now exist, but no
page component yet — that's fine, `app-router.tsx` isn't touched until Task 10).

```bash
git add frontend/src/shared/constants/permissions.ts frontend/src/shared/constants/app-routes.ts \
        frontend/src/app/layouts/nav-items.ts frontend/src/app/ui/app-nav.tsx
git commit -m "feat(frontend): wire course/class permissions, routes, and nav items"
```

---

## Task 9: Forms and mutations

**Files:**
- Create: `frontend/src/modules/courses/model/courses-forms.ts`
- Create: `frontend/src/modules/courses/api/use-courses-mutations.ts`

**Interfaces:**
- Consumes: `courseApi`/`classApi`/`courseKeys`/`classKeys` (Task 7), `catalogKeys` (existing,
  `@/entities/rbac-catalog` — branch/teacher dropdowns still read from the RBAC catalog for names,
  but Task 10's dialogs source raw branch/teacher id LISTS straight from `useBranches`/a new
  `useRbacCatalog`-backed teacher list, not re-invented here).
- Produces: `createCourseFormSchema`, `updateCourseFormSchema`, `createClassFormSchema`,
  `updateClassFormSchema`, `useCreateCourse()`, `useUpdateCourse(courseId)`, `useCreateClass()`,
  `useUpdateClass(classId)` — Task 10's dialogs call these by name.

- [ ] **Step 1: Forms**

`frontend/src/modules/courses/model/courses-forms.ts`:
```ts
import { DAY_OF_WEEK } from "@/entities/class";
import { z } from "zod";

const COURSE_CODE_PATTERN = /^[A-Z][A-Z0-9-]*$/;
const CLASS_CODE_PATTERN = /^[A-Z][A-Z0-9-]*$/;

const scheduleSlotFormSchema = z.object({
  dayOfWeek: z.enum([
    DAY_OF_WEEK.mon,
    DAY_OF_WEEK.tue,
    DAY_OF_WEEK.wed,
    DAY_OF_WEEK.thu,
    DAY_OF_WEEK.fri,
    DAY_OF_WEEK.sat,
    DAY_OF_WEEK.sun,
  ]),
  startTime: z.string().min(1, "Chọn giờ bắt đầu"),
  endTime: z.string().min(1, "Chọn giờ kết thúc"),
});

export const createCourseFormSchema = z.object({
  code: z
    .string()
    .min(1, "Nhập mã khóa học")
    .regex(COURSE_CODE_PATTERN, "Mã chỉ gồm chữ in hoa, số và dấu gạch ngang, ví dụ TA-GT"),
  name: z.string().min(1, "Nhập tên khóa học"),
  description: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  standardSessionCount: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : Number(value))),
});

export const updateCourseFormSchema = z.object({
  name: z.string().min(1, "Nhập tên khóa học"),
  description: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : value)),
  standardSessionCount: z
    .string()
    .trim()
    .transform((value) => (value.length === 0 ? null : Number(value))),
  active: z.boolean(),
});

export const createClassFormSchema = z.object({
  courseId: z.string().uuid("Chọn khóa học"),
  code: z
    .string()
    .min(1, "Nhập mã lớp")
    .regex(CLASS_CODE_PATTERN, "Mã chỉ gồm chữ in hoa, số và dấu gạch ngang, ví dụ TA-GT-K15"),
  branchId: z.string().uuid("Chọn chi nhánh"),
  teacherId: z.string().uuid("Chọn giáo viên"),
  maxSeats: z
    .string()
    .min(1, "Nhập sĩ số tối đa")
    .transform((value) => Number(value))
    .refine((value) => value > 0, "Sĩ số phải lớn hơn 0"),
  schedule: z.array(scheduleSlotFormSchema),
});

export const updateClassFormSchema = z.object({
  teacherId: z.string().uuid("Chọn giáo viên"),
  maxSeats: z
    .string()
    .min(1, "Nhập sĩ số tối đa")
    .transform((value) => Number(value))
    .refine((value) => value > 0, "Sĩ số phải lớn hơn 0"),
  active: z.boolean(),
  schedule: z.array(scheduleSlotFormSchema),
});
```

- [ ] **Step 2: Mutations**

`frontend/src/modules/courses/api/use-courses-mutations.ts`:
```ts
import { courseApi, courseKeys, type CreateCoursePayload, type UpdateCoursePayload } from "@/entities/course";
import { classApi, classKeys, type CreateClassPayload, type UpdateClassPayload } from "@/entities/class";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useCoursesInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: courseKeys.all });
  };
}

function useClassesInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    // Danh sách lớp hiển thị tên khóa học - đổi khóa học cũng có thể ảnh hưởng, nên bỏ cache cả hai.
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: classKeys.all }),
      queryClient.invalidateQueries({ queryKey: courseKeys.all }),
    ]);
  };
}

export function useCreateCourse() {
  const invalidate = useCoursesInvalidation();
  return useMutation({
    mutationFn: (payload: CreateCoursePayload) => courseApi.createCourse(payload),
    onSuccess: invalidate,
  });
}

export function useUpdateCourse(courseId: string) {
  const invalidate = useCoursesInvalidation();
  return useMutation({
    mutationFn: (payload: UpdateCoursePayload) => courseApi.updateCourse(courseId, payload),
    onSuccess: invalidate,
  });
}

export function useCreateClass() {
  const invalidate = useClassesInvalidation();
  return useMutation({
    mutationFn: (payload: CreateClassPayload) => classApi.createClass(payload),
    onSuccess: invalidate,
  });
}

export function useUpdateClass(classId: string) {
  const invalidate = useClassesInvalidation();
  return useMutation({
    mutationFn: (payload: UpdateClassPayload) => classApi.updateClass(classId, payload),
    onSuccess: invalidate,
  });
}
```

- [ ] **Step 3: Typecheck + commit**

Run: `cd frontend && npm run typecheck && npm run lint`
Expected: both clean.

```bash
git add frontend/src/modules/courses/model frontend/src/modules/courses/api
git commit -m "feat(frontend): add courses/classes form schemas and mutations"
```

---

## Task 10: UI (tables, dialogs, pages) + router wiring

**Files:**
- Create: `frontend/src/modules/courses/ui/courses-table.tsx`
- Create: `frontend/src/modules/courses/ui/create-course-dialog.tsx`
- Create: `frontend/src/modules/courses/ui/edit-course-dialog.tsx`
- Create: `frontend/src/modules/courses/ui/schedule-slot-editor.tsx`
- Create: `frontend/src/modules/courses/ui/classes-table.tsx`
- Create: `frontend/src/modules/courses/ui/create-class-dialog.tsx`
- Create: `frontend/src/modules/courses/ui/edit-class-dialog.tsx`
- Create: `frontend/src/modules/courses/pages/courses-page.tsx`
- Create: `frontend/src/modules/courses/pages/classes-page.tsx`
- Create: `frontend/src/modules/courses/index.ts`
- Modify: `frontend/src/app/router/app-router.tsx`

**Interfaces:**
- Consumes: everything from Tasks 7–9, plus existing `@/entities/rbac-catalog` (`useRbacCatalog` —
  `.data.branches` for the branch picker, same source `TransferBranchDialog` already uses) and
  `@/entities/account` (`useAccounts` — for the teacher picker; filter client-side to accounts whose
  `roleCode === "TEACHER"` isn't available from that endpoint's shape, so instead fetch a generously-
  sized page, e.g. `useAccounts(0, 100, null)`, and let the admin pick any account — restricting the
  dropdown to teachers only is a UX nicety, not a correctness requirement the backend enforces either,
  consistent with Review Focus item 1).

- [ ] **Step 1: ScheduleSlotEditor (shared between create/edit class dialogs)**

`frontend/src/modules/courses/ui/schedule-slot-editor.tsx`:
```tsx
import { DAY_OF_WEEK_LABEL, type WeeklyScheduleSlot } from "@/entities/class";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassSelect } from "@/shared/ui/glass-select";
import { Plus, X } from "lucide-react";

export interface ScheduleSlotEditorProps {
  readonly slots: readonly WeeklyScheduleSlot[];
  readonly onChange: (slots: readonly WeeklyScheduleSlot[]) => void;
}

const DAY_OPTIONS = Object.entries(DAY_OF_WEEK_LABEL) as [WeeklyScheduleSlot["dayOfWeek"], string][];

export function ScheduleSlotEditor({ slots, onChange }: ScheduleSlotEditorProps) {
  const addSlot = () => {
    onChange([...slots, { dayOfWeek: "MON", startTime: "18:00", endTime: "20:00" }]);
  };

  const updateSlot = (index: number, patch: Partial<WeeklyScheduleSlot>) => {
    onChange(slots.map((slot, i) => (i === index ? { ...slot, ...patch } : slot)));
  };

  const removeSlot = (index: number) => {
    onChange(slots.filter((_, i) => i !== index));
  };

  return (
    <div className="flex flex-col gap-2">
      {slots.map((slot, index) => (
        <div key={index} className="flex items-center gap-2">
          <GlassSelect
            value={slot.dayOfWeek}
            onChange={(event) => updateSlot(index, { dayOfWeek: event.target.value as WeeklyScheduleSlot["dayOfWeek"] })}
            className="w-32"
          >
            {DAY_OPTIONS.map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </GlassSelect>
          <GlassInput
            type="time"
            value={slot.startTime}
            onChange={(event) => updateSlot(index, { startTime: event.target.value })}
            className="w-28"
          />
          <span className="text-slate-400">–</span>
          <GlassInput
            type="time"
            value={slot.endTime}
            onChange={(event) => updateSlot(index, { endTime: event.target.value })}
            className="w-28"
          />
          <GlassButton
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => removeSlot(index)}
            aria-label="Xoá buổi học"
          >
            <X size={14} aria-hidden />
          </GlassButton>
        </div>
      ))}
      <GlassButton type="button" variant="secondary" size="sm" onClick={addSlot} icon={<Plus size={14} aria-hidden />}>
        Thêm buổi học
      </GlassButton>
    </div>
  );
}
```

- [ ] **Step 2: Courses table + dialogs**

`frontend/src/modules/courses/ui/courses-table.tsx`:
```tsx
import type { CourseSummary } from "@/entities/course";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { Pencil } from "lucide-react";

export interface CoursesTableProps {
  readonly rows: readonly CourseSummary[];
  readonly onEdit: (course: CourseSummary) => void;
}

export function CoursesTable({ rows, onEdit }: CoursesTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((course, index) => (
        <m.li
          key={course.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)_auto_auto]"
        >
          <p className="truncate text-sm text-mist-100">{course.code}</p>
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">{course.name}</p>
            <p className="truncate text-xs text-mist-500">
              {course.standardSessionCount ? `${course.standardSessionCount} buổi chuẩn` : "Chưa có số buổi chuẩn"}
            </p>
          </div>
          <Badge tone={course.active ? "positive" : "neutral"}>
            {course.active ? "Đang hoạt động" : "Đã vô hiệu hoá"}
          </Badge>
          <Can {...ACCESS_RULE.updateCourse}>
            <div className="justify-self-start lg:justify-self-end">
              <GlassButton variant="secondary" size="sm" onClick={() => onEdit(course)} icon={<Pencil size={14} aria-hidden />}>
                Sửa
              </GlassButton>
            </div>
          </Can>
        </m.li>
      ))}
    </ul>
  );
}
```

`frontend/src/modules/courses/ui/create-course-dialog.tsx`:
```tsx
import { useCreateCourse } from "@/modules/courses/api/use-courses-mutations";
import { createCourseFormSchema } from "@/modules/courses/model/courses-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface CreateCourseDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateCourseDialog({ open, onClose }: CreateCourseDialogProps) {
  const createCourse = useCreateCourse();

  const form = useZodForm({
    schema: createCourseFormSchema,
    initialValues: { code: "", name: "", description: "", standardSessionCount: "" },
    onSubmit: async (values) => {
      await createCourse.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Tạo khóa học" description="Mã khóa học không sửa được sau khi tạo.">
      <form id="create-course-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Mã khóa học" htmlFor="course-code" hint="Chữ in hoa, số và dấu gạch ngang. Ví dụ: TA-GT." error={form.fieldErrors.code}>
          <GlassInput
            id="course-code"
            autoFocus
            value={form.values.code}
            invalid={form.fieldErrors.code !== undefined}
            onChange={(event) => form.setValue("code", event.target.value.toUpperCase())}
          />
        </FormField>

        <FormField label="Tên khóa học" htmlFor="course-name" error={form.fieldErrors.name}>
          <GlassInput
            id="course-name"
            value={form.values.name}
            invalid={form.fieldErrors.name !== undefined}
            onChange={(event) => form.setValue("name", event.target.value)}
          />
        </FormField>

        <FormField label="Mô tả" htmlFor="course-description" hint="Không bắt buộc.">
          <GlassInput
            id="course-description"
            value={form.values.description}
            onChange={(event) => form.setValue("description", event.target.value)}
          />
        </FormField>

        <FormField label="Số buổi chuẩn" htmlFor="course-session-count" hint="Không bắt buộc.">
          <GlassInput
            id="course-session-count"
            type="number"
            min={0}
            value={form.values.standardSessionCount}
            onChange={(event) => form.setValue("standardSessionCount", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-course-form" loading={form.isSubmitting}>
          Tạo khóa học
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

`frontend/src/modules/courses/ui/edit-course-dialog.tsx`:
```tsx
import type { CourseSummary } from "@/entities/course";
import { useUpdateCourse } from "@/modules/courses/api/use-courses-mutations";
import { updateCourseFormSchema } from "@/modules/courses/model/courses-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { CheckableRow } from "@/shared/ui/checkable-row";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface EditCourseDialogProps {
  readonly course: CourseSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function EditCourseDialog({ course, open, onClose }: EditCourseDialogProps) {
  const updateCourse = useUpdateCourse(course.id);

  const form = useZodForm({
    schema: updateCourseFormSchema,
    initialValues: {
      name: course.name,
      description: course.description ?? "",
      standardSessionCount: course.standardSessionCount?.toString() ?? "",
      active: course.active,
    },
    onSubmit: async (values) => {
      await updateCourse.mutateAsync(values);
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title={`Sửa khóa học ${course.code}`} description="Mã khóa học bất biến, chỉ sửa được các thông tin còn lại.">
      <form id="edit-course-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Tên khóa học" htmlFor="edit-course-name" error={form.fieldErrors.name}>
          <GlassInput
            id="edit-course-name"
            autoFocus
            value={form.values.name}
            invalid={form.fieldErrors.name !== undefined}
            onChange={(event) => form.setValue("name", event.target.value)}
          />
        </FormField>

        <FormField label="Mô tả" htmlFor="edit-course-description" hint="Không bắt buộc.">
          <GlassInput
            id="edit-course-description"
            value={form.values.description}
            onChange={(event) => form.setValue("description", event.target.value)}
          />
        </FormField>

        <FormField label="Số buổi chuẩn" htmlFor="edit-course-session-count" hint="Không bắt buộc.">
          <GlassInput
            id="edit-course-session-count"
            type="number"
            min={0}
            value={form.values.standardSessionCount}
            onChange={(event) => form.setValue("standardSessionCount", event.target.value)}
          />
        </FormField>

        <CheckableRow
          label="Đang hoạt động"
          description="Bỏ chọn để vô hiệu hoá khóa học mà không xoá dữ liệu."
          checked={form.values.active}
          onToggle={() => form.setValue("active", !form.values.active)}
        />
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="edit-course-form" loading={form.isSubmitting}>
          Lưu thay đổi
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

- [ ] **Step 3: courses-page.tsx**

`frontend/src/modules/courses/pages/courses-page.tsx`:
```tsx
import { useCourses, type CourseSummary } from "@/entities/course";
import { Can, RequirePermission } from "@/entities/permission";
import { CoursesTable } from "@/modules/courses/ui/courses-table";
import { CreateCourseDialog } from "@/modules/courses/ui/create-course-dialog";
import { EditCourseDialog } from "@/modules/courses/ui/edit-course-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { BookOpen, Plus } from "lucide-react";
import { useState } from "react";

export function CoursesPage() {
  const [page, setPage] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editing, setEditing] = useState<CourseSummary | null>(null);
  const courses = useCourses(page, DEFAULT_PAGE_SIZE);

  return (
    <RequirePermission {...ACCESS_RULE.readCourse}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Khóa học"
          description="Danh mục khóa học dùng chung toàn tổ chức. Khóa học chỉ được vô hiệu hoá, không xoá."
          actions={
            <Can {...ACCESS_RULE.createCourse}>
              <GlassButton onClick={() => setCreateDialogOpen(true)} icon={<Plus size={16} aria-hidden />}>
                Khóa học mới
              </GlassButton>
            </Can>
          }
        />

        {courses.isError ? <ErrorNotice error={courses.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {courses.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {courses.data ? (
            courses.data.items.length === 0 ? (
              <EmptyState
                icon={<BookOpen size={28} aria-hidden />}
                title="Chưa có khóa học nào"
                description="Tạo khóa học đầu tiên để bắt đầu mở lớp."
              />
            ) : (
              <>
                <CoursesTable rows={courses.data.items} onEdit={setEditing} />
                <Pagination
                  page={courses.data.page}
                  totalPages={courses.data.totalPages}
                  totalItems={courses.data.totalItems}
                  onPageChange={setPage}
                  itemLabel="khóa học"
                />
              </>
            )
          ) : null}
        </GlassPanel>

        <CreateCourseDialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} />

        {editing === null ? null : (
          <EditCourseDialog key={editing.id} course={editing} open={editing !== null} onClose={() => setEditing(null)} />
        )}
      </div>
    </RequirePermission>
  );
}
```

- [ ] **Step 4: Typecheck courses-only slice**

Run: `cd frontend && npm run typecheck`
Expected: PASS. Fix anything before moving to the class side (same patterns repeat there, catch
mistakes once).

- [ ] **Step 5: Classes table + dialogs**

`frontend/src/modules/courses/ui/classes-table.tsx`:
```tsx
import type { ClassSummary } from "@/entities/class";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { Pencil } from "lucide-react";

export interface ClassesTableProps {
  readonly rows: readonly ClassSummary[];
  readonly onEdit: (cls: ClassSummary) => void;
}

export function ClassesTable({ rows, onEdit }: ClassesTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((cls, index) => (
        <m.li
          key={cls.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)_minmax(0,1fr)_minmax(0,1fr)_auto_auto]"
        >
          <p className="truncate text-sm text-mist-100">{cls.code}</p>
          <p className="truncate text-sm text-mist-100">{cls.courseName}</p>
          <p className="truncate text-xs text-mist-400">{cls.branchName ?? "—"}</p>
          <p className="truncate text-xs text-mist-400">{cls.teacherName ?? "Chưa gán giáo viên"}</p>
          <Badge tone={cls.active ? "positive" : "neutral"}>
            {cls.active ? "Đang hoạt động" : "Đã vô hiệu hoá"}
          </Badge>
          <Can {...ACCESS_RULE.updateClass}>
            <div className="justify-self-start lg:justify-self-end">
              <GlassButton variant="secondary" size="sm" onClick={() => onEdit(cls)} icon={<Pencil size={14} aria-hidden />}>
                Sửa
              </GlassButton>
            </div>
          </Can>
        </m.li>
      ))}
    </ul>
  );
}
```

`frontend/src/modules/courses/ui/create-class-dialog.tsx`:
```tsx
import { useAccounts } from "@/entities/account";
import { useCourses } from "@/entities/course";
import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useCreateClass } from "@/modules/courses/api/use-courses-mutations";
import { createClassFormSchema } from "@/modules/courses/model/courses-forms";
import { ScheduleSlotEditor } from "@/modules/courses/ui/schedule-slot-editor";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateClassDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateClassDialog({ open, onClose }: CreateClassDialogProps) {
  const createClass = useCreateClass();
  const courses = useCourses(0, 100);
  const catalog = useRbacCatalog();
  const teachers = useAccounts(0, 100, null);

  const form = useZodForm({
    schema: createClassFormSchema,
    initialValues: { courseId: "", code: "", branchId: "", teacherId: "", maxSeats: "", schedule: [] },
    onSubmit: async (values) => {
      await createClass.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Mở lớp" description="Khóa học và chi nhánh không sửa được sau khi tạo.">
      <form id="create-class-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Khóa học" htmlFor="class-course" error={form.fieldErrors.courseId}>
          <GlassSelect
            id="class-course"
            value={form.values.courseId}
            invalid={form.fieldErrors.courseId !== undefined}
            onChange={(event) => form.setValue("courseId", event.target.value)}
          >
            <option value="">-- Chọn khóa học --</option>
            {(courses.data?.items ?? []).map((course) => (
              <option key={course.id} value={course.id}>
                {course.name}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Mã lớp" htmlFor="class-code" hint="Ví dụ: TA-GT-K15." error={form.fieldErrors.code}>
          <GlassInput
            id="class-code"
            value={form.values.code}
            invalid={form.fieldErrors.code !== undefined}
            onChange={(event) => form.setValue("code", event.target.value.toUpperCase())}
          />
        </FormField>

        <FormField label="Chi nhánh" htmlFor="class-branch" error={form.fieldErrors.branchId}>
          <GlassSelect
            id="class-branch"
            value={form.values.branchId}
            invalid={form.fieldErrors.branchId !== undefined}
            onChange={(event) => form.setValue("branchId", event.target.value)}
          >
            <option value="">-- Chọn chi nhánh --</option>
            {(catalog.data?.branches ?? []).map((branch) => (
              <option key={branch.id} value={branch.id}>
                {branch.name}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Giáo viên" htmlFor="class-teacher" error={form.fieldErrors.teacherId}>
          <GlassSelect
            id="class-teacher"
            value={form.values.teacherId}
            invalid={form.fieldErrors.teacherId !== undefined}
            onChange={(event) => form.setValue("teacherId", event.target.value)}
          >
            <option value="">-- Chọn giáo viên --</option>
            {(teachers.data?.items ?? []).map((account) => (
              <option key={account.id} value={account.id}>
                {account.fullName}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Sĩ số tối đa" htmlFor="class-max-seats" error={form.fieldErrors.maxSeats}>
          <GlassInput
            id="class-max-seats"
            type="number"
            min={1}
            value={form.values.maxSeats}
            invalid={form.fieldErrors.maxSeats !== undefined}
            onChange={(event) => form.setValue("maxSeats", event.target.value)}
          />
        </FormField>

        <FormField label="Lịch học hàng tuần" htmlFor="class-schedule" hint="Có thể để trống rồi thêm sau.">
          <ScheduleSlotEditor slots={form.values.schedule} onChange={(slots) => form.setValue("schedule", slots)} />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-class-form" loading={form.isSubmitting}>
          Mở lớp
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

`frontend/src/modules/courses/ui/edit-class-dialog.tsx`:
```tsx
import { useAccounts } from "@/entities/account";
import type { ClassSummary } from "@/entities/class";
import { useUpdateClass } from "@/modules/courses/api/use-courses-mutations";
import { updateClassFormSchema } from "@/modules/courses/model/courses-forms";
import { ScheduleSlotEditor } from "@/modules/courses/ui/schedule-slot-editor";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { CheckableRow } from "@/shared/ui/checkable-row";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface EditClassDialogProps {
  readonly cls: ClassSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function EditClassDialog({ cls, open, onClose }: EditClassDialogProps) {
  const updateClass = useUpdateClass(cls.id);
  const teachers = useAccounts(0, 100, null);

  const form = useZodForm({
    schema: updateClassFormSchema,
    initialValues: {
      teacherId: cls.teacherId,
      maxSeats: cls.maxSeats.toString(),
      active: cls.active,
      schedule: cls.schedule,
    },
    onSubmit: async (values) => {
      await updateClass.mutateAsync(values);
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title={`Sửa lớp ${cls.code}`} description="Khóa học và chi nhánh bất biến, chỉ sửa giáo viên/sĩ số/lịch học/trạng thái.">
      <form id="edit-class-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Giáo viên" htmlFor="edit-class-teacher" error={form.fieldErrors.teacherId}>
          <GlassSelect
            id="edit-class-teacher"
            value={form.values.teacherId}
            invalid={form.fieldErrors.teacherId !== undefined}
            onChange={(event) => form.setValue("teacherId", event.target.value)}
          >
            {(teachers.data?.items ?? []).map((account) => (
              <option key={account.id} value={account.id}>
                {account.fullName}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Sĩ số tối đa" htmlFor="edit-class-max-seats" error={form.fieldErrors.maxSeats}>
          <GlassInput
            id="edit-class-max-seats"
            type="number"
            min={1}
            value={form.values.maxSeats}
            invalid={form.fieldErrors.maxSeats !== undefined}
            onChange={(event) => form.setValue("maxSeats", event.target.value)}
          />
        </FormField>

        <FormField label="Lịch học hàng tuần" htmlFor="edit-class-schedule">
          <ScheduleSlotEditor slots={form.values.schedule} onChange={(slots) => form.setValue("schedule", slots)} />
        </FormField>

        <CheckableRow
          label="Đang hoạt động"
          description="Bỏ chọn để vô hiệu hoá lớp mà không xoá dữ liệu."
          checked={form.values.active}
          onToggle={() => form.setValue("active", !form.values.active)}
        />
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="edit-class-form" loading={form.isSubmitting}>
          Lưu thay đổi
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
```

- [ ] **Step 6: classes-page.tsx + barrel**

`frontend/src/modules/courses/pages/classes-page.tsx`:
```tsx
import { useClasses, type ClassSummary } from "@/entities/class";
import { Can, RequirePermission } from "@/entities/permission";
import { ClassesTable } from "@/modules/courses/ui/classes-table";
import { CreateClassDialog } from "@/modules/courses/ui/create-class-dialog";
import { EditClassDialog } from "@/modules/courses/ui/edit-class-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { CalendarDays, Plus } from "lucide-react";
import { useState } from "react";

export function ClassesPage() {
  const [page, setPage] = useState(0);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editing, setEditing] = useState<ClassSummary | null>(null);
  const classes = useClasses(page, DEFAULT_PAGE_SIZE);

  return (
    <RequirePermission {...ACCESS_RULE.readClass}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Lớp học"
          description="Danh sách lớp đã mở. Lớp chỉ được vô hiệu hoá, không xoá."
          actions={
            <Can {...ACCESS_RULE.createClass}>
              <GlassButton onClick={() => setCreateDialogOpen(true)} icon={<Plus size={16} aria-hidden />}>
                Mở lớp mới
              </GlassButton>
            </Can>
          }
        />

        {classes.isError ? <ErrorNotice error={classes.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {classes.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {classes.data ? (
            classes.data.items.length === 0 ? (
              <EmptyState
                icon={<CalendarDays size={28} aria-hidden />}
                title="Chưa có lớp nào"
                description="Mở lớp đầu tiên từ một khóa học đã có."
              />
            ) : (
              <>
                <ClassesTable rows={classes.data.items} onEdit={setEditing} />
                <Pagination
                  page={classes.data.page}
                  totalPages={classes.data.totalPages}
                  totalItems={classes.data.totalItems}
                  onPageChange={setPage}
                  itemLabel="lớp"
                />
              </>
            )
          ) : null}
        </GlassPanel>

        <CreateClassDialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} />

        {editing === null ? null : (
          <EditClassDialog key={editing.id} cls={editing} open={editing !== null} onClose={() => setEditing(null)} />
        )}
      </div>
    </RequirePermission>
  );
}
```

`frontend/src/modules/courses/index.ts`:
```ts
export { ClassesPage } from "@/modules/courses/pages/classes-page";
export { CoursesPage } from "@/modules/courses/pages/courses-page";
```

- [ ] **Step 7: Router wiring**

In `frontend/src/app/router/app-router.tsx`, add two lazy imports after the `BranchesPage` one:
```ts
const CoursesPage = lazy(() =>
  import("@/modules/courses").then((module) => ({ default: module.CoursesPage })),
);
const ClassesPage = lazy(() =>
  import("@/modules/courses").then((module) => ({ default: module.ClassesPage })),
);
```
and two routes after the `branches` route, inside the `<Route element={<RequireAuth />}>` block:
```tsx
          <Route path={APP_ROUTE.courses} element={<CoursesPage />} />
          <Route path={APP_ROUTE.classes} element={<ClassesPage />} />
```

- [ ] **Step 8: Full frontend verify**

Run: `cd frontend && npm run typecheck && npm run lint && npm run build`
Expected: all three clean. If `useAccounts` in Task 10's dialogs doesn't match the real 3-arg
signature from the already-shipped branch-switcher work (`page, size, branchId`), fix the call sites
here — that signature was set in a prior feature and this plan's author may have mis-recalled it;
trust the actual compiler error over this plan's text.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/modules/courses frontend/src/app/router/app-router.tsx
git commit -m "feat(frontend): add courses and classes admin pages"
```

---

## Task 11: Final whole-branch verification

**Files:** none (verification only).

- [ ] **Step 1: Full backend verify**

Run: `cd backend && mvn verify -Dit.test='!AccountSelfServiceControllerIT'`
Expected: BUILD SUCCESS, all tests green (including Tasks 1–6's new tests).

- [ ] **Step 2: ModularityTests specifically**

Run: `cd backend && mvn test -Dtest=ModularityTests`
Expected: 2/2 green — confirms no accidental cycle between `courses` and any existing module.

- [ ] **Step 3: Full frontend verify**

Run: `cd frontend && npm run typecheck && npm run lint && npm run build`
Expected: all green.

- [ ] **Step 4: Manual smoke test**

Start both dev servers (`cd backend && mvn spring-boot:run`; separately `cd frontend && npm run dev`),
sign in as ADMIN, open "Khóa học" → create one, open "Lớp học" → create a class against it with a
teacher/branch/schedule, edit the class (change teacher, add a second schedule slot), confirm the
list reflects it without a manual refresh (mutations already invalidate the right query keys per
Task 9).

- [ ] **Step 5: Final commit if anything was fixed during verification**

```bash
git add -A
git commit -m "fix: address issues found in whole-branch verification"
```
(Skip this step entirely if nothing needed fixing.)
