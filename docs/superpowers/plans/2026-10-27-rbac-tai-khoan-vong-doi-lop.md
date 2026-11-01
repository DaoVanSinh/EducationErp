# Plan: Hoàn thiện quản trị tài khoản, phân quyền động và vòng đời lớp học

Ngày: 2026-10-27 · Nhánh: `feature/attendance-assignments-minitasks` (chưa commit, làm tiếp trên nhánh này)

## Goal

Sửa các lỗ hổng logic đã rà soát ở màn Tài khoản, Vai trò và Lớp học, để đúng với spec gốc
(`specs/2026-09-27-education-erp-architecture-design.md` mục 5–6) và đề cương ĐATN, đồng thời giữ cả
3 role Admin, Giáo viên, Học viên chạy đúng luồng.

## Scope

In:
1. Admin đổi vai trò của tài khoản đã có.
2. Khoá, mở khoá tài khoản đang dùng; buộc đăng xuất mọi phiên (spec 6.3 Force-logout).
3. Màn Vai trò hiển thị đúng nhóm quyền của từng vai trò; sửa vai trò; sửa nhóm quyền (spec 5, 6.1).
4. Vai trò tự tạo dùng được thật:
   - kiểm tra theo năng lực (permission) thay vì so mã `TEACHER`/`STUDENT` (rbac.md: "Permissions over Roles");
   - scope `BRANCH` có hiệu lực ở lớp 2 (spec 6.4) cho các tài nguyên vận hành theo chi nhánh.
5. Tạo học viên/giáo viên một bước (tài khoản + hồ sơ); tạo tài khoản có vai trò học viên/giáo viên ở màn
   Tài khoản cũng tự sinh hồ sơ.
6. Vòng đời lớp: ngày khai giảng, ngày kết thúc dự kiến, sĩ số tối thiểu, trạng thái
   Đang tuyển sinh → Đang học → Đã kết thúc / Đã huỷ; tự động khai giảng khi đủ điều kiện.

Out:
- Nhóm người dùng (Group): chỉ đề xuất, chưa code — chờ người dùng chọn hướng.
- Cập nhật file báo cáo ĐATN (docx) — liệt kê mục cần sửa trong tin nhắn cuối.
- Xoá vai trò / xoá nhóm quyền.

## Skills Applied

- springboot-modular-scaffold: gọi chéo module chỉ qua Facade; phụ thuộc ngược dùng interface do module thấp
  khai báo (mẫu `AccountExistenceCheck`); một use case một class, `execute()` public `@Transactional`;
  hằng số trong `<Module>Constants` inner class; evict cache sau commit; lỗi là `AppException` → ProblemDetail.
- rbac.md: kiểm tra năng lực, không so tên role; scope ORGANIZATION ⊇ BRANCH ⊇ PERSONAL.
- nextjs-modular-architecture: logic trong `hooks/use-*-controller.ts`, UI thuần; hằng số từ
  `@/shared/constants`; entity là nguồn schema duy nhất; độ phức tạp < 15, component < 200 dòng.
- reviewing-code-against-skills: `ModularityTests`, `mvn verify`, `npm run typecheck`, `npm run lint`.

## Plugins

- superpowers, ui-ux-pro-max, gitnexus: không có trong phiên làm việc này. Thay bằng rà soát thủ công blast
  radius bằng grep, và bám đúng design token đang dùng (glass panel, Badge tone, GlassButton) cho UI mới.

## Impact Assessment (grep thủ công thay GitNexus)

| Symbol | Nơi dùng | Rủi ro | Giảm thiểu |
|---|---|---|---|
| `CoursesManagement.ClassInfoResponse.active` | enrollment, attendance, assessment, academic | Cao | Bỏ field, thay bằng `status` + helper `acceptsEnrollment()`, sửa mọi nơi gọi, IT |
| `classes.active` (cột DB) | Class entity, ITs | Cao | Migration V31 chuyển sang `status`, map dữ liệu cũ có kiểm soát |
| `AccessRules.*` scope | mọi controller | Trung bình | Chỉ hạ scope các rule vận hành; admin ORG không đổi hành vi |
| `CreateStudentProfileRequest/CreateTeacherProfileRequest` | controller, FE dialog, IT | Trung bình | Đổi hợp đồng API, sửa FE + IT cùng lúc |
| `RbacCatalogResponse` | FE catalog schema, nhiều dialog | Trung bình | Giữ `id`,`name` trong mỗi phần tử, chỉ thêm field |
| `TeacherAssignmentGuard`, `ClassGradeAccessGuard`, attendance/assessment guard | nhiều use case | Trung bình | Thêm nhánh BRANCH, giữ nguyên nhánh ORG/teacher |
| `JwtTokenService`, `CookieAuthenticationFilter`, `RefreshSession` | mọi request | Cao | Chỉ thêm kiểm tra; token cũ không có `iat` vẫn hợp lệ nếu không bị buộc đăng xuất |

