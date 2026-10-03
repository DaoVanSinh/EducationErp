# Education ERP — Kiến trúc tổng thể & Đặc tả Phân hệ 1 (Core: Định danh & Quản trị)

Ngày: 2026-09-27
Trạng thái: Chờ duyệt

## 1. Bối cảnh & phạm vi

Xây dựng hệ thống Quản lý Đào tạo (QLDT) cho **một trung tâm, nhiều chi nhánh**, gồm 5 phân hệ chức năng (mục 2) cộng thêm thanh toán học phí online.

**Đây không phải dự án hoàn toàn mới về mặt nghiệp vụ.** Repo này từng có một hệ thống điểm danh bằng QR code (package `com.qrcode.backend`, frontend HTML/JS thuần theo role admin/teacher/student) với các tính năng: tài khoản + JWT refresh token, khóa học/lớp/học kỳ/thời khóa biểu, ghi danh (enrollment), điểm danh qua quét QR + điểm danh thủ công + theo dõi điểm danh real-time, xuất báo cáo Excel, đổi/quên mật khẩu, hồ sơ + avatar, và tự động tạo tài khoản Admin mặc định khi khởi động (self-healing seed). Hệ thống này đã bị gỡ bỏ toàn bộ ở commit `2764de6` ("feat: remove legacy code") ngay trước khi bắt đầu thiết kế lại theo kiến trúc modular mới — **có chủ đích**, không phải do nhầm lẫn.

Hệ quả cho spec này:
- Một số cơ chế đã chứng minh hiệu quả ở hệ cũ (điểm danh qua QR, xuất Excel, theo dõi điểm danh real-time, tự tạo Admin mặc định) được **giữ lại làm tham khảo** và đưa vào phần tương ứng bên dưới, nhưng **không** copy nguyên kiến trúc cũ (role cố định enum `ADMIN/TEACHER/STUDENT`, JWT không có blacklist, không có khái niệm chi nhánh) — đây chính là những giới hạn của hệ cũ mà thiết kế mới phải giải quyết.
- Vì hệ cũ dùng Spring Boot + JPA + Flyway migrations (`V1`...`V17`) + PostgreSQL, môi trường vận hành thực tế đã quen với stack này — spec giữ nguyên PostgreSQL + Flyway, không đổi sang công cụ khác.

**Ràng buộc phạm vi tường minh:** đây là hệ thống QLDT — quản lý và vận hành đào tạo (lịch học, điểm danh, học phí, điểm số do giảng viên nhập), **không bao gồm** một engine để học viên tự làm bài/thi trực tuyến trong hệ thống. Ràng buộc này áp dụng cho Phân hệ 4.

## 2. Năm phân hệ & thứ tự xây dựng

