-- =========================================================
-- V3__schema_updates_after_v2.sql
-- Entity changes made after V2:
--   1. Assets: product type is free text
--   2. Finances: EMI due day 1-31, overdue percentage, AutoPay mandate fields
--   3. Finance documents: file name
--   4. Payments: lookup indexes
--   5. Customer documents (Stage 2 uploads, before a finance exists)
--   6. Product catalog and product images
-- Every statement is safe to run when Hibernate (ddl-auto=update) already
-- created the column / table / index.
-- =========================================================

-- 1. Product type is free text now (any product, not only TOTO / BATTERY)
ALTER TABLE assets DROP CONSTRAINT IF EXISTS assets_product_type_check;
ALTER TABLE assets ALTER COLUMN product_type TYPE VARCHAR(50);

-- 2. Finances
-- EMI due day may be any day; in shorter months the EMI falls on the last day
ALTER TABLE finances DROP CONSTRAINT IF EXISTS finances_emi_due_day_check;
ALTER TABLE finances ADD CONSTRAINT finances_emi_due_day_check CHECK (emi_due_day BETWEEN 1 AND 31);

ALTER TABLE finances ADD COLUMN IF NOT EXISTS overdue_percentage NUMERIC(5,2);
UPDATE finances SET overdue_percentage = 0 WHERE overdue_percentage IS NULL;
ALTER TABLE finances ALTER COLUMN overdue_percentage SET DEFAULT 0;
ALTER TABLE finances ALTER COLUMN overdue_percentage SET NOT NULL;

ALTER TABLE finances ADD COLUMN IF NOT EXISTS subscription_id VARCHAR(100);
ALTER TABLE finances ADD COLUMN IF NOT EXISTS auto_pay_status VARCHAR(50);

-- 3. Finance documents keep the client's file name for display
ALTER TABLE finance_documents ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);

-- 4. Payments are looked up by transaction id / reference to catch duplicates
CREATE INDEX IF NOT EXISTS idx_payments_txnid ON payments(txn_id);
CREATE INDEX IF NOT EXISTS idx_payments_reference ON payments(reference);

-- 5. Documents uploaded for a customer before a finance exists (Stage 2).
--    At most one per type; copied to finance_documents when the finance is created (Stage 3).
--    file_id has no foreign key: an uploaded file can be deleted on its own.
CREATE TABLE IF NOT EXISTS customer_documents (
    id VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    doc_key VARCHAR(30) NOT NULL,
    file_id VARCHAR(255) NOT NULL,
    doc_number VARCHAR(50),
    file_name VARCHAR(255),
    url VARCHAR(500),
    verification VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    uploaded_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT customer_documents_pkey PRIMARY KEY (id),
    CONSTRAINT uk_customer_documents_key UNIQUE (customer_id, doc_key),
    CONSTRAINT fk_customer_documents_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    CONSTRAINT fk_customer_documents_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(userid),
    CONSTRAINT customer_documents_verification_check CHECK (verification IN ('VERIFIED', 'PENDING', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_customer_documents_customer ON customer_documents(customer_id);

-- 6. Product catalog: stock available for sale or financing (not the financed unit; that is assets)
CREATE TABLE IF NOT EXISTS products (
    product_id VARCHAR(255) NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    price NUMERIC(12,2) NOT NULL,
    details TEXT,
    product_type VARCHAR(50),
    brand VARCHAR(100),
    model VARCHAR(100),
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    in_stock BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT products_pkey PRIMARY KEY (product_id),
    CONSTRAINT fk_products_created_by FOREIGN KEY (created_by) REFERENCES users(userid)
);

CREATE INDEX IF NOT EXISTS idx_products_product_name ON products(product_name);
CREATE INDEX IF NOT EXISTS idx_products_product_type ON products(product_type);

CREATE TABLE IF NOT EXISTS product_images (
    product_id VARCHAR(255) NOT NULL,
    image_url VARCHAR(500) NOT NULL,

    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE INDEX IF NOT EXISTS idx_product_images_product ON product_images(product_id);
