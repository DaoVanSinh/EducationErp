# Phân hệ 3 — Tuyển sinh & Học phí (Admissions & Tuition) — Thiết kế

## 1. Bối cảnh & Phạm vi

Phân hệ tiếp theo trong roadmap (`docs/superpowers/specs/2026-09-27-education-erp-architecture-design.md`, mục 8), phụ
thuộc Phân hệ 1 (Core/RBAC), Phân hệ 2 (`modules.courses`: Course/Class), Phân hệ 2.5 (`modules.students`:
StudentProfile) — cả ba đã xây xong, đang ở `develop`.

**Trong phạm vi:**
- Ghi danh (enrollment) học viên vào Class, theo dõi vòng đời ghi danh.
- Học phí tính theo Course, thu theo tối đa 3 đợt (installment) do kế toán tự quyết định số tiền mỗi đợt.
- Theo dõi công nợ/lịch sử thanh toán trên từng hoá đơn (invoice), hỗ trợ đóng một phần.
- Thanh toán online qua **MoMo** và **VNPay** (tích hợp đầy đủ: tạo link/QR + nhận callback/IPN tự động).
- Ghi nhận thanh toán thủ công (tiền mặt/chuyển khoản) do kế toán nhập tay.
- Tác vụ nền (Spring `@Scheduled`) đánh dấu hoá đơn quá hạn.

**Ngoài phạm vi (không làm ở phase này):**
- Gửi email/SMS nhắc nợ tự động (chỉ publish event `InvoiceOverdue`, việc gửi thông báo để phase sau).
- Chiết khấu/học bổng, phê duyệt giảm giá.
- Báo cáo doanh thu/dashboard tổng hợp học phí (Phân hệ Dashboard mở rộng sau nếu cần).
- Tự động chia đều số tiền từng đợt — kế toán tự nhập số tiền mỗi đợt.
- Huỷ ghi danh tự động khi tài khoản Account bị DISABLED (không có quy tắc nghiệp vụ nào yêu cầu — khác với Payroll,
  ở đây không rõ ai sẽ disable tài khoản phụ huynh/học viên trong hệ thống này).

**Đây là trung tâm dạy (tutoring center), không phải trường học** — không có khái niệm niên khoá/học kỳ cố định, học
viên có thể ghi danh nhiều Class của nhiều Course khác nhau cùng lúc bất kỳ thời điểm nào trong năm.

## 2. Nghiên cứu cổng thanh toán (đã tra cứu, không suy đoán)

| Cổng | Phí giao dịch | Thời gian về tiền | Độ phức tạp tích hợp |
|---|---|---|---|
| MoMo Business | 0.5%–2% | T+1 đến T+7 | Trung bình |
| VNPay | 1.8%–3.5% (nhóm cổng trung gian/thẻ) | T+3 đến T+7 | Cao hơn (nhiều thủ tục) |

Người dùng chọn tích hợp **cả hai**, đầy đủ API tạo link + callback/IPN tự động. Chi tiết API thật:

**VNPay** (sandbox: `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`): build URL với các tham số
`vnp_Version=2.1.0, vnp_Command=pay, vnp_TmnCode, vnp_Amount` (nhân 100 vì VNPay tính theo đơn vị xu),
`vnp_CurrCode=VND, vnp_TxnRef, vnp_OrderInfo, vnp_OrderType, vnp_Locale=vn, vnp_ReturnUrl, vnp_IpAddr,
vnp_CreateDate (yyyyMMddHHmmss)` → sort key theo alphabet → nối thành query string → HMAC-SHA512 với
`vnp_HashSecret` → gắn vào `vnp_SecureHash`. Verify callback/IPN: tách `vnp_SecureHash` ra khỏi tham số nhận được,
hash lại phần còn lại (sort alphabet) bằng cùng secret, so khớp; `vnp_ResponseCode=00` là giao dịch thành công.

