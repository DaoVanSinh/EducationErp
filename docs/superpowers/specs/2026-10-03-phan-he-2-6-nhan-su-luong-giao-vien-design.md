# Phân hệ 2.6 — Nhân sự & Tiền lương: thiết kế

> Spec này đặc tả Phân hệ 2.6 trong roadmap tổng
> (`docs/superpowers/specs/2026-09-27-education-erp-architecture-design.md`, mục 2 và mục 8).
> Phụ thuộc Phân hệ 1 (Core) và Phân hệ 2.5 (Hồ sơ Giáo viên/Học viên + Thông báo) — cả hai đã
> xây xong, merge vào `develop` ngày 2026-10-03.

## 1. Bối cảnh & phạm vi

Trung tâm dạy học (tutoring center, **không phải** trường/đại học) cần quản lý hợp đồng lao động
và chốt lương hàng tháng cho nhân viên — bao gồm cả giáo viên (`modules.teachers`, Phân hệ 2.5)
lẫn nhân viên hành chính khác (lễ tân, quản lý...). Phạm vi được người dùng xác nhận qua
brainstorming:

- Hợp đồng gắn với `Account` (không gắn riêng `TeacherProfile`) — áp dụng cho **mọi nhân viên có
  hợp đồng lao động**, không giới hạn giáo viên.
- Hai loại hợp đồng song song, đúng thực tế trung tâm dạy: **HĐLĐ chính thức** (lương tháng cố
  định, có BHXH) và **CTV/thời vụ** (trả theo giờ dạy thực tế, không BHXH).
- Lưu file hợp đồng (PDF) qua S3-compatible storage (MinIO ở dev, AWS S3 thật ở production).
- Tự động tính BHXH/BHYT/BHTN (tỷ lệ cố định, ít thay đổi); thuế TNCN **nhập tay** (kế toán tự
  tính/nhập dựa trên hoàn cảnh cá nhân từng người, hệ thống chỉ lưu lại để đối soát) — lý do xem
  mục 2.
- Quy trình chốt lương có bước duyệt riêng: kế toán tạo → chủ trung tâm duyệt → khoá.

### Ngoài phạm vi (out of scope, ghi rõ để không suy đoán thêm)

- Tự động tính thuế TNCN (biểu lũy tiến, giảm trừ gia cảnh, người phụ thuộc) — xem lý do mục 2.
- Tạm ứng lương (salary advance) — không được yêu cầu, không suy đoán thêm.
- Tích hợp số giờ dạy thực tế từ hệ thống điểm danh — Phân hệ 4 (Quản lý Học tập) chưa xây, điểm
  danh/chấm công chưa tồn tại. Giờ dạy của hợp đồng CTV được **nhập tay** mỗi kỳ lương trong phân
  hệ này; đây là điểm tích hợp tự nhiên khi Phân hệ 4 ra đời (thay nhập tay bằng tổng giờ từ điểm
  danh), không xây trước.
- Mở khoá lại bảng lương đã duyệt (`APPROVED`) — sửa sai sau khi khoá phải tạo kỳ lương điều
  chỉnh mới, không mở ngược trạng thái đã khoá (giữ vết kiểm toán).
- Đăng ký BHXH điện tử, nộp tờ khai thuế, các nghiệp vụ nộp hồ sơ với cơ quan nhà nước — hệ thống
  chỉ tính toán và lưu trữ nội bộ, không tích hợp cổng nộp hồ sơ.

## 2. Nghiên cứu nghiệp vụ & pháp lý (Việt Nam)

Tra cứu trước khi viết spec, theo đúng yêu cầu của người dùng — không suy đoán.

- **Lương thử việc** (Điều 26 Bộ luật Lao động 2019): tối thiểu bằng 85% mức lương chính thức của
  công việc đó. Mốc 85% tính trên lương chính thức cụ thể của vị trí, không phải trực tiếp trên
  lương tối thiểu vùng. Vi phạm có thể bị phạt đến 10 triệu đồng và buộc truy trả đủ.
