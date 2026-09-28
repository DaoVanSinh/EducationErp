CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Không có khoá ngoại tới accounts: log phải sống sót khi tài khoản bị xoá, nếu không thì
    -- xoá một tài khoản sẽ xoá luôn dấu vết những gì tài khoản đó đã làm.
    actor_account_id UUID,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64),
    branch_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);

-- Dashboard đọc "N hoạt động gần nhất theo loại + hành động", nên thứ tự thời gian phải nằm
-- trong index; nếu không, mỗi lần mở dashboard là một lần sort toàn bảng audit.
CREATE INDEX idx_audit_logs_recent ON audit_logs (entity_type, action, occurred_at DESC);
