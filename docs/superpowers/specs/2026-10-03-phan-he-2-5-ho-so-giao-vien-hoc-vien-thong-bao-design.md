# Phân hệ 2.5 — Hồ sơ Giáo viên & Học viên + Thông báo: Spec thiết kế

## 1. Bối cảnh & phạm vi

Phân hệ 1 (Core) và Phân hệ 2 (Khóa học & Lớp học) đã xây xong, đang chạy trên `develop`. Theo
`docs/superpowers/specs/2026-09-27-education-erp-architecture-design.md` mục 2 (đã cập nhật
2026-10-03), Phân hệ 2.5 chèn giữa Phân hệ 2 và Phân hệ 3, làm nền cho cả hai: Phân hệ 3 (Tuyển
sinh & Học phí) cần `modules.students` đã tồn tại để ghi danh; Phân hệ 4 (Quản lý Học tập) cũng cần
hồ sơ Teacher/Student đầy đủ hơn `Account` trần của Phân hệ 1.

**Lưu ý bối cảnh nghiệp vụ**: đây là ERP cho **trung tâm dạy** (tutoring/teaching center), không
phải trường học hay đại học — tránh mọi khái niệm kiểu "khoa", "niên khóa", "mã số sinh viên
trường", học kỳ cứng nhắc. Model `Course`/`Class` của Phân hệ 2 (danh mục + lớp mở theo lịch tuần
lặp lại, không học kỳ/năm học) đã đúng tinh thần này và là chuẩn tham chiếu cho phân hệ này.

**Trong phạm vi phân hệ này**:
- Hồ sơ nghiệp vụ cơ bản của Giáo viên (`modules.teachers`) và Học viên (`modules.students`) — tách
  khỏi `Account` chung của Phân hệ 1, CRUD đầy đủ, chỉ dành Admin quản trị (scope `ORGANIZATION`).
- Luồng admin tạo tài khoản mới — **chưa tồn tại ở Phân hệ 1** (ngoài tài khoản Admin seed sẵn,
  không có cách nào tạo Account mới) — kèm mời qua email, bắt đổi mật khẩu ở lần đăng nhập đầu, gửi
  lại/thu hồi lời mời.
- `integrations.notification` — module mới, kênh SSE dùng chung cho đổi quyền tức thì và sự kiện
  nghiệp vụ các phân hệ sau.
- Mailer mời tài khoản mới trong `modules.identity`, dùng lại `integrations.mail` đã có sẵn từ
  Phân hệ 1 (không tạo module mail mới).

**Ngoài phạm vi** (để dành phân hệ khác, không động tới ở đây):
- Lương, phụ cấp, hợp đồng lao động, thử việc/chính thức, đối soát kế toán của giáo viên — tách
  riêng thành Phân hệ 2.6 (Nhân sự & Tiền lương Giáo viên), cần nghiên cứu nghiệp vụ kế toán/nhân sự
  kỹ hơn trước khi viết spec. Lý do tách: đây là nghiệp vụ Nhân sự-Lương đầy đủ, có ràng buộc pháp lý
  (Bộ luật Lao động 2019 Điều 26: lương thử việc ≥ 85% lương chính thức VÀ không thấp hơn lương tối
  thiểu vùng), không phải vài field phụ trong hồ sơ.
- Thông tin phụ huynh/người giám hộ trong hồ sơ học viên — đã xác nhận không cần ở phase này.
- Ghi danh học viên vào lớp cụ thể, học phí — Phân hệ 3.
- Mọi trigger SSE nghiệp vụ ngoài "đổi quyền" (vd lớp đổi giáo viên, học phí đến hạn) — hạ tầng sẵn
  sàng mở rộng theo đúng khuôn `@ApplicationModuleListener`, nhưng phân hệ sau tự thêm listener của
  mình khi tới lượt; không dựng trước ở đây vì một số sự kiện nguồn (vd gán role) hiện chưa được
  phát ra, thêm vào sẽ đụng module đã ship ngoài phạm vi phân hệ này.
