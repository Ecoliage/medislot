package fr.medislot.repository;

import fr.medislot.domain.AppUser;
import org.springframework.data.jpa.repository.*;

public interface UserRepository extends JpaRepository<AppUser, Long> {
  java.util.Optional<AppUser> findByEmail(String email);

  java.util.Optional<AppUser> findByGoogleSub(String googleSub);

  boolean existsByEmail(String email);

  @Query(
      "select u from AppUser u where u.id in (select m.userId from Membership m where m.clinicId = :clinicId) order by u.id")
  java.util.List<AppUser> findByClinic(Long clinicId);
}