| # | Phân hệ | Phụ thuộc | Ghi chú phạm vi |
|---|---|---|---|
| 1 | **Core: Định danh & Quản trị** | — | Tài khoản, RBAC linh hoạt, Audit Log, Dashboard khung. **Đang thiết kế ở spec này.** |
| 2 | **Khóa học & Lớp học** | 1 | Danh mục khóa học, tạo lớp, phân công giảng viên, thời khóa biểu. **Đã xây xong** (nhánh `feature/courses-classes`, merge vào `develop` ngày 2026-10-03). |
| 2.5 | **Hồ sơ Giáo viên & Học viên + Thông báo** | 1 | `modules.teachers`/`modules.students` — hồ sơ nghiệp vụ CƠ BẢN của giáo viên/học viên (chuyên môn dạy, liên hệ, trạng thái — không gồm lương/hợp đồng, xem 2.6), CRUD đầy đủ, kèm luồng admin tạo tài khoản + mời qua email (mật khẩu ngẫu nhiên + token mời hết hạn 7 ngày trong Redis, bắt đổi mật khẩu ở lần đăng nhập đầu). `integrations.mail` (đã có sẵn từ Phân hệ 1, dùng lại) — thêm mailer mời tài khoản. `integrations.notification` — kênh SSE dùng chung, phục vụ cả đổi quyền tức thì (mục 6.4) lẫn sự kiện nghiệp vụ các phân hệ sau. Thêm vào roadmap ngày 2026-10-03 theo yêu cầu người dùng, dựa trên khảo sát thực tế Education ERP (Student là bounded context riêng, Teacher có module hồ sơ riêng, tách khỏi RBAC thuần phân quyền). **Lưu ý: đây là ERP trung tâm dạy (tutoring center), không phải trường/đại học** — tránh field kiểu khoa/niên khóa/mã số trường. **Đã xây xong** (nhánh `feature/teacher-student-profiles`, merge vào `develop` ngày 2026-10-03, qua whole-branch review + fix pass). |
| 2.6 | **Nhân sự & Tiền lương Giáo viên** | 1, 2.5 | Hợp đồng lao động (file lưu S3), lương cơ bản/phụ cấp, thử việc (tỷ lệ % ràng buộc theo Điều 26 BLLĐ — tối thiểu 85% lương chính thức)/chính thức, BHXH/BHYT/BHTN tự động, thuế TNCN nhập tay, đối soát kế toán. Tách riêng khỏi 2.5 ngày 2026-10-03 vì đây là nghiệp vụ Nhân sự-Lương đầy đủ, có ràng buộc pháp lý. **Đã xây xong** (nhánh `feature/payroll-hr`, merge vào `develop` ngày 2026-10-03, qua whole-branch review + fix pass). |
| 3 | **Tuyển sinh & Học phí** | 1, 2, 2.5 | Đăng ký, tiếp nhận học viên (dựa trên `modules.students` của 2.5), tình trạng học phí, lịch sử thanh toán, **thanh toán học phí online** (cổng thanh toán VN, chọn cụ thể khi tới lượt spec phân hệ này) |
| 4 | **Quản lý Học tập** | 1, 2, 2.5, 3 | Danh sách lớp, điểm danh, theo dõi tiến độ bài tập/mini-test, nhập điểm cuối khóa do giảng viên thực hiện. **Không** xây engine học viên làm bài/thi trong hệ thống. Cân nhắc kế thừa cơ chế điểm danh QR + theo dõi real-time từ hệ cũ khi thiết kế chi tiết phân hệ này |
| 5 | **AI Chatbot** | 1, 2 | Chat cho học viên, RAG trên danh mục khóa học đang mở |

Phân hệ 2.5-5 chỉ có ghi chú phạm vi sơ bộ ở mục 8 — mỗi phân hệ sẽ có vòng brainstorm → spec → plan riêng khi tới lượt xây, theo đúng thứ tự phụ thuộc trên.

## 3. Stack kỹ thuật

| Thành phần | Lựa chọn |
|---|---|
| Backend | Spring Boot (Spring Modulith) — theo skill `springboot-modular-scaffold`: một deployable duy nhất, mỗi phân hệ là một module theo package, biên giới kiểm chứng bằng `ApplicationModules.verify()` |
| Frontend | React + Vite (**không** dùng Next.js) — theo skill `nextjs-modular-architecture`, bản Vite (`references/vite-adaptation.md`): React Router thay file-based routing, TanStack Query cho toàn bộ data fetching, shadcn/ui làm nền UI kit |
| Icon | `lucide-react` cho icon tĩnh thông thường; gói `lucide` (icon data thô) + `morphicons` riêng cho icon cần hiệu ứng morph (menu↔close, chevron mở/đóng, trạng thái loading...) |
| Giao diện | Theme "Liquid Glass" — lớp thiết kế riêng (backdrop-blur, độ trong suốt, bo góc lớn, highlight ánh sáng) đắp lên token có sẵn của `shadcn/ui`, không thay thế toàn bộ component |
| Database | PostgreSQL, migration bằng Flyway (giữ nguyên công cụ hệ cũ đã dùng) |
| Cache / session | Redis — cache quyền hiệu lực theo user, blacklist token |

## 4. Mô hình Tổ chức & Chi nhánh

- Một tổ chức duy nhất, nhiều `Branch` (chi nhánh) — không phải multi-tenant SaaS, không cần cách ly dữ liệu giữa các chi nhánh ở tầng hạ tầng.
- Mỗi `Account` có đúng một `homeBranchId` tại một thời điểm (`null` = phạm vi toàn tổ chức, dành cho tài khoản quản trị cấp cao).
- Việc phân công giảng dạy (giáo viên dạy lớp nào, ở chi nhánh nào) thuộc Phân hệ 2 (Khóa học & Lớp học) và **không** bị giới hạn bởi chi nhánh chủ quản của giáo viên — một giáo viên vẫn dạy được nhiều chi nhánh bình thường.
- Đổi chi nhánh chủ quản là một thao tác tường minh — **"chuyển chi nhánh"** — không phải gán nhiều chi nhánh song song cho một tài khoản. Thao tác này bắt buộc ghi vào Audit Log.

