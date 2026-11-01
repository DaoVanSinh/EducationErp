-- Thu tay tách thành tiền mặt (CASH) / chuyển khoản (BANK_TRANSFER) thay cho MANUAL gộp chung.
-- Mỗi lần thu tay giữ thêm:
--   note        - số phiếu thu, nội dung chuyển khoản... để đối soát (null với giao dịch qua cổng)
--   recorded_by - tài khoản kế toán đã ghi nhận (null với giao dịch qua cổng: không có người thu)
-- method vẫn là VARCHAR(16) không CHECK constraint (V-billing gốc), nên giá trị mới không cần sửa ràng buộc.
-- Bản ghi MANUAL cũ giữ nguyên: không biết chắc đó là tiền mặt hay chuyển khoản.
ALTER TABLE payments
    ADD COLUMN note        VARCHAR(255),
    ADD COLUMN recorded_by UUID;
