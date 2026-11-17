package fr.medislot;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class TenantIsolationTest extends PostgresTestBase {

  @Test
  void eachClinicHasItsOwnDirectory() throws Exception {
    mvc.perform(get("/api/professionals?size=50").header(HEADER, CLINIC_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(4))
        .andExpect(jsonPath("$.items[*].name").value(hasItem("Dr Sarah Martin")))
        .andExpect(jsonPath("$.items[*].name").value(not(hasItem("Dr Amel Benali"))));
    mvc.perform(get("/api/professionals?size=50").header(HEADER, CLINIC_B))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(7))
        .andExpect(jsonPath("$.items[*].name").value(hasItem("Dr Amel Benali")))
        .andExpect(jsonPath("$.items[*].name").value(not(hasItem("Dr Sarah Martin"))));
  }

  @Test
  void currentClinicIsDescribedFromTheHeader() throws Exception {
    mvc.perform(get("/api/clinic").header(HEADER, CLINIC_A))
        .andExpect(jsonPath("$.name").value("Cabinet des Tilleuls"));
    mvc.perform(get("/api/clinic").header(HEADER, CLINIC_B))
        .andExpect(jsonPath("$.name").value("Maison de santé du Canal"));
    mvc.perform(get("/api/professionals"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Cabinet manquant ou invalide."));
  }

  @Test
  void practitionerOfAnotherClinicIsNotFound() throws Exception {
    int practitioner = firstPractitioner(CLINIC_A);

    mvc.perform(get("/api/professionals/" + practitioner).header(HEADER, CLINIC_A))
        .andExpect(status().isOk());
    mvc.perform(get("/api/professionals/" + practitioner).header(HEADER, CLINIC_B))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/professionals/" + practitioner + "/slots").header(HEADER, CLINIC_B))
        .andExpect(status().isNotFound());
  }

  @Test
  void appointmentBookedInAIsUnreachableFromB() throws Exception {
    int appointment = book(CLINIC_A, freeSlot(CLINIC_A));

    mvc.perform(in(get("/api/appointments"), CLINIC_A))
        .andExpect(jsonPath("$[*].id").value(hasItem(appointment)));
    mvc.perform(in(get("/api/appointments"), CLINIC_B))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id").value(not(hasItem(appointment))));
    for (String path :
        List.of("", "/history", "/fiche", "/convocation.pdf")) {
      mvc.perform(in(get("/api/appointments/" + appointment + path), CLINIC_B))
          .andExpect(status().isNotFound());
    }
    mvc.perform(in(get("/api/calendar/" + appointment), CLINIC_B)).andExpect(status().isNotFound());
    mvc.perform(in(get("/api/appointments/export"), CLINIC_B))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("\n" + appointment + ";"))));
    mvc.perform(in(get("/api/appointments/export"), CLINIC_A))
        .andExpect(content().string(containsString("\n" + appointment + ";")));
  }

  @Test
  void appointmentOfACannotBeChangedFromB() throws Exception {
    int appointment = book(CLINIC_A, freeSlot(CLINIC_A));

    mvc.perform(in(post("/api/appointments/" + appointment + "/cancel"), CLINIC_B).with(csrf()))
        .andExpect(status().isNotFound());
    mvc.perform(
            in(patch("/api/appointments/" + appointment), CLINIC_B)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":" + freeSlot(CLINIC_B) + "}"))
        .andExpect(status().isNotFound());
    mvc.perform(in(get("/api/appointments/" + appointment), CLINIC_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  @Test
  void slotOfACannotBeBookedFromB() throws Exception {
    int slot = freeSlot(CLINIC_A);

    mvc.perform(
            in(post("/api/appointments"), CLINIC_B)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":" + slot + "}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void nonMemberIsRejectedLikeAnUnknownClinic() throws Exception {
    mvc.perform(get("/api/appointments").header(HEADER, CLINIC_A).with(as(OTHER)))
        .andExpect(status().isOk());
    String refused =
        mvc.perform(get("/api/appointments").header(HEADER, CLINIC_B).with(as(OTHER)))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String unknown =
        mvc.perform(get("/api/appointments").header(HEADER, "cabinet-z").with(as(OTHER)))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(unknown, refused);
  }

  @Test
  void notificationsStayInTheirClinic() throws Exception {
    int before = notifications(CLINIC_B);
    int beforeInA = notifications(CLINIC_A);

    book(CLINIC_A, freeSlot(CLINIC_A));

    assertEquals(before, notifications(CLINIC_B));
    assertTrue(notifications(CLINIC_A) > beforeInA || beforeInA == 30);
  }

  @Test
  void rawSqlSearchOnlyReturnsTheCurrentClinic() throws Exception {
    mvc.perform(get("/api/praticiens/recherche?nom=dr").header(HEADER, CLINIC_A))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[*].ville").value(not(hasItem("Lyon"))));
    mvc.perform(get("/api/praticiens/recherche?nom=dr").header(HEADER, CLINIC_B))
        .andExpect(jsonPath("$.length()").value(7))
        .andExpect(jsonPath("$[*].ville").value(not(hasItem("Paris"))));
  }

  @Test
  void proximitySearchOnlyReturnsTheCurrentClinic() throws Exception {
    String bellecour = "/api/professionals?lat=45.7576&lon=4.8317&radiusKm=50&size=50";

    mvc.perform(get(bellecour).header(HEADER, CLINIC_B)).andExpect(jsonPath("$.total").value(7));
    mvc.perform(get(bellecour).header(HEADER, CLINIC_A)).andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void registrationJoinsOnlyTheRequestedClinic() throws Exception {
    String email = "nouveau-" + System.nanoTime() + "@medislot.test";

    mvc.perform(
            post("/api/register")
                .header(HEADER, CLINIC_B)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\""
                        + email
                        + "\",\"name\":\"Patient de test\",\"password\":\"MediSlot-test-2026!\"}"))
        .andExpect(status().isCreated());

    mvc.perform(get("/api/me/clinics").with(as(email)))
        .andExpect(jsonPath("$[*].slug").value(contains(CLINIC_B)));
    mvc.perform(get("/api/appointments").header(HEADER, CLINIC_B).with(as(email)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/appointments").header(HEADER, CLINIC_A).with(as(email)))
        .andExpect(status().isNotFound());
  }

  @Test
  void membershipsOfTheDemoAccounts() throws Exception {
    mvc.perform(get("/api/me/clinics").with(as(PATIENT)))
        .andExpect(jsonPath("$[*].slug").value(contains(CLINIC_A, CLINIC_B)));
    mvc.perform(get("/api/me/clinics").with(as(OTHER)))
        .andExpect(jsonPath("$[*].slug").value(contains(CLINIC_A)));
  }

  private static MockHttpServletRequestBuilder in(
      MockHttpServletRequestBuilder request, String clinic) {
    return request.header(HEADER, clinic).with(as(PATIENT));
  }

  private int firstPractitioner(String clinic) throws Exception {
    return JsonPath.read(body(get("/api/professionals").header(HEADER, clinic)), "$.items[0].id");
  }

  private int freeSlot(String clinic) throws Exception {
    return JsonPath.read(
        body(get("/api/professionals/" + firstPractitioner(clinic) + "/slots").header(HEADER, clinic)),
        "$[0].id");
  }

  private int book(String clinic, int slot) throws Exception {
    return JsonPath.read(
        mvc.perform(
                in(post("/api/appointments"), clinic)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"slotId\":" + slot + "}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.id");
  }

  private int notifications(String clinic) throws Exception {
    return JsonPath.read(body(in(get("/api/notifications"), clinic)), "$.length()");
  }

  private String body(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }
}
