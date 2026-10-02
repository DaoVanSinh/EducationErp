# Phân hệ 2 — Khóa học & Lớp học: Spec thiết kế

## 1. Bối cảnh & phạm vi

Phân hệ 1 (Core: Định danh & Quản trị — `identity`/`access`/`organization`/`audit`/`dashboard`) đã
xây xong và đang chạy trên `develop`. Theo đúng thứ tự xây dựng ở
`docs/superpowers/specs/2026-09-27-education-erp-architecture-design.md` mục 2, Phân hệ 2 — Khóa học
& Lớp học — là phần tiếp theo, dùng làm nền cho Phân hệ 3 (Tuyển sinh & Học phí) và Phân hệ 4 (Quản
lý Học tập).

**Trong phạm vi phân hệ này**: danh mục khóa học, mở lớp, phân công giáo viên chính, đặt lịch học
hàng tuần — toàn bộ chỉ dành cho Admin quản trị (scope `ORGANIZATION`), đúng mô hình RBAC đã có.

**Ngoài phạm vi** (để dành phân hệ sau, không động tới ở đây):
- Ghi danh học viên vào lớp, sĩ số thực tế, học phí, thanh toán — Phân hệ 3.
- Điểm danh, theo dõi tiến độ, điểm số, màn hình "lớp của tôi" cho giáo viên — Phân hệ 4.
- Vòng đời nhiều trạng thái của lớp (DRAFT/OPEN/ONGOING/CLOSED) — chỉ dùng `active`/`inactive` đơn
  giản như `Branch`, không có state machine.
- Lịch học theo từng buổi cụ thể (ngày tháng) — chỉ lưu mẫu lặp lại hàng tuần.
- Nhiều giáo viên mỗi lớp (đồng giảng dạy) — mỗi lớp đúng một giáo viên chính.

## 2. Mô hình dữ liệu

### 2.1 Course (danh mục khóa học — dùng chung toàn tổ chức)

Mirror `Branch`: danh mục toàn tổ chức, không thuộc chi nhánh nào, `active`/`inactive`, không xoá
cứng.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | UUID | PK |
| `code` | String, unique | Bất biến sau khi tạo, giống `Branch.code` |
| `name` | String | |
| `description` | String, null | |
| `standard_session_count` | Integer, null | Số buổi chuẩn của khóa — thông tin tham khảo, không ràng buộc nghiệp vụ ở phase này |
| `active` | boolean | Mặc định `true` |