- Tự đăng ký tài khoản (self-registration) — chỉ Admin tạo tài khoản.

## 2. Mô hình dữ liệu

### 2.1 Quan hệ với `Account` (Phân hệ 1)

`TeacherProfile`/`StudentProfile` quan hệ **1-1 với `Account` qua `accountId` (UUID trần, không
`@ManyToOne`)** — đúng rule #3 (không JPA quan hệ xuyên module), giống cách `modules.courses` tham
chiếu `branchId`/`teacherId`. Tạo theo đúng 2 bước tách rời:

1. **Bước 1 — tạo Account** (`modules.identity`, mới): admin nhập email, họ tên, chi nhánh, **role**
   (đúng 1 Role/Account theo mô hình RBAC — mục 5 spec Core) → kích hoạt luồng mời qua email (mục
   2.3). Account tạo ra ở trạng thái `ACTIVE`, chưa có profile nghiệp vụ nào gắn vào.
2. **Bước 2 — tạo hồ sơ**: admin chọn 1 `accountId` đã có role `TEACHER`/`STUDENT` và **chưa có**
   `TeacherProfile`/`StudentProfile` tương ứng → tạo hồ sơ gắn vào accountId đó.

Hai bước độc lập có chủ đích: một Account có thể tồn tại mà chưa có hồ sơ nghiệp vụ (vd admin tạo
Account trước, điền hồ sơ sau khi có đủ thông tin), và việc tạo Account là năng lực chung của
`modules.identity`, không riêng gì Teacher/Student.

### 2.2 `TeacherProfile`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | UUID | PK |
| `account_id` | UUID, unique, not null | FK logic tới `accounts.id` (DB-level FK, không JPA relation) |
| `subjects` | `List<String>` (bảng con `teacher_profile_subjects` hoặc cột mảng Postgres `text[]`) | Môn/chuyên môn dạy — text tự do, không taxonomy cố định |
| `phone` | String, null | |
| `bio` | String, null | Mô tả ngắn, optional |
| `active` | boolean | Mặc định `true`, không xoá cứng — giống `Course`/`Branch` |

### 2.3 `StudentProfile`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | UUID | PK |
| `account_id` | UUID, unique, not null | FK logic tới `accounts.id` |
| `date_of_birth` | LocalDate, null | |
| `phone` | String, null | |
| `source_channel` | String, null | Biết đến trung tâm qua đâu — text tự do (vd "Giới thiệu", "Mạng xã hội", "Khác") |
| `active` | boolean | Mặc định `true` |

Không có field phụ huynh/người giám hộ — đã xác nhận ngoài phạm vi.

## 3. Luồng tạo Account + mời qua email

### 3.1 Tạo Account (admin)

Use case mới `CreateAccount` (`modules.identity.usecase`):
1. Nhận `email`, `fullName`, `homeBranchId` (null = toàn tổ chức), `roleCode`.
2. Sinh mật khẩu ngẫu nhiên (vd 12 ký tự, đủ mạnh), hash lưu vào `Account.passwordHash` ngay khi
   tạo — **không** để trạng thái "chưa có mật khẩu nào dùng được".
3. Lưu `Account` mới (`status = ACTIVE`, `lastLogin = null` — field mới, mục 3.3).
4. Gọi `access.assignRole(accountId, roleCode)` (đã có sẵn).
5. Đánh dấu lời mời còn hiệu lực trong Redis: key
   `CacheKeyBuilder.key(IdentityConstants.CacheNamespaces.ACCOUNT_INVITE, accountId)` → giá trị là
   thời điểm tạo (ISO timestamp, chỉ để debug/hiển thị, không có ý nghĩa nghiệp vụ gì khác), TTL 7
   ngày (mục 3.2 lý do dùng `accountId` làm key).