## Architecture Impact

Backend:
- `access`: `DataScope`, `AccessManagement.grants/dataScopeOf/changeRole`, SPI `RoleAssignmentParticipant`,
  SPI `AccountBranchLookup`, use case `ChangeAccountRole`, `UpdateRole`, `UpdatePermissionGroup`, catalog chi tiết,
  sự kiện `AccountRoleChanged`, `PermissionsChanged`, `RoleUpdated`, `PermissionGroupUpdated`.
- `identity`: `DisableAccount`, `EnableAccount`, `SignOutEverywhere`, kiểm tra trạng thái ở filter/refresh,
  `iat` + mốc `notBefore`, `IdentityManagement.inviteAccount`, cài `AccountBranchLookup`.
- `students`, `teachers`: tạo một bước, participant đảm bảo/vô hiệu hồ sơ, lọc chi nhánh (students).
- `courses`: vòng đời lớp, SPI `ClassRosterPort`, scheduler tự khai giảng, guard năng lực giáo viên,
  participant chặn đổi vai trò giáo viên còn lớp mở, lọc chi nhánh.
- `enrollment`: cài `ClassRosterPort`, chặn ghi danh theo trạng thái lớp, lọc chi nhánh, participant chặn
  đổi vai trò học viên còn ghi danh.
- `attendance`, `assessment`, `academic`: kiểm trạng thái lớp, guard BRANCH, tiến trình tính từ ngày khai giảng.
- `billing`, `dashboard`: lọc/kiểm chi nhánh.
- `audit`, `integrations.notification`: lắng nghe sự kiện mới.

Frontend: `modules/rbac` (đổi vai trò, khoá/mở khoá/buộc đăng xuất, màn vai trò chi tiết + sửa),
`modules/students`, `modules/teachers` (tạo một bước), `modules/courses` (vòng đời lớp), `entities/class`,
`entities/rbac-catalog`, `entities/account`, `shared/constants/permissions` (scope BRANCH),
`modules/teaching`, `modules/learning` (hiển thị trạng thái lớp).

## Steps

1. Backend access: DataScope, SPI, đổi vai trò, sửa vai trò/nhóm quyền, catalog chi tiết, sự kiện, hằng số.
2. Backend identity: khoá/mở khoá/buộc đăng xuất, kiểm trạng thái, `iat`, `inviteAccount`, lookup chi nhánh.
3. Backend students/teachers: tạo một bước, participant hồ sơ, guard năng lực.
4. Migration V31 + backend courses: vòng đời lớp, SPI roster, scheduler, participant giáo viên.
5. Backend enrollment/attendance/assessment/academic: theo trạng thái lớp, tiến trình, guard BRANCH.
6. Backend billing/dashboard + hạ scope các AccessRules vận hành.
7. Audit + notification listener.
8. Sửa/viết test backend; `mvn verify` xanh (kể cả `ModularityTests`).
9. Frontend entities/constants.
10. Frontend tài khoản + vai trò.
11. Frontend học viên/giáo viên + lớp học + cổng giảng dạy/học tập.
12. `npm run typecheck`, `npm run lint`, build; chạy thử bản build với backend Docker khi người dùng cấp tài khoản.

## Luật nghiệp vụ chốt

Tài khoản:
- Đổi vai trò, khoá, mở khoá, buộc đăng xuất cần `ACCOUNT:UPDATE` scope ORGANIZATION; không thao tác lên chính mình.
- Không được bỏ năng lực giáo viên khi người đó còn dạy lớp Đang tuyển sinh/Đang học; không được bỏ năng lực
  học viên khi còn ghi danh đang học.
- Mất năng lực → hồ sơ tương ứng chuyển "ngừng hoạt động"; có năng lực mà chưa có hồ sơ → tự tạo hồ sơ trống.
- Khoá: trạng thái DISABLED, mọi phiên bị thu hồi ngay; mở khoá chỉ cho tài khoản đã từng kích hoạt
  (chưa kích hoạt thì dùng "Gửi lại lời mời").

Vai trò:
- Vai trò `ADMIN` và nhóm quyền "Toàn quyền hệ thống" chỉ xem, không sửa — chống tự khoá hệ thống.
- Sửa vai trò/nhóm quyền xoá cache quyền của mọi tài khoản bị ảnh hưởng sau commit và đẩy SSE.

