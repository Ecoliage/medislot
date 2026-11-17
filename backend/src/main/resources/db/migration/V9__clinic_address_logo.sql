-- Address and logo of a clinic, set by platform administrators.
-- The logo file lives on disk (medislot.logo-dir); only its file name is stored.
ALTER TABLE clinic ADD COLUMN address VARCHAR(160);
ALTER TABLE clinic ADD COLUMN logo_file VARCHAR(64);
GRANT UPDATE (address, logo_file) ON clinic TO medislot_app;