## 5. Mô hình RBAC (áp dụng cho MỌI phân hệ, không chỉ Phân hệ 1)

- **Permission** = một hành động CRUD trên một tài nguyên, dạng `(resource, action)` — ví dụ `COURSE.CREATE`, `TUITION.APPROVE`. Mỗi phân hệ tự khai báo permission của mình khi được xây (đúng nguyên tắc "module sở hữu constants của nó" — rule đặt tên trong `springboot-modular-scaffold`), không có một file `Permission` catalog dùng chung ở root.
- **Scope** — mỗi permission khi gán vào một nhóm quyền đi kèm đúng một trong ba cấp độ phạm vi dữ liệu:
  - `PERSONAL` — chỉ bản ghi do chính tài khoản tạo/sở hữu
  - `BRANCH` — toàn bộ dữ liệu trong `homeBranchId` của tài khoản
  - `ORGANIZATION` — toàn hệ thống, không lọc theo chi nhánh
- **PermissionGroup** ("nhóm quyền") — một bộ `(permission, scope)` đóng gói để tái sử dụng, gán được cho cả `Role` lẫn `Group`.
- **Role** — vai trò chính của tài khoản, đúng 1 Role/Account, gồm nhiều PermissionGroup. Seed sẵn `ADMIN`/`TEACHER`/`STUDENT` (`isSystemDefault = true`, không xoá được nhưng sửa được quyền), tạo thêm Role tuỳ ý qua UI quản trị.
- **Group** ("nhóm người dùng", ví dụ Phòng Kế toán, Ban Giám đốc) — một Account thuộc được nhiều Group cùng lúc, mỗi Group cũng gán nhiều PermissionGroup.
- **Quyền hiệu lực** của một Account = hợp (union) quyền từ Role + quyền từ tất cả Group đang thuộc về. Nếu cùng một permission xuất hiện với scope khác nhau ở nhiều nhóm quyền, lấy **scope rộng nhất** (`ORGANIZATION` > `BRANCH` > `PERSONAL`) — quy tắc dedupe duy nhất trong hệ thống, không có ưu tiên/độ ưu tiên nào khác.

## 6. Đặc tả chi tiết Phân hệ 1: Core — Định danh & Quản trị

### 6.1 Data model (package `com.eduerp.identity` — base package `com.eduerp` tạm đặt theo tên repo, xác nhận lại groupId thật khi bootstrap dự án)

| Entity | Trường chính | Ghi chú |
|---|---|---|
| `Branch` | `id`, `name`, `code`, `address`, `active` | |
| `Account` | `id`, `email`, `passwordHash`, `fullName`, `avatarUrl`, `status`, `homeBranchId` (FK, null=toàn tổ chức), `roleId` (FK) | `status`: `ACTIVE`/`DISABLED` |
| `Role` | `id`, `name`, `code`, `isSystemDefault` | Seed `ADMIN`/`TEACHER`/`STUDENT` |
| `Group` | `id`, `name`, `description` | n-n với `Account` qua `AccountGroup` |
| `Permission` | `id`, `resource`, `action` | Catalog cố định, mỗi phân hệ tự bổ sung khi được xây |
| `PermissionGroup` | `id`, `name`, `description` | |
| `PermissionGroupItem` | `permissionGroupId`, `permissionId`, `scope` | `scope` ∈ {`PERSONAL`, `BRANCH`, `ORGANIZATION`} |
| `RolePermissionGroup` / `GroupPermissionGroup` | join n-n | Role/Group ↔ PermissionGroup |
| `AuditLog` | `actorAccountId`, `action`, `entityType`, `entityId`, `beforeState` (json), `afterState` (json), `branchId`, `timestamp`, `ip` | Ghi cả thao tác "chuyển chi nhánh" và mọi thay đổi Role/Group/Permission |

### 6.2 Tài khoản & tự phục vụ

