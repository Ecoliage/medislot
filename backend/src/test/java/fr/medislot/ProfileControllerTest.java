package fr.medislot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fr.medislot.api.Errors;
import fr.medislot.api.ProfileController;
import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProfileControllerTest {
  private final UserRepository users = mock(UserRepository.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(
              new ProfileController(users, NoOpPasswordEncoder.getInstance()))
          .setControllerAdvice(new Errors())
          .build();
  private final AppUser patient = new AppUser();

  ProfileControllerTest() {
    patient.id = 3L;
    patient.email = "patient@test";
    patient.name = "Camille Dupont";
    patient.password = "MediSlot-demo-2026!";
    patient.role = "PATIENT";
    when(users.findByEmail("patient@test")).thenReturn(Optional.of(patient));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void nameChangeDoesNotNeedThePassword() throws Exception {
    mvc.perform(modification("{\"name\":\"Camille Martin\",\"email\":\"patient@test\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Camille Martin"));
    verify(users).save(patient);
  }

  @Test
  void emailChangeWithoutPasswordIsRejected() throws Exception {
    mvc.perform(modification("{\"name\":\"Camille Dupont\",\"email\":\"pirate@test\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value("Le mot de passe actuel est requis pour changer d'adresse."));
    assertEquals("patient@test", patient.email);
    verify(users, never()).save(any());
  }

  @Test
  void emailChangeWithWrongPasswordIsForbidden() throws Exception {
    mvc.perform(
            modification(
                "{\"name\":\"Camille Dupont\",\"email\":\"pirate@test\","
                    + "\"currentPassword\":\"mauvais-mot-de-passe\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Mot de passe actuel incorrect."));
    verify(users, never()).save(any());
  }

  @Test
  void emailChangeWithPasswordUpdatesTheSession() throws Exception {
    MvcResult result =
        mvc.perform(
                modification(
                    "{\"name\":\"Camille Dupont\",\"email\":\"Nouvelle@Test\","
                        + "\"currentPassword\":\"MediSlot-demo-2026!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("nouvelle@test"))
            .andReturn();
    verify(users).save(patient);
    SecurityContext session =
        (SecurityContext)
            result
                .getRequest()
                .getSession()
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    assertEquals("nouvelle@test", session.getAuthentication().getName());
  }

  @Test
  void googleAccountCannotChangeItsEmail() throws Exception {
    patient.googleSub = "1234567890";
    mvc.perform(
            modification(
                "{\"name\":\"Camille Dupont\",\"email\":\"pirate@test\","
                    + "\"currentPassword\":\"MediSlot-demo-2026!\"}"))
        .andExpect(status().isForbidden());
    verify(users, never()).save(any());
  }

  @Test
  void controlCharactersInNameAreRejected() throws Exception {
    mvc.perform(modification("{\"name\":\"Camille\\nDupont\",\"email\":\"patient@test\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("name"));
    verify(users, never()).save(any());
  }

  private static RequestBuilder modification(String corps) {
    return put("/api/patients/3")
        .principal(() -> "patient@test")
        .contentType(MediaType.APPLICATION_JSON)
        .content(corps);
  }
}
