package fr.medislot.document;

import fr.medislot.domain.AppUser;
import fr.medislot.domain.Appointment;
import fr.medislot.domain.Professional;
import fr.medislot.domain.Slot;
import fr.medislot.repository.AppointmentRepository;
import fr.medislot.repository.ProfessionalRepository;
import fr.medislot.repository.SlotRepository;
import fr.medislot.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConvocationService {

  public static final String NOT_FOUND = "Rendez-vous introuvable.";
  public static final String CANCELLED = "Ce rendez-vous est annulé : il n'a plus de convocation.";

  private final UserRepository users;
  private final AppointmentRepository appointments;
  private final SlotRepository slots;
  private final ProfessionalRepository professionals;
  private final PdfRenderer renderer;

  public ConvocationService(
      UserRepository users,
      AppointmentRepository appointments,
      SlotRepository slots,
      ProfessionalRepository professionals,
      PdfRenderer renderer) {
    this.users = users;
    this.appointments = appointments;
    this.slots = slots;
    this.professionals = professionals;
    this.renderer = renderer;
  }

  public byte[] build(String email, Long id) {
    AppUser patient = users.findByEmail(email).orElseThrow(ConvocationService::notFound);
    Appointment appointment =
        appointments
            .findById(id)
            .filter(a -> a.patientId.equals(patient.id))
            .orElseThrow(ConvocationService::notFound);
    if ("CANCELLED".equals(appointment.status)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, CANCELLED);
    }
    return renderer.render(view(patient, appointment));
  }

  public String filename(Long id) {
    return "convocation-" + id + ".pdf";
  }

  private ConvocationView view(AppUser patient, Appointment appointment) {
    Slot slot = slots.findById(appointment.slotId).orElseThrow(ConvocationService::notFound);
    Professional professional =
        professionals.findById(slot.professionalId).orElseThrow(ConvocationService::notFound);
    return new ConvocationView(
        patient.name,
        ConvocationFormats.day(slot.startsAt),
        ConvocationFormats.time(slot.startsAt),
        professional.name,
        ConvocationView.specialtyLabel(professional.specialty),
        professional.address,
        professional.city,
        ConvocationView.reference(appointment.id));
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
  }
}
