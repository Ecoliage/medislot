package fr.medislot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;
import fr.medislot.security.UserService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class GoogleUserTest {

  private final UserRepository users = mock(UserRepository.class);
  private final UserService service = new UserService(users, NoOpPasswordEncoder.getInstance());

  @Test
  void firstLoginCreatesAPatient() {
    when(users.findByEmail("lea.martin@gmail.com")).thenReturn(Optional.empty());
    when(users.save(any())).thenAnswer(i -> i.getArgument(0));

    OidcUser user = service.localUser(google("Lea.Martin@gmail.com", "Léa Martin", true));

    ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
    verify(users).save(saved.capture());
    assertEquals("lea.martin@gmail.com", saved.getValue().email);
    assertEquals("Léa Martin", saved.getValue().name);
    assertEquals("PATIENT", saved.getValue().role);
    assertEquals("lea.martin@gmail.com", user.getName());
    assertTrue(user.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_PATIENT")));
  }

  @Test
  void knownAccountKeepsItsRole() {
    AppUser admin = new AppUser();
    admin.email = "admin@medislot.test";
    admin.role = "ADMIN";
    when(users.findByEmail("admin@medislot.test")).thenReturn(Optional.of(admin));

    OidcUser user = service.localUser(google("admin@medislot.test", "Admin", true));

    verify(users, never()).save(any());
    assertTrue(user.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  void unverifiedEmailIsRejected() {
    assertThrows(
        OAuth2AuthenticationException.class,
        () -> service.localUser(google("lea.martin@gmail.com", "Léa Martin", false)));
    verify(users, never()).save(any());
  }

  @Test
  void missingNameFallsBackToEmail() {
    when(users.findByEmail("lea.martin@gmail.com")).thenReturn(Optional.empty());
    when(users.save(any())).thenAnswer(i -> i.getArgument(0));

    service.localUser(google("lea.martin@gmail.com", null, true));

    ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
    verify(users).save(saved.capture());
    assertEquals("lea.martin@gmail.com", saved.getValue().name);
  }

  private static OidcUser google(String email, String name, boolean verified) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", "1234567890");
    claims.put("email", email);
    claims.put("email_verified", verified);
    if (name != null) claims.put("name", name);
    Instant now = Instant.now();
    OidcIdToken token = new OidcIdToken("jeton", now, now.plusSeconds(60), claims);
    return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), token);
  }
}
