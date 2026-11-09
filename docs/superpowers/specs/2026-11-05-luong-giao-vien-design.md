# Lương giáo viên: bỏ hợp đồng lao động, làm rõ kỳ lương và công thức

Thay thế các mục về hợp đồng, kỳ lương và tính lương của
`2026-10-03-phan-he-2-6-nhan-su-luong-giao-vien-design.md`.

## 1. Vấn đề của thiết kế cũ

| Vấn đề | Hệ quả |
|---|---|
| Tạo giáo viên và tạo hợp đồng là hai việc tách rời | Giáo viên có thể dạy cả tháng mà không có lương, vì không có hợp đồng thì không có phiếu |
| Giờ dạy CTV nhập tay, dù điểm danh đã có | Một sự thật hai nguồn, số giờ lương có thể lệch số buổi đã điểm danh |
| Thuế TNCN nhập tay | Không có công thức thật, mỗi phiếu là một con số tự gõ |
| Thử việc chốt tại ngày đầu tháng | Người vào làm giữa tháng được tính 100% dù đang thử việc, vì ngày 01 trước ngày bắt đầu thử việc |
| Người vào làm hoặc nghỉ giữa tháng vẫn nhận đủ lương tháng | Không có tính theo ngày |
| Kỳ lương chỉ có `year`/`month` | Không rõ khoảng ngày, tạo được kỳ tương lai, gửi duyệt khi tháng chưa hết |
| Giáo viên thấy phiếu lương của kỳ còn nháp | Lộ con số chưa duyệt, có thể còn đổi |
| Trang kỳ lương không hiện lý do từ chối | Kế toán không biết phải sửa gì |
| Hợp đồng chỉ lưu dữ liệu tính lương và một file scan | Màn hình riêng không thêm nghiệp vụ nào ngoài điều khoản lương |

## 2. Quyết định

1. **Bỏ hẳn hợp đồng lao động**: bảng, API, màn hình, tab hồ sơ, công cụ MCP.
2. **Điều khoản lương nằm trên hồ sơ giáo viên**, nhập ngay khi tạo giáo viên. Mọi ô nhập đều đi vào công thức.
3. **Giờ dạy lấy từ điểm danh**, không nhập tay. Sai giờ thì sửa điểm danh rồi tính lại.
4. **Tiền thuế và bảo hiểm do hệ thống tính.** Kế toán chỉ nhập một khoản Thưởng/điều chỉnh kèm lý do.
5. **Kỳ lương chỉ tính cho giáo viên.** Nhân viên văn phòng cần lương sau này thì thêm một loại hồ sơ nhân sự riêng.

Dữ liệu hiện có khi viết spec:

- 11 hợp đồng đang hiệu lực đều của giáo viên, nên không mất điều khoản lương của ai.
- 2 giáo viên có buổi dạy tháng 10/2026 nhưng không có hợp đồng hiệu lực, nên sau migration họ ở trạng thái chưa thiết lập lương. Thiết kế cũ âm thầm bỏ sót lương của họ; thiết kế mới chặn gửi duyệt cho tới khi có mức lương.
- Kỳ 10/2026 đã duyệt với 0 phiếu đang khoá cả tháng đó. Migration gỡ các kỳ rỗng đã gửi duyệt hoặc đã duyệt.

## 3. Dữ liệu cần khi tạo giáo viên

| Trường | Bắt buộc | Dùng để làm gì |
|---|---|---|
| Họ tên, email | Có | Tài khoản đăng nhập, thư mời, tên trên phiếu lương |
| Chi nhánh chủ quản | Có | Lọc danh sách theo chi nhánh, phạm vi quyền |
| Môn dạy | Có, ít nhất 1 | Gợi ý phân công lớp, trang giới thiệu |
| Số điện thoại, giới thiệu | Không | Liên hệ, trang giới thiệu |
| Hình thức trả lương | Có | Chọn công thức ở mục 5 |
| Ngày bắt đầu làm việc | Có | Mốc tính lương theo ngày |
| Lương tháng | Có nếu trả theo tháng | Công thức A |
| Hết thử việc | Không, chỉ trả theo tháng | Ngày thử việc hưởng 85% |
| Số người phụ thuộc | Không, chỉ trả theo tháng | Giảm trừ gia cảnh khi tính thuế |
| Phụ cấp cố định | Không, chỉ trả theo tháng | Cộng vào thu nhập, tính theo ngày làm |
| Đơn giá một giờ | Có nếu trả theo giờ | Công thức B |

