-- =========================================================
-- V4__activate_existing_finances.sql
-- Admin approval of finances was added after these loans were made.
--   1. Before that nothing ever moved a finance out of PENDING_APPROVAL, so every loan
--      still in that state is already in use: mark it ACTIVE so its payments keep working.
--      approved_by stays NULL to show these were activated by this migration, not an admin.
--   2. The DO number is now added by an admin (at creation or when approving), so a
--      finance waiting for approval has none yet. Still unique when set.
-- =========================================================

-- 1. Existing loans
UPDATE finances
SET status = 'ACTIVE',
    approved_at = COALESCE(approved_at, now())
WHERE status = 'PENDING_APPROVAL';

-- 2. DO number is empty until an admin adds it
ALTER TABLE finances ALTER COLUMN do_number DROP NOT NULL;
