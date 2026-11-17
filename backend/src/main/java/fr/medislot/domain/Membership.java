package fr.medislot.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "membership")
public class Membership {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long userId;
  public Long clinicId;
  public java.time.Instant createdAt;
}
