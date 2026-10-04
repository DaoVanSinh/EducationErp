# Phân hệ 3.1 — Combo khoá học giảm giá (bổ sung cho Tuyển sinh & Học phí)

## 1. Bối cảnh & Phạm vi

Phân hệ 3 (Tuyển sinh & Học phí) đã xây xong và merge vào `develop`: mỗi `Enrollment` (ghi danh vào
một `Class` cụ thể) có các `Invoice` riêng, tối đa 3 đợt, tổng không vượt `Course.tuitionFee`.

Yêu cầu mới: khi một học viên ghi danh **nhiều khoá cùng lúc**, kế toán có thể gộp các khoá đó thành
một **combo** được giảm giá theo số lượng khoá, và thu tiền combo đó theo đúng quy tắc cũ — **tối đa
3 đợt, một hạn đóng chung cho cả combo** (không phải 3 đợt cho từng khoá riêng).

Các quyết định đã chốt với người dùng (brainstorm ngày 2026-10-04):

1. **Combo không phải một thực thể định sẵn** — kế toán tự chọn các `Enrollment` đang `ACTIVE` của
   một học viên lúc tạo combo; hệ thống tự tính % giảm giá theo **số lượng khoá** trong combo.
2. **Bậc giảm giá (số khoá → % giảm) do admin cấu hình qua UI**, lưu trong DB — không hardcode.
3. **Hạn đóng (due date) của combo do kế toán tự nhập** khi tạo combo — không tự suy ra từ lịch học.
4. **Tối đa 3 đợt đóng cho CẢ combo** (không phải 3 đợt/khoá), tổng không vượt tổng tiền combo sau
   giảm giá — mirror đúng logic `CreateInvoice` hiện có, chỉ đổi đơn vị neo từ `enrollmentId` sang
   `comboId`.

## 2. Vì sao Combo là một đơn vị thanh toán độc lập, không chia nhỏ về từng Enrollment

Một thiết kế khác đã xem xét: chia tiền giảm giá về lại từng `Enrollment`, mỗi khoá vẫn có `Invoice`
riêng với số tiền đã giảm. Phương án này **bị loại** vì người dùng mô tả rõ: "đóng combo tối đa 3
lần, hạn đến khi nào đó" — nghĩa là **một tổng tiền, một lịch đóng** cho toàn combo. Chia nhỏ về từng
khoá sẽ phá đúng yêu cầu này (lại thành N hoá đơn độc lập, N hạn riêng, không còn khái niệm "combo").

Vì vậy: **`Combo` là một đơn vị billing độc lập**, nằm trong `modules.billing` (đây là khái niệm thanh
toán, không phải khái niệm ghi danh — `modules.enrollment` không đổi gì). `Invoice` sẽ thuộc về
**đúng một trong hai**: một `Enrollment` (luồng cũ, giữ nguyên 100%) hoặc một `Combo` (luồng mới).

## 3. Combo không tạo Enrollment mới — chỉ gộp Enrollment đã có sẵn

`EnrollmentManagement` (facade của `modules.enrollment`) hiện chỉ public
`getEnrollment(UUID): Optional<EnrollmentSummaryResponse>` — không có method tạo ghi danh nào lộ ra
module khác. Thêm một method tạo ghi danh vào facade để `modules.billing` gọi xuyên module sẽ là một
kiểu orchestration-ghi mới (billing tạo enrollment lẫn invoice trong một transaction) — rủi ro hơn
mức cần thiết cho yêu cầu này.

**Quyết định:** kế toán ghi danh từng khoá như bình thường trước (luồng `CreateEnrollment` có sẵn,
không đổi gì), sau đó mới "Tạo combo" bằng cách **chọn các `Enrollment` đã `ACTIVE`** của học viên đó.
`CreateCombo` chỉ gọi `enrollmentManagement.getEnrollment(id)` (đã có sẵn, read-only) cho từng id được
chọn — đúng mirror cách `CreateInvoice` đang làm, không cần thêm quyền ghi xuyên module nào.

## 4. Data model mới (`modules.billing.internal.model`)

### `Combo`

