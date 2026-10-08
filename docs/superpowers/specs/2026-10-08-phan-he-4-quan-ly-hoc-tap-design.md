# Phân hệ 4 — Quản lý Học tập (Điểm & Tiến trình học tập)

## 1. Bối cảnh & Phạm vi

Theo roadmap (`docs/superpowers/specs/2026-09-27-education-erp-architecture-design.md` mục 8), Phân hệ
4 ban đầu dự kiến gồm điểm danh, bài tập/mini-test, nhập điểm cuối khóa. Qua brainstorm thực tế với
người dùng (2026-10-08), phạm vi đã thu hẹp lại đúng với nhu cầu vận hành hiện tại của trung tâm:

- **KHÔNG điểm danh** — trung tâm không còn cần tính năng này.
- **KHÔNG bài tập/mini-test** — hệ thống này không phải nơi quản lý bài tập.
- **CHỈ CÒN**: quản lý **điểm** (nhiều cột theo trọng số, do giáo viên nhập) và **tiến trình học
  tập** (tự động tính từ lịch học, không ai phải nhập).

Đây là ERP cho trung tâm dạy (tutoring center), không phải trường/đại học — giữ nguyên ràng buộc đã áp
dụng xuyên suốt dự án.

**Ngoài phạm vi, nêu rõ để không bỏ sót ngầm**: học viên KHÔNG tự xem được điểm/tiến trình của mình qua
hệ thống ở giai đoạn này — brainstorm chỉ xác nhận nhu cầu "giáo viên nhập điểm, trung tâm xem/xuất
báo cáo", chưa có yêu cầu cổng tự xem cho học viên. Không suy đoán thêm; để làm follow-up nếu có nhu
cầu sau.

## 2. Quyết định đã chốt với người dùng

1. **Tiến trình học tập**: tự tính từ lịch lớp (đã có từ Phân hệ 2) — "đã học bao nhiêu buổi / tổng số
   buổi chuẩn của khóa", không cần giáo viên nhập gì.
2. **Cấu trúc điểm**: nhiều cột điểm theo trọng số (ví dụ Giữa kỳ 40% + Cuối kỳ 60%), **cấu hình theo
   Course** (không phải theo Class) — mọi lớp cùng khóa dùng chung một cấu trúc điểm.
3. **Tổng trọng số các cột điểm của một Course bắt buộc = 100%** — chặn lưu nếu khác.
4. **Quyền nhập/sửa điểm**: chỉ giáo viên được phân công dạy đúng lớp đó (+ admin toàn quyền).
5. **Xuất Excel**: hai báo cáo theo từng lớp — (a) bảng điểm (học viên × cột điểm × điểm tổng kết),
   (b) bảng tiến trình học tập (học viên × % hoàn thành).

## 3. Kiến trúc module

- **`modules.courses`** (đã có) — thêm `CourseGradeColumn` làm entity con của `Course` (mirror
  `ClassSchedule` của `Class`): cấu hình cột điểm + trọng số là một thuộc tính của khóa học, đúng vị
  trí logic, không phải một module riêng.
- **`modules.academic`** (module mới) — sở hữu điểm thật (`GradeEntry`) và việc TÍNH tiến trình học
  tập. Đây là nơi duy nhất biết "một học viên trong một lớp đang ở đâu" về mặt học thuật — tách khỏi
  `modules.courses` (danh mục), `modules.enrollment` (ai học lớp nào), `modules.billing` (ai nợ bao
  nhiêu). Tránh God Module: không nhét điểm vào `modules.courses` hay `modules.enrollment`.
- **`modules.enrollment`** — bổ sung MỘT method đọc vào facade: `listActiveEnrollmentsByClass(classId)`
  (để `modules.academic` lấy danh sách học viên của một lớp dựng bảng điểm/tiến trình). Không có
  method ghi nào được thêm.

## 4. `modules.courses` — cấu hình cột điểm

### `CourseGradeColumn` (con của `Course`, cùng module)