Bổ sung từ kinh nghiệm hệ cũ (đã hoạt động tốt, giữ lại):
- Đổi mật khẩu (`change-password`), quên mật khẩu qua email (`forgot-password` → token reset có hạn dùng, lưu Redis thay vì cột riêng trên `Account` như hệ cũ).
- Hồ sơ cá nhân + avatar (`ProfileResponse`/`ProfileUpdateRequest` tương đương hệ cũ).
- **Seed self-healing**: mỗi lần khởi động, nếu chưa tồn tại tài khoản nào có Role `ADMIN` (`isSystemDefault`) ở scope `ORGANIZATION`, tự tạo một tài khoản Admin mặc định (tương đương `DataInitializer` hệ cũ) — đảm bảo luôn có đường vào hệ thống sau khi triển khai mới.

### 6.3 Luồng Auth (JWT gọn + cookie + Redis)

- **Access token**: JWT gọn, chỉ chứa `sub` (accountId) + `jti`, hạn ngắn (~15 phút), cookie `access_token` (`HttpOnly`, `Secure`, `SameSite=Strict`).
- **Refresh token**: cookie riêng `refresh_token`, hạn dài hơn (~7-30 ngày), **rotate** mỗi lần dùng — token cũ bị đẩy vào blacklist Redis ngay khi refresh thành công.
- **CSRF**: cookie đọc được `XSRF-TOKEN` (không `HttpOnly`), FE gửi lại qua header cho mọi request thay đổi dữ liệu — dùng cơ chế double-submit-cookie có sẵn của Spring Security (bắt buộc vì auth qua cookie, không phải bearer header).
- **Logout**: đẩy `jti` của access + refresh hiện tại vào blacklist Redis (TTL = thời gian còn lại của token), xoá cookie.
- **Force-logout** (admin thao tác): đẩy toàn bộ `jti` đang hoạt động của một Account vào blacklist — cần một Redis set `sessions:account:{id}` theo dõi các `jti` đang hoạt động.

### 6.4 Phân quyền lúc chạy — 2 lớp

- **Cache quyền hiệu lực**: Redis key `perm:account:{id}`, tính từ DB khi cache-miss. Khi admin đổi Role/Group/PermissionGroup của một Account, **xoá ngay** key tương ứng — request tiếp theo của Account đó tự tính lại (gần như tức thời, không cần đợi JWT hết hạn hay đăng nhập lại).
- **Lớp 1 (coarse)** — `@PreAuthorize` + `PermissionEvaluator` tuỳ biến: kiểm tra Account có permission đó không, bất kể scope.
- **Lớp 2 (fine, theo scope)** — tại tầng repository/use-case: `ORGANIZATION` không lọc thêm; `BRANCH` lọc theo `homeBranchId` của Account; `PERSONAL` lọc theo Account đã tạo bản ghi.
- **Frontend phản ánh thay đổi quyền**: sau mọi mutation đổi quyền, FE gọi `queryClient.invalidateQueries` để màn hình admin cập nhật ngay lập tức. Tài khoản **bị** đổi quyền (đang có phiên khác) sẽ nhận quyền mới ở lần gọi API tiếp theo mà không cần đăng nhập lại — đẩy real-time qua WebSocket/SSE cho tài khoản đó (không cần họ thao tác gì) là phần mở rộng, nằm ngoài phạm vi bản đầu tiên.

### 6.5 Audit Log

Ghi qua Spring AOP (aspect quanh các use-case được đánh dấu ghi log), không rải lệnh ghi log thủ công trong từng service — tránh tình trạng quên ghi log ở một vài chỗ như thường gặp.

### 6.6 Dashboard (phạm vi Phân hệ 1)

Chỉ số liệu cơ bản: tổng số tài khoản (theo Role/chi nhánh), tổng số chi nhánh, hoạt động đăng nhập gần đây. Số liệu của các phân hệ khác (khóa học, học phí, điểm danh...) sẽ bổ sung dần khi phân hệ đó được xây.

### 6.7 Frontend Phân hệ 1

```
src/modules/auth/         login form, hooks useLogin/useLogout/useMe (TanStack Query)
src/entities/account/     Account type, avatar, thông tin hiển thị dùng chung cho các phân hệ sau
src/entities/permission/  RESOURCES/ACTIONS/SCOPES constants + <Can>/useCan (theo pattern rbac-ui.md, nguồn dữ liệu đổi sang mô hình scope mới)
```