| Field | Kiểu | Ghi chú |
|---|---|---|
| `id` | UUID | PK |
| `studentProfileId` | UUID | bắt buộc — mọi Enrollment trong combo phải cùng học viên này |
| `branchId` | UUID | bắt buộc — lấy từ chính Enrollment đầu tiên (giống `CreateInvoice` lấy `branchId` từ enrollment, không phải từ actor — accountant cấp tổ chức có thể tạo combo cho bất kỳ chi nhánh nào), mọi Enrollment còn lại trong combo phải cùng giá trị này |
| `totalOriginalAmount` | BigDecimal | tổng `tuitionFee` của các Course, snapshot lúc tạo |
| `discountPercent` | BigDecimal | % giảm giá áp dụng, snapshot lúc tạo (bậc giảm giá có thể đổi sau, không ảnh hưởng combo đã tạo) |
| `totalDiscountedAmount` | BigDecimal | `totalOriginalAmount * (1 - discountPercent/100)`, scale 0 (`BillingRules.money`) |
| `dueDate` | LocalDate | kế toán nhập tay lúc tạo |
| `createdByAccountId` | UUID | |
| `createdAt` | Instant | |
| `enrollments` | `List<ComboEnrollment>` | `@OneToMany(cascade = ALL, orphanRemoval = true)`, mirror `Class.schedule` |

Không có cột `status`: một combo **chỉ tồn tại khi chưa bị huỷ** — huỷ combo (mục 6) xoá cứng cả
`Combo` và `ComboEnrollment` của nó (chỉ được phép huỷ khi chưa phát hành hoá đơn nào — xem mục 6),
nên không cần trạng thái `CANCELLED` tồn tại song song như `Invoice`. Một khi đã có hoá đơn, combo
không thể huỷ được nữa — tại thời điểm đó nó là một chứng từ tài chính đã chốt, không phải bản nháp.

### `ComboEnrollment` (con của `Combo`, cùng module — mirror `Class`→`ClassSchedule`)

