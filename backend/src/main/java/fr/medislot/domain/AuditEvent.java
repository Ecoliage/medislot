package fr.medislot.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "audit_event")
public class AuditEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @org.hibernate.annotations.TenantId
  @Column(name = "clinic_id")
  public Long clinicId;

  public Long appointmentId;
  public Long actorId;
  public String action;
  public java.time.Instant createdAt;
}
