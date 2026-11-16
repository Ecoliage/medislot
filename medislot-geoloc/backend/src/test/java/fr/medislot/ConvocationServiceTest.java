package fr.medislot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fr.medislot.document.*;
import fr.medislot.domain.*;
import fr.medislot.repository.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class ConvocationServiceTest {
  private final UserRepository users = mock(UserRepository.class);
  private final AppointmentRepository appointments = mock(AppointmentRepository.class);
  private final SlotRepository slots = mock(SlotRepository.class);
  private final ProfessionalRepository professionals = mock(ProfessionalRepository.class);
  private final PdfRenderer renderer = mock(PdfRenderer.class);
  private final ConvocationService service =
      new ConvocationService(users, appointments, slots, professionals, renderer);

  ConvocationServiceTest() {
    var patient = new AppUser();
    patient.id = 1L;
    patient.email = "patient@test";
    patient.name = "Hélène Durand";
    when(users.findByEmail("patient@test")).thenReturn(Optional.of(patient));
    var slot = new Slot();
    slot.id = 5L;
    slot.professionalId = 9L;
    slot.startsAt = Instant.parse("2026-01-12T08:30:00Z");
    when(slots.findById(5L)).thenReturn(Optional.of(slot));
    var pro = new Professional();
    pro.id = 9L;
    pro.name = "Dr Camille Lefèvre";
    pro.specialty = "general";
    pro.address = "12 rue de la République";
    pro.city = "Lyon";
    when(professionals.findById(9L)).thenReturn(Optional.of(pro));
  }

  private void appointment(Long id, Long patientId, String status) {
    var a = new Appointment();
    a.id = id;
    a.patientId = patientId;
    a.slotId = 5L;
    a.status = status;
    when(appointments.findById(id)).thenReturn(Optional.of(a));
  }

  private int statusOf(Long id) {
    return assertThrows(ResponseStatusException.class, () -> service.build("patient@test", id))
        .getStatusCode()
        .value();
  }

  @Test
  void unknownAppointmentIsNotFoundAndNeverRendered() {
    when(appointments.findById(99L)).thenReturn(Optional.empty());
    assertEquals(404, statusOf(99L));
    verify(renderer, never()).render(any());
  }

  @Test
  void appointmentOfAnotherPatientIsNotFoundAndNeverRendered() {
    appointment(7L, 2L, "CONFIRMED");
    assertEquals(404, statusOf(7L));
    verify(renderer, never()).render(any());
  }

  @Test
  void cancelledAppointmentIsConflictAndNeverRendered() {
    appointment(7L, 1L, "CANCELLED");
    var e = assertThrows(ResponseStatusException.class, () -> service.build("patient@test", 7L));
    assertEquals(409, e.getStatusCode().value());
    assertEquals(ConvocationService.CANCELLED, e.getReason());
    verify(renderer, never()).render(any());
  }

  @Test
  void confirmedAppointmentIsRenderedWithItsDetails() {
    appointment(7L, 1L, "CONFIRMED");
    byte[] pdf = {37, 80, 68, 70};
    when(renderer.render(any())).thenReturn(pdf);
    assertArrayEquals(pdf, service.build("patient@test", 7L));
    var captor = ArgumentCaptor.forClass(ConvocationView.class);
    verify(renderer).render(captor.capture());
    var view = captor.getValue();
    assertEquals("Hélène Durand", view.patientName());
    assertEquals("Médecine générale", view.specialty());
    assertEquals("12 rue de la République", view.address());
    assertEquals("RDV-7", view.reference());
    assertEquals("9 h 30", view.time());
  }
}