Phạm vi BRANCH (lớp 2):
- Hạ tối thiểu xuống BRANCH, có lọc theo chi nhánh chủ quản: ACCOUNT:READ, CLASS:*, STUDENT:*, ENROLLMENT:*,
  INVOICE:* (trừ bậc giảm giá combo), DASHBOARD:READ; GRADE/ATTENDANCE/ASSIGNMENT theo chi nhánh của lớp.
- Hạ xuống BRANCH nhưng không lọc (danh mục toàn tổ chức): COURSE:READ, TEACHER:READ.
- Giữ ORGANIZATION: ACCOUNT:CREATE/UPDATE, ROLE, PERMISSION_GROUP, BRANCH, COURSE:CREATE/UPDATE,
  TEACHER:CREATE/UPDATE, PAYROLL, bậc giảm giá combo.

Lớp học:
- Mở lớp: ngày khai giảng (không trước hôm nay), ngày kết thúc dự kiến (tuỳ chọn), sĩ số tối thiểu 1..tối đa.
- Ghi danh khi lớp Đang tuyển sinh hoặc Đang học, chưa đầy.
- Khai giảng (thủ công hoặc tự động mỗi ngày): Đang tuyển sinh, đã tới ngày khai giảng, có lịch học,
  số ghi danh đang học ≥ sĩ số tối thiểu.
  - Sửa 2026-11-01: job tự khai giảng chạy mỗi giờ (phút 05) và chạy bù một lượt khi app khởi động. Chạy một lần
    lúc 00:30 thì lớp đủ điều kiện kẹt ở Đang tuyển sinh tới hết ngày hôm sau nếu app đang deploy đúng giờ đó.
  - Lớp quá ngày khai giảng mà chưa đủ điều kiện vẫn giữ Đang tuyển sinh (không tự huỷ); admin và giáo viên thấy
    cảnh báo, học viên thấy "chưa khai giảng, trung tâm sẽ báo ngày mới" thay vì "dự kiến khai giảng" ngày đã qua.
- Kết thúc: Đang học → Đã kết thúc, ghi danh đang học → hoàn tất. Huỷ: Đang tuyển sinh → Đã huỷ,
  ghi danh đang học → rút.
- Điểm danh chỉ khi lớp Đang học, ngày điểm danh trong [ngày khai giảng, hôm nay].
- Nhập điểm khi lớp Đang học hoặc Đã kết thúc. Giao bài/mini-task mới khi lớp chưa kết thúc/huỷ.
- Tiến trình tính từ max(ngày ghi danh, lúc khai giảng) tới min(bây giờ, lúc rút, lúc kết thúc lớp).
- Sửa lớp: không cho khi lớp đã kết thúc/huỷ; sĩ số tối đa ≥ số đang học; ngày khai giảng và sĩ số tối thiểu
  chỉ sửa khi Đang tuyển sinh.
- Dữ liệu cũ (V31): lớp `active=false` → Đã kết thúc; lớp đang hoạt động có ghi danh → Đang học
  (khai giảng = ngày ghi danh sớm nhất); còn lại → Đang tuyển sinh (khai giảng = hôm nay).

## Test / Verify Checklist

- [x] `mvn verify` (unit + IT + ModularityTests) xanh.
- [x] IT mới (AccountLifecycleIT, RoleManagementIT, ClassLifecycleIT, BranchScopeIT): đổi vai trò (chặn khi còn lớp/ghi danh, tự tạo hồ sơ), khoá → 401 ngay, buộc đăng xuất,
      sửa vai trò → quyền đổi ngay, BRANCH scope lọc đúng, vòng đời lớp, tạo học viên một bước.
- [x] `npm run typecheck`, `npm run lint`, `npm run build` xanh.
- [ ] Chạy thử với tài khoản thật của 3 role (cần người dùng cung cấp).

## Risks

- Migration V31 chạy trên DB dùng chung khi build lại image — map dữ liệu cũ phải an toàn, có thể chạy lại không lỗi.
- Hạ scope sai rule có thể mở lộ dữ liệu: admin luôn ORG nên không ảnh hưởng; chỉ vai trò tự tạo dùng BRANCH.

## Ghi chú triển khai

- Chạy IT trên máy này cần JDK 21 và `-DargLine="-Dapi.version=1.44 -Duser.timezone=Asia/Ho_Chi_Minh"` (Docker Engine 29 + múi giờ Windows).
- Thu hồi phiên dùng claim `ims` (mili-giây) thay vì `iat` (giây) để mở khoá xong đăng nhập lại ngay không bị chặn nhầm.
- Fixture test `ClassFixtures.inProgress` khai giảng từ 30 ngày trước, tương đương lớp "active" trước V31.
