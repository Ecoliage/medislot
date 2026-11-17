package fr.medislot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.medislot.repository.MembershipRepository;
import fr.medislot.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** /api/clinics/**: create, read, update and logo; administrators only, no X-Clinic header. */
class ClinicAdminTest extends PostgresTestBase {
  private static final String ADMIN = "admin@medislot.test";
  private static final String NEW = "cabinet-test";
  private static final RequestPostProcessor AS_ADMIN = user(ADMIN).roles("ADMIN");

  @Autowired UserRepository users;
  @Autowired MembershipRepository memberships;

  /** The application role can neither delete clinics nor rename seeded ones back: work as owner. */
  @AfterEach
  void restoreClinics() throws Exception {
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
      try (PreparedStatement ps =
          c.prepareStatement(
              "update clinic set name = 'Maison de santé du Canal', city = 'Lyon', color = '#0b7a5c',"
                  + " address = null, logo_file = null where slug = 'cabinet-b'")) {
        ps.executeUpdate();
      }
    }
  }

  @Test
  void anAdministratorCreatesAClinicWithItsFirstAdministrator() throws Exception {
    mvc.perform(
            json(
                    post("/api/clinics"),
                    """
                    {"slug":"%s","name":"Cabinet Test","city":"Nantes","address":" 12 rue Servient ",
                     "color":"#1A2B3C","adminEmail":"Autre@Medislot.test"}"""
                        .formatted(NEW))
                .with(AS_ADMIN))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value(NEW))
        .andExpect(jsonPath("$.name").value("Cabinet Test"))
        .andExpect(jsonPath("$.address").value("12 rue Servient"))
        .andExpect(jsonPath("$.color").value("#1a2b3c"))
        .andExpect(jsonPath("$.hasLogo").value(false));

    Long clinic = clinicId(NEW);
    assertTrue(memberships.existsByUserIdAndClinicId(users.findByEmail(OTHER).orElseThrow().id, clinic));
    assertTrue(memberships.existsByUserIdAndClinicId(users.findByEmail(ADMIN).orElseThrow().id, clinic));
    mvc.perform(get("/api/platform/clinics").with(AS_ADMIN))
        .andExpect(jsonPath("$.length()").value(3));
  }

  @Test
  void theFirstAdministratorMustAlreadyHaveAnAccount() throws Exception {
    mvc.perform(
            json(post("/api/clinics"), clinic(NEW, "Cabinet Test", "Nantes", "#1a2b3c").replace(OTHER, "personne@medislot.test"))
                .with(AS_ADMIN))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Aucun compte n'existe avec cet e-mail."));
    assertTrue(clinics.findBySlug(NEW).isEmpty());
  }

  @Test
  void anAdministratorReadsAClinic() throws Exception {
    mvc.perform(get("/api/clinics/" + CLINIC_A).with(AS_ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Cabinet des Tilleuls"))
        .andExpect(jsonPath("$.hasLogo").value(false));
    mvc.perform(get("/api/clinics/inconnu").with(AS_ADMIN)).andExpect(status().isNotFound());
  }

  @Test
  void aPngLogoIsStoredAndServed() throws Exception {
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13};
    mvc.perform(logo(CLINIC_B, "logo.svg", png))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasLogo").value(true));

    mvc.perform(get("/api/clinics/" + CLINIC_B + "/logo").with(AS_ADMIN))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(content().bytes(png));
  }

  @Test
  void anSvgLogoReplacesThePngAndIsServedWithoutScripts() throws Exception {
    mvc.perform(logo(CLINIC_B, "a.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}))
        .andExpect(status().isOk());
    byte[] svg =
        "<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
            .getBytes(StandardCharsets.UTF_8);
    mvc.perform(logo(CLINIC_B, "a.png", svg)).andExpect(status().isOk());

    mvc.perform(get("/api/clinics/" + CLINIC_B + "/logo").with(AS_ADMIN))
        .andExpect(content().contentType("image/svg+xml"))
        .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("sandbox")))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    assertTrue(java.nio.file.Files.notExists(LOGO_DIR.resolve(clinicId(CLINIC_B) + ".png")));
  }

  @Test
  void aLogoMustBeASmallPngOrSvg() throws Exception {
    mvc.perform(logo(CLINIC_B, "logo.png", "<html><script>x</script></html>".getBytes(StandardCharsets.UTF_8)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Le logo doit être un PNG ou un SVG."));
    byte[] big = new byte[200 * 1024 + 1];
    System.arraycopy(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, 0, big, 0, 8);
    mvc.perform(logo(CLINIC_B, "logo.png", big))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Le logo dépasse 200 Ko."));
    mvc.perform(get("/api/clinics/" + CLINIC_B + "/logo").with(AS_ADMIN)).andExpect(status().isNotFound());
  }

  @Test
  void aPatientCannotSendALogo() throws Exception {
    mvc.perform(
            multipart(HttpMethod.PUT, "/api/clinics/" + CLINIC_A + "/logo")
                .file(new MockMultipartFile("file", "l.svg", "image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8)))
                .with(csrf())
                .with(as(PATIENT))
                .header(HEADER, CLINIC_A))
        .andExpect(status().isForbidden());
  }

  @Test
  void aTakenSlugIsAConflict() throws Exception {
    mvc.perform(json(post("/api/clinics"), clinic(CLINIC_A, "Doublon", "Paris", "#0873b9")).with(AS_ADMIN))
        .andExpect(status().isConflict());
  }

  @Test
  void anInvalidClinicIsRefused() throws Exception {
    mvc.perform(json(post("/api/clinics"), clinic("Pas Un Slug", "X", "Paris", "rouge")).with(AS_ADMIN))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(2));
  }

  @Test
  void anAdministratorUpdatesAClinicButNotItsSlug() throws Exception {
    mvc.perform(
            json(
                    put("/api/clinics/" + CLINIC_B),
                    "{\"slug\":\"autre\",\"name\":\" Canal Santé \",\"city\":\"Villeurbanne\","
                        + "\"address\":\"3 quai du Canal\",\"color\":\"#AA0000\"}")
                .with(AS_ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value(CLINIC_B))
        .andExpect(jsonPath("$.name").value("Canal Santé"))
        .andExpect(jsonPath("$.city").value("Villeurbanne"))
        .andExpect(jsonPath("$.address").value("3 quai du Canal"))
        .andExpect(jsonPath("$.color").value("#aa0000"));

    var stored = clinics.findBySlug(CLINIC_B).orElseThrow();
    assertEquals("Canal Santé", stored.name);
    assertTrue(clinics.findBySlug("autre").isEmpty());
  }

  @Test
  void updatingAnUnknownClinicIsNotFound() throws Exception {
    mvc.perform(json(put("/api/clinics/inconnu"), changes()).with(AS_ADMIN))
        .andExpect(status().isNotFound());
  }

  @Test
  void aPatientCanNeitherCreateNorUpdate() throws Exception {
    mvc.perform(
            json(post("/api/clinics"), clinic(NEW, "Cabinet Test", "Nantes", "#1a2b3c"))
                .with(as(PATIENT))
                .header(HEADER, CLINIC_A))
        .andExpect(status().isForbidden());
    mvc.perform(json(put("/api/clinics/" + CLINIC_A), changes()).with(as(PATIENT)).header(HEADER, CLINIC_A))
        .andExpect(status().isForbidden());
    assertTrue(clinics.findBySlug(NEW).isEmpty());
    assertEquals("Cabinet des Tilleuls", clinics.findBySlug(CLINIC_A).orElseThrow().name);
  }

  @Test
  void anonymousIsRefused() throws Exception {
    mvc.perform(json(post("/api/clinics"), clinic(NEW, "Cabinet Test", "Nantes", "#1a2b3c")))
        .andExpect(status().isUnauthorized());
  }

  private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
    return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static String clinic(String slug, String name, String city, String color) {
    return """
        {"slug":"%s","name":"%s","city":"%s","color":"%s","adminEmail":"%s"}"""
        .formatted(slug, name, city, color, OTHER);
  }

  private static RequestBuilder logo(String slug, String name, byte[] bytes) {
    return multipart(HttpMethod.PUT, "/api/clinics/" + slug + "/logo")
        .file(new MockMultipartFile("file", name, "application/octet-stream", bytes))
        .with(csrf())
        .with(AS_ADMIN);
  }

  private static String changes() {
    return "{\"name\":\"Piraté\",\"city\":\"Paris\",\"color\":\"#000000\"}";
  }
}
