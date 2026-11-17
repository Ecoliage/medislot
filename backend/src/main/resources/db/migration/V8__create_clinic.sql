-- Platform administrators create and edit clinics.
-- clinic is a shared table (no RLS). The slug is the clinic's address and
-- never changes: UPDATE is limited to the editable columns.
-- The id sequence was already granted to medislot_app in V5.
GRANT INSERT ON clinic TO medislot_app;
GRANT UPDATE (name, city, color) ON clinic TO medislot_app;

-- The clinic list also shows the color, to prefill the edit form.
-- New columns go last, as CREATE OR REPLACE VIEW requires; owner and grants are kept.
CREATE OR REPLACE VIEW platform_clinic AS
SELECT
  c.id,
  c.slug,
  c.name,
  c.city,
  c.created_at,
  (SELECT count(*) FROM professional p WHERE p.clinic_id = c.id) AS practitioners,
  (SELECT count(*) FROM appointment a
     WHERE a.clinic_id = c.id AND a.created_at >= now() - interval '30 days') AS appointments_30d,
  c.color
FROM clinic c;
