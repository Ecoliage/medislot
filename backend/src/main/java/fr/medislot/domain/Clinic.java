package fr.medislot.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "clinic")
public class Clinic {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public String slug;
  public String name;
  public String city;
  public String color;
}
