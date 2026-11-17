package fr.medislot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformClinicsTest extends PostgresTestBase {

  @Test
  void anAdministratorSeesEveryClinicWithCountsOnly() throws Exception {
    String body =
        mvc.perform(get("/api/platform/clinics").with(user("admin@medislot.test").roles("ADMIN")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<Map<String, Object>> rows = JsonPath.read(body, "$");
    Map<String, Object> a = bySlug(rows, CLINIC_A);
    Map<String, Object> b = bySlug(rows, CLINIC_B);
    assertEquals(4, a.get("practitioners"));
    assertEquals(7, b.get("practitioners"));
    assertEquals(
        List.of("appointments30d", "city", "color", "createdAt", "name", "practitioners", "slug"),
        a.keySet().stream().sorted().toList());
  }

  @Test
  void theListDoesNotNeedAClinicHeader() throws Exception {
    mvc.perform(get("/api/platform/clinics").with(user("admin@medislot.test").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void aPatientIsRefused() throws Exception {
    mvc.perform(get("/api/platform/clinics").with(as(PATIENT)).header(HEADER, CLINIC_A))
        .andExpect(status().isForbidden());
  }

  @Test
  void anonymousIsRefused() throws Exception {
    mvc.perform(get("/api/platform/clinics")).andExpect(status().isUnauthorized());
  }

  private static Map<String, Object> bySlug(List<Map<String, Object>> rows, String slug) {
    return rows.stream().filter(row -> slug.equals(row.get("slug"))).findFirst().orElseThrow();
  }
}