| Field | Kiểu | Ghi chú |
|---|---|---|
| `id` | UUID | PK |
| `course` | `@ManyToOne` | cùng module, quan hệ JPA thật (mirror `ClassSchedule.parentClass`) |
| `name` | String | "Giữa kỳ", "Cuối kỳ"... |
| `weightPercent` | NUMERIC(5,2) | % trong điểm tổng kết |
| `maxScore` | NUMERIC(5,2) | thang điểm của cột này (ví dụ 10) |
| `displayOrder` | int | thứ tự hiển thị |

`Course.gradeColumns` là `@OneToMany(cascade=ALL, orphanRemoval=true)`, sửa = thay TOÀN BỘ danh sách
(mirror cách `Class.schedule` đã làm ở Phân hệ 2 — PATCH thay hết, không vá từng dòng). Lưu phải kiểm
`Σ weightPercent == 100.00` (sai lệch do làm tròn chấp nhận trong khoảng ±0.01) — khác thì chặn bằng
`CourseGradeColumnWeightsInvalidException`.

### Thay đổi facade `CoursesManagement`

- `ClassInfoResponse` **thêm ba field**: `teacherId` (đã có sẵn ở cột `classes.teacher_id`, chưa lộ ra
  facade — dùng để kiểm "giáo viên này có được phân công dạy lớp này không" ở mục 6);
  `schedule: List<ClassScheduleSlot(dayOfWeek, startTime, endTime)>` (đã có sẵn ở `Class.schedule`,
  chưa lộ ra facade — cần để đếm số buổi đã học ở mục 5); `standardSessionCount` (đã có sẵn ở
  `Course.standardSessionCount`, hiện chỉ `getCourseTuition` đọc được qua `CourseTuitionResponse`
  nhưng field đó không có — gộp luôn vào `ClassInfoResponse` để `modules.academic` chỉ cần MỘT lần gọi
  facade (`getClassInfo`) là đủ dữ liệu tính tiến trình, không cần gọi thêm `getCourseTuition` cho mục
  đích không liên quan tới học phí).
- Thêm `getGradeColumns(courseId): List<GradeColumnResponse>` (read-only).

## 5. Tiến trình học tập — công thức tính (không lưu, tính lúc đọc)

Với một `Enrollment`:

```
mốc_bắt_đầu = Enrollment.enrolledAt
mốc_kết_thúc = Enrollment.withdrawnAt nếu status == WITHDRAWN, ngược lại là thời điểm hiện tại
số_buổi_đã_học = đếm số lần lịch tuần của Class (ClassSchedule: dayOfWeek + startTime) rơi vào
                 khoảng [mốc_bắt_đầu, mốc_kết_thúc]
tiến_trình_% = round(min(số_buổi_đã_học, Course.standardSessionCount) / Course.standardSessionCount * 100)
```

- Rút khỏi lớp (`WITHDRAWN`) ĐÓNG BĂNG tiến trình tại `withdrawnAt` — không tiếp tục tăng sau khi đã
  rút.
- `COMPLETED` không cần mốc riêng: một khi đủ `standardSessionCount` buổi, công thức tự nhiên đạt
  100% và dừng ở đó (có trần `min(...)`).
- `Course.standardSessionCount == null` → không tính được, trả `null` kèm lý do "chưa cấu hình tổng số
  buổi chuẩn" — không suy đoán.
- Phép đếm "số buổi đã học" là một hàm THUẦN (không I/O), nhận vào danh sách `ClassSchedule` + hai mốc
  thời gian, trả về số nguyên — test được độc lập, không cần DB.

## 6. `modules.academic` — Điểm

### Data model

**`GradeEntry`**: `id`, `enrollmentId` (UUID trần), `courseGradeColumnId` (UUID trần), `score`
(NUMERIC(5,2)), `enteredByAccountId`, `enteredAt`, `updatedAt`. UNIQUE
`(enrollment_id, course_grade_column_id)` — một điểm cho một cột của một lượt ghi danh.

### Quy tắc điểm tổng kết

Điểm tổng kết của một `Enrollment` = `Σ (score / maxScore * weightPercent)` qua mọi cột **CHỈ KHI** đủ
điểm ở MỌI cột đã cấu hình cho khóa đó. Thiếu một cột → điểm tổng kết là `null` ("chưa đủ điểm"),
**không** tính trung bình trên phần đã có (tránh một con số gây hiểu lầm).

