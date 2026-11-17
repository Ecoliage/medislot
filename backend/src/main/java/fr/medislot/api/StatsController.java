package fr.medislot.api;

import fr.medislot.repository.AppointmentRepository;
import fr.medislot.repository.MembershipRepository;
import fr.medislot.repository.ProfessionalRepository;
import fr.medislot.repository.SlotRepository;
import fr.medislot.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clinic")
public class StatsController {
  private final MembershipRepository memberships;
  private final ProfessionalRepository professionals;
  private final SlotRepository slots;
  private final AppointmentRepository appointments;

  public StatsController(
      MembershipRepository memberships,
      ProfessionalRepository professionals,
      SlotRepository slots,
      AppointmentRepository appointments) {
    this.memberships = memberships;
    this.professionals = professionals;
    this.slots = slots;
    this.appointments = appointments;
  }

  public record Stats(long patients, long practitioners, long slots, long appointments) {}

  @GetMapping("/stats")
  public Stats stats() {
    return new Stats(
        memberships.countByClinicId(TenantContext.require()),
        professionals.count(),
        slots.count(),
        appointments.count());
  }
}
