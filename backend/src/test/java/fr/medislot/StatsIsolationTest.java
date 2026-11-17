package fr.medislot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class StatsIsolationTest extends PostgresTestBase {

  @Test
  void activityInBDoesNotMoveTheCountersReadFromA() throws Exception {
    Map<String, Object> before = stats(CLINIC_A);
    int patientsOfB = JsonPath.read(statsBody(CLINIC_B), "$.patients");

    registerPatient(CLINIC_B);
    bookFirstFreeSlot(CLINIC_B);

    assertEquals(before, stats(CLINIC_A));
    assertEquals(patientsOfB + 1, (int) JsonPath.read(statsBody(CLINIC_B), "$.patients"));
  }

  @Test
  void countersDescribeTheCurrentClinic() throws Exception {
    assertEquals(4, (int) JsonPath.read(statsBody(CLINIC_A), "$.practitioners"));
    assertEquals(7, (int) JsonPath.read(statsBody(CLINIC_B), "$.practitioners"));
    assertEquals(240, (int) JsonPath.read(statsBody(CLINIC_A), "$.slots"));
    assertEquals(420, (int) JsonPath.read(statsBody(CLINIC_B), "$.slots"));
  }

  private Map<String, Object> stats(String clinic) throws Exception {
    return JsonPath.read(statsBody(clinic), "$");
  }

  private String statsBody(String clinic) throws Exception {
    return mvc.perform(get("/api/clinic/stats").header(HEADER, clinic).with(as(PATIENT)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void registerPatient(String clinic) throws Exception {
    mvc.perform(
            post("/api/register")
                .header(HEADER, clinic)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"stats-"
                        + System.nanoTime()
                        + "@medislot.test\",\"name\":\"Patient de test\","
                        + "\"password\":\"MediSlot-test-2026!\"}"))
        .andExpect(status().isCreated());
  }

  private void bookFirstFreeSlot(String clinic) throws Exception {
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
    mvc.perform(
            post("/api/appointments")
                .header(HEADER, clinic)
                .with(as(PATIENT))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":" + slot + "}"))
        .andExpect(status().isCreated());
  }
}