### Quyền nhập/sửa điểm (Resource mới `GRADE`, dùng lại `Actions.CREATE/READ/UPDATE`)

Không có tiền lệ "chỉ người được phân công mới thao tác được" trong hệ thống này — RBAC hiện tại chỉ
có `@PreAuthorize` thô (có quyền ở scope X hay không), không biết "X của ai". Cách làm:

1. `@PreAuthorize` gác cửa bình thường: `GRADE:UPDATE` (mặc định tối thiểu `PERSONAL` — ai có quyền ở
   bất kỳ scope nào cũng qua được bước này).
2. Trong usecase, gọi `accessManagement.effectivePermissions(actorAccountId)` (đã có sẵn ở
   `AccessManagement`, không cần method mới) để lấy scope THẬT của caller cho `GRADE:UPDATE`.
3. Nếu scope là `ORGANIZATION` → bỏ qua bước kiểm tiếp theo (admin toàn quyền).
4. Nếu scope là `PERSONAL`/`BRANCH` → gọi `coursesManagement.getClassInfo(classId)` (đã thêm
   `teacherId` ở mục 4), so `teacherId == actorAccountId` — khác thì chặn bằng
   `NotAssignedToClassException` (403).

Seed RBAC: `TEACHER` được cấp `GRADE:READ:PERSONAL` + `GRADE:UPDATE:PERSONAL`; nhóm "Toàn quyền hệ
thống" (ADMIN) được cấp `GRADE:READ:ORGANIZATION` + `GRADE:UPDATE:ORGANIZATION`, mirror đúng cách V19
đã seed `ENROLLMENT`/`INVOICE` cho nhóm admin.

### Usecase

- `RecordGrade(actorAccountId, actorBranchId, enrollmentId, courseGradeColumnId, score)` — kiểm quyền
  theo mục trên, kiểm `score` trong `[0, maxScore]` của đúng cột, kiểm cột thuộc đúng khóa của ghi danh
  đó, upsert `GradeEntry`, publish `AcademicEvents.GradeRecorded`.
- `GetClassGradebook(actorAccountId, classId)` — kiểm quyền `GRADE:READ` tương tự, trả danh sách học
  viên đang học (`EnrollmentManagement.listActiveEnrollmentsByClass`) × điểm từng cột × điểm tổng kết.
- `GetClassProgress(actorAccountId, classId)` — tương tự, trả % tiến trình từng học viên (mục 5).
- `ExportClassGradebook`/`ExportClassProgress` — xuất file Excel từ đúng dữ liệu hai usecase trên,
  dùng chung một tầng tính toán (không lệch số giữa xem trên web và tải Excel).

## 7. Xuất Excel

**Thêm dependency mới**: Apache POI (`org.apache.poi:poi-ooxml`) — dự án hiện chưa có khả năng sinh
file Excel nào. Đây là thư viện phổ biến nhất cho `.xlsx` trên JVM, không có thay thế nhẹ hơn đáng kể
trong hệ sinh thái Spring Boot.

- `GET /api/academic/classes/{classId}/gradebook/export` → file `.xlsx` bảng điểm.
- `GET /api/academic/classes/{classId}/progress/export` → file `.xlsx` bảng tiến trình.
- Cùng kiểm quyền `GRADE:READ` (và cùng ràng buộc "chỉ giáo viên được phân công") như hai usecase đọc
  ở trên — không có đường tắt tải Excel bỏ qua kiểm tra quyền.

## 8. Migration

- Bảng mới `course_grade_columns` (con của `courses`).
- Bảng mới `grade_entries` (UNIQUE `(enrollment_id, course_grade_column_id)`).
- Seed RBAC: `permissions` thêm `(GRADE, CREATE)`, `(GRADE, READ)`, `(GRADE, UPDATE)`; gán vào nhóm
  admin ở `ORGANIZATION` (mirror V19); gán riêng cho role `TEACHER` ở `PERSONAL` (lần đầu tiên một role
  khác ADMIN được seed quyền cho một resource hoàn toàn mới — cần đọc kỹ cách
  `permission_group_items`/role-permission hiện tại ánh xạ TEACHER's permission group trước khi viết
  migration, không suy đoán cấu trúc bảng).

