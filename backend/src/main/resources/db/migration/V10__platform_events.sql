CREATE TABLE platform_event (
  id BIGSERIAL PRIMARY KEY,
  action VARCHAR(40) NOT NULL,
  actor_email VARCHAR(254) NOT NULL,
  clinic_slug VARCHAR(40) NOT NULL,
  clinic_name VARCHAR(120) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX platform_event_recent_idx ON platform_event (created_at DESC, id DESC);
GRANT SELECT, INSERT ON platform_event TO medislot_app;
GRANT USAGE, SELECT ON SEQUENCE platform_event_id_seq TO medislot_app;
