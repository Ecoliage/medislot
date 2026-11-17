package fr.medislot.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "clinic")
public class Clinic {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(updatable = false)
  public String slug;
  public String name;
  public String city;
  public String color;
  public String address;

  /** File name in medislot.logo-dir, e.g. "12.png"; null without logo. */
  @Column(name = "logo_file")
  public String logoFile;
}
