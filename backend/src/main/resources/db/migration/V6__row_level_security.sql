CREATE FUNCTION current_clinic() RETURNS BIGINT
  LANGUAGE sql STABLE
  AS $$ SELECT nullif(current_setting('app.current_clinic', true), '')::bigint $$;

ALTER TABLE professional ENABLE ROW LEVEL SECURITY;
ALTER TABLE professional FORCE ROW LEVEL SECURITY;
CREATE POLICY clinic_isolation ON professional
  USING (clinic_id = current_clinic())
  WITH CHECK (clinic_id = current_clinic());

ALTER TABLE slot ENABLE ROW LEVEL SECURITY;
ALTER TABLE slot FORCE ROW LEVEL SECURITY;
CREATE POLICY clinic_isolation ON slot
  USING (clinic_id = current_clinic())
  WITH CHECK (clinic_id = current_clinic());

ALTER TABLE appointment ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointment FORCE ROW LEVEL SECURITY;
CREATE POLICY clinic_isolation ON appointment
  USING (clinic_id = current_clinic())
  WITH CHECK (clinic_id = current_clinic());

ALTER TABLE audit_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_event FORCE ROW LEVEL SECURITY;
CREATE POLICY clinic_isolation ON audit_event
  USING (clinic_id = current_clinic())
  WITH CHECK (clinic_id = current_clinic());

ALTER TABLE notification ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification FORCE ROW LEVEL SECURITY;
CREATE POLICY clinic_isolation ON notification
  USING (clinic_id = current_clinic())
  WITH CHECK (clinic_id = current_clinic());
