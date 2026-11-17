package fr.medislot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fr.medislot.api.DebugController;
import fr.medislot.application.AdminBookingService;
import fr.medislot.domain.AppUser;
import fr.medislot.domain.Appointment;
import fr.medislot.repository.*;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DebugControllerTest {
  private DebugController controller(String adminPassword) {
    return controller(adminPassword, List.of());
  }

  private DebugController controller(String adminPassword, List<AppUser> patients) {
    var appointments = mock(AppointmentRepository.class);
    var users = mock(UserRepository.class);
    var slots = mock(SlotRepository.class);
    var rdv = new Appointment();
    rdv.status = "CONFIRMED";
    when(appointments.findAll()).thenReturn(List.of(rdv));
    when(users.findAll()).thenReturn(patients);
    when(slots.count()).thenReturn(3L);
    return new DebugController(
        appointments,
        slots,
        users,
        mock(AdminBookingService.class),
        mock(DataSource.class),
        adminPassword);
  }

  @Test
  void exportBucketDoesNotExposeTheAccessKeyId() {
    var infos =
        controller("secret-de-test").info("secret-de-test", mock(HttpServletResponse.class));
    assertEquals("s3://medislot-exports", infos.get("bucketExport"));
  }

  @Test
  void csvCellsAreQuotedAndFormulasNeutralized() {
    var patient = new AppUser();
    patient.id = 4L;
    patient.name = "=HYPERLINK(\"http://x\";\"clic\")";
    patient.email = "-patient@test";
    patient.role = "PATIENT";
    assertEquals(
        "id;nom;email;role\n"
            + "\"4\";\"'=HYPERLINK(\"\"http://x\"\";\"\"clic\"\")\";"
            + "\"'-patient@test\";\"PATIENT\"\n",
        controller("secret-de-test", List.of(patient)).exporterPatients());
  }

  @Test
  void wrongPasswordIsForbidden() {
    assertEquals(
        403,
        assertThrows(
                ResponseStatusException.class,
                () -> controller("secret-de-test").info("autre", mock(HttpServletResponse.class)))
            .getStatusCode()
            .value());
  }

  @Test
  void emptyConfiguredPasswordNeverGrantsAccess() {
    assertThrows(
        ResponseStatusException.class,
        () -> controller("").info("", mock(HttpServletResponse.class)));
  }
}