**MoMo** (sandbox: `POST https://test-payment.momo.vn/v2/gateway/api/create`): body JSON gồm
`partnerCode, accessKey, requestId, amount, orderId, orderInfo, redirectUrl, ipnUrl, requestType=captureWallet,
signature`. `signature` = HMAC-SHA256 của chuỗi
`accessKey=$accessKey&amount=$amount&extraData=$extraData&ipnUrl=$ipnUrl&orderId=$orderId&orderInfo=$orderInfo&partnerCode=$partnerCode&redirectUrl=$redirectUrl&requestId=$requestId&requestType=$requestType`
ký bằng `secretKey`. Response trả `payUrl` để redirect/hiển thị QR. IPN: MoMo POST JSON tới `ipnUrl` cấu hình sẵn,
verify signature theo cùng công thức, `resultCode=0` là thành công.

Nguồn: [vnpay.js docs](https://vnpay.js.org/en/create-payment-url), [MoMo developers — One-Time Payments](https://developers.momo.vn/v3/docs/payment/api/credit/onetime/).

## 3. Kiến trúc module

Ba thành phần mới, tuân thủ nguyên tắc tránh God Module (`springboot-modular-scaffold`):

```
modules.enrollment  →  modules.courses (facade), modules.students (facade)
modules.billing     →  modules.enrollment (facade), modules.courses (facade)
integrations.payment → không phụ thuộc module nào, chỉ implement 2 client (MoMo/VNPay)
modules.billing     →  integrations.payment (gọi trực tiếp, giống modules.payroll gọi integrations.storage)
```

`modules.enrollment` KHÔNG biết đến `modules.billing` (không phụ thuộc ngược). `modules.courses` cần sửa: thêm field
`tuitionFee` vào `Course` (hiện chưa có field học phí nào).

## 4. `modules.enrollment`

### Entity `Enrollment` (package `internal.model`)
- `id: UUID`
- `studentProfileId: UUID` (not null, immutable)
- `classId: UUID` (not null, immutable)
- `courseId: UUID` (not null, immutable — snapshot từ `Class.course.id` lúc ghi danh)
- `branchId: UUID` (not null, immutable — snapshot từ `Class.branchId`)
- `status: EnrollmentConstants.EnrollmentStatus` (not null, default `ACTIVE`)
- `enrolledAt: Instant` (not null)
- `withdrawnAt: Instant` (nullable)
- `createdByAccountId: UUID` (not null)

DB: partial unique index `(student_profile_id, class_id) WHERE status = 'ACTIVE'` — chặn ghi danh trùng ở tầng DB,
không chỉ tầng application (defense in depth).

### `EnrollmentConstants`
```java
public final class EnrollmentConstants {
    private EnrollmentConstants() {}
    public enum EnrollmentStatus { ACTIVE, WITHDRAWN, COMPLETED }
}
```

### `EnrollmentException` + subclasses
`EnrollmentNotFoundException`, `StudentProfileNotFoundException`, `StudentNotActiveException`,
`ClassNotFoundException`, `ClassNotActiveException`, `ClassFullException`, `DuplicateActiveEnrollmentException`,
`EnrollmentNotActiveException` (rút/hoàn tất khi không ở trạng thái ACTIVE).

### `EnrollmentEvents`
`EnrollmentCreated`, `EnrollmentWithdrawn`, `EnrollmentCompleted` — đều `(UUID enrollmentId, UUID actorAccountId,
UUID actorBranchId)`, mirror `CoursesEvents`.

### Mở rộng facade các module khác cần dùng
- `CoursesManagement` thêm:
  ```java
  record ClassInfoResponse(UUID classId, UUID courseId, UUID branchId, int maxSeats, boolean active) {}
  @Transactional(readOnly = true)
  Optional<ClassInfoResponse> getClassInfo(UUID classId);
  ```
- `StudentsManagement` thêm:
  ```java
  record StudentSummaryResponse(UUID studentProfileId, UUID accountId, boolean active) {}
  @Transactional(readOnly = true)
  Optional<StudentSummaryResponse> getProfile(UUID studentProfileId);
  ```

### `EnrollmentManagement` facade (cho `modules.billing` gọi)
```java
record EnrollmentSummaryResponse(UUID enrollmentId, UUID studentProfileId, UUID courseId, UUID branchId,
                                  EnrollmentConstants.EnrollmentStatus status) {}
@Transactional(readOnly = true)
Optional<EnrollmentSummaryResponse> getEnrollment(UUID enrollmentId);
```

### Usecase `CreateContract`-tương-đương: `CreateEnrollment`
1. `studentsManagement.getProfile(studentProfileId)` → rỗng → `StudentProfileNotFoundException`; `active=false` →
   `StudentNotActiveException`.
2. `coursesManagement.getClassInfo(classId)` → rỗng → `ClassNotFoundException`; `active=false` →
   `ClassNotActiveException`.
3. Đếm `Enrollment` có `classId` + `status=ACTIVE` qua repository; nếu `>= maxSeats` → `ClassFullException`.
4. Kiểm tra đã tồn tại `Enrollment` ACTIVE cùng `(studentProfileId, classId)` → `DuplicateActiveEnrollmentException`.
5. Lưu `Enrollment(courseId = classInfo.courseId(), branchId = classInfo.branchId(), status=ACTIVE,
   enrolledAt=now)`.
6. Publish `EnrollmentCreated`.

### `WithdrawEnrollment` / `CompleteEnrollment`
Load theo id; nếu `status != ACTIVE` → `EnrollmentNotActiveException`; set `WITHDRAWN`/`COMPLETED` (+`withdrawnAt`
cho withdraw); publish event tương ứng. **Rút khỏi lớp không huỷ các invoice đã phát hành** — chỉ chặn
`modules.billing` tạo invoice mới cho enrollment không còn `ACTIVE` (xem mục 5).

### `ListEnrollments`
Phân trang, lọc theo `studentProfileId` và/hoặc `classId` (tham số optional).

### Web — `EnrollmentAdminController`
| Method | Path | Quyền |
|---|---|---|
| POST | `/api/enrollment/enrollments` | `CREATE_ENROLLMENT` |
| GET | `/api/enrollment/enrollments` | `READ_ENROLLMENT` |
| GET | `/api/enrollment/enrollments/{id}` | `READ_ENROLLMENT` |
| POST | `/api/enrollment/enrollments/{id}/withdraw` | `UPDATE_ENROLLMENT` |
| POST | `/api/enrollment/enrollments/{id}/complete` | `UPDATE_ENROLLMENT` |

DTO: `CreateEnrollmentRequest(studentProfileId, classId)`,
`EnrollmentResponse(id, studentProfileId, classId, courseId, branchId, status, enrolledAt, withdrawnAt)`.

## 5. `modules.billing`

### Sửa `modules.courses`: thêm học phí vào `Course`
- `Course.tuitionFee: BigDecimal` (nullable, scale 0 — VND không có phần thập phân), có setter.
- `CreateCourseRequest`/`UpdateCourseRequest`/`CourseResponse` thêm field `tuitionFee` (nullable khi tạo — khoá học
  cũ/ mới có thể chưa gắn giá ngay).
- `CoursesManagement` thêm:
  ```java
  record CourseTuitionResponse(UUID courseId, BigDecimal tuitionFee, boolean active) {}
  @Transactional(readOnly = true)
  Optional<CourseTuitionResponse> getCourseTuition(UUID courseId);
  ```

### Entity `Invoice`
- `id, enrollmentId, studentProfileId, courseId, branchId` (snapshot từ `EnrollmentSummaryResponse` lúc tạo)
- `installmentNumber: int` (1..3, immutable)
- `amount: BigDecimal` (not null, scale 0)
- `amountPaid: BigDecimal` (not null, default `0`, scale 0)
- `status: BillingConstants.InvoiceStatus`
- `dueDate: LocalDate` (not null)
- `issuedAt: Instant` (not null)
- `createdByAccountId: UUID`

### Entity `Payment` (cùng module, FK JPA thật tới `Invoice`)
- `id, invoice (@ManyToOne, not null, updatable=false), amount, method: BillingConstants.PaymentMethod,
  gatewayTransactionId: String (nullable, unique khi có giá trị), status: BillingConstants.PaymentStatus,
  paidAt: Instant (nullable), createdAt: Instant`

### `BillingConstants`
```java
public final class BillingConstants {
    private BillingConstants() {}
    public enum InvoiceStatus { UNPAID, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED }
    public enum PaymentMethod { MOMO, VNPAY, MANUAL }
    public enum PaymentStatus { PENDING, SUCCESS, FAILED }
    public static final class Limits {
        private Limits() {}
        public static final int MAX_INSTALLMENTS_PER_ENROLLMENT = 3;
    }
}
```

### `BillingException` + subclasses
`InvoiceNotFoundException`, `PaymentNotFoundException`, `EnrollmentNotFoundException` (tham chiếu enrollment không
tồn tại), `EnrollmentNotActiveForBillingException`, `CourseNotFoundException`, `CourseTuitionNotConfiguredException`
(Course chưa gắn `tuitionFee`), `InstallmentLimitExceededException` (đã đủ 3 đợt), `InvoiceAmountExceedsTuitionException`
(tổng các đợt vượt học phí Course), `InvoiceNotPayableException` (đã `PAID`/`CANCELLED` mà vẫn cố thanh toán),
`InvalidPaymentAmountException`, `InvalidCallbackSignatureException`, `UnknownPaymentGatewayException`.

### `BillingEvents`
`InvoiceCreated`, `PaymentReceived`, `InvoiceOverdue` — `(UUID id, UUID actorAccountId (nullable khi hệ thống tự
sinh), UUID actorBranchId)`.

### Usecase `CreateInvoice(enrollmentId, amount, dueDate, actor)`
1. `enrollmentManagement.getEnrollment(enrollmentId)` rỗng → `EnrollmentNotFoundException`; `status != ACTIVE` →
   `EnrollmentNotActiveForBillingException`.
2. `coursesManagement.getCourseTuition(enrollment.courseId())` rỗng → `CourseNotFoundException`; `tuitionFee == null`
   → `CourseTuitionNotConfiguredException`.
3. Đếm invoice hiện có của `enrollmentId` với `status != CANCELLED`; `>= 3` → `InstallmentLimitExceededException`.
4. Tổng `amount` các invoice hiện có (status != CANCELLED) + `amount` mới > `tuitionFee` →
   `InvoiceAmountExceedsTuitionException`.
5. `installmentNumber = số invoice hiện có + 1`.
6. Lưu `Invoice(status=UNPAID, amountPaid=0, issuedAt=now)`; publish `InvoiceCreated`.

### Usecase `InitiateOnlinePayment(invoiceId, gateway, actor)`
1. Load invoice; `status` không thuộc `{UNPAID, PARTIALLY_PAID}` → `InvoiceNotPayableException`.
2. `remaining = amount - amountPaid`.
3. `orderId = invoiceId + "-" + installmentNumber + "-" + System.currentTimeMillis()` (duy nhất, không trùng trong
   ngày theo yêu cầu VNPay).
4. Chọn `PaymentGatewayClient` theo `gateway` (resolver nội bộ map `BillingConstants.PaymentMethod` →
   `integrations.payment.PaymentGatewayType`, MANUAL không hợp lệ ở đây → `UnknownPaymentGatewayException`).
5. `client.createPaymentUrl(new PaymentRequest(orderId, remaining, orderInfo, returnUrl, ipnUrl))` → `payUrl`.
6. Lưu `Payment(invoice, amount=remaining, method=gateway, gatewayTransactionId=orderId, status=PENDING,
   createdAt=now)`.
7. Trả `payUrl` cho controller (không publish event ở bước này — chỉ khi có kết quả thật từ callback).

### Usecase `HandlePaymentCallback(gatewayType, rawParams)`
1. `client.verifyCallback(rawParams)` — sai chữ ký → `InvalidCallbackSignatureException` (controller trả 400, không
   lộ thông tin nội bộ).
2. Tìm `Payment` theo `gatewayTransactionId = result.orderId()`; không thấy → log cảnh báo, return (bỏ qua, không
   throw — tránh để kẻ tấn công dò lỗi qua response code).
3. **Idempotency bắt buộc**: nếu `Payment.status != PENDING` → return ngay, không xử lý lại (chặn callback gọi 2
   lần cộng tiền 2 lần).
4. `result.success() == true`: `Payment.status=SUCCESS, paidAt=now`; `Invoice.amountPaid += Payment.amount`;
   `Invoice.status = amountPaid >= amount ? PAID : PARTIALLY_PAID`; publish `PaymentReceived`.
   `false`: `Payment.status=FAILED`.

### Usecase `RecordManualPayment(invoiceId, amount, actor)`
1. Load invoice; không thuộc `{UNPAID, PARTIALLY_PAID}` → `InvoiceNotPayableException`.
2. `amount <= 0` hoặc `amount > (invoice.amount - invoice.amountPaid)` → `InvalidPaymentAmountException`.
3. Lưu `Payment(invoice, amount, method=MANUAL, status=SUCCESS, paidAt=now, createdAt=now)`.
4. Cập nhật `amountPaid`/`status` như trên; publish `PaymentReceived`.

### Usecase `CancelInvoice(invoiceId, actor)`
Cho phép kế toán huỷ một invoice tạo nhầm. Chỉ cho phép khi `status == UNPAID` (chưa có bất kỳ `Payment` nào —
`PARTIALLY_PAID` tức là đã có tiền vào, không được huỷ, phải xử lý hoàn tiền ngoài hệ thống trước). Khác `UNPAID` →
`InvoiceNotPayableException`. Set `status = CANCELLED`. Invoice đã `CANCELLED` không tính vào tổng 3 đợt/giới hạn
học phí ở `CreateInvoice` (đã mô tả ở bước 3-4 mục trên). Dùng `UPDATE_INVOICE` (không dùng `Actions.DELETE` — giữ
nhất quán với toàn hệ thống hiện chưa dùng `DELETE` ở bất kỳ resource nào, state chỉ chuyển qua `UPDATE`).

### Usecase `MarkOverdueInvoices()` (không nhận request, gọi bởi scheduler)
Tìm `Invoice` có `status IN (UNPAID, PARTIALLY_PAID)` và `dueDate < LocalDate.now()` → set `OVERDUE`, publish
`InvoiceOverdue` cho từng cái. Idempotent tự nhiên (lần chạy sau không tìm thấy nữa vì status đã đổi).

### `OverdueInvoiceScheduler` (package `internal`)
```java
@Component
class OverdueInvoiceScheduler {
    private final MarkOverdueInvoices useCase;
    @Scheduled(cron = "0 0 1 * * *") // 1:00 sáng mỗi ngày
    void run() { useCase.execute(); }
}
```
Job thân rất mỏng — chỉ gọi usecase, mọi logic nghiệp vụ nằm trong `MarkOverdueInvoices`.

### `ListInvoices` / `GetInvoiceDetail` / `GetPaymentStatus`
- `ListInvoices(studentProfileId?, enrollmentId?, status?, pageable)` → `Page<InvoiceResponse>`.
- `GetInvoiceDetail(invoiceId)` → `InvoiceDetailResponse` (các field Invoice + `List<PaymentResponse>` lịch sử
  thanh toán, sắp xếp `createdAt DESC`).
- `GetPaymentStatus(gatewayTransactionId)` → `PaymentResponse` — dùng cho trang Return URL frontend poll trạng thái
  sau khi phụ huynh quay lại từ cổng thanh toán (vì IPN là nguồn sự thật, xử lý bất đồng bộ, có thể chưa kịp cập
  nhật lúc redirect về).

### Web
**`InvoiceAdminController`**

| Method | Path | Quyền |
|---|---|---|
| POST | `/api/billing/invoices` | `CREATE_INVOICE` |
| GET | `/api/billing/invoices` | `READ_INVOICE` |
| GET | `/api/billing/invoices/{id}` | `READ_INVOICE` |
| POST | `/api/billing/invoices/{id}/online-payment` | `UPDATE_INVOICE` |
| POST | `/api/billing/invoices/{id}/manual-payment` | `UPDATE_INVOICE` |
| POST | `/api/billing/invoices/{id}/cancel` | `UPDATE_INVOICE` |
| GET | `/api/billing/payments/{gatewayTransactionId}/status` | `READ_INVOICE` |

**`PaymentCallbackController`** — **public, không `@PreAuthorize`, không yêu cầu đăng nhập** (cổng thanh toán gọi
server-to-server, không có session/cookie của ứng dụng):
| Method | Path |
|---|---|
| GET | `/api/billing/payments/callback/vnpay` (VNPay gọi IPN bằng GET query params) |
| POST | `/api/billing/payments/callback/momo` (MoMo gọi IPN bằng POST JSON) |

**Cấu hình bảo mật quan trọng**: thêm đúng 2 path trên vào danh sách `permitAll()` trong Spring Security config,
giống cách `login`/`forgot-password`/`reset-password` đã public hiện nay. Xác thực không qua session mà qua verify
chữ ký (HMAC) trong chính usecase — sai chữ ký thì từ chối ở tầng usecase, không phải tầng Security.

DTO: `CreateInvoiceRequest(enrollmentId, amount, dueDate)`,
`InvoiceResponse(id, enrollmentId, studentProfileId, courseId, branchId, installmentNumber, amount, amountPaid,
status, dueDate, issuedAt)`, `PaymentResponse(id, amount, method, status, paidAt, createdAt)`,
`InvoiceDetailResponse(... + List<PaymentResponse> payments)`,
`InitiateOnlinePaymentRequest(gateway)`, `InitiateOnlinePaymentResponse(payUrl)`,
`RecordManualPaymentRequest(amount)`.

## 6. `integrations.payment`

Mirror `integrations.storage` (client trừu tượng + 2 implementation), KHÔNG phụ thuộc bất kỳ `modules.*` nào.

```java
public enum PaymentGatewayType { MOMO, VNPAY }

public record PaymentRequest(String orderId, BigDecimal amount, String orderInfo, String returnUrl, String ipnUrl) {}
public record PaymentUrlResult(String payUrl, String gatewayOrderId) {}
public record PaymentCallbackResult(String orderId, boolean success, BigDecimal amount, String rawMessage) {}

public interface PaymentGatewayClient {
    PaymentGatewayType type();
    PaymentUrlResult createPaymentUrl(PaymentRequest request);
    PaymentCallbackResult verifyCallback(Map<String, String> rawParams);
}
```

- `MomoProperties` (`@ConfigurationProperties(prefix = "payment.momo")`): `partnerCode, accessKey, secretKey,
  endpoint, redirectUrl, ipnUrl`.
- `VnPayProperties` (`@ConfigurationProperties(prefix = "payment.vnpay")`): `tmnCode, hashSecret, payUrl, returnUrl,
  ipnUrl`.
- `MomoPaymentGatewayClient`, `VnPayPaymentGatewayClient` — `@Component`, implement đúng công thức ký ở mục 2.
  `verifyCallback` của MoMo nhận `Map<String,String>` được controller convert từ JSON body (Jackson `ObjectMapper`
  đọc body thành `Map`).
- `application.yml` thêm block `payment.momo`/`payment.vnpay` với giá trị sandbox mặc định (giống cách `storage` đã
  làm ở Phân hệ 2.6), đọc từ biến môi trường, không hardcode secret thật trong code.
- `modules.billing.internal` có 1 `PaymentGatewayClientResolver` (`@Component`) nhận `List<PaymentGatewayClient>` từ
  Spring, map theo `PaymentGatewayType`, expose `resolve(BillingConstants.PaymentMethod): PaymentGatewayClient`
  (ném `UnknownPaymentGatewayException` nếu gọi với `MANUAL`).

## 7. RBAC

Thêm vào `AccessConstants` (`modules.access`), theo đúng pattern khối `PAYROLL` gần nhất:
- `Resources.ENROLLMENT`, `Resources.INVOICE` (mới).
- Dùng lại `Actions.CREATE/READ/UPDATE` hiện có — **không cần `APPROVE`** (không có luồng duyệt ở phân hệ này).
- `AccessRules`: `CREATE_ENROLLMENT, READ_ENROLLMENT, UPDATE_ENROLLMENT, CREATE_INVOICE, READ_INVOICE,
  UPDATE_INVOICE` — scope tối thiểu `ORGANIZATION` như mọi rule hiện có.
- Không thêm `RoleCodes` cứng mới — Kế toán/Lễ tân là role tự tạo qua RBAC UI có sẵn (đúng quyết định đã chọn ở
  Phân hệ 2.6, áp dụng lại ở đây).

## 8. Migration

Tiếp theo `V15` (Phân hệ 2.6 payroll):
- `V16__add_course_tuition_fee.sql` — `ALTER TABLE courses ADD COLUMN tuition_fee NUMERIC(14,0);`
- `V17__create_enrollments.sql` — bảng `enrollments` + partial unique index `(student_profile_id, class_id) WHERE
  status = 'ACTIVE'`.
- `V18__create_billing_tables.sql` — bảng `invoices`, `payments` (FK `payments.invoice_id → invoices.id`).
- `V19__seed_enrollment_billing_rbac.sql` — seed permission `ENROLLMENT:{CREATE,READ,UPDATE}` và
  `INVOICE:{CREATE,READ,UPDATE}` vào permission group ADMIN (UUID `11111111-0000-0000-0000-000000000001`, cùng
  group đã dùng ở `V10`/`V13`/`V14`).

## 9. `modules.audit`

Thêm vào `AuditConstants`: `Actions.ENROLLMENT_CREATE, ENROLLMENT_WITHDRAW, ENROLLMENT_COMPLETE, INVOICE_CREATE,
PAYMENT_RECEIVED, INVOICE_OVERDUE`; `EntityTypes.ENROLLMENT, INVOICE`. `AuditEventListeners` thêm
`@ApplicationModuleListener` cho `EnrollmentEvents` (3 loại) và `BillingEvents` (3 loại) — mirror cách Phân hệ 2.6
nghe `PayrollEvents`. `InvoiceOverdue` không có `actorAccountId` (hệ thống tự sinh) — audit log ghi `actorAccountId
= null`/hệ thống, cần `AuditEventListeners` xử lý được actor rỗng (kiểm tra pattern hiện có cho sự kiện hệ thống
sinh, nếu chưa có thì đây là điểm cần lưu ý khi viết plan).

## 10. Frontend

Mirror `entities/course` + `modules/courses`:
- `entities/enrollment/` — `model/schema.ts` (Zod), `api/enrollment-api.ts`, `api/enrollment-keys.ts`,
  `hooks/use-enrollments.ts`.
- `entities/billing/` — schema cho `Invoice`/`Payment`, `api/billing-api.ts`, `api/billing-keys.ts`,
  `hooks/use-invoices.ts`.
- `modules/enrollment/` — trang danh sách ghi danh theo học viên/lớp, dialog tạo ghi danh (chọn học viên + lớp,
  validate client-side còn chỗ dựa trên dữ liệu trả về), nút Rút/Hoàn tất.
- `modules/billing/` — trang hoá đơn theo enrollment (hiển thị tổng học phí Course, các đợt đã tạo, số dư còn lại),
  dialog "Tạo đợt thu" (nhập số tiền + hạn), nút "Thu online" (chọn MoMo/VNPay → mở `payUrl` ở tab mới), nút "Ghi
  nhận thanh toán thủ công" (nhập số tiền), bảng lịch sử thanh toán trong chi tiết hoá đơn.
- `app/` — route mới `/payment/return/:gateway` (public, không cần đăng nhập — phụ huynh có thể chưa login khi quay
  về từ cổng thanh toán): đọc query param `orderId`/`vnp_TxnRef` tương ứng, gọi `GET
  /api/billing/payments/{gatewayTransactionId}/status`, hiển thị "Đang xử lý"/"Thành công"/"Thất bại" (không tự
  đánh dấu thành công chỉ dựa vào query param trên URL — luôn gọi lại API để lấy trạng thái thật đã được IPN xác
  nhận).
- `frontend/src/shared/constants/permissions.ts`, `api-routes.ts`, `app-routes.ts` thêm theo đúng mirror.
- Tách logic khỏi render theo đúng 3 Mandates: `hooks/use-enrollments-page-controller.ts`,
  `hooks/use-invoice-detail-controller.ts`.

## 11. Testing & Review Focus

TDD toàn bộ. `integrations.payment`: unit test thuần (không gọi mạng thật) cho hàm build chữ ký — cố định input mẫu,
assert ra đúng hash tính tay theo công thức ở mục 2 (pin thuật toán, không chỉ test round-trip sign→verify vì vậy
sẽ không phát hiện nếu cả 2 hàm cùng sai theo cùng một cách).

**Review Focus** (5 điều cần test cụ thể, không chỉ suy luận từ spec):
1. Ghi danh trùng: gọi `CreateEnrollment` 2 lần cùng `(studentProfileId, classId)` khi lần đầu còn `ACTIVE` → lần 2
   phải bị chặn ở cả tầng usecase (đếm/exists query) lẫn tầng DB (unique index) — test cả hai, không chỉ một.
2. `ClassFullException` khi số `Enrollment ACTIVE` đã bằng `maxSeats` — test ở ranh giới chính xác (`maxSeats - 1`
   ghi danh được, ghi danh thứ `maxSeats` cũng được, ghi danh thứ `maxSeats + 1` bị chặn).
3. Tổng tiền 3 đợt vượt học phí Course: test case tạo đợt 1 = 50%, đợt 2 = 50%, đợt 3 bất kỳ số tiền dương nào đều
   phải bị `InvoiceAmountExceedsTuitionException` (không chỉ test đúng giới hạn `MAX_INSTALLMENTS=3`).
4. Idempotency của `HandlePaymentCallback`: gọi callback thành công 2 lần với cùng `gatewayTransactionId` →
   `Invoice.amountPaid` chỉ cộng 1 lần, `Payment.status` ở lần gọi thứ 2 không bị ghi đè lại từ `SUCCESS`.
5. Callback với chữ ký sai (`InvalidCallbackSignatureException`) không được làm lộ việc `orderId` có tồn tại hay
   không qua response code/message khác nhau — test rằng cả "chữ ký sai" và "orderId không tồn tại" trả về cùng một
   dạng phản hồi từ `PaymentCallbackController` (tránh oracle cho kẻ tấn công dò order id hợp lệ).

## 12. Tổng kết quyết định đã chốt với người dùng

- Enrollment là entity riêng, học viên ghi danh nhiều Class cùng lúc.
- Học phí tính theo Course (không theo Class).
- Thu theo tối đa 3 đợt, kế toán tự nhập % / số tiền mỗi đợt khi tạo invoice (không tự động chia đều).
- Theo dõi thanh toán một phần trên từng Invoice (`UNPAID/PARTIALLY_PAID/PAID/OVERDUE/CANCELLED` — `CANCELLED` dùng
  cho usecase `CancelInvoice` khi kế toán tạo nhầm, chỉ cho phép lúc invoice chưa nhận bất kỳ khoản thanh toán nào).
- Overdue kiểm tra bằng Spring `@Scheduled` job mỏng, không dùng hạ tầng polling riêng.
- Cả MoMo và VNPay, tích hợp đầy đủ API + callback/IPN tự động.
- `modules.enrollment` và `modules.billing` tách riêng, giao tiếp qua facade.
- Rút khỏi lớp giữ nguyên công nợ đã phát sinh, chỉ chặn tạo invoice mới.
