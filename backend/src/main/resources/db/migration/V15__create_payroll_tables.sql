CREATE TABLE employment_contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    contract_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    base_salary NUMERIC(14,2),
    hourly_rate NUMERIC(14,2),
    probation_start_date DATE,
    probation_end_date DATE,
    start_date DATE NOT NULL,
    end_date DATE,
    contract_file_key VARCHAR(512)
);

CREATE INDEX idx_employment_contracts_account ON employment_contracts (account_id);
-- Một account chỉ một hợp đồng ACTIVE tại một thời điểm - enforce ở usecase (CreateContract, Task 9),
-- không dùng partial unique index ở đây để tránh phụ thuộc cú pháp CREATE UNIQUE INDEX ... WHERE
-- đặc thù Postgres trong migration nền tảng.

CREATE TABLE contract_allowances (
    contract_id UUID NOT NULL REFERENCES employment_contracts(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    amount NUMERIC(14,2) NOT NULL
);

CREATE INDEX idx_contract_allowances_contract ON contract_allowances (contract_id);

CREATE TABLE payroll_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    year INT NOT NULL,
    month INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    rejection_reason VARCHAR(1000),
    UNIQUE (year, month)
);

CREATE TABLE payslips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payroll_run_id UUID NOT NULL REFERENCES payroll_runs(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id),
    contract_type VARCHAR(16) NOT NULL,
    gross_pay NUMERIC(14,2) NOT NULL,
    allowances_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    social_insurance_employee NUMERIC(14,2) NOT NULL DEFAULT 0,
    social_insurance_employer NUMERIC(14,2) NOT NULL DEFAULT 0,
    income_tax_withheld NUMERIC(14,2) NOT NULL DEFAULT 0,
    hours_worked NUMERIC(10,2),
    in_probation BOOLEAN NOT NULL DEFAULT false,
    note VARCHAR(1000)
);

CREATE INDEX idx_payslips_run ON payslips (payroll_run_id);
CREATE INDEX idx_payslips_account ON payslips (account_id);
