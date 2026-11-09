-- Contract-side step: use the column. A unique index for non-null references.
CREATE UNIQUE INDEX payments_external_ref_uq ON payments (external_ref) WHERE external_ref IS NOT NULL;
