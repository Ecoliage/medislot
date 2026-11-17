package fr.medislot.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "appointment")
public class Appointment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @org.hibernate.annotations.TenantId
  @Column(name = "clinic_id")
  public Long clinicId;

  public Long patientId;
  public Long slotId;
  public String status;
  public java.time.Instant createdAt;
}