- **Lương tối thiểu vùng 2026** (Nghị định 293/2025/NĐ-CP, hiệu lực 1/1/2026): Vùng I 5.310.000đ,
  Vùng II 4.730.000đ, Vùng III 4.140.000đ, Vùng IV 3.700.000đ/tháng. Mức này đổi theo nghị định
  mới gần như hàng năm — **không hard-code trong code**, lưu dưới dạng cấu hình có ngày hiệu lực
  (xem mục 3.4).
- **BHXH/BHYT/BHTN**: tổng 32% lương tham gia BHXH — doanh nghiệp đóng 21,5% (BHXH 17,5% + BHYT
  3% + BHTN 1%), người lao động đóng 10,5% (BHXH 8% + BHYT 1,5% + BHTN 1%). Chỉ áp dụng cho hợp
  đồng **chính thức** (HĐLĐ); CTV/thời vụ theo giờ không tham gia BHXH bắt buộc theo diện này.
- **Thuế TNCN từ tiền lương**: từ kỳ tính thuế 2026, biểu lũy tiến từng phần rút còn 5 bậc
  (5%/10%/20%/30%/35%), giảm trừ gia cảnh bản thân 15,5 triệu đồng/tháng + 6,2 triệu đồng/người
  phụ thuộc (Nghị quyết 110/2025/UBTVQH15). Biểu thuế và mức giảm trừ **vừa đổi hoàn toàn** so với
  giai đoạn trước (7 bậc → 5 bậc) — minh chứng rằng bảng này không ổn định theo thời gian và phụ
  thuộc hoàn cảnh cá nhân (số người phụ thuộc) mà hệ thống hiện chưa mô hình hoá (không có bảng
  khai người phụ thuộc). Với CTV không ký HĐLĐ (hoặc HĐLĐ dưới 3 tháng): khấu trừ 10% tại nguồn
  nếu mỗi lần trả từ 2 triệu đồng trở lên — một cơ chế tính thuế **khác hẳn** so với lương HĐLĐ.
  → **Quyết định thiết kế**: không tự động hoá TNCN. Field nhập tay, hệ thống chỉ cộng vào tổng
  khấu trừ để ra lương thực nhận và lưu lại phục vụ đối soát kế toán.
- **Thực tế trung tâm ngoại ngữ/tutoring**: giáo viên phổ biến có 2 dạng — toàn thời gian (lương
  tháng cố định + BHXH) hoặc CTV trả theo giờ dạy thực tế (không BHXH, linh hoạt lịch). Hợp đồng
  cần ghi rõ đơn giá/giờ hoặc lương cố định, không có một chuẩn thống nhất giữa các trung tâm —
  củng cố quyết định để `baseSalary`/`hourlyRate` và phụ cấp là dữ liệu tự do nhập theo từng hợp
  đồng, không hard-code mức lương/phụ cấp mẫu.

## 3. Backend

### 3.1 Module boundary

Module mới `modules.payroll` (base package `com.eduerp`), theo đúng khuôn
`springboot-modular-scaffold`. Không tách `modules.hr` riêng: `EmploymentContract` và
`PayrollRun`/`Payslip` luôn dùng cùng nhau trong một vòng nghiệp vụ (hợp đồng là input để tính
lương), tách sớm là over-engineering khi chưa có use case nào cần `EmploymentContract` độc lập mà
không liên quan lương. Mirror cấu trúc `modules.courses` (ôm cả `Course` và `Class` theo đúng
tinh thần này).

Module tích hợp mới `integrations.storage` — S3-compatible object storage (mirror
`integrations.mail`: một `StorageClient` mỏng, cấu hình qua `@ConfigurationProperties`).

Không có cross-module JPA relationship: `EmploymentContract.accountId` là `UUID` trần, tra cứu
tên/email qua `IdentityManagement.summariesOf(Collection<UUID>)` (đã có sẵn, dùng lại y nguyên
pattern `ListAccounts`/`GetDashboardStats` đang dùng).

