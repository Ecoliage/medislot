CREATE TABLE clinic (
  id BIGSERIAL PRIMARY KEY,
  slug VARCHAR(40) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  name VARCHAR(120) NOT NULL,
  city VARCHAR(80) NOT NULL,
  color VARCHAR(7) NOT NULL CHECK (color ~ '^#[0-9a-f]{6}$')
);

INSERT INTO clinic (slug, name, city, color) VALUES
  ('cabinet-a', 'Cabinet des Tilleuls', 'Paris', '#0873b9'),
  ('cabinet-b', 'Maison de santé du Canal', 'Lyon', '#0b7a5c');

CREATE TABLE membership (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES app_user(id),
  clinic_id BIGINT NOT NULL REFERENCES clinic(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, clinic_id)
);
CREATE INDEX membership_clinic_idx ON membership (clinic_id);

INSERT INTO membership (user_id, clinic_id)
SELECT u.id, c.id FROM app_user u CROSS JOIN clinic c WHERE c.slug = 'cabinet-a';

ALTER TABLE professional ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
ALTER TABLE slot ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
ALTER TABLE appointment ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
ALTER TABLE audit_event ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);
ALTER TABLE notification ADD COLUMN clinic_id BIGINT REFERENCES clinic(id);

UPDATE professional SET clinic_id = (SELECT id FROM clinic WHERE slug = 'cabinet-a');
UPDATE slot SET clinic_id = (SELECT id FROM clinic WHERE slug = 'cabinet-a');
UPDATE appointment SET clinic_id = (SELECT id FROM clinic WHERE slug = 'cabinet-a');
UPDATE audit_event SET clinic_id = (SELECT id FROM clinic WHERE slug = 'cabinet-a');
UPDATE notification SET clinic_id = (SELECT id FROM clinic WHERE slug = 'cabinet-a');

ALTER TABLE professional ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE slot ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE appointment ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE audit_event ALTER COLUMN clinic_id SET NOT NULL;
ALTER TABLE notification ALTER COLUMN clinic_id SET NOT NULL;

ALTER TABLE professional ADD CONSTRAINT professional_clinic_id_key UNIQUE (clinic_id, id);
ALTER TABLE slot ADD CONSTRAINT slot_clinic_id_key UNIQUE (clinic_id, id);
ALTER TABLE appointment ADD CONSTRAINT appointment_clinic_id_key UNIQUE (clinic_id, id);

ALTER TABLE slot DROP CONSTRAINT slot_professional_id_fkey;
ALTER TABLE slot ADD CONSTRAINT slot_clinic_professional_fkey
  FOREIGN KEY (clinic_id, professional_id) REFERENCES professional (clinic_id, id);

ALTER TABLE appointment DROP CONSTRAINT appointment_slot_id_fkey;
ALTER TABLE appointment ADD CONSTRAINT appointment_clinic_slot_fkey
  FOREIGN KEY (clinic_id, slot_id) REFERENCES slot (clinic_id, id);

ALTER TABLE audit_event DROP CONSTRAINT audit_event_appointment_id_fkey;
ALTER TABLE audit_event ADD CONSTRAINT audit_event_clinic_appointment_fkey
  FOREIGN KEY (clinic_id, appointment_id) REFERENCES appointment (clinic_id, id);

DROP INDEX appointment_patient_idx;
CREATE INDEX appointment_patient_idx ON appointment (clinic_id, patient_id, created_at DESC);
CREATE INDEX professional_clinic_idx ON professional (clinic_id, name);
CREATE INDEX notification_patient_idx ON notification (clinic_id, patient_id, created_at DESC);
CREATE INDEX audit_event_appointment_idx ON audit_event (clinic_id, appointment_id, created_at);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'medislot_app') THEN
    CREATE ROLE medislot_app LOGIN;
  END IF;
END
$$;
ALTER ROLE medislot_app WITH LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '${app_password}';

GRANT SELECT, INSERT, UPDATE, DELETE ON app_user, membership, professional, slot, appointment, audit_event, notification TO medislot_app;
GRANT SELECT ON clinic TO medislot_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO medislot_app;
