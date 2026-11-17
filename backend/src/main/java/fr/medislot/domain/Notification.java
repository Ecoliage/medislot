package fr.medislot.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "notification")
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @org.hibernate.annotations.TenantId
  @Column(name = "clinic_id")
  public Long clinicId;

  public Long patientId;
  public String message;
  public java.time.Instant createdAt;
}
