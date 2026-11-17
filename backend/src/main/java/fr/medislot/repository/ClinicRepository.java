package fr.medislot.repository;

import fr.medislot.domain.Clinic;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicRepository extends JpaRepository<Clinic, Long> {
  java.util.Optional<Clinic> findBySlug(String slug);
}
