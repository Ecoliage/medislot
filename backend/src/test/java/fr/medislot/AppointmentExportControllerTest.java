package fr.medislot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.medislot.api.AppointmentExportController;
import fr.medislot.application.BookingService;
import fr.medislot.domain.Appointment;
import fr.medislot.domain.Professional;
import fr.medislot.domain.Slot;
import fr.medislot.repository.AppointmentRepository;
import fr.medislot.repository.ProfessionalRepository;
import fr.medislot.repository.SlotRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AppointmentExportControllerTest {
  private static final String EMAIL = "patient@test";
  private static final String HEADER = "id;praticien;specialite;ville;date;statut;tarif_ttc\n";

  private String export(String name, String specialty, String city) {
    var service = mock(BookingService.class);
    var appointments = mock(AppointmentRepository.class);
    var slots = mock(SlotRepository.class);
    var professionals = mock(ProfessionalRepository.class);
    var appointment = new Appointment();
    appointment.id = 5L;
    appointment.patientId = 7L;
    appointment.slotId = 11L;
    appointment.status = "CONFIRMED";
    var slot = new Slot();
    slot.id = 11L;
    slot.professionalId = 13L;
    slot.startsAt = Instant.parse("2026-11-02T09:00:00Z");
    var professional = new Professional();
    professional.id = 13L;
    professional.name = name;
    professional.specialty = specialty;
    professional.city = city;
    professional.fee = 50;
    when(service.patient(EMAIL)).thenReturn(7L);
    when(appointments.findAll()).thenReturn(List.of(appointment));
    when(slots.findById(11L)).thenReturn(Optional.of(slot));
    when(professionals.findById(13L)).thenReturn(Optional.of(professional));
    byte[] body =
        new AppointmentExportController(service, appointments, slots, professionals)
            .exporter(() -> EMAIL)
            .getBody();
    return new String(body, StandardCharsets.UTF_8);
  }

  @Test
  void plainValuesAreLeftUntouched() {
    assertEquals(
        HEADER + "5;Dr Martin;Cardiologie;Lyon;2026-11-02T09:00:00Z;Confirme le 2026-11-02;60.0\n",
        export("Dr Martin", "cardiology", "Lyon"));
  }

  @Test
  void missingCityKeepsItsPlaceholder() {
    assertEquals(
        HEADER + "5;Dr Martin;Cardiologie;-;2026-11-02T09:00:00Z;Confirme le 2026-11-02;60.0\n",
        export("Dr Martin", "cardiology", null));
  }

  @Test
  void separatorsAndQuotesAreQuoted() {
    assertEquals(
        HEADER
            + "5;\"Dr \"\"Jo\"\"; Martin\";Cardiologie;\"Lyon\nParis\";"
            + "2026-11-02T09:00:00Z;Confirme le 2026-11-02;60.0\n",
        export("Dr \"Jo\"; Martin", "cardiology", "Lyon\nParis"));
  }

  @Test
  void formulasAreNeutralized() {
    assertEquals(
        HEADER
            + "5;\"'=HYPERLINK(\"\"http://x\"\";\"\"clic\"\")\";'+1;'@SUM(A1);"
            + "2026-11-02T09:00:00Z;Confirme le 2026-11-02;60.0\n",
        export("=HYPERLINK(\"http://x\";\"clic\")", "+1", "@SUM(A1)"));
  }
}
