package fr.medislot.repository;

import fr.medislot.domain.Clinic;
import org.springframework.data.jpa.repository.*;

public interface ClinicRepository extends JpaRepository<Clinic, Long> {
  java.util.Optional<Clinic> findBySlug(String slug);

  @Query(
      "select c from Clinic c where c.id in (select m.clinicId from Membership m where m.userId = :userId) order by c.name")
  java.util.List<Clinic> findByMember(Long userId);
}
