package fr.medislot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** /api/clinics/{slug}/professionals/**: administrators read a minimised calendar. */
class ClinicCalendarTest extends PostgresTestBase {
  private static final RequestPostProcessor AS_ADMIN = user("admin@medislot.test").roles("ADMIN");

  /** Demo appointments sit 7 to 10 days ahead: the week of today + 8 always holds some. */
  private static final LocalDate WEEK = LocalDate.now(ZoneId.of("Europe/Paris")).plusDays(8);

  private String read(String url) throws Exception {
    return mvc.perform(get(url).with(AS_ADMIN))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private long firstPractitioner(String slug) throws Exception {
    return ((Number) JsonPath.read(read("/api/clinics/" + slug + "/professionals"), "$[0].id"))
        .longValue();
  }

  @Test
  void anAdministratorReadsTheWeekWithPseudonymisedPatients() throws Exception {
    long practitioner = firstPractitioner(CLINIC_A);
    String body =
        read("/api/clinics/cabinet-a/professionals/" + practitioner + "/appointments?week=" + WEEK);
    List<Map<String, Object>> week = JsonPath.read(body, "$");
    assertFalse(week.isEmpty(), body);
    assertFalse(body.contains("medislot.test"), body);
    assertFalse(body.contains("Camille"), body);
    for (Map<String, Object> entry : week) {
      assertEquals(List.of("id", "startsAt", "patient", "status"), List.copyOf(entry.keySet()));
      assertTrue(((String) entry.get("patient")).matches("P-[0-9A-F]{4}"), body);
      assertEquals(week.get(0).get("patient"), entry.get("patient"));
    }
  }

  @Test
  void aPractitionerOfAnotherClinicIsNotFound() throws Exception {
    long practitionerOfB = firstPractitioner(CLINIC_B);
    mvc.perform(
            get("/api/clinics/cabinet-a/professionals/" + practitionerOfB + "/appointments")
                .param("week", WEEK.toString())
                .with(AS_ADMIN))
        .andExpect(status().isNotFound());
  }

  @Test
  void eachClinicListsOnlyItsPractitioners() throws Exception {
    assertEquals(4, ((List<?>) JsonPath.read(read("/api/clinics/cabinet-a/professionals"), "$")).size());
    assertEquals(7, ((List<?>) JsonPath.read(read("/api/clinics/cabinet-b/professionals"), "$")).size());
  }

  @Test
  void aPatientIsRefused() throws Exception {
    mvc.perform(get("/api/clinics/cabinet-a/professionals").with(as(PATIENT)))
        .andExpect(status().isForbidden());
  }

  @Test
  void anUnknownClinicIsNotFound() throws Exception {
    mvc.perform(get("/api/clinics/nope/professionals").with(AS_ADMIN))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Cabinet introuvable."));
  }
}
