-- Vai trò của một account chuyển từ cột role_id trên accounts sang bảng riêng do module access sở
-- hữu — giống account_groups đã có từ V4. Lý do: module access tính effective permission phải tự
-- đủ dữ liệu (role + group của một account), không được JOIN/đọc thẳng bảng accounts (ranh giới
-- module). account_id ở đây chỉ là UUID trần có FK mức DB, không phải quan hệ JPA sang Account.
CREATE TABLE account_roles (
    account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id)
);

INSERT INTO account_roles (account_id, role_id) SELECT id, role_id FROM accounts;

ALTER TABLE accounts DROP COLUMN role_id;
