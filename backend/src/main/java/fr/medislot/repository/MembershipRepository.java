package fr.medislot.repository;

import fr.medislot.domain.Membership;
import org.springframework.data.jpa.repository.*;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
  boolean existsByUserIdAndClinicId(Long userId, Long clinicId);

  long countByClinicId(Long clinicId);
}