### 3.2 `modules.payroll` — base package

- `PayrollManagement.java` — facade, type duy nhất module khác được gọi.
  - `createContract(CreateContractRequest, actorAccountId, actorBranchId): ContractResponse`
  - `updateContract(UUID contractId, UpdateContractRequest, actorAccountId, actorBranchId): ContractResponse`
  - `terminateContract(UUID contractId, actorAccountId, actorBranchId): void` — đặt `status = TERMINATED`, `endDate = now()`.
  - `listContracts(Pageable, UUID accountId filter tuỳ chọn): PageResponse<ContractResponse>`
  - `getActiveContractFor(UUID accountId): Optional<ContractResponse>` — dùng khi tạo `Payslip`.
  - `createPayrollRun(CreatePayrollRunRequest, actorAccountId, actorBranchId): PayrollRunResponse` — tạo kỳ lương `DRAFT`, tự sinh một `Payslip` nháp cho mỗi hợp đồng `ACTIVE` (xem 3.3).
  - `updatePayslip(UUID payslipId, UpdatePayslipRequest, actorAccountId, actorBranchId): PayslipResponse` — chỉ cho phép khi `PayrollRun.status == DRAFT`.
  - `submitForApproval(UUID payrollRunId, actorAccountId, actorBranchId): void` — `DRAFT → PENDING_APPROVAL`.
  - `approvePayrollRun(UUID payrollRunId, actorAccountId, actorBranchId): void` — `PENDING_APPROVAL → APPROVED` (khoá).
  - `rejectPayrollRun(UUID payrollRunId, String reason, actorAccountId, actorBranchId): void` — `PENDING_APPROVAL → DRAFT`, lưu lý do.
  - `listPayrollRuns(Pageable): PageResponse<PayrollRunResponse>`
  - `getPayrollRun(UUID payrollRunId): PayrollRunDetailResponse` — kèm danh sách `Payslip`.
- `PayrollConstants.java`:
  - `Limits` (ví dụ `MAX_ALLOWANCE_NAME_LENGTH = 100`, `MAX_ALLOWANCES_PER_CONTRACT = 20`).
  - `ContractTypes`: `OFFICIAL`, `COLLABORATOR` (enum thật `PayrollConstants.ContractType`, không
    phải String — theo đúng mandate "No Hardcoded Strings", nhưng vẫn liệt kê hằng số ở đây để
    nơi khác tham chiếu tên nếu cần serialize).
  - `ContractStatus`: `ACTIVE`, `TERMINATED`.
  - `PayrollRunStatus`: `DRAFT`, `PENDING_APPROVAL`, `APPROVED`.
  - `StatutoryRates` — `EMPLOYER_SHARE = new BigDecimal("0.215")`, `EMPLOYEE_SHARE = new
    BigDecimal("0.105")` (BHXH/BHYT/BHTN, mục 2). Đặt dưới dạng hằng số Java (không phải bảng
    DB), vì tỷ lệ BHXH bắt buộc hiếm khi đổi và mỗi lần đổi là thay đổi luật lớn cần deploy lại
    toàn hệ thống dù sao (giống cách `IdentityConstants.Limits` xử lý hằng số nghiệp vụ hiện có).
  - `CacheNamespaces` — không cần cache riêng cho module này ở V1 (không có read nóng cần cache).
- `PayrollProperties.java` — hiện tại không cần property cấu hình riêng (không có TTL/threshold
  nào); để trống class theo đúng khuôn tier 0 nếu sau này cần (ví dụ ngưỡng cảnh báo).