| Field | Kiểu | Ghi chú |
|---|---|---|
| `id` | UUID | PK |
| `combo` | `@ManyToOne` tới `Combo` | cùng module, quan hệ JPA thật |
| `enrollmentId` | UUID | tham chiếu `modules.enrollment`, UUID trần (rule #3 — không `@ManyToOne` xuyên module) |
| `courseId` | UUID | snapshot từ `EnrollmentSummaryResponse.courseId()` lúc tạo combo |
| `originalTuitionFee` | BigDecimal | snapshot `CourseTuitionResponse.tuitionFee()` lúc tạo combo |

**`enrollment_id` có UNIQUE constraint ở DB** (không cần partial/`WHERE` — vì huỷ combo xoá cứng
dòng này, nên dòng chỉ tồn tại khi combo đó còn "sống"; một enrollment chỉ có thể nằm trong đúng một
combo còn sống tại một thời điểm). Đây là lớp phòng thủ cho race hai request đồng thời cùng chọn một
`enrollmentId` vào hai combo khác nhau — `CreateCombo` dùng `saveAndFlush` + bắt
`DataIntegrityViolationException` dịch sang `EnrollmentAlreadyInComboException`, mirror
`CreateInvoice` (Phase 3 final review Important #3).

### `ComboDiscountTier` (cấu hình admin, module-level, không thuộc một Combo cụ thể)

| Field | Kiểu | Ghi chú |
|---|---|---|
| `id` | UUID | PK |
| `minCourseCount` | int | UNIQUE — "từ N khoá trở lên" |
| `discountPercent` | BigDecimal | 0-100 |
| `active` | boolean | không có `Actions.DELETE` trong hệ thống này (xác nhận lại ở `InvoiceAdminController`) — "xoá" một bậc giảm giá nghĩa là tắt `active`, mirror `Course.active` |

**Cách chọn bậc:** lấy tier có `minCourseCount` lớn nhất thoả `minCourseCount <= số khoá trong combo`
VÀ `active = true`. Không có tier nào thoả → `ComboDiscountTierNotConfiguredException` (chặn hẳn,
không mặc định 0% — mirror `CourseTuitionNotConfiguredException` của Phase 3: giá chưa cấu hình thì
chặn, không suy đoán).

## 5. Thay đổi trên `Invoice` (bảng đã có dữ liệu thật — ALTER, không phải CREATE)

`Invoice.enrollmentId`/`Invoice.courseId` hiện là `NOT NULL`. Để một Invoice có thể thuộc về
`Combo` thay vì `Enrollment`, migration V22:

```sql
ALTER TABLE invoices ALTER COLUMN enrollment_id DROP NOT NULL;
ALTER TABLE invoices ALTER COLUMN course_id DROP NOT NULL;
ALTER TABLE invoices ADD COLUMN combo_id UUID REFERENCES combos(id);
ALTER TABLE invoices ADD CONSTRAINT chk_invoices_enrollment_xor_combo
    CHECK ((enrollment_id IS NOT NULL AND combo_id IS NULL)
        OR (enrollment_id IS NULL AND combo_id IS NOT NULL));
-- Mirror V20: tối đa 3 đợt theo combo, invoice CANCELLED không chiếm chỗ.
CREATE UNIQUE INDEX uq_invoices_combo_installment
    ON invoices (combo_id, installment_number)
    WHERE status <> 'CANCELLED' AND combo_id IS NOT NULL;
```

Dữ liệu cũ (mọi Invoice hiện có đều có `enrollment_id` + `course_id` set, `combo_id` null) thoả CHECK
constraint này ngay — không cần backfill. `studentProfileId`/`branchId` vẫn `NOT NULL` luôn (cả hai
luồng đều cần, lấy từ `Enrollment` hoặc từ `Combo` tuỳ trường hợp).

`InvoiceResponse` (DTO, hợp đồng với Zod frontend) thêm field `comboId` (nullable); `courseId` và
`enrollmentId` trở thành nullable trong response (combo invoice thì cả hai đều `null`,
`installmentNumber` vẫn có nghĩa — "đợt N của combo").

**Các usecase/luồng đã có của Phase 3 giữ nguyên hành vi, không sửa:** `CreateInvoice` (vẫn luôn yêu
cầu `enrollmentId`, không đổi), `CancelInvoice`, `InitiateOnlinePayment`, `RecordManualPayment`,
`HandlePaymentCallback`, `MarkOverdueInvoices`, `GetPaymentStatus` — tất cả chỉ thao tác trên
`Invoice` qua `id`/`amount`/`status` (method `applyPayment`/`cancel`/`markOverdue`), không đọc
`enrollmentId`/`courseId` trong logic nghiệp vụ, nên vận hành đúng như cũ cho cả invoice combo lẫn
invoice đơn khoá, không cần nhánh rẽ nào. `ListInvoices`/`GetInvoiceDetail` chỉ cần phản ánh field
mới (nullable) qua, không đổi logic.

## 6. Usecase mới (`modules.billing.usecase`)

- **`CreateCombo(actorAccountId, actorBranchId, CreateComboRequest{studentProfileId, enrollmentIds: List<UUID>, dueDate})`**
  1. `enrollmentIds` phải có ≥ 2 phần tử khác nhau, nếu không → `MinimumComboSizeException`.
  2. Với mỗi id: `enrollmentManagement.getEnrollment(id)` phải tồn tại, `status == ACTIVE`,
     `studentProfileId` phải khớp đúng `request.studentProfileId`, và `branchId` phải khớp đúng
     `branchId` của Enrollment ĐẦU TIÊN trong danh sách (không phải `actorBranchId` của người thao
     tác — mirror cách `CreateInvoice` lấy `branchId` từ enrollment) — khác bất kỳ điều kiện nào ở
     trên → `StudentMismatchInComboException`. Enrollment không tồn tại/không active →
     `EnrollmentNotFoundException`/`EnrollmentNotActiveForBillingException` (dùng lại, đã có từ Phase 3).
  3. Với mỗi `courseId` thu được: `coursesManagement.getCourseTuition(courseId)` phải có
     `tuitionFee` — thiếu → `CourseTuitionNotConfiguredException` (dùng lại).
  4. Cộng `tuitionFee` → `totalOriginalAmount`; chọn tier theo mục 4 → `discountPercent`; tính
     `totalDiscountedAmount`.
  5. `saveAndFlush` `Combo` + N `ComboEnrollment` trong try/catch `DataIntegrityViolationException`
     → `EnrollmentAlreadyInComboException` (race, mục 4).
  6. Publish `BillingEvents.ComboCreated(comboId, actorAccountId, actorBranchId)`.
- **`CreateComboInvoice(actorAccountId, actorBranchId, CreateComboInvoiceRequest{comboId, amount, dueDate})`**
  — mirror `CreateInvoice` nhưng neo theo `comboId`: combo phải tồn tại; đếm invoice còn sống theo
  `comboId` < 3 (`InstallmentLimitExceededException`, dùng lại); tổng đã phát hành + `amount` không
  vượt `combo.totalDiscountedAmount` (`InvoiceAmountExceedsTuitionException`, dùng lại — tên exception
  giữ nguyên dù ngữ cảnh là combo, vì bản chất lỗi giống nhau: "vượt tổng tiền phải thu"); tạo
  `Invoice` với `enrollmentId = null`, `courseId = null`, `comboId` set, `studentProfileId`/`branchId`
  lấy từ `Combo`. Cùng pattern `saveAndFlush` + dịch race sang `InstallmentLimitExceededException`.
- **`CancelCombo(actorAccountId, actorBranchId, comboId)`** — chỉ cho phép khi
  `invoices.countByComboId(comboId) == 0` (chưa phát hành hoá đơn nào), ngược lại
  `ComboHasInvoicesException`. Xoá cứng `Combo` (cascade xoá `ComboEnrollment`). Publish
  `BillingEvents.ComboCancelled(comboId, actorAccountId, actorBranchId)` **trước khi xoá** trong cùng
  transaction (audit listener đọc `comboId` dạng UUID thuần, không cần `Combo` còn tồn tại).
- **`GetComboDetail(comboId)`** — trả `Combo` + danh sách `ComboEnrollment` (kèm `courseId`,
  `originalTuitionFee`) + danh sách `Invoice` có `comboId` này (`InvoiceRepository.findAllByComboId`).
- **`ListCombos(pageable, studentProfileId?)`** — danh sách combo, lọc optional theo học viên.
- **`ListComboDiscountTiers`**, **`CreateComboDiscountTier`**, **`UpdateComboDiscountTier`** — CRUD
  đơn giản cho admin (không có `DeleteComboDiscountTier`, dùng `active=false` để "xoá" — mirror
  `Course.active`).

## 7. Thay đổi nhỏ ở `modules.enrollment` (bổ sung filter, không đổi hành vi cũ)

Để màn hình "Tạo combo" chọn được đúng các Enrollment `ACTIVE` của một học viên, thêm filter
`status` (optional) vào filter có sẵn (`studentProfileId`/`classId`):
`EnrollmentRepository.search(studentProfileId, classId, status, pageable)` (thêm một
`:p IS NULL OR ...` nữa, giữ complexity ở 1, mirror cách hai filter cũ đã làm) →
`ListEnrollments.execute(pageable, studentProfileId, classId, status)` →
`EnrollmentAdminController.list` thêm `@RequestParam(required = false) EnrollmentConstants.EnrollmentStatus status`.
Đây là bổ sung thuần filter, không đổi hành vi khi `status` để trống (giữ nguyên mọi test cũ).

## 8. RBAC

**Không thêm resource/permission mới.** Combo là một hình thức thu học phí — dùng lại đúng
`Actions.CREATE_INVOICE`/`READ_INVOICE`/`UPDATE_INVOICE` trên `Resources.INVOICE` cho mọi endpoint
combo (tạo combo + tạo invoice combo → `CREATE_INVOICE`; xem combo/danh sách → `READ_INVOICE`; huỷ
combo → `UPDATE_INVOICE`), kể cả CRUD bậc giảm giá (`ComboDiscountTier`) — đây là cấu hình giá, cùng
nhóm quyền với người quản lý học phí, không cần một resource tách riêng cho một bảng cấu hình nhỏ.
Không cần migration seed permission mới.

## 9. `modules.audit`

Thêm `BillingEvents.ComboCreated`/`ComboCancelled` (mục 6), `AuditConstants.Actions.COMBO_CREATE` =
`"COMBO_CREATE"`, `COMBO_CANCEL` = `"COMBO_CANCEL"`, hai method `on(...)` mới trong
`AuditEventListeners` — mirror 1:1 cách `InvoiceCreated`/`InvoiceCancelled` đã làm.

## 10. Frontend

- `entities/billing`: `invoiceSchema` — `courseId`/`enrollmentId` → `.nullable()`, thêm
  `comboId: z.string().uuid().nullable()`. Thêm `comboSchema`, `comboEnrollmentSchema`,
  `comboDiscountTierSchema`. Thêm API/hooks: `listCombos`, `createCombo`, `getComboDetail`,
  `cancelCombo`, `createComboInvoice`, `listComboDiscountTiers`, `createComboDiscountTier`,
  `updateComboDiscountTier`.
- `entities/enrollment`: `enrollment-api.ts`/`use-enrollments.ts` thêm param `status` optional (mục 7).
- `modules/billing`: trang mới `combos-page.tsx` (danh sách combo), `combo-detail-page.tsx` (thông
  tin combo + danh sách khoá trong combo + bảng hoá đơn, tái dùng `PaymentsTable`/nút thu tiền online-
  /thủ công như `invoice-detail-page.tsx` nhưng gọi `createComboInvoice`), `create-combo-dialog.tsx`
  (chọn học viên → load enrollment `ACTIVE` của học viên đó → multi-select ≥2 khoá → xem trước % giảm
  + tổng tiền sau giảm theo bậc hiện tại → nhập hạn đóng → tạo), trang admin
  `combo-discount-tiers-page.tsx` (bảng bậc giảm giá, tạo/sửa, không có nút xoá — chỉ toggle active).
  Tất cả tuân 3 Mandate: tách logic ra `hooks/use-*-controller.ts`, không hardcode string permission
  (dùng `ACCESS_RULE.*`), component < 200 dòng.
- `app/router/app-router.tsx` + `nav-items.ts`: thêm route `/billing/combos`,
  `/billing/combos/:comboId`, `/billing/combo-discount-tiers`.

## 11. Testing & Review Focus

Checklist bắt buộc (mirror Phase 3 mục 11), mỗi dòng phải có task/test riêng khi viết plan:

1. **Combo lẫn học viên khác nhau** — chọn 2 enrollment của 2 học viên khác nhau → phải bị chặn
   (`StudentMismatchInComboException`), không được âm thầm lấy học viên của enrollment đầu tiên.
2. **Combo chỉ 1 khoá** — phải chặn (`MinimumComboSizeException`), không tạo "combo" 1 phần tử.
3. **Race điều kiện enrollment bị gộp hai lần** — hai request đồng thời cùng chọn một
   `enrollmentId` vào hai combo khác nhau → DB (UNIQUE `combo_enrollments.enrollment_id`) phải chặn
   một trong hai, dịch đúng sang `EnrollmentAlreadyInComboException`, không rơi thành lỗi 500.
4. **Bậc giảm giá chưa cấu hình** — tạo combo 2 khoá khi chưa có tier nào cho `minCourseCount <= 2`
   → `ComboDiscountTierNotConfiguredException`, không mặc định 0%.
5. **Vượt tổng tiền combo sau giảm** — tổng các đợt invoice combo vượt `totalDiscountedAmount` →
   `InvoiceAmountExceedsTuitionException`, dù số đợt (3) chưa chạm hạn mức — đúng tinh thần Phase 3
   Review Focus #3 (hai lỗi khác nhau, không lẫn vào nhau).
6. **Huỷ combo đã có hoá đơn** — combo đã phát hành ít nhất 1 invoice (bất kể trạng thái, kể cả đã
   CANCELLED) → `CancelCombo` phải chặn (`ComboHasInvoicesException`), không xoá cứng một combo đã
   có chứng từ tài chính gắn vào.
7. **Rút khỏi một khoá trong combo sau khi combo đã tạo** — `WithdrawEnrollment` ở `modules.enrollment`
   không biết gì về combo (không đổi) → combo/invoice combo giữ nguyên, không tự huỷ/tính lại giảm
   giá — test xác nhận hành vi "giữ nguyên công nợ" (đã chốt ở Phase 3) áp dụng nguyên vẹn cho combo.
8. **Dữ liệu Invoice cũ (Phase 3) vẫn hợp lệ sau migration V22** — IT test đọc lại một Invoice đơn-
   khoá đã tạo trước migration, xác nhận CHECK constraint không chặn dữ liệu cũ, `comboId` đọc ra
   `null`.
9. **Payment flow dùng chung không rẽ nhánh theo loại Invoice** — `InitiateOnlinePayment`/
   `HandlePaymentCallback`/`RecordManualPayment`/`CancelInvoice`/`MarkOverdueInvoices` chạy đúng trên
   một invoice combo giống hệt invoice đơn-khoá (test parametrize hoặc nhân bản 1 test hiện có với
   invoice combo).

## 12. Tổng kết quyết định đã chốt với người dùng

- Combo = kế toán tự chọn Enrollment có sẵn, không có combo định sẵn.
- Bậc giảm giá theo số khoá, admin cấu hình qua UI, lưu DB.
- Hạn đóng do kế toán nhập tay, không tự suy từ lịch học.
- Tối đa 3 đợt cho CẢ combo (không phải theo từng khoá), tổng không vượt tổng tiền combo sau giảm.
- Huỷ combo chỉ được phép trước khi có hoá đơn đầu tiên (quyết định của tôi, không hỏi lại — hệ quả
  tự nhiên của nguyên tắc "chứng từ tài chính đã chốt thì không xoá" đã áp dụng xuyên Phase 3).
- Không thêm RBAC resource mới — dùng lại `INVOICE` (quyết định của tôi, YAGNI — không có yêu cầu
  phân quyền tách riêng cho combo).