## 9. `modules.audit`

Thêm `AcademicEvents.GradeRecorded(enrollmentId, courseGradeColumnId, actorAccountId, actorBranchId)`,
`AuditConstants.Actions.GRADE_RECORD`, `AuditConstants.EntityTypes.GRADE_ENTRY`, một listener method
mirror các event billing/enrollment đã có.

## 10. Frontend

- `entities/academic` (mới): schema `gradeColumnSchema`, `gradebookSchema`, `classProgressSchema`.
- `modules/courses`: màn hình sửa Course thêm tab/section "Cột điểm" (mirror UI sửa lịch học của
  Class) — thêm/sửa/xoá dòng cột điểm, hiển thị tổng trọng số real-time, chặn submit nếu ≠ 100%.
- `modules/academic` (mới): trang "Sổ điểm" theo lớp (chỉ hiện với giáo viên được phân công hoặc
  admin — ẩn nút sửa nếu không đủ quyền, **nhưng vẫn phải dựa vào lỗi 403 thật từ backend làm nguồn sự
  thật**, không tự suy luận "tôi là giáo viên lớp này" chỉ từ dữ liệu phía client), trang "Tiến trình
  học tập" theo lớp, nút tải Excel ở cả hai.
- Route mới `/admin/academic/classes/:classId/gradebook`, `/admin/academic/classes/:classId/progress`
  — vào từ trang chi tiết `Class` đã có (không thêm mục menu cấp 1 riêng, mirror cách `invoiceDetail`
  không có mục menu).

## 11. Testing & Review Focus

1. **Tính tiến trình không lệch khi học viên vào giữa chừng** — hai học viên cùng lớp, `enrolledAt`
   khác nhau, phải ra % khác nhau dù cùng một `Class`/`Course`.
2. **Rút khỏi lớp đóng băng tiến trình** — tiến trình tại thời điểm rút không được tăng thêm dù thời
   gian trôi qua sau đó.
3. **Điểm tổng kết `null` khi thiếu một cột**, không tính trung bình trên phần đã có.
4. **Giáo viên KHÔNG được phân công dạy lớp đó bị chặn** `NotAssignedToClassException` dù có quyền
   `GRADE:UPDATE:PERSONAL` nói chung — kiểm cả chiều ngược lại: giáo viên ĐÚNG lớp thao tác được.
5. **Admin (scope ORGANIZATION) bỏ qua hoàn toàn kiểm tra phân công**, thao tác được trên bất kỳ lớp
   nào.
6. **Tổng trọng số cột điểm ≠ 100%** bị chặn lưu, kể cả khi sửa (không chỉ lúc tạo mới).
7. **Xuất Excel dùng chung tầng tính toán với API đọc** — số trên file Excel phải khớp số trả về từ
   `GetClassGradebook`/`GetClassProgress`, không tính lại theo đường riêng.
8. **`Course.standardSessionCount` chưa cấu hình** → tiến trình trả về rõ ràng "chưa cấu hình", không
   chia cho 0 hay trả một con số sai.

## 12. Tổng kết quyết định đã chốt với người dùng

- Bỏ hẳn điểm danh và bài tập/mini-test khỏi Phân hệ 4 — chỉ còn điểm + tiến trình học tập.
- Tiến trình tự tính từ lịch lớp, không ai nhập.
- Điểm nhiều cột theo trọng số, cấu hình theo Course, tổng trọng số bắt buộc = 100%.
- Chỉ giáo viên được phân công dạy đúng lớp (+ admin) được nhập/sửa điểm — cơ chế kiểm qua
  `AccessManagement.effectivePermissions` + `CoursesManagement.getClassInfo`, không phát minh một loại
  scope RBAC mới.
- Xuất Excel hai báo cáo: bảng điểm và bảng tiến trình, theo từng lớp.
- Quyết định của tôi (không hỏi lại, nêu rõ ở đây): dùng Apache POI cho xuất Excel (chưa có thư viện
  Excel nào trong dự án); đặt cấu hình cột điểm trong `modules.courses` (con của `Course`) thay vì một
  module riêng, vì nó là thuộc tính của danh mục khóa học, mirror đúng cách `tuitionFee`/`schedule` đã
  được đặt.
