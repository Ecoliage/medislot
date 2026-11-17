package fr.medislot.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "platform_event")
public class PlatformEvent {
  public static final String CLINIC_CREATED = "CLINIC_CREATED";
  public static final String CLINIC_UPDATED = "CLINIC_UPDATED";
  public static final String CLINIC_LOGO_CHANGED = "CLINIC_LOGO_CHANGED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public String action;
  public String actorEmail;
  public String clinicSlug;
  public String clinicName;
  public java.time.Instant createdAt;
}