## 7. Kiểm thử

- Unit test thuần (không Spring context) cho quy tắc "lấy scope rộng nhất khi permission trùng ở nhiều nhóm quyền".
- Use-case test với Mockito fake cho repository — không cần database.
- `@ApplicationModuleTest` kiểm tra biên giới module `identity`.
- `@SpringBootTest` + Testcontainers (Postgres + Redis) cho luồng login → refresh → logout thật, và luồng force-logout/đổi quyền có hiệu lực ngay.

## 8. Ghi chú phạm vi các Phân hệ 2.5-5 (sơ bộ — spec chi tiết sẽ làm riêng khi tới lượt)

- **Phân hệ 2 — Khóa học & Lớp học**: danh mục khóa học, tạo lớp, phân công giảng viên (không giới hạn theo chi nhánh chủ quản giáo viên — mục 4), thời khóa biểu. **Đã xây xong.**
- **Phân hệ 2.5 — Hồ sơ Giáo viên & Học viên + Thông báo**: CRUD hồ sơ nghiệp vụ Teacher (môn dạy/chuyên môn, không phải "khoa" kiểu trường học) và Student (ngày sinh, nguồn biết đến trung tâm — **không** cần thông tin phụ huynh, đã xác nhận) — tách khỏi `Account` chung của Phân hệ 1. Luồng admin tạo tài khoản mới (chưa tồn tại ở Phân hệ 1) + mời qua email: mật khẩu ngẫu nhiên + token mời Redis hết hạn 7 ngày, bắt đổi mật khẩu ở lần đăng nhập đầu (dựa trên `Account.lastLogin == null`), hết hạn mời thì chặn hẳn đăng nhập bằng mật khẩu cũ, "thu hồi lời mời" chuyển Account sang `DISABLED` (mời lại thì bật `ACTIVE` + sinh mời mới). Email template cho cả onboarding lẫn thông báo nghiệp vụ (dùng lại `integrations.mail` có sẵn từ Phân hệ 1). Hạ tầng SSE (`integrations.notification`, mới) dùng chung cho đổi quyền tức thì và sự kiện nghiệp vụ các phân hệ sau. **Không** gồm lương/hợp đồng (xem Phân hệ 2.6).
- **Phân hệ 2.6 — Nhân sự & Tiền lương Giáo viên**: hợp đồng lao động (file S3), lương cơ bản/phụ cấp, thử việc/chính thức, đối soát kế toán. Ràng buộc pháp lý cần tôn trọng khi thiết kế chi tiết: lương thử việc ≥ 85% lương chính thức VÀ không thấp hơn lương tối thiểu vùng (Điều 26 Bộ luật Lao động 2019). Cần nghiên cứu kỹ nghiệp vụ kế toán/nhân sự thực tế (không suy đoán) trước khi viết spec.
- **Phân hệ 3 — Tuyển sinh & Học phí**: đăng ký, tiếp nhận học viên (dựa trên hồ sơ Student của Phân hệ 2.5), tình trạng học phí, lịch sử thanh toán, **thanh toán học phí online** — chọn cổng thanh toán cụ thể (VNPay/MoMo/ZaloPay...) khi vào spec phân hệ này.
- **Phân hệ 4 — Quản lý Học tập**: danh sách lớp, điểm danh, theo dõi tiến độ bài tập/mini-test, nhập điểm cuối khóa do giảng viên thực hiện. Ràng buộc cứng: **không** xây engine học viên làm bài/thi trong hệ thống. Cân nhắc kế thừa cơ chế điểm danh QR + theo dõi điểm danh real-time + xuất Excel từ hệ cũ khi thiết kế chi tiết.
- **Phân hệ 5 — AI Chatbot**: chat cho học viên, RAG trên danh mục khóa học đang mở, cần dữ liệu từ Phân hệ 2.

## 9. Bước tiếp theo

Sau khi spec này được duyệt: viết implementation plan chi tiết cho Phân hệ 1 (qua skill `writing-plans`), rồi triển khai theo `springboot-modular-scaffold` (backend) và `nextjs-modular-architecture` bản Vite (frontend). Phân hệ 2-5 quay lại bước brainstorm khi tới lượt, không thiết kế trước.