- `PayrollException.java` — sealed hierarchy: `ContractNotFoundException`, `PayrollRunNotFoundException`,
  `PayslipNotFoundException`, `ContractAlreadyTerminatedException`, `PayrollRunNotEditableException`
  (sửa payslip khi run không ở `DRAFT`), `PayrollRunNotPendingApprovalException` (approve/reject
  khi run không ở `PENDING_APPROVAL`), `InvalidContractTermsException` (ví dụ: `OFFICIAL` thiếu
  `baseSalary`, `COLLABORATOR` thiếu `hourlyRate`).
- `PayrollEvents.java` — `ContractCreated`, `ContractTerminated`, `PayrollRunApproved` (record,
  publish qua `ApplicationEventPublisher`; `modules.audit` nghe qua `@ApplicationModuleListener`
  giống `AuditEventListeners` hiện có, không cần thiết kế thêm — chỉ đăng ký listener mới ở
  `modules.audit`).
- `dto/` (`@NamedInterface("dto")`):
  - `CreateContractRequest(UUID accountId, ContractType contractType, BigDecimal baseSalary,
    BigDecimal hourlyRate, LocalDate probationStartDate, LocalDate probationEndDate, LocalDate
    startDate, List<AllowanceRequest> allowances)`
  - `AllowanceRequest(String name, BigDecimal amount)`
  - `UpdateContractRequest(BigDecimal baseSalary, BigDecimal hourlyRate, LocalDate
    probationEndDate, List<AllowanceRequest> allowances)`
  - `ContractResponse(UUID id, UUID accountId, String accountFullName, String accountEmail,
    ContractType contractType, ContractStatus status, BigDecimal baseSalary, BigDecimal
    hourlyRate, LocalDate probationStartDate, LocalDate probationEndDate, LocalDate startDate,
    LocalDate endDate, List<AllowanceResponse> allowances, String contractFileKey)`
  - `AllowanceResponse(String name, BigDecimal amount)`
  - `CreatePayrollRunRequest(int year, int month)`
  - `UpdatePayslipRequest(BigDecimal hoursWorked, BigDecimal incomeTaxWithheld, String note)` —
    `hoursWorked` chỉ có ý nghĩa với `COLLABORATOR`; `incomeTaxWithheld` là ô TNCN nhập tay (mục 2).
  - `PayrollRunResponse(UUID id, int year, int month, PayrollRunStatus status, int payslipCount,
    BigDecimal totalGrossPay)`
  - `PayslipResponse(UUID id, UUID accountId, String accountFullName, ContractType contractType,
    BigDecimal grossPay, BigDecimal socialInsuranceEmployee, BigDecimal socialInsuranceEmployer,
    BigDecimal incomeTaxWithheld, BigDecimal netPay, BigDecimal hoursWorked, boolean inProbation)`
  - `PayrollRunDetailResponse(PayrollRunResponse run, List<PayslipResponse> payslips)`
- `usecase/` — một class một use case, `execute(...)` `public @Transactional`, mirror
  `modules.courses.usecase`: `CreateContract`, `UpdateContract`, `TerminateContract`,
  `ListContracts`, `CreatePayrollRun` (chứa logic tính `grossPay`/BHXH mô tả ở 3.3),
  `UpdatePayslip`, `SubmitPayrollRunForApproval`, `ApprovePayrollRun`, `RejectPayrollRun`,
  `GetPayrollRun`, `ListPayrollRuns`.
- `web/` — `ContractAdminController` (`/api/payroll/contracts`), `PayrollRunAdminController`
  (`/api/payroll/runs`), mirror `CourseAdminController`/`ClassAdminController` 1:1 về cấu trúc
  (thin controller, `@PreAuthorize` theo `AccessConstants.AccessRules`, map exception → `ProblemDetail`
  qua `GlobalExceptionHandler` có sẵn).
- `internal/model/` — `EmploymentContract` (JPA `@Entity`, bảng `employment_contracts`),
  `ContractAllowance` (`@ElementCollection` trên `EmploymentContract`, mirror
  `teacher_profile_subjects` của Phân hệ 2.5 — ghi chú m9 của review trước: không có PK riêng,
  đây là shape chuẩn của `@ElementCollection`, chấp nhận được), `PayrollRun` (`@Entity`, bảng
  `payroll_runs`, unique constraint `(year, month)` — một kỳ lương một lần), `Payslip` (`@Entity`,
  bảng `payslips`, FK nội bộ module tới `PayrollRun`, `accountId` là `UUID` trần không FK xuyên
  module).
