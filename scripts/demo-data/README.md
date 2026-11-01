# Dữ liệu demo EduERP

Nạp một bộ dữ liệu đủ dày để trình diễn mọi phân hệ, không phải nhập tay. Chạy trên máy có Docker, nhắm vào
container Postgres `agent-postgres`, database `eduerp`.

## Chạy

```powershell
# Nạp (tự sao lưu DB vào scripts/demo-data/backups/ trước khi ghi):
make demo-seed MAILBOX=ten.cua.ban@gmail.com
# hoặc
powershell -ExecutionPolicy Bypass -File scripts\demo-data\demo-data.ps1 -Action seed -Mailbox ten.cua.ban@gmail.com

# Gỡ sạch dữ liệu demo:
make demo-cleanup

# Khôi phục một bản sao lưu:
powershell -ExecutionPolicy Bypass -File scripts\demo-data\demo-data.ps1 -Action restore -BackupFile scripts\demo-data\backups\<file>.dump
```

Chạy lại `demo-seed` bao nhiêu lần cũng được: script xoá bộ demo cũ rồi tạo lại trong cùng một transaction.
Mọi ngày tháng tính theo ngày chạy, nên trước buổi demo chạy lại để lịch học, hạn nộp, hạn học phí khớp
"hôm nay".

## Tài khoản

Không cần nhiều hộp thư thật. Gmail giao thư gửi tới `ten+bat-ky-chu-gi@gmail.com` về chính hộp thư
`ten@gmail.com`, nên mọi tài khoản demo dùng một hộp thư duy nhất mà vẫn nhận được thư mời và thư đặt lại
mật khẩu. Cách này cũng dùng được khi tạo tài khoản bằng tay trên giao diện.

| Vai trò | Email | Mật khẩu |
|---|---|---|
| Giáo viên | `ten+gv01@gmail.com` ... `ten+gv12@gmail.com` | `Demo@2026` |
| Học viên | `ten+hv001@gmail.com` ... `ten+hv150@gmail.com` | `Demo@2026` |

`hv148` - `hv150` bị khoá sẵn để trình diễn chức năng khoá/mở khoá.

Giáo viên thật đang có trong hệ thống được phân công thêm hai lớp demo đang học, và học viên thật được ghi
danh vào ba lớp demo. Đăng nhập bằng tài khoản thật vẫn thấy dữ liệu.

## Nội dung

| Phân hệ | Dữ liệu |
|---|---|
| Tổ chức | Thêm chi nhánh Hồ Chí Minh, dùng chung các chi nhánh đang có |
| Nhân sự | 12 giáo viên kèm hồ sơ, môn dạy, hợp đồng chính thức hoặc cộng tác viên, phụ cấp |
| Khoá học | 8 khoá học có học phí, số buổi chuẩn, cột điểm theo trọng số |
| Lớp học | 20 lớp ở cả 4 trạng thái, có lịch học |
| Tuyển sinh | 150 học viên, khoảng 225 lượt ghi danh, có học viên rút khỏi lớp |
| Học phí | Hoá đơn trả đủ, chia kỳ, trả một phần, quá hạn, đã huỷ. Thanh toán tiền mặt, MoMo, VNPay, có giao dịch lỗi. 3 combo giảm giá |
| Học tập | Điểm danh mọi buổi đã qua, điểm thành phần, bài tập và mini-test hằng tuần, mini-task có lời giải và lượt làm |
| Dashboard | Nhật ký đăng nhập 7 ngày gần nhất |

Bảng lương không được nạp sẵn. Vào màn Bảng lương và bấm tạo bảng lương tháng để hệ thống tự tính từ các
hợp đồng demo. Đây cũng là một thao tác nên trình diễn trực tiếp.

## Nhận diện và gỡ bỏ

Mọi bản ghi demo có id bắt đầu bằng `de30`. `cleanup-demo.sql` xoá các bản ghi đó, kèm những gì người dùng
tạo thêm trên dữ liệu demo trong lúc trình diễn. Dữ liệu thật không bị đụng tới.
