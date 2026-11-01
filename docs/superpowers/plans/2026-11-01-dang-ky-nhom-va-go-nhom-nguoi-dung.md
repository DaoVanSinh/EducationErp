# Plan: Tự khai giảng đúng hạn, đăng ký nhóm học chung, gỡ "Nhóm người dùng"

Ngày: 2026-11-01 · Nhánh: `feature/attendance-assignments-minitasks` (chưa commit)

## Goal

1. Lớp đã qua ngày khai giảng không còn kẹt ở "Đang tuyển sinh" khi đủ điều kiện; khi chưa đủ điều kiện thì cả 3 role
   thấy đúng tình trạng.
2. Thêm cách được giảm thứ hai: nhiều người rủ nhau học chung một khoá (đăng ký nhóm), cạnh combo nhiều khoá.
3. Gỡ tính năng "Nhóm người dùng" (RBAC Group) chưa dùng được và chưa có nhu cầu.
4. Rà phần khoá học ở trang chủ.

## Luật nghiệp vụ chốt

Khai giảng:
- Job tự khai giảng chạy mỗi giờ (phút 05) và chạy bù một lượt khi app khởi động. Trước đây chỉ 00:30 mỗi ngày,
  app deploy đúng lúc đó thì lớp đủ điều kiện kẹt thêm một ngày (lớp A301 ngày 31/10).
- Lớp quá ngày khai giảng mà chưa đủ sĩ số/lịch: giữ "Đang tuyển sinh" (không tự huỷ). Admin và giáo viên thấy cảnh báo,
  học viên thấy "chưa khai giảng, trung tâm sẽ báo ngày mới".
- Thẻ lớp ở cổng học viên chỉ hiện một nhãn: trạng thái lớp nếu ghi danh còn hiệu lực, trạng thái ghi danh nếu đã rút/hoàn tất.

Đăng ký nhóm (`modules.billing`, V36):
- Từ 2 học viên khác nhau, cùng khoá, cùng chi nhánh, mỗi ghi danh đang học, chưa có hoá đơn sống.
- Bậc giảm dùng chung bảng `combo_discount_tiers` ("từ N suất": N khoá của một người hoặc N người cùng một khoá).
- Mỗi người tự đóng học phí của mình qua hoá đơn theo ghi danh (tối đa 3 đợt); trần tổng hoá đơn = học phí đã giảm.
- Combo và nhóm loại trừ nhau. Ghi danh trong combo không được phát hành hoá đơn lẻ (trước đây lọt, thu hai lần).
- Huỷ nhóm: xoá cứng, chỉ khi chưa thành viên nào có hoá đơn sống. Quyền: INVOICE CREATE/READ/UPDATE như combo.

Gỡ Nhóm người dùng (V37):
- Xoá `account_groups`, `group_permission_groups`, `user_groups`, quyền tài nguyên `GROUP`. DB thật có 0 nhóm, 0 thành viên.
- Quyền hiệu lực chỉ tính từ vai trò. "Nhóm quyền" (permission group) giữ nguyên.

Trang chủ:
- Công cụ dự toán có 2 chế độ: một người nhiều khoá / rủ bạn học chung; khoá chưa có giá không vào dự toán.
- Lớp mở hiện trạng thái, ngày khai giảng, chỗ trống; giờ học "18:00–20:00" (không còn giây); khoá chưa cấu hình
  số buổi ghi "Lộ trình linh hoạt" thay vì bịa 24 buổi.
- Nút "Nhận báo giá"/"Giữ chỗ" điền sẵn ghi chú cho tư vấn viên.

## Verify

- [x] `mvn verify`: 449 unit + 313 IT xanh (gồm ModularityTests, GroupRegistrationAdminControllerIT mới).
- [x] `npm run typecheck`, `npm run lint`, `npm run build` xanh.
- [ ] Deploy lại container `eduerp-app` để V36/V37 chạy và lớp A301 được khai giảng ở lượt chạy bù.

## Ngoài phạm vi (đề xuất riêng)

- Thời khoá biểu dạng lưới tuần cho 3 role, kiểm tra trùng lịch giáo viên/học viên.
