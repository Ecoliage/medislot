package fr.medislot.api;

import fr.medislot.application.BookingService;
import fr.medislot.domain.Appointment;
import fr.medislot.domain.Professional;
import fr.medislot.domain.Slot;
import fr.medislot.repository.AppointmentRepository;
import fr.medislot.repository.ProfessionalRepository;
import fr.medislot.repository.SlotRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentExportController {

  private static final BigDecimal TVA = new BigDecimal("1.2");
  private static final Map<String, String> SPECIALITES =
      Map.of(
          "general",
          "Medecine generale",
          "cardiology",
          "Cardiologie",
          "dermatology",
          "Dermatologie",
          "dentistry",
          "Chirurgie dentaire");

  private final BookingService service;
  private final AppointmentRepository appointments;
  private final SlotRepository slots;
  private final ProfessionalRepository professionals;

  public AppointmentExportController(
      BookingService service,
      AppointmentRepository appointments,
      SlotRepository slots,
      ProfessionalRepository professionals) {
    this.service = service;
    this.appointments = appointments;
    this.slots = slots;
    this.professionals = professionals;
  }

  @GetMapping("/export")
  public ResponseEntity<byte[]> exporter(Principal principal) {
    Long patient = service.patient(principal.getName());
    StringBuilder csv = new StringBuilder("id;praticien;specialite;ville;date;statut;tarif_ttc\n");
    for (Appointment a : appointments.findAll()) {
      if (patient.equals(a.patientId)) {
        Slot s = creneau(a.slotId);
        Professional p = praticien(s.professionalId);
        BigDecimal ttc = BigDecimal.valueOf(p.fee).multiply(TVA);
        csv.append(ligne(a, p, s, ttc));
      }
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=mes-rendez-vous.csv")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(csv.toString().getBytes(StandardCharsets.UTF_8));
  }

  private String ligne(Appointment a, Professional p, Slot s, BigDecimal tarif) {
    String date = s.startsAt.toString();
    String specialite = SPECIALITES.getOrDefault(p.specialty, p.specialty);
    String statut =
        switch (a.status) {
          case "CONFIRMED" -> "Confirme le " + date.substring(0, 10);
          case "CANCELLED" -> "Annule";
          default -> "Inconnu";
        };
    String ville = p.city == null || p.city.isEmpty() ? "-" : p.city;
    return String.join(
            ";",
            String.valueOf(a.id),
            p.name,
            specialite,
            ville,
            date,
            statut,
            tarif.toPlainString())
        + "\n";
  }

  @GetMapping("/{id}/fiche")
  public AppointmentController.AppointmentDto fiche(@PathVariable Long id, Principal principal) {
    Long patient = service.patient(principal.getName());
    Appointment a =
        appointments
            .findById(id)
            .filter(x -> patient.equals(x.patientId))
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Rendez-vous introuvable."));
    Slot s = creneau(a.slotId);
    Professional p = praticien(s.professionalId);
    return new AppointmentController.AppointmentDto(
        a.id, s.id, p.id, p.name, p.specialty, p.address, p.city, s.startsAt, a.status);
  }

  private Slot creneau(Long id) {
    return slots
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Créneau introuvable."));
  }

  private Professional praticien(Long id) {
    return professionals
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Praticien introuvable."));
  }
}