### 2.2 Class (một lần mở cụ thể của một Course)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | UUID | PK |
| `course_id` | FK → `courses.id`, JPA `@ManyToOne` | **Là** relation thật — `Course` và `Class` cùng module `courses`, rule #3 chỉ cấm JOIN/relation **xuyên module**, không cấm trong nội bộ một module. Nhờ vậy `ListClasses` lấy tên khóa học qua chính relation này, không cần batch-lookup riêng. Bất biến sau khi tạo (không đổi khóa học của một lớp đã mở) |
| `code` | String, unique | Vd `TA-GT-K15`. Bất biến sau khi tạo |
| `branch_id` | UUID trần | **Không** JPA relation — `organization` là module khác (rule #3). Chi nhánh nơi dạy, bất biến sau khi tạo — đổi chi nhánh nghĩa là đóng lớp cũ, mở lớp mới (tránh dữ liệu lịch sử điểm danh/học phí ở phân hệ sau bị trôi chi nhánh) |
| `teacher_id` | UUID trần | **Không** JPA relation — `identity` là module khác (rule #3). Account giáo viên chính. **Sửa được** qua PATCH (đổi giáo viên giữa chừng là nghiệp vụ hợp lệ) |
| `max_seats` | int | Sĩ số tối đa — thuộc tính của lớp (phòng/giáo viên chịu được bao nhiêu người), không phải logic tuyển sinh |
| `active` | boolean | Mặc định `true`. Sửa được qua PATCH |

### 2.3 ClassSchedule (lịch học hàng tuần — con của Class)

Khác với `course_id`/`branch_id`/`teacher_id` ở trên, đây **là** JPA relation thật
(`@ManyToOne` tới `Class`) vì `ClassSchedule` không tồn tại độc lập ngoài một `Class` — một bảng con
thuần tuý trong cùng aggregate, không phải tham chiếu xuyên khái niệm nghiệp vụ.

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | UUID | PK |
| `class_id` | FK → `classes.id` | `@ManyToOne`, cascade theo Class |
| `day_of_week` | enum (MON..SUN) | |
| `start_time` | LocalTime | |
| `end_time` | LocalTime | |

Một Class có 1..N dòng `ClassSchedule` (vd lớp học T2-T4-T6 thì 3 dòng cùng giờ, hoặc giờ khác nhau
nếu cần).

## 3. Module backend — `modules.courses`

Mirror cấu trúc `modules.organization`:

```
modules/courses/
├── package-info.java              @ApplicationModule(displayName = "Courses & Classes")
├── CoursesManagement.java         facade — cửa duy nhất cho module khác
├── CourseConstants.java           Resources/Actions nếu cần hằng riêng module (phần lớn dùng chung AccessConstants)
├── CoursesException.java          sealed base
├── CourseNotFoundException.java / ClassNotFoundException.java / CourseCodeAlreadyExistsException.java / ClassCodeAlreadyExistsException.java
├── CoursesEvents.java             CourseCreated/ClassCreated/ClassUpdated — cho audit lắng nghe
├── dto/                           @NamedInterface("dto")
│   ├── CourseResponse, CreateCourseRequest, UpdateCourseRequest
│   ├── ClassResponse, CreateClassRequest, UpdateClassRequest
│   └── WeeklyScheduleSlot (record con: dayOfWeek, startTime, endTime)
├── usecase/
│   ├── CreateCourse, UpdateCourse, ListCourses
│   └── CreateClass, UpdateClass, ListClasses
├── web/
│   └── CourseAdminController, ClassAdminController
└── internal/
    ├── model/        Course, Class, ClassSchedule, DayOfWeek (enum)
    └── repository/    CourseRepository, ClassRepository, ClassScheduleRepository
```

**Phụ thuộc một chiều**: `courses → access` (dùng `AccessConstants.AccessRules` cho `@PreAuthorize`,
giống tiền lệ `organization`/`identity`) và `courses → identity` — nhưng theo đúng cách tránh vòng lặp
đã áp dụng cho `organization ↔ access` (xem `access.BranchCatalog`): nếu `identity` không bao giờ cần
đọc ngược từ `courses` thì **không cần** cổng port/adapter gì cả, `courses` cứ import thẳng
`IdentityManagement` để xác nhận `teacherId` tồn tại — một chiều, không có cạnh ngược, không có nguy
cơ vòng lặp. Việc này cần xác nhận lại lúc code: nếu sau này `identity` cần biết "account này có đang
dạy lớp nào không" thì mới áp dụng port pattern.

Validate ở use case: `CreateClass`/`UpdateClass` gọi `identity.summariesOf(List.of(teacherId))` (đã
có sẵn) để xác nhận account tồn tại — **không** validate account đó có role TEACHER hay không ở phase
này (out of scope — nghiệp vụ "ai được phân làm giáo viên" để lại cho sau, phase này chỉ cần teacherId
trỏ tới một account có thật).

## 4. RBAC

Thêm vào `AccessConstants.Resources`: `COURSE`, `CLASS`. `AccessConstants.AccessRules` thêm 6 hằng,
cùng khuôn `CHECK_PREFIX + Resources.X + CHECK_SEPARATOR + Actions.Y + MINIMUM_SCOPE(ORGANIZATION) + CHECK_SUFFIX`:
`CREATE_COURSE`, `READ_COURSE`, `UPDATE_COURSE`, `CREATE_CLASS`, `READ_CLASS`, `UPDATE_CLASS`.

**Migration mới** (không sửa `V5` đã áp dụng) — `V9__seed_courses_rbac.sql`: insert 6 permission rows
(`COURSE`/`CLASS` × `CREATE`/`READ`/`UPDATE`) và gán vào `permission_group_items` của permission group
"Toàn quyền hệ thống" (`11111111-0000-0000-0000-000000000001`) ở scope `ORGANIZATION` — đúng cách V5
đã làm cho `BRANCH`.

## 5. Migration schema

`V9__create_courses_and_classes.sql` (trước hoặc gộp cùng migration RBAC ở mục 4 — quyết định lúc
viết plan, không ảnh hưởng thiết kế): tạo 3 bảng `courses`, `classes`, `class_schedules` theo mục 2.
Index: `courses.code` unique, `classes.code` unique, `classes.branch_id` (lọc theo chi nhánh sau này
giống `accounts.home_branch_id`), `class_schedules.class_id`.

## 6. API Contract

| Method | Path | Quyền | Ghi chú |
|---|---|---|---|
| `GET` | `/api/courses/courses` | `READ_COURSE` | Phân trang, không filter ở phase này (danh mục toàn tổ chức, danh sách thường ngắn) |
| `POST` | `/api/courses/courses` | `CREATE_COURSE` | Trả UUID vừa tạo, giống `CreateBranch` |
| `PATCH` | `/api/courses/courses/{id}` | `UPDATE_COURSE` | name/description/standardSessionCount/active — `code` bất biến |
| `GET` | `/api/courses/classes` | `READ_CLASS` | Phân trang. Tên khóa học lấy qua relation `Class.course` (cùng module). Tên chi nhánh + tên giáo viên batch qua facade `organization`/`identity`, tránh N+1 — đúng pattern `ListAccounts` |
| `POST` | `/api/courses/classes` | `CREATE_CLASS` | Body gồm courseId, code, branchId, teacherId, maxSeats, schedule (list `WeeklyScheduleSlot`) |
| `PATCH` | `/api/courses/classes/{id}` | `UPDATE_CLASS` | teacherId/maxSeats/schedule/active — courseId/branchId/code bất biến |

Response shape dùng `PageResponse<T>` từ `shared` (đã có sẵn).

## 7. Audit

`CoursesEvents.CourseCreated`, `ClassCreated`, `ClassUpdated` — publish, `audit` module lắng nghe qua
`@ApplicationModuleListener` giống hệt `OrganizationEvents`. Thêm `AuditConstants.Actions.COURSE_CREATE`,
`CLASS_CREATE`, `CLASS_UPDATE` và `AuditConstants.EntityTypes.COURSE`, `CLASS`.

## 8. Frontend

**Entity layer**: `entities/course` (schema, api, query hook — mirror `entities/branch`),
`entities/class` (schema, api, query hook — `ClassSummary` gồm tên khóa học/chi nhánh/giáo viên đã
resolve sẵn từ response, không phải tự ghép ở frontend).

**Module**: `modules/courses/` — 2 trang phẳng riêng, mirror `modules/organization`:
- `pages/courses-page.tsx` — danh sách khóa học, dialog tạo/sửa.
- `pages/classes-page.tsx` — danh sách lớp (cột: mã lớp, khóa học, chi nhánh, giáo viên, sĩ số, trạng
  thái), dialog tạo/sửa (sửa gồm cả lịch học — danh sách dòng ngày/giờ thêm/bớt được trong form).

**Wiring**: `APP_ROUTE.courses`/`APP_ROUTE.classes`, 2 mục nav mới trong section "Điều hành & Nghiệp
vụ" (cạnh "Chi nhánh"), `ACCESS_RULE` thêm `readCourse/createCourse/updateCourse/readClass/createClass/updateClass`.

## 9. Test

`CourseAdminControllerIT`, `ClassAdminControllerIT` (mirror `BranchAdminControllerIT`): tạo khóa học,
mở lớp kèm lịch học, cập nhật giáo viên/lịch/sĩ số, 403 cho role thiếu quyền, 400 khi thiếu
trường bắt buộc hoặc `teacherId` không tồn tại, 404 khi update id không tồn tại.

## 10. Bước tiếp theo

Sau khi spec này được duyệt: viết implementation plan chi tiết qua skill `writing-plans`, triển khai
theo `springboot-modular-scaffold` (backend) và `nextjs-modular-architecture` (frontend) — đúng quy
trình đã áp dụng cho Phân hệ 1 và các tính năng đã thêm sau đó (Branch CRUD, branch context switcher).
