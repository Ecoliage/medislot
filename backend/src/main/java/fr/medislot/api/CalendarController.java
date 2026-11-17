package fr.medislot.api;

import org.springframework.web.bind.annotation.*;
import fr.medislot.domain.AppUser;
import fr.medislot.domain.Appointment;
import fr.medislot.domain.Slot;
import fr.medislot.document.IcsWriter;
import fr.medislot.repository.AppointmentRepository;
import fr.medislot.repository.SlotRepository;
import fr.medislot.repository.UserRepository;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {
      private final AppointmentRepository appointmentRepository;
      private final SlotRepository slotRepository;
      private final UserRepository userRepository;
      private final IcsWriter icsWriter;

      public CalendarController(AppointmentRepository appointmentRepository, SlotRepository slotRepository, UserRepository userRepository, IcsWriter icsWriter) {
          this.appointmentRepository = appointmentRepository;
          this.slotRepository = slotRepository;
          this.userRepository = userRepository;
          this.icsWriter = icsWriter;
      }

      @GetMapping(value = "/{appointmentId}", produces = "text/calendar")
      public ResponseEntity<String> getIcs(@PathVariable Long appointmentId, Principal principal) {
            Appointment appointment = findAppointment(appointmentId, principal);
            Slot slot = findSlot(appointment.slotId);
            String ics = icsWriter.write(slot.startsAt, slot.endsAt, appointmentId);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rendez-vous-" + appointmentId + ".ics\"")
                .body(ics);
      }

      private Appointment findAppointment(Long appointmentId, Principal principal) {
            AppUser patient = userRepository.findByEmail(principal.getName()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Réservation introuvable.")
            );
            Appointment a = appointmentRepository.findById(appointmentId).filter(r -> r.patientId.equals(patient.id)).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Réservation introuvable.")
            );
            return a;
      }

      private Slot findSlot(Long slotId) {
           Slot s = slotRepository.findById(slotId).orElseThrow(
               () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Réservation introuvable.")
           );
           return s;
      }
}
