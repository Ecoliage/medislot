-- Clinic list for platform administration.
-- The application (medislot_app) cannot read tenant tables outside its clinic:
-- it goes through a view that only returns counts, never rows.

ALTER TABLE clinic ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'medislot_platform') THEN
    CREATE ROLE medislot_platform NOLOGIN;
  END IF;
END
$$;
ALTER ROLE medislot_platform WITH NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;

GRANT SELECT ON clinic, professional, appointment TO medislot_platform;

-- The view is owned by medislot_platform, so RLS on the tables it reads
-- applies to that role. These policies, limited to this NOLOGIN role,
-- let it read every clinic, only through the view.
CREATE POLICY platform_read ON professional FOR SELECT TO medislot_platform USING (true);
CREATE POLICY platform_read ON appointment FOR SELECT TO medislot_platform USING (true);

CREATE VIEW platform_clinic AS
SELECT
  c.id,
  c.slug,
  c.name,
  c.city,
  c.created_at,
  (SELECT count(*) FROM professional p WHERE p.clinic_id = c.id) AS practitioners,
  (SELECT count(*) FROM appointment a
     WHERE a.clinic_id = c.id AND a.created_at >= now() - interval '30 days') AS appointments_30d
FROM clinic c;

ALTER VIEW platform_clinic OWNER TO medislot_platform;
GRANT SELECT ON platform_clinic TO medislot_app;