Điều khoản lương không nằm trong `TeacherProfileResponse`, vì quyền đọc giáo viên rộng hơn quyền xem lương. Chúng đi qua
API riêng `GET`/`PUT /api/teachers/profiles/{id}/pay`, gác bằng `READ_PAYROLL`/`UPDATE_PAYROLL`. Riêng lúc tạo giáo viên,
quyền `CREATE_TEACHER` đủ để nhập mức lương khởi điểm, vì con số vẫn phải qua bước duyệt kỳ lương mới có hiệu lực.

Tài khoản được cấp vai trò có quyền giảng dạy mà không đi qua form trên (`TeacherRoleParticipant`) sẽ có hồ sơ chưa thiết
lập lương. Danh sách và trang kỳ lương cảnh báo những người này.

## 4. Kỳ lương

Một kỳ là trọn một tháng dương lịch, từ ngày 1 đến ngày cuối tháng. Mỗi tháng có đúng một kỳ.

| Trạng thái | Ai làm | Điều kiện | Sửa được gì |
|---|---|---|---|
| Nháp | Kế toán tạo, cần `PAYROLL:CREATE` | Tháng không ở tương lai, chưa có kỳ cùng tháng | Thưởng/điều chỉnh, ghi chú, tính lại |
| Chờ duyệt | Kế toán gửi, cần `PAYROLL:UPDATE` | Tháng đã kết thúc, có ít nhất một phiếu, không còn giáo viên có buổi dạy mà chưa thiết lập lương | Không |
| Đã duyệt | Chủ trung tâm duyệt, cần `PAYROLL:APPROVE` | Đang chờ duyệt | Không, số liệu được khoá |

Từ chối đưa kỳ về Nháp kèm lý do, lý do hiện trên trang chi tiết và tự xoá khi gửi duyệt lại.

Quy tắc số liệu:

- Phiếu là **ảnh chụp**: đơn giá, số giờ, số người phụ thuộc được lưu trên phiếu. Đổi lương giáo viên về sau không làm đổi kỳ đã duyệt.
- Khi còn Nháp, **Tính lại** đọc lại điều khoản lương và điểm danh. Gửi duyệt luôn tự tính lại, nên kỳ chờ duyệt không dùng số cũ. Thưởng/điều chỉnh được giữ nguyên.
- Giáo viên chỉ thấy phiếu của kỳ **Đã duyệt**.
- Sai sót sau khi duyệt được bù bằng Thưởng/điều chỉnh ở kỳ sau.

Ai có phiếu trong kỳ:

- Trả theo tháng: có ít nhất một ngày làm việc trong kỳ.
- Trả theo giờ: có ít nhất một buổi đã điểm danh trong kỳ, kể cả khi hồ sơ đã ngừng hoạt động.
- Khoá tài khoản đăng nhập không ảnh hưởng lương. Hồ sơ ngừng hoạt động thì ngày đó là ngày làm việc cuối.

## 5. Công thức

```text
D  = số ngày của tháng
N  = số ngày làm việc trong kỳ (từ ngày bắt đầu hoặc ngày 1, tới ngày nghỉ hoặc cuối tháng)
Nt = số ngày trong N còn thử việc

A. Trả theo tháng
   Lương chính = Lương tháng / D x ((N - Nt) + 85% x Nt)
   Phụ cấp     = Tổng phụ cấp x N / D

B. Trả theo giờ
   Giờ dạy     = tổng thời lượng các buổi đã điểm danh trong kỳ, làm tròn 2 chữ số
   Lương chính = Giờ dạy x Đơn giá
   Phụ cấp     = 0

Chung
   Thu nhập    = Lương chính + Phụ cấp + Thưởng/điều chỉnh
   BHXH NLĐ    = 10,5% x min(Lương chính, 50.600.000)  (chỉ A)
   BHXH DN     = 21,5% x min(Lương chính, 50.600.000)  (chỉ A, chi phí trung tâm, không trừ vào lương)
   Thuế A      = Biểu lũy tiến x (Thu nhập - BHXH NLĐ - 15.500.000 - 6.200.000 x Số người phụ thuộc), phần âm tính 0
   Thuế B      = 10% x Thu nhập nếu Thu nhập >= 5.000.000, ngược lại 0
   Thực nhận   = Thu nhập - BHXH NLĐ - Thuế
```

Biểu lũy tiến theo tháng: đến 10 triệu 5%, trên 10 đến 30 triệu 10%, trên 30 đến 60 triệu 20%, trên 60 đến 100 triệu 30%,
trên 100 triệu 35%. Mọi khoản tiền làm tròn tới đồng.

