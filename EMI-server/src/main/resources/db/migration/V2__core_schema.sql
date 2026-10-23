-- =========================================================
-- V2__core_schema.sql
-- Everything after V1 (users), merged into one migration:
--   1. Finance core: customers, assets, finances, emi_installments
--   2. Payments: payments, payment_allocations
--   3. Follow-ups: followups
--   4. Documents: uploaded_files, finance_documents
--   5. Audit: audit_logs
-- Money is NUMERIC(12,2) (₹, 2 decimals) — never float.
-- =========================================================

-- =========================================================
-- 1. Finance core
-- =========================================================

-- 1.1 Customers (borrower profile; login account, if any, lives in users)
CREATE TABLE IF NOT EXISTS customers (
    customer_id VARCHAR(255) NOT NULL,
    -- Profile fields are optional at creation time, so full_name may be empty
    full_name VARCHAR(150),
    guardian_name VARCHAR(150),
    date_of_birth DATE,
    gender VARCHAR(10),
    phone_number VARCHAR(15) NOT NULL,
    alternate_phone_number VARCHAR(15),
    email VARCHAR(255),
    address_line VARCHAR(255),
    village VARCHAR(100),
    post_office VARCHAR(100),
    police_station VARCHAR(100),
    district VARCHAR(100),
    state VARCHAR(100),
    pin_code VARCHAR(6),
    -- Full 12-digit Aadhaar number; API responses show it masked
    aadhaar_number VARCHAR(12),
    pan_number VARCHAR(10),
    voter_id_number VARCHAR(20),
    user_id VARCHAR(255),
    assigned_staff_id VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT customers_pkey PRIMARY KEY (customer_id),
    CONSTRAINT uk_customers_user_id UNIQUE (user_id),
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES users(userid),
    CONSTRAINT fk_customers_assigned_staff FOREIGN KEY (assigned_staff_id) REFERENCES users(userid),
    CONSTRAINT fk_customers_created_by FOREIGN KEY (created_by) REFERENCES users(userid),
    CONSTRAINT customers_gender_check CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT customers_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_customers_phone_number ON customers(phone_number);
CREATE INDEX IF NOT EXISTS idx_customers_assigned_staff ON customers(assigned_staff_id);
CREATE INDEX IF NOT EXISTS idx_customers_full_name ON customers(full_name);

-- 1.2 Assets (the specific financed unit, not a product catalog)
CREATE TABLE IF NOT EXISTS assets (
    asset_id VARCHAR(255) NOT NULL,
    product_type VARCHAR(20) NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    brand VARCHAR(100),
    model VARCHAR(100),
    vehicle_number VARCHAR(30),
    chassis_number VARCHAR(50),
    engine_number VARCHAR(50),
    registration_number VARCHAR(30),
    purchase_price NUMERIC(12,2) NOT NULL,
    dealer_name VARCHAR(150),
    dealer_contact VARCHAR(15),
    purchase_date DATE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT assets_pkey PRIMARY KEY (asset_id),
    CONSTRAINT assets_product_type_check CHECK (product_type IN ('TOTO', 'BATTERY')),
    CONSTRAINT assets_purchase_price_check CHECK (purchase_price > 0)
);

CREATE INDEX IF NOT EXISTS idx_assets_vehicle_number ON assets(vehicle_number);

-- 1.3 Finances (one agreement = one customer + one asset)
-- Human-readable number FIN-YYYY-NNNNNN comes from this sequence
CREATE SEQUENCE IF NOT EXISTS finance_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS finances (
    finance_id VARCHAR(255) NOT NULL,
    finance_number VARCHAR(20) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    asset_id VARCHAR(255) NOT NULL,
    do_number VARCHAR(50) NOT NULL,
    product_price NUMERIC(12,2) NOT NULL,
    down_payment NUMERIC(12,2) NOT NULL,
    finance_amount NUMERIC(12,2) NOT NULL,
    interest_type VARCHAR(20) NOT NULL,
    -- Annual rate in percent, e.g. 18.000
    interest_rate NUMERIC(6,3) NOT NULL,
    tenure_months INTEGER NOT NULL,
    emi_amount NUMERIC(12,2) NOT NULL,
    total_interest NUMERIC(12,2) NOT NULL,
    processing_fee NUMERIC(12,2) NOT NULL DEFAULT 0,
    other_charges NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_payable NUMERIC(12,2) NOT NULL,
    first_emi_date DATE NOT NULL,
    -- Day of month EMIs fall due; capped at 28 so every month has it
    emi_due_day INTEGER NOT NULL,
    grace_days INTEGER NOT NULL DEFAULT 0,
    -- Fixed fee charged once per installment that goes overdue past the grace period
    late_fee_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL',
    remarks VARCHAR(500),
    created_by VARCHAR(255) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMP(6) WITH TIME ZONE,
    closed_at TIMESTAMP(6) WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT finances_pkey PRIMARY KEY (finance_id),
    CONSTRAINT uk_finances_finance_number UNIQUE (finance_number),
    CONSTRAINT uk_finances_do_number UNIQUE (do_number),
    CONSTRAINT uk_finances_asset_id UNIQUE (asset_id),
    CONSTRAINT fk_finances_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    CONSTRAINT fk_finances_asset FOREIGN KEY (asset_id) REFERENCES assets(asset_id),
    CONSTRAINT fk_finances_created_by FOREIGN KEY (created_by) REFERENCES users(userid),
    CONSTRAINT fk_finances_approved_by FOREIGN KEY (approved_by) REFERENCES users(userid),
    CONSTRAINT finances_interest_type_check CHECK (interest_type IN ('FLAT', 'REDUCING_BALANCE')),
    CONSTRAINT finances_status_check CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'CLOSED', 'FORECLOSED', 'CANCELLED')),
    CONSTRAINT finances_down_payment_check CHECK (down_payment >= 0 AND down_payment < product_price),
    CONSTRAINT finances_amount_check CHECK (finance_amount = product_price - down_payment),
    CONSTRAINT finances_interest_rate_check CHECK (interest_rate >= 0),
    CONSTRAINT finances_tenure_check CHECK (tenure_months > 0),
    CONSTRAINT finances_emi_due_day_check CHECK (emi_due_day BETWEEN 1 AND 28),
    CONSTRAINT finances_grace_days_check CHECK (grace_days >= 0),
    CONSTRAINT finances_charges_check CHECK (processing_fee >= 0 AND other_charges >= 0 AND late_fee_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_finances_customer ON finances(customer_id);
CREATE INDEX IF NOT EXISTS idx_finances_status ON finances(status);

-- 1.4 EMI installments (one row per month of the schedule)
-- Upcoming / Due / Overdue are derived from due_date + today, never stored
CREATE TABLE IF NOT EXISTS emi_installments (
    installment_id VARCHAR(255) NOT NULL,
    finance_id VARCHAR(255) NOT NULL,
    installment_no INTEGER NOT NULL,
    due_date DATE NOT NULL,
    principal_amount NUMERIC(12,2) NOT NULL,
    interest_amount NUMERIC(12,2) NOT NULL,
    emi_amount NUMERIC(12,2) NOT NULL,
    amount_paid NUMERIC(12,2) NOT NULL DEFAULT 0,
    late_fee NUMERIC(12,2) NOT NULL DEFAULT 0,
    late_fee_paid NUMERIC(12,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at DATE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT emi_installments_pkey PRIMARY KEY (installment_id),
    CONSTRAINT uk_emi_installments_finance_no UNIQUE (finance_id, installment_no),
    CONSTRAINT fk_emi_installments_finance FOREIGN KEY (finance_id) REFERENCES finances(finance_id),
    CONSTRAINT emi_installments_status_check CHECK (status IN ('PENDING', 'PARTIALLY_PAID', 'PAID', 'WAIVED', 'CANCELLED')),
    CONSTRAINT emi_installments_amount_check CHECK (emi_amount = principal_amount + interest_amount),
    CONSTRAINT emi_installments_paid_check CHECK (amount_paid >= 0 AND amount_paid <= emi_amount),
    CONSTRAINT emi_installments_late_fee_check CHECK (late_fee >= 0 AND late_fee_paid >= 0 AND late_fee_paid <= late_fee)
);

CREATE INDEX IF NOT EXISTS idx_emi_installments_due_status ON emi_installments(due_date, status);

-- =========================================================
-- 2. Payments received against a finance, and how each verified
--    payment was applied to the EMI schedule
-- =========================================================

-- Human-readable numbers PAY-NNNNNN and RCT-YYYY-NNNNNN both come from this sequence
CREATE SEQUENCE IF NOT EXISTS payment_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS payments (
    payment_id VARCHAR(255) NOT NULL,
    payment_number VARCHAR(20) NOT NULL,
    receipt_no VARCHAR(20) NOT NULL,
    finance_id VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    -- Date the money was received (not when it was entered)
    payment_date DATE NOT NULL,
    method VARCHAR(20) NOT NULL,
    -- UPI ref / UTR / cheque number; null for cash
    txn_id VARCHAR(100),
    reference VARCHAR(150),
    remarks VARCHAR(500),
    -- Staff who physically collected the money; null for online payments recorded by an admin
    collected_by VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    verified_by VARCHAR(255),
    verified_at TIMESTAMP(6) WITH TIME ZONE,
    created_by VARCHAR(255) NOT NULL,
    -- Client-supplied key so a double tap on "Record" can't create two receipts
    idempotency_key VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT payments_pkey PRIMARY KEY (payment_id),
    CONSTRAINT uk_payments_payment_number UNIQUE (payment_number),
    CONSTRAINT uk_payments_receipt_no UNIQUE (receipt_no),
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_payments_finance FOREIGN KEY (finance_id) REFERENCES finances(finance_id),
    CONSTRAINT fk_payments_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    CONSTRAINT fk_payments_collected_by FOREIGN KEY (collected_by) REFERENCES users(userid),
    CONSTRAINT fk_payments_verified_by FOREIGN KEY (verified_by) REFERENCES users(userid),
    CONSTRAINT fk_payments_created_by FOREIGN KEY (created_by) REFERENCES users(userid),
    CONSTRAINT payments_amount_check CHECK (amount > 0),
    CONSTRAINT payments_method_check CHECK (method IN ('CASH', 'UPI', 'BANK_TRANSFER', 'PAYMENT_GATEWAY', 'CHEQUE')),
    CONSTRAINT payments_status_check CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_payments_finance ON payments(finance_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_payment_date ON payments(payment_date);

-- Written when a payment is verified. installment_no is null for money left after the last EMI (advance credit).
CREATE TABLE IF NOT EXISTS payment_allocations (
    allocation_id VARCHAR(255) NOT NULL,
    payment_id VARCHAR(255) NOT NULL,
    installment_id VARCHAR(255),
    installment_no INTEGER,
    amount NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT payment_allocations_pkey PRIMARY KEY (allocation_id),
    CONSTRAINT fk_payment_allocations_payment FOREIGN KEY (payment_id) REFERENCES payments(payment_id),
    CONSTRAINT fk_payment_allocations_installment FOREIGN KEY (installment_id) REFERENCES emi_installments(installment_id),
    CONSTRAINT payment_allocations_amount_check CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_payment_allocations_payment ON payment_allocations(payment_id);

-- =========================================================
-- 3. Call / visit log used by the collection queue.
--    Staff log the outcome after each call.
-- =========================================================

CREATE TABLE IF NOT EXISTS followups (
    followup_id VARCHAR(255) NOT NULL,
    finance_id VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    staff_id VARCHAR(255) NOT NULL,
    call_status VARCHAR(30) NOT NULL DEFAULT 'Connected',
    response VARCHAR(500) NOT NULL,
    promise_date DATE,
    next_date DATE NOT NULL,
    remarks VARCHAR(500),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT followups_pkey PRIMARY KEY (followup_id),
    CONSTRAINT fk_followups_finance FOREIGN KEY (finance_id) REFERENCES finances(finance_id),
    CONSTRAINT fk_followups_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    CONSTRAINT fk_followups_staff FOREIGN KEY (staff_id) REFERENCES users(userid)
);

CREATE INDEX IF NOT EXISTS idx_followups_finance ON followups(finance_id);
CREATE INDEX IF NOT EXISTS idx_followups_customer ON followups(customer_id);
CREATE INDEX IF NOT EXISTS idx_followups_next_date ON followups(next_date);

-- =========================================================
-- 4. Uploaded document photos, and the documents attached to a finance
-- =========================================================

-- Human-readable file ids FILE-NNNNNN come from this sequence
CREATE SEQUENCE IF NOT EXISTS file_number_seq START WITH 1 INCREMENT BY 1;

-- One row per uploaded photo. A photo is uploaded first, then attached to a finance by its file number.
CREATE TABLE IF NOT EXISTS uploaded_files (
    file_id VARCHAR(255) NOT NULL,
    file_number VARCHAR(20) NOT NULL,
    doc_key VARCHAR(30) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    -- Name of the file on disk (never the client's file name)
    stored_name VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INTEGER,
    height INTEGER,
    mime_type VARCHAR(50) NOT NULL,
    url VARCHAR(500),
    uploaded_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uploaded_files_pkey PRIMARY KEY (file_id),
    CONSTRAINT uk_uploaded_files_file_number UNIQUE (file_number),
    CONSTRAINT uk_uploaded_files_stored_name UNIQUE (stored_name),
    CONSTRAINT fk_uploaded_files_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(userid),
    CONSTRAINT uploaded_files_size_check CHECK (size_bytes > 0)
);

-- A finance holds at most one document per type; replacing the photo updates the row
CREATE TABLE IF NOT EXISTS finance_documents (
    document_id VARCHAR(255) NOT NULL,
    finance_id VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    doc_key VARCHAR(30) NOT NULL,
    file_id VARCHAR(255) NOT NULL,
    doc_number VARCHAR(50),
    verification VARCHAR(20) NOT NULL,
    uploaded_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT finance_documents_pkey PRIMARY KEY (document_id),
    CONSTRAINT uk_finance_documents_finance_key UNIQUE (finance_id, doc_key),
    CONSTRAINT fk_finance_documents_finance FOREIGN KEY (finance_id) REFERENCES finances(finance_id),
    CONSTRAINT fk_finance_documents_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    CONSTRAINT fk_finance_documents_file FOREIGN KEY (file_id) REFERENCES uploaded_files(file_id),
    CONSTRAINT fk_finance_documents_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(userid),
    CONSTRAINT finance_documents_verification_check CHECK (verification IN ('VERIFIED', 'PENDING', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_finance_documents_finance ON finance_documents(finance_id);

-- =========================================================
-- 5. Append-only trail of who did what: sign-ins, loans created,
--    payments, follow-ups, documents.
--    Rows are only ever inserted; the application has no update or delete path.
-- =========================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    audit_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,
    detail VARCHAR(500),
    -- Related finance number or customer id; empty when there is none
    ref VARCHAR(255),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT audit_logs_pkey PRIMARY KEY (audit_id),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(userid)
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);
