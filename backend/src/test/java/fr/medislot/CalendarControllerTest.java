package fr.medislot;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fr.medislot.api.CalendarController;
import fr.medislot.api.Errors;
import fr.medislot.document.IcsWriter;
import fr.medislot.domain.AppUser;
import fr.medislot.domain.Appointment;
import fr.medislot.domain.Slot;
import fr.medislot.repository.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CalendarControllerTest {
  private final AppointmentRepository appointments = mock(AppointmentRepository.class);
  private final SlotRepository slots = mock(SlotRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(
              new CalendarController(appointments, slots, users, new IcsWriter()))
          .setControllerAdvice(new Errors())
          .build();

  CalendarControllerTest() {
    var proprietaire = new AppUser();
    proprietaire.id = 1L;
    var autre = new AppUser();
    autre.id = 2L;
    when(users.findByEmail("patient@test")).thenReturn(Optional.of(proprietaire));
    when(users.findByEmail("autre@test")).thenReturn(Optional.of(autre));
    var rdv = new Appointment();
    rdv.id = 7L;
    rdv.patientId = 1L;
    rdv.slotId = 5L;
    var slot = new Slot();
    slot.id = 5L;
    slot.startsAt = Instant.parse("2030-01-15T09:00:00Z");
    slot.endsAt = Instant.parse("2030-01-15T09:30:00Z");
    when(appointments.findById(7L)).thenReturn(Optional.of(rdv));
    when(slots.findById(5L)).thenReturn(Optional.of(slot));
  }

  @Test
  void ownerDownloadsTheEvent() throws Exception {
    mvc.perform(get("/api/calendar/7").principal(() -> "patient@test"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("DTSTART:20300115T090000Z")));
  }

  @Test
  void anotherPatientGetsNotFound() throws Exception {
    mvc.perform(get("/api/calendar/7").principal(() -> "autre@test"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Réservation introuvable."));
    verify(slots, never()).findById(any());
  }
}