Thời lượng một buổi: tổng các ca của lớp trong đúng thứ của ngày đó. Buổi học bù vào ngày không có ca thì lấy ca dài nhất
của lớp. Thời lượng và giáo viên được chụp vào `attendance_sessions` lúc điểm danh, nên đổi lịch hoặc đổi giáo viên về sau
không làm đổi buổi đã dạy. Một buổi được tính khi đã có điểm danh, kể cả khi học viên vắng hết, vì giáo viên vẫn đến lớp.

### Ví dụ

Trả theo tháng, 15.000.000 đ, 2 phụ cấp tổng 1.130.000 đ, 1 người phụ thuộc, tháng 30 ngày, làm đủ:

```text
Lương chính 15.000.000 + Phụ cấp 1.130.000 = Thu nhập 16.130.000
BHXH NLĐ    15.000.000 x 10,5% = 1.575.000
Tính thuế   16.130.000 - 1.575.000 - 15.500.000 - 6.200.000 < 0, thuế 0
Thực nhận   14.555.000
```

Trả theo giờ, 250.000 đ/giờ, 36 giờ:

```text
Lương chính 9.000.000, thuế 10% = 900.000, thực nhận 8.100.000
```

### Căn cứ và điều chưa mô hình hoá

| Nội dung | Căn cứ |
|---|---|
| Thử việc 85% | Điều 26 Bộ luật Lao động 2019 |
| Tỷ lệ 10,5% và 21,5% | Bảng đóng BHXH, BHYT, BHTN hiện hành |
| Trần 50.600.000 đ | 20 lần mức tham chiếu 2.530.000 đ, từ 01/07/2026 |
| Biểu 5 bậc | Điều 9 Luật Thuế TNCN 109/2025/QH15, từ kỳ tính thuế 2026 |
| Giảm trừ 15,5 và 6,2 triệu | Nghị quyết 110/2025/UBTVQH15 |
| Khấu trừ 10% từ 5 triệu/lần | Khoản 2 Điều 50 Nghị định 253/2026/NĐ-CP, từ 01/07/2026, đối chiếu qua bài của đơn vị tư vấn, chưa đọc văn bản gốc |

Các con số này là hằng số trong `PayrollConstants`, áp dụng cho mọi kỳ, nên kỳ trước 01/07/2026 sẽ dùng mức mới.

Những điểm cố ý đơn giản hoá, kế toán xử lý bằng Thưởng/điều chỉnh khi cần:

- BHTN thực tế có trần riêng theo lương tối thiểu vùng. Ở đây dùng chung một trần, chỉ lệch với lương trên 50,6 triệu.
- Mọi phụ cấp đều chịu thuế và không tính vào lương đóng BHXH.
- Chưa xét trường hợp làm dưới 14 ngày không phải đóng BHXH, và chưa xét cam kết 08/CK-TNCN của người trả theo giờ.
- Giáo viên trả theo tháng không có định mức giờ và tiền vượt giờ.
- Không có tạm ứng lương, xuất file ngân hàng, nộp tờ khai thuế.

## 6. Kiến trúc

Phụ thuộc mới: `payroll → teachers` (điều khoản lương) và `payroll → attendance` (giờ dạy), không tạo vòng.

- `teacher_profiles` thêm các cột lương, bảng mới `teacher_allowances`, `TeachersManagement.listPayTerms()`.
- `attendance_sessions` thêm `teacher_account_id`, `duration_minutes`, facade mới `AttendanceManagement.teachingSummaries()`.
- `payslips`: đổi `contract_type` thành `pay_type`, thêm `pay_rate`, `session_count`, `paid_days`, `probation_days`, `dependent_count`, `adjustment`.
- Migration `V39` chuyển hợp đồng đang hiệu lực sang hồ sơ giáo viên rồi xoá `employment_contracts`, `contract_allowances`.
- File scan hợp đồng đã tải lên MinIO **không bị xoá** cùng migration.

## 7. Kiểm thử

- `PayrollRulesTest`: từng nhánh công thức, ví dụ ở mục 5, biên của bậc thuế và ngưỡng 5 triệu.
- Test usecase: chặn kỳ tương lai, chặn gửi duyệt khi chưa hết kỳ, giữ điều chỉnh khi tính lại.
- `PayrollRunAdminControllerIT`: toàn luồng với điểm danh thật, giáo viên không thấy phiếu chưa duyệt.
- `TeacherAdminControllerIT`: tạo giáo viên kèm lương, người thiếu quyền lương không đọc được mức lương.
- `ModularityTests`: phụ thuộc mới không tạo vòng.