6. Gọi `AccountInviteMailer.sendInvite(email, fullName, mậtKhẩuNgẫuNhiên)` — gửi đồng bộ trong cùng
   use case, đúng pattern `ForgotPassword` hiện tại (không có cơ chế async/retry nào đang tồn tại
   trong repo — giữ nhất quán). Gửi mail lỗi → cả transaction rollback, Account không được tạo.

### 3.2 Vì sao Redis key theo `accountId`, không phải một token mời riêng

Khác với `ForgotPassword` (nơi token là bí mật duy nhất chứng minh quyền sở hữu email, bắt buộc nằm
trong link), lời mời tài khoản dùng chính **mật khẩu ngẫu nhiên** làm bí mật xác thực (qua API
`Login` có sẵn, không qua link riêng — xem mục 3.4). Vậy Redis ở đây không cần một token bí mật nào
cả, chỉ cần trả lời đúng một câu hỏi: "lời mời của account này còn hiệu lực không?" — nên khoá thẳng
bằng `accountId`, không có khái niệm nhiều lời mời song song cho cùng một Account tại một thời điểm.
"Gửi lại mời"/"thu hồi mời" vì vậy thao tác trực tiếp bằng accountId đã biết, không cần tra ngược gì
thêm.

### 3.3 Field mới: `Account.lastLogin`

Thêm cột `last_login` (timestamp, null) vào bảng `accounts` (migration mới trong `modules.identity`,
không sửa `V4__create_accounts.sql` đã áp dụng). `Login` use case (đã có) cập nhật `lastLogin = now()`
**chỉ sau khi** luồng đổi mật khẩu bắt buộc (nếu có) hoàn tất — tức với một Account mới tạo,
`lastLogin` vẫn `null` cho tới khi họ đổi mật khẩu xong ở mục 3.4 bước 3.

### 3.4 Lần đăng nhập đầu tiên (bắt đổi mật khẩu)

1. User gọi API `Login` (đã có) bằng email + mật khẩu ngẫu nhiên nhận qua mail.
2. `Login` kiểm tra: nếu `account.lastLogin == null`:
   - Kiểm tra Redis key mời (mục 3.2) còn tồn tại không.
     - **Còn hiệu lực** → xác thực mật khẩu đúng như bình thường, nhưng trả về một response đặc biệt
       (vd `{"requiresPasswordChange": true, "accountId": ...}`, **không** set cookie session đầy
       đủ) thay vì `SessionResponse` bình thường.
     - **Hết hiệu lực** (quá 7 ngày, Redis key đã tự xoá) → từ chối đăng nhập dù mật khẩu đúng, trả
       lỗi rõ ràng (vd `INVITE_EXPIRED`) yêu cầu liên hệ admin gửi lại mời. **Chặn hẳn**, không cho
       qua bằng mật khẩu cũ (đã chốt).
3. User gọi use case **mới** `CompletePasswordInvite` (không tái dùng `ChangePassword` — use case đó
   bắt buộc xác nhận mật khẩu cũ qua form "đổi mật khẩu khi đã đăng nhập", khác bản chất với luồng
   một-lần này): cập nhật `passwordHash` mới, set `lastLogin = now()`, xoá Redis invite key. Trả về
   `SessionResponse` đầy đủ (cấp cookie session) — từ đây về sau login bình thường, không bắt đổi mật
   khẩu nữa vì `lastLogin` đã khác `null`.

### 3.5 Gửi lại mời / Thu hồi mời (admin thao tác trên Account đã tạo, chưa từng login)

- **Gửi lại mời** (`ResendAccountInvite` use case): chỉ áp dụng khi `lastLogin == null`. Sinh mật
  khẩu ngẫu nhiên MỚI (ghi đè `passwordHash` cũ), sinh lại Redis invite key với TTL 7 ngày mới (ghi
  đè timestamp cũ), gửi lại mail. Mật khẩu ngẫu nhiên cũ (nếu còn nhớ) không còn dùng được.