- `internal/repository/` — `EmploymentContractRepository`, `PayrollRunRepository`,
  `PayslipRepository` (Spring Data JPA, mirror `CourseRepository`).
- `internal/rules/PayrollRules.java` — pure, 100% unit-test bằng `new`, không I/O:
  - `BigDecimal baseGrossPay(EmploymentContract contract, LocalDate payPeriodStart, BigDecimal
    hoursWorked)` — `OFFICIAL`: `isInProbation(contract, payPeriodStart) ? baseSalary *
    0.85 : baseSalary`; `COLLABORATOR`: `hourlyRate * hoursWorked`.
  - `boolean isInProbation(EmploymentContract contract, LocalDate asOf)` — so `asOf` với
    `probationStartDate`/`probationEndDate`.
  - `BigDecimal totalAllowances(List<ContractAllowance> allowances)`.
  - `BigDecimal socialInsuranceEmployeeShare(BigDecimal grossPay, ContractType type)` —
    `OFFICIAL` → `grossPay * StatutoryRates.EMPLOYEE_SHARE`; `COLLABORATOR` → `ZERO`.
  - `BigDecimal socialInsuranceEmployerShare(BigDecimal grossPay, ContractType type)` — tương tự
    với `EMPLOYER_SHARE` (lưu lại để đối soát chi phí doanh nghiệp, không trừ vào lương nhân
    viên).
  - `BigDecimal netPay(BigDecimal grossPay, BigDecimal allowances, BigDecimal
    socialInsuranceEmployeeShare, BigDecimal incomeTaxWithheld)` — `grossPay + allowances -
    socialInsuranceEmployeeShare - incomeTaxWithheld`.
- `internal/util/` — không cần transform đặc biệt ở V1.
- `package-info.java` — `@ApplicationModule(displayName = "Payroll & Nhân sự")`.

### 3.3 Logic tính `Payslip` khi tạo `PayrollRun` (trong `CreatePayrollRun` usecase)

1. Lấy mọi `EmploymentContract` có `status = ACTIVE`.
2. Với mỗi hợp đồng, tạo một `Payslip` nháp:
   - `OFFICIAL`: `grossPay = PayrollRules.baseGrossPay(...)` tính theo `probationStartDate` so
     với ngày đầu kỳ lương (`year-month-01`); `hoursWorked = null` (không áp dụng).
   - `COLLABORATOR`: `hoursWorked = ZERO` khi khởi tạo (kế toán **phải nhập tay** qua
     `UpdatePayslip` trước khi `submitForApproval`); `grossPay` tính lại mỗi lần `UpdatePayslip`
     thay đổi `hoursWorked`.
   - `socialInsuranceEmployee`/`socialInsuranceEmployer` tính ngay theo `grossPay` ban đầu, tính
     lại mỗi lần `grossPay` đổi (qua `UpdatePayslip`).
   - `incomeTaxWithheld = ZERO` ban đầu — kế toán nhập tay qua `UpdatePayslip` (mục 2).
3. `PayrollRun.status = DRAFT`.
4. **Ràng buộc chặn submit thiếu dữ liệu**: `SubmitPayrollRunForApproval` từ chối (ném
   `InvalidContractTermsException`) nếu còn `Payslip` của hợp đồng `COLLABORATOR` với
   `hoursWorked == 0` — tránh duyệt nhầm lương 0 đồng cho CTV do quên nhập giờ dạy.

### 3.4 Lương tối thiểu vùng — ghi nhận, không tự chặn

