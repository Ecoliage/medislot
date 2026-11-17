package fr.medislot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class ClinicLogsTest extends PostgresTestBase {
  private static final RequestPostProcessor ADMIN = user("admin@medislot.test").roles("ADMIN");

  @Test
  void aBookingInBAppearsFirstInTheLogOfBAndNotInA() throws Exception {
    List<Map<String, Object>> logsOfA = logs(CLINIC_A);

    int appointment = bookFirstFreeSlot(CLINIC_B);

    Map<String, Object> latest = logs(CLINIC_B).get(0);
    assertEquals(appointment, latest.get("appointmentId"));
    assertEquals("CREATED", latest.get("action"));
    assertEquals("Réservation confirmée", latest.get("label"));
    assertFalse(((String) latest.get("practitioner")).isBlank());
    assertEquals(logsOfA, logs(CLINIC_A));
  }

  @Test
  void anEntryExposesNoPatientIdentity() throws Exception {
    bookFirstFreeSlot(CLINIC_A);

    assertEquals(
        List.of("action", "appointmentId", "createdAt", "id", "label", "practitioner"),
        logs(CLINIC_A).get(0).keySet().stream().sorted().toList());
  }

  @Test
  void aPatientIsRefused() throws Exception {
    mvc.perform(get("/api/clinic/logs").header(HEADER, CLINIC_A).with(as(PATIENT)))
        .andExpect(status().isForbidden());
  }

  @Test
  void anonymousIsRefused() throws Exception {
    mvc.perform(get("/api/clinic/logs").header(HEADER, CLINIC_A))
        .andExpect(status().isUnauthorized());
  }

  private List<Map<String, Object>> logs(String clinic) throws Exception {
    return JsonPath.read(
        mvc.perform(get("/api/clinic/logs").header(HEADER, clinic).with(ADMIN))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$");
  }

  private int bookFirstFreeSlot(String clinic) throws Exception {
    int practitioner =
        JsonPath.read(
            mvc.perform(get("/api/professionals").header(HEADER, clinic))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.items[0].id");
    int slot =
        JsonPath.read(
            mvc.perform(get("/api/professionals/" + practitioner + "/slots").header(HEADER, clinic))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$[0].id");
    return JsonPath.read(
        mvc.perform(
                post("/api/appointments")
                    .header(HEADER, clinic)
                    .with(as(PATIENT))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"slotId\":" + slot + "}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.id");
  }
}
