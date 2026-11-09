# Vòng đời tài khoản, quản trị chi nhánh, thu chi toàn bộ

Bổ sung cho `2026-11-05-luong-giao-vien-design.md`. Mọi quyết định dưới đây do chủ dự án chốt ngày 05/11/2026. Các chi nhánh
cùng thuộc một công ty, nên vẫn dùng một kỳ lương chung cho cả chuỗi.

## 1. Trạng thái tài khoản

| Trạng thái | Đăng nhập | Mở lại | Dừng lương |
|---|---|---|---|
| Hoạt động | Được | Không áp dụng | Không |
| Tạm đóng | Không | Được | Không |
| Đã đóng | Không | Không, muốn dùng lại email thì tạo tài khoản mới | Có |

- Một email chỉ có một tài khoản chưa đóng. Dữ liệu của tài khoản đã đóng được giữ nguyên, admin vẫn xem được.
- Đóng tài khoản bị chặn khi: giáo viên còn lớp đang mở, học viên còn đang học, học viên còn nợ học phí.
- Admin chi nhánh tạm đóng và đóng được tài khoản thuộc chi nhánh mình. Chỉ admin gốc xử lý được tài khoản admin chi nhánh.
  Không ai đóng được admin gốc, và không ai tự đóng chính mình.

## 2. Đổi vai trò và tạo giáo viên

- Không đổi vai trò vào hoặc ra khỏi Giáo viên, Học viên. Phải đóng tài khoản cũ rồi tạo tài khoản mới cùng email.
- Đổi giữa các vai trò nhân viên không có hồ sơ riêng vẫn dùng Đổi vai trò.
- Tài khoản giáo viên chỉ tạo ở màn Giáo viên, nhập đủ chi nhánh, môn dạy, lương. Màn Tài khoản không tạo được.
- Vai trò Quản trị viên không gán được cho ai khác ngoài admin gốc.

## 3. Người đã nghỉ

**Cho nghỉ** là việc nghiệp vụ, tách khỏi tài khoản: có ngày nghỉ, bị chặn khi còn lớp đang mở, lương tháng dừng từ ngày đó.
Tài khoản vẫn đăng nhập được và chỉ xem được dữ liệu của chính mình cho tới khi admin đóng.

- Học viên thôi học: lớp đã kết thúc hoặc đã rút nằm ở mục lịch sử.
- Giáo viên: lớp đã dạy và phiếu lương cũ vẫn xem được, kể cả lớp đã bàn giao cho người khác.
- Admin chi nhánh: admin gốc đổi sang vai trò "Đã nghỉ việc", chỉ còn quyền xem dữ liệu của mình.
- Quay lại làm: đóng tài khoản cũ, tạo tài khoản mới với mức lương mới. Mỗi tài khoản là đúng một đợt làm.

## 4. Giáo viên theo chi nhánh

- Một giáo viên chỉ dạy lớp thuộc chi nhánh chủ quản của mình. Đây là quyết định thiết kế để phân quyền và chi trả rõ ràng.
- Phân công giáo viên bị chặn khi: khác chi nhánh, đã nghỉ, chưa có lương, trùng giờ với lớp khác đang mở.
- Lớp đang mở vi phạm từ trước được giữ tới khi kết thúc, có cảnh báo. Quy tắc chỉ áp dụng cho phân công mới.
- Chỉ admin gốc chuyển chi nhánh, giáo viên phải bàn giao hết lớp đang mở trước. Lịch sử chuyển được lưu, lớp và phiếu cũ
  giữ nguyên chi nhánh cũ.
- Thay giáo viên nhập "từ ngày": buổi trước ngày đó tính giờ cho giáo viên cũ, kể cả khi ghi bù sau này.

## 5. Quyền của admin chi nhánh

Vai trò mới **Quản trị chi nhánh**, phạm vi BRANCH: giáo viên, học viên, lớp, ghi danh, học phí, điểm danh, điểm của chi nhánh
mình. Khoá học chỉ xem. Không tạo được kỳ lương, không đổi được mức lương, không đổi vai trò, không chuyển chi nhánh.

Lúc tạo giáo viên, admin chi nhánh nhập mức lương khởi điểm vì đó là dữ liệu bắt buộc. Con số vẫn qua bước duyệt của admin gốc.

Giáo viên và học viên chỉ xem được dữ liệu của chính mình.

## 6. Lương theo chi nhánh

1. Admin gốc tạo và duyệt kỳ lương. Duyệt xong, phiếu tự chuyển xuống chi nhánh chủ quản của giáo viên tại thời điểm tính.
2. Admin chi nhánh chỉ thấy phiếu đã duyệt của chi nhánh mình, không sửa được số.
3. Admin chi nhánh đánh dấu Đã thanh toán từng phiếu, trả đủ một lần, kèm ngày và hình thức trả.
4. Admin gốc theo dõi tiến độ từng chi nhánh, chỉ admin gốc huỷ được đánh dấu nhầm, kèm lý do.
5. Giáo viên thấy phiếu ngay khi duyệt, trạng thái Chờ thanh toán hoặc Đã thanh toán.

## 7. Học phí

- Mỗi ghi danh một hoá đơn, tự lập khi ghi danh, thu đủ một lần. Không chia đợt, không trả một phần.
- Dữ liệu cũ đã chia đợt hoặc đang trả dở giữ nguyên tới khi thu xong: phần còn lại phải thu đủ một lần.
- Hạn đóng là ngày thứ 7 tính từ ngày khai giảng. Đổi ngày khai giảng thì hạn đổi theo, trừ hoá đơn đã được gia hạn.
- Vào combo hoặc đăng ký nhóm: hoá đơn chưa thu bị huỷ và thay bằng hoá đơn đã giảm giá.
- Rút khỏi lớp: hoá đơn chưa thu bị huỷ.
- Rút một lớp khỏi combo chưa thu đồng nào: combo được giải thể, các lớp còn học quay về hoá đơn giá thường, vì giá đã giảm không còn đúng. Combo đã có giao dịch thì giữ nguyên để đối soát hoàn phí bằng tay.
- Hoá đơn quá hạn không huỷ tay được, vì huỷ là xoá nợ. Muốn huỷ thì admin gốc gia hạn trước, hoặc học viên rút lớp.
- Combo chỉ huỷ được khi chưa có giao dịch nào, kể cả giao dịch VNPay thất bại, vì dấu vết thanh toán không được mất.
- Sửa lớp mà không đổi ngày khai giảng thì không đụng tới hạn. Đổi ngày khai giảng chỉ dời các hoá đơn đang đúng hạn theo ngày cũ, nên hoá đơn chia đợt cũ giữ nguyên hạn riêng.
- Quá hạn: vào danh sách cần xử lý của admin chi nhánh và admin gốc. Admin gốc gia hạn kèm lý do. Admin tạm đóng tài khoản nếu cần.

## 8. Lọc và tìm kiếm

Tài khoản lọc theo vai trò, chi nhánh, trạng thái và tìm theo tên hoặc email. Giáo viên và Học viên tìm theo tên hoặc email.
Admin chi nhánh luôn bị giới hạn trong chi nhánh của mình, bất kể gửi tham số gì.