Mục 2 xác nhận lương tối thiểu vùng đổi theo nghị định gần như hàng năm. V1 **không** tự động so
sánh `baseSalary`/`hourlyRate` với bảng lương tối thiểu vùng (cần bảng vùng theo địa bàn chi
nhánh + ngày hiệu lực, một hạng mục cấu hình riêng không nằm trong yêu cầu đã duyệt) — đây là
giới hạn được ghi nhận rõ trong spec, không phải thiếu sót bỏ quên. Hệ thống chỉ enforce ràng
buộc có thể kiểm tra thuần nội bộ, ổn định theo thời gian và không cần dữ liệu ngoài: lương thử
việc = 85% lương chính thức (đã tính ở `PayrollRules.baseGrossPay`).

### 3.5 `integrations.storage` — lưu file hợp đồng

Mirror `integrations.mail`:
- `StorageProperties.java` — `@ConfigurationProperties(prefix = "storage")`: `endpoint`,
  `region`, `bucket`, `accessKey`, `secretKey`. Biến môi trường tương ứng trong
  `.env.example`/`backend/.env.docker`: `STORAGE_ENDPOINT` (trống = AWS thật, có giá trị =
  MinIO/S3-compatible tự host), `STORAGE_REGION`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`,
  `STORAGE_SECRET_KEY` — đúng quy ước biến môi trường hiện có của dự án (`DB_HOST`,
  `REDIS_HOST`...).
- `StorageClient.java` — `String upload(String keyPrefix, String fileName, byte[] content,
  String contentType): String` (trả về object key đã lưu), `byte[] download(String key)`,
  `void delete(String key)`. Dùng AWS SDK v2 (`software.amazon.awssdk:s3`) với
  `S3Client.builder().endpointOverride(...)` khi `endpoint` được cấu hình (MinIO), bỏ qua
  `endpointOverride` khi trống (AWS thật) — một đoạn code chạy đúng cả hai môi trường, đúng tinh
  thần Rule 11 của `springboot-modular-scaffold` (prefix cấu hình theo tên module, không theo
  tên vendor).
- `docker-compose.yml` thêm service `minio` (image `minio/minio`) cho dev local, theo đúng cách
  file này đã ghi chú là chạy tại chỗ dùng container sẵn có của máy — **việc thêm MinIO vào
  compose cụ thể để lại cho giai đoạn viết plan** (cần kiểm tra lại cổng không đụng container
  khác đang chạy trên máy, ví dụ `agent-postgres`/`redis-stack` đã chiếm sẵn một số cổng).
- `package-info.java` — `@ApplicationModule(displayName = "Object Storage")`.
- Version AWS SDK v2 cụ thể: xác định tại thời điểm viết plan bằng cách tra Maven Central (không
  chốt cứng ở đây để tránh ghi một số phiên bản đã lỗi thời ngay khi viết).

`ContractAdminController` nhận file hợp đồng qua `multipart/form-data` ở endpoint tạo/cập nhật
hợp đồng, gọi `StorageClient.upload(...)`, lưu `contractFileKey` trả về vào `EmploymentContract`.
Endpoint riêng `GET /api/payroll/contracts/{id}/file` trả về file (stream lại qua
`StorageClient.download`, không ký URL trực tiếp tới S3/MinIO ở V1 — đơn giản hơn, chấp nhận chi
phí băng thông qua backend).

### 3.6 RBAC

`AccessConstants` (sửa trực tiếp, mirror cách Phase 2.5 thêm `TEACHER`/`STUDENT`):
- `Resources.PAYROLL = "PAYROLL"`.
- `Actions.APPROVE` đã tồn tại sẵn (chưa ai dùng tới) — dùng lại nguyên, không thêm action mới.
- `AccessRules`: `CREATE_PAYROLL`, `READ_PAYROLL`, `UPDATE_PAYROLL`, `APPROVE_PAYROLL` — theo
  đúng khuôn `MINIMUM_SCOPE = ORGANIZATION` như **mọi** `AccessRule` hiện có trong hệ thống (xác
  nhận bằng cách đọc lại toàn bộ `AccessConstants.AccessRules` hiện tại — không có resource nào
  dùng scope khác ORGANIZATION ở tầng `@PreAuthorize`; việc lọc theo chi nhánh là một bước lọc
  dữ liệu tuỳ chọn phía sau, như `ListAccounts`/`GetDashboardStats` đã làm, không phải một tầng
  scope PreAuthorize riêng).
- **Không** thêm `RoleCodes` cứng mới (không có `ACCOUNTANT`/`OWNER` trong `RoleCodes`) — đúng
  tinh thần RBAC linh hoạt đã có của dự án (vai trò là dữ liệu do tổ chức tự tạo qua màn hình RBAC
  có sẵn, không phải hằng số biên dịch). Migration seed chỉ cấp `PAYROLL:*:ORGANIZATION` cho
  `RoleCodes.ADMIN` (mirror V10/V13 — admin mặc định có mọi quyền); tổ chức tự tạo nhóm quyền
  "Kế toán" (CREATE+READ+UPDATE, không APPROVE) và "Chủ trung tâm" (đủ 4 quyền, gồm APPROVE) qua
  màn RBAC hiện có — không cần code thêm cho việc này, chỉ cần tài liệu hướng dẫn (ghi trong
  README hoặc hướng dẫn vận hành, ngoài phạm vi spec kỹ thuật).
- Migration `V14__seed_payroll_rbac.sql` mirror chính xác `V10__seed_courses_rbac.sql`/
  `V13__seed_teachers_students_rbac.sql`.
- Migration `V15__create_payroll_tables.sql` — `employment_contracts`, `contract_allowances`,
  `payroll_runs`, `payslips` (chi tiết cột viết ở bước plan, theo đúng field đã liệt kê ở 3.2).

### 3.7 `modules.audit`

Thêm `@ApplicationModuleListener` mới trong `AuditEventListeners` (file đã tồn tại từ Phase 1,
chỉ thêm method) nghe `PayrollEvents.ContractCreated`, `ContractTerminated`, `PayrollRunApproved`
— mirror chính xác cách các event khác (vd `AccessEvents.AccountJoinedGroup`) đã được audit.

## 4. Frontend

Mirror `entities/course` + `modules/courses` 1:1 về cấu trúc:
- `entities/payroll/model/payroll-schema.ts` — Zod schema khớp từng DTO ở mục 3.2 (dùng
  `z.string()` cho `BigDecimal`/tiền tệ qua wire, parse về number tại nơi hiển thị — đúng cách
  `accountSummarySchema` hiện đang xử lý các trường tương tự).
- `entities/payroll/api/{payroll-api.ts, payroll-keys.ts, use-contracts.ts, use-payroll-runs.ts}`.
- `modules/payroll/ui/contracts-page.tsx` — danh sách hợp đồng theo nhân viên, nút tạo/kết thúc
  hợp đồng, input tải lên file PDF.
- `modules/payroll/ui/payroll-runs-page.tsx` — danh sách kỳ lương, trang chi tiết một kỳ (bảng
  `Payslip` sửa được khi `DRAFT`, nút "Gửi duyệt"/"Duyệt"/"Từ chối" hiện theo
  `usePermissions().allows(ACCESS_RULE.approvePayroll)`).
- `modules/payroll/hooks/` — tách toàn bộ state/mutation ra hook riêng theo đúng Mandate #2 của
  `CLAUDE.md` (`use-contracts-page-controller.ts`, `use-payroll-run-detail-controller.ts`), các
  trang `ui/*.tsx` chỉ render.
- `shared/constants/permissions.ts` — thêm `RESOURCE.payroll`, `ACCESS_RULE.readPayroll/
  createPayroll/updatePayroll/approvePayroll`.
- `shared/constants/app-routes.ts` + `app/router/app-router.tsx` + nav — thêm mục "Lương & Hợp
  đồng", chỉ hiện khi `usePermissions().allows(ACCESS_RULE.readPayroll)` (mirror cách Teachers/
  Students đã làm ở Phase 2.5).

## 5. Kiểm thử

- `internal/rules/PayrollRulesTest.java` — pure JUnit, không I/O: thử việc 85%, CTV theo giờ,
  tính BHXH đúng/sai hai loại hợp đồng, `netPay` đúng công thức.
- `usecase/CreatePayrollRunTest.java` — Mockito, không DB: sinh đúng số `Payslip`, chỉ lấy hợp
  đồng `ACTIVE`, set `hoursWorked = 0` ban đầu cho CTV.
- `web/ContractAdminControllerIT.java`, `web/PayrollRunAdminControllerIT.java` — MockMvc +
  Testcontainers (Postgres/Redis), mirror `TeacherAdminControllerIT`: CRUD hợp đồng, upload file
  (dùng `StorageClient` thật trỏ container MinIO test hoặc mock — quyết định cụ thể ở bước plan),
  toàn bộ luồng `DRAFT → PENDING_APPROVAL → APPROVED`, từ chối submit khi còn CTV chưa nhập giờ,
  403 khi thiếu quyền `APPROVE_PAYROLL`.
- `ModularityTests` — pin thêm `modules.payroll` và `integrations.storage` vào danh sách module
  đã detect (mirror cách Task 8.5 của Phase 2.5 làm với `modules.teachers`/`modules.students`).

## 6. Review Focus (để plan + review cuối nhánh đặc biệt chú ý)

1. **Thử việc đổi giữa chừng kỳ lương**: nếu `probationEndDate` rơi vào giữa tháng đang chốt
   lương, `grossPay` tính như thế nào (toàn tháng theo trạng thái đầu kỳ, hay chia theo ngày)?
   Quyết định tại plan: **toàn tháng theo trạng thái tại ngày đầu kỳ** (đơn giản, giống cách đa số
   phần mềm lương SME xử lý; chia theo ngày là một cải tiến có thể thêm sau, không bắt buộc V1).
2. **Hợp đồng `COLLABORATOR` không có `probationStartDate`**: `CreateContract` phải từ chối nhận
   `probationStartDate`/`probationEndDate` khi `contractType = COLLABORATOR` (thử việc là khái
   niệm của HĐLĐ, không áp dụng CTV) — `InvalidContractTermsException`.
3. **Hai hợp đồng `ACTIVE` cùng lúc cho một `accountId`**: phải bị chặn (`CreateContract` kiểm
   tra `getActiveContractFor` trước, từ chối nếu đã có hợp đồng `ACTIVE`) — một người chỉ có một
   hợp đồng hiệu lực tại một thời điểm.
4. **Tạo `PayrollRun` trùng kỳ (year, month)**: phải bị chặn bởi unique constraint DB +
   kiểm tra tường minh ở usecase (trả lỗi rõ ràng, không để lộ constraint-violation thô).
5. **Tài khoản bị vô hiệu hoá (`Account.status = DISABLED`) nhưng còn hợp đồng `ACTIVE`**: không
   tự động `TERMINATED` hợp đồng — đây là hai khái niệm độc lập (tài khoản đăng nhập vs hợp đồng
   lao động); `CreatePayrollRun` vẫn tính lương cho họ nếu hợp đồng còn `ACTIVE` (ví dụ nhân viên
   nghỉ phép dài hạn bị khoá tài khoản tạm thời nhưng vẫn hưởng lương) — ghi rõ để tránh suy đoán
   sai trong lúc viết use case.

## 7. Bước tiếp theo

Sau khi spec này được duyệt: viết implementation plan chi tiết qua skill `writing-plans`, mirror
cách Phân hệ 2.5 đã làm (chỉ rõ file tiền lệ cụ thể để mirror từng bước: `modules.courses` cho
CRUD, `TeacherAdminControllerIT` cho test shape, `integrations.mail` cho `integrations.storage`).
