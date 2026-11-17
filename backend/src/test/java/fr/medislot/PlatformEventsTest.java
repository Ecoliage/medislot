package fr.medislot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PlatformEventsTest extends PostgresTestBase {
  private static final String ADMIN = "admin@medislot.test";
  private static final String SECOND_ADMIN = "second-admin@medislot.test";
  private static final String NEW = "cabinet-journal";

  @AfterEach
  void removeCreatedClinic() throws Exception {
    try (Connection c =
        DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
      for (String sql :
          List.of(
              "delete from membership where clinic_id in (select id from clinic where slug = ?)",
              "delete from clinic where slug = ?")) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
          ps.setString(1, NEW);
          ps.executeUpdate();
        }
      }
    }
  }

  @Test
  void aCreationIsRecordedWithTheAdministratorWhoDidIt() throws Exception {
    mvc.perform(
            post("/api/clinics")
                .with(user(ADMIN).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"slug":"%s","name":"Cabinet Journal","city":"Nantes","color":"#1a2b3c",
                     "adminEmail":"autre@medislot.test"}"""
                        .formatted(NEW)))
        .andExpect(status().isCreated());

    Map<String, Object> latest = events().get(0);
    assertEquals("CLINIC_CREATED", latest.get("action"));
    assertEquals("Cabinet créé", latest.get("label"));
    assertEquals(ADMIN, latest.get("actorEmail"));
    assertEquals(NEW, latest.get("clinicSlug"));
    assertEquals("Cabinet Journal", latest.get("clinicName"));
  }

  @Test
  void anUpdateIsRecordedWithTheAdministratorWhoDidIt() throws Exception {
    mvc.perform(
            put("/api/clinics/" + CLINIC_B)
                .with(user(SECOND_ADMIN).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Maison de santé du Canal","city":"Lyon","color":"#0b7a5c"}"""))
        .andExpect(status().isOk());

    Map<String, Object> latest = events().get(0);
    assertEquals("CLINIC_UPDATED", latest.get("action"));
    assertEquals(SECOND_ADMIN, latest.get("actorEmail"));
    assertEquals(CLINIC_B, latest.get("clinicSlug"));
  }

  @Test
  void aRefusedCreationLeavesNoTrace() throws Exception {
    int before = events().size();

    mvc.perform(
            post("/api/clinics")
                .with(user(ADMIN).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"slug":"%s","name":"Doublon","city":"Lyon","color":"#1a2b3c",
                     "adminEmail":"autre@medislot.test"}"""
                        .formatted(CLINIC_A)))
        .andExpect(status().isConflict());

    assertEquals(before, events().size());
  }

  @Test
  void aPatientIsRefused() throws Exception {
    mvc.perform(get("/api/platform/events").with(as(PATIENT))).andExpect(status().isForbidden());
  }

  @Test
  void anonymousIsRefused() throws Exception {
    mvc.perform(get("/api/platform/events")).andExpect(status().isUnauthorized());
  }

  private List<Map<String, Object>> events() throws Exception {
    return JsonPath.read(
        mvc.perform(get("/api/platform/events").with(user(ADMIN).roles("ADMIN")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$");
  }
}
