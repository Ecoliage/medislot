package fr.medislot.tenant;

import fr.medislot.domain.Membership;
import fr.medislot.repository.ClinicRepository;
import fr.medislot.repository.MembershipRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ClinicMemberships {
  private final MembershipRepository memberships;
  private final ClinicRepository clinics;
  private final String defaultClinic;

  public ClinicMemberships(
      MembershipRepository memberships,
      ClinicRepository clinics,
      @Value("${medislot.default-clinic}") String defaultClinic) {
    this.memberships = memberships;
    this.clinics = clinics;
    this.defaultClinic = defaultClinic;
  }

  public void join(Long userId, Long clinicId) {
    if (memberships.existsByUserIdAndClinicId(userId, clinicId)) return;
    Membership membership = new Membership();
    membership.userId = userId;
    membership.clinicId = clinicId;
    membership.createdAt = Instant.now();
    memberships.save(membership);
  }

  public void joinDefault(Long userId) {
    join(userId, clinics.findBySlug(defaultClinic).orElseThrow().id);
  }

  public boolean isMember(Long userId, Long clinicId) {
    return memberships.existsByUserIdAndClinicId(userId, clinicId);
  }
}
