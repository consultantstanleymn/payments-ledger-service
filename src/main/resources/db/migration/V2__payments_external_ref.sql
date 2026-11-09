-- Expand step: a nullable column. An older image ignores it.
ALTER TABLE payments ADD COLUMN external_ref text NULL;