- **Thu hồi mời** (`RevokeAccountInvite` use case): chỉ áp dụng khi `lastLogin == null`. Xoá Redis
  invite key, chuyển `Account.status = DISABLED` (đã chốt — rõ ràng hơn "chỉ xoá token mà Account
  vẫn ACTIVE không dùng được"). Admin muốn mời lại thì gọi lại **Gửi lại mời**, use case này tự bật
  `status = ACTIVE` trước khi sinh mời mới.
- Cả hai đều yêu cầu permission `UPDATE_ACCOUNT` đã có sẵn (cùng bản chất: sửa trạng thái Account),
  không thêm permission riêng.

## 4. RBAC mới

Thêm vào `AccessConstants` (`modules.access`):
- `Resources.TEACHER`, `Resources.STUDENT` (mới).
- `AccessRules.CREATE_TEACHER/READ_TEACHER/UPDATE_TEACHER`, `CREATE_STUDENT/READ_STUDENT/UPDATE_STUDENT`
  — scope `ORGANIZATION`, admin-only, đúng mô hình Phân hệ 2.
- `AccessRules.CREATE_ACCOUNT` (mới, `Resources.ACCOUNT` đã có) — cho use case tạo Account mục 3.1.
- Gửi lại/thu hồi mời dùng `UPDATE_ACCOUNT` đã có, không thêm permission riêng.
- Seed permission mới vào nhóm "Toàn quyền hệ thống" trong migration mới (theo đúng cách
  `V10__seed_courses_rbac.sql` đã làm ở Phân hệ 2), không sửa migration cũ.

## 5. `integrations.notification` (SSE) — module mới

Mirror cách `modules.audit` lắng nghe sự kiện xuyên module hiện tại:

- **`NotificationManagement`** (facade duy nhất, rule #1): `SseEmitter subscribe(UUID accountId)` +
  `void push(UUID accountId, String type, Object payload)`. Module khác (Phân hệ 3/4 sau này) chỉ
  gọi `push(...)` khi muốn báo một account, không cần biết cơ chế SSE.
- **Nội bộ**: `SseEmitterRegistry` (package-private) giữ
  `ConcurrentHashMap<UUID, List<SseEmitter>>` (nhiều tab/thiết bị cùng lúc cho một account) —
  in-memory hợp lý vì stack là 1 deployable duy nhất (mục 3 spec tổng, không multi-instance). Tự dọn
  emitter khi `onCompletion`/`onTimeout`/`onError`.
- **`NotificationController`** (web): `GET /api/notifications/stream` (SSE, `text/event-stream`) —
  chỉ cần đăng nhập (`@AuthenticationPrincipal`), không cần permission riêng — mỗi account chỉ xem
  được kênh của chính mình.
- **`NotificationEventListeners`** (`internal/listener`, `@ApplicationModuleListener`, mirror
  `AuditEventListeners`): nghe sự kiện từ module khác, dịch sang `push(accountId, type, payload)`.

**Trigger cụ thể dựng trong phân hệ này** (chứng minh hạ tầng chạy thật, không chỉ khung rỗng):
lắng nghe `AccessEvents.AccountJoinedGroup` (sự kiện đổi quyền **duy nhất đang được phát ra** trong
repo hiện tại — `AccessManagement.assignRole` chưa phát event, nằm ngoài phạm vi sửa ở đây) → gọi
`push(event.accountId(), "PERMISSION_CHANGED", null)`.

Các trigger nghiệp vụ khác (lớp đổi giáo viên, học phí đến hạn, điểm danh...) để phân hệ sở hữu sự
kiện đó tự thêm `@ApplicationModuleListener` theo đúng khuôn này khi tới lượt xây — không dựng
trước.

## 6. Email

Dùng lại nguyên `integrations.mail.MailClient` đã có (Phân hệ 1) — **không** tạo module mail mới.
Thêm `AccountInviteMailer` trong `modules.identity.internal.mail`, mirror hoàn toàn
`PasswordResetMailer` đã có: template Thymeleaf riêng
(`com/eduerp/modules/identity/internal/mail/templates/account-invite.html`), gọi
`mailClient.sendHtml(...)`. Nội dung: họ tên, email đăng nhập, mật khẩu tạm, nhắc hết hạn sau 7 ngày.

## 7. Frontend

- `entities/teacher`, `entities/student` — schema (Zod) + query-keys + fetchers/hooks, mirror
  `entities/course`/`entities/class` của Phân hệ 2.
- `modules/teachers`, `modules/students` — forms/mutations/dialogs/pages CRUD hồ sơ, mirror
  `modules/courses` (2 trang phẳng riêng: "Giáo viên" và "Học viên", đúng tinh thần Phân hệ 2 đã
  chọn "2 trang phẳng riêng" thay vì gộp).
- Luồng tạo Account + quản lý lời mời **gắn vào `modules/rbac` đã có**
  (`accounts-page.tsx`/`accounts-table.tsx` hiện đang hiển thị danh sách Account + nút "chuyển chi
  nhánh") — thêm nút "Tạo tài khoản" (dialog mới, chọn role) + 2 nút "Gửi lại mời"/"Thu hồi mời"
  trên mỗi dòng Account có `lastLogin == null`. Không tạo module riêng vì đây vẫn là vòng đời của
  cùng một Account đang hiển thị ở đó.
- Trang Login (đã có) xử lý thêm response `requiresPasswordChange` từ mục 3.4 bước 2 — chuyển sang
  form "Đặt mật khẩu mới" thay vì báo lỗi, rồi gọi `CompletePasswordInvite`.
- `entities/notification` (mới) — `useNotificationStream()` hook: mở `EventSource` tới
  `/api/notifications/stream`, nhận `type: "PERMISSION_CHANGED"` thì gọi
  `queryClient.invalidateQueries` cho các query quyền/RBAC (khớp đúng mục 6.4 spec Core: "FE phản
  ánh thay đổi quyền ngay"). Mount một lần ở `require-auth.tsx` (cùng chỗ `PermissionProvider`) —
  không cần UI hiển thị riêng ở v1, chỉ cần tác dụng invalidate chạy ngầm.

## 8. Kiểm thử

- Unit test thuần cho rule sinh mật khẩu ngẫu nhiên (độ dài/độ mạnh) — không cần Spring context.
- `@SpringBootTest` + Testcontainers (Postgres + Redis) cho:
  - Tạo Account → mời → login lần đầu bị bắt đổi mật khẩu → đổi xong → login lại bình thường.
  - Lời mời hết hạn (giả lập TTL ngắn trong test) → login bị chặn dù đúng mật khẩu.
  - Gửi lại mời → mật khẩu cũ không còn dùng được, mật khẩu mới dùng được.
  - Thu hồi mời → Account chuyển `DISABLED`, login bị từ chối; gửi lại mời → `ACTIVE` lại.
  - Tạo `TeacherProfile`/`StudentProfile` gắn vào accountId có đúng role; từ chối nếu accountId
    không tồn tại hoặc role không khớp (vd gắn `TeacherProfile` vào accountId có role `STUDENT`).
  - SSE: subscribe, publish `AccessEvents.AccountJoinedGroup`, assert emitter nhận đúng
    `PERMISSION_CHANGED` cho đúng accountId, không nhận cho accountId khác.
- `ModularityTests` xanh với 2 module mới (`modules.teachers`, `modules.students`,
  `integrations.notification`) được thêm vào danh sách module đã detect.

## 9. Bước tiếp theo

Sau khi spec này được duyệt: viết implementation plan chi tiết qua skill `writing-plans`, mirror các
file tiền lệ cụ thể của Phân hệ 2 (`CourseAdminController`, `CreateClass`/`UpdateClass`/`ListClasses`,
`ClassesPage`, v.v.) và của `PasswordResetMailer`/`ForgotPassword` cho phần mời tài khoản.
