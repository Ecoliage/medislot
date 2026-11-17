package fr.medislot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fr.medislot.domain.AppUser;
import fr.medislot.domain.Clinic;
import fr.medislot.repository.ClinicRepository;
import fr.medislot.repository.MembershipRepository;
import fr.medislot.repository.UserRepository;
import fr.medislot.tenant.TenantContext;
import fr.medislot.tenant.TenantFilter;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class TenantFilterTest {
  private static final Long CLINIC_A = 1L;
  private static final Long CLINIC_B = 2L;
  private static final Long CAMILLE = 10L;
  private static final Long ALEX = 11L;

  private final ClinicRepository clinics = mock(ClinicRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final MembershipRepository memberships = mock(MembershipRepository.class);
  private final TenantFilter filter = new TenantFilter(clinics, users, memberships);
  private final AtomicReference<Long> seenInChain = new AtomicReference<>();
  private final FilterChain chain = (request, response) -> seenInChain.set(TenantContext.get());

  TenantFilterTest() {
    when(clinics.findBySlug("cabinet-a")).thenReturn(Optional.of(clinic(CLINIC_A, "cabinet-a")));
    when(clinics.findBySlug("cabinet-b")).thenReturn(Optional.of(clinic(CLINIC_B, "cabinet-b")));
    when(clinics.findBySlug("cabinet-z")).thenReturn(Optional.empty());
    when(users.findByEmail("patient@medislot.test")).thenReturn(Optional.of(user(CAMILLE)));
    when(users.findByEmail("autre@medislot.test")).thenReturn(Optional.of(user(ALEX)));
    when(memberships.existsByUserIdAndClinicId(CAMILLE, CLINIC_A)).thenReturn(true);
    when(memberships.existsByUserIdAndClinicId(CAMILLE, CLINIC_B)).thenReturn(true);
    when(memberships.existsByUserIdAndClinicId(ALEX, CLINIC_A)).thenReturn(true);
  }

  @AfterEach
  void reset() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
  }

  @Test
  void requestWithoutClinicIsRefusedWith400() throws Exception {
    MockHttpServletResponse response = call(request("/api/professionals"));

    assertEquals(400, response.getStatus());
    assertTrue(response.getContentAsString().contains(TenantFilter.MISSING));
    assertNull(seenInChain.get());
  }

  @Test
  void malformedSlugIsRefusedWith400WithoutTouchingTheDatabase() throws Exception {
    for (String slug : List.of("Cabinet-A", "cabinet_a", "../cabinet-a", "-a", "a".repeat(41), "")) {
      MockHttpServletRequest request = request("/api/professionals");
      request.addHeader(TenantFilter.HEADER, slug);
      assertEquals(400, call(request).getStatus(), slug);
    }
    verifyNoInteractions(clinics);
  }

  @Test
  void unknownClinicIs404() throws Exception {
    MockHttpServletResponse response = call(inClinic("/api/professionals", "cabinet-z"));

    assertEquals(404, response.getStatus());
    assertTrue(response.getContentAsString().contains(TenantFilter.UNKNOWN));
    assertNull(seenInChain.get());
  }

  @Test
  void anonymousVisitorReachesThePublicDirectoryOfTheClinic() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    assertEquals(200, call(inClinic("/api/professionals", "cabinet-b")).getStatus());
    assertEquals(CLINIC_B, seenInChain.get());
    verifyNoInteractions(memberships);
  }

  @Test
  void memberReachesItsClinic() throws Exception {
    signIn("patient@medislot.test");

    assertEquals(200, call(inClinic("/api/appointments", "cabinet-b")).getStatus());
    assertEquals(CLINIC_B, seenInChain.get());
  }

  @Test
  void nonMemberGetsTheSame404AsAnUnknownClinic() throws Exception {
    signIn("autre@medislot.test");

    MockHttpServletResponse refused = call(inClinic("/api/appointments", "cabinet-b"));
    MockHttpServletResponse unknown = call(inClinic("/api/appointments", "cabinet-z"));

    assertEquals(404, refused.getStatus());
    assertEquals(unknown.getContentAsString(), refused.getContentAsString());
    assertNull(seenInChain.get());
  }

  @Test
  void contextIsClearedEvenWhenTheRequestFails() {
    FilterChain failing =
        (request, response) -> {
          seenInChain.set(TenantContext.get());
          throw new IllegalStateException("panne");
        };

    assertThrows(
        IllegalStateException.class,
        () ->
            filter.doFilter(
                inClinic("/api/professionals", "cabinet-a"),
                new MockHttpServletResponse(),
                failing));
    assertEquals(CLINIC_A, seenInChain.get());
    assertNull(TenantContext.get());
  }

  @Test
  void queryParameterCarriesTheClinicWhenNoHeaderCanBeSent() throws Exception {
    MockHttpServletRequest stream = request("/api/events");
    stream.setParameter(TenantFilter.PARAM, "cabinet-b");
    assertEquals(200, call(stream).getStatus());
    assertEquals(CLINIC_B, seenInChain.get());

    MockHttpServletRequest ambiguous = inClinic("/api/events", "cabinet-a");
    ambiguous.setParameter(TenantFilter.PARAM, "cabinet-b");
    assertEquals(400, call(ambiguous).getStatus());
  }

  @Test
  void onlySharedPathsSkipTheClinic() throws Exception {
    for (String shared : List.of("/api/csrf", "/api/login", "/api/me", "/api/clinics", "/api/clinics/cabinet-a")) {
      seenInChain.set(-1L);
      assertEquals(200, call(request(shared)).getStatus(), shared);
      assertNull(seenInChain.get(), shared);
    }
    for (String scoped : List.of("/api/messages", "/api/medecins", "/api/clinic", "/api/register", "/api/me/clinics")) {
      assertEquals(400, call(request(scoped)).getStatus(), scoped);
    }
  }

  private MockHttpServletResponse call(MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);
    return response;
  }

  private static MockHttpServletRequest request(String path) {
    return new MockHttpServletRequest("GET", path);
  }

  private static MockHttpServletRequest inClinic(String path, String slug) {
    MockHttpServletRequest request = request(path);
    request.addHeader(TenantFilter.HEADER, slug);
    return request;
  }

  private static void signIn(String email) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                email, null, AuthorityUtils.createAuthorityList("ROLE_PATIENT")));
  }

  private static Clinic clinic(Long id, String slug) {
    Clinic clinic = new Clinic();
    clinic.id = id;
    clinic.slug = slug;
    return clinic;
  }

  private static AppUser user(Long id) {
    AppUser user = new AppUser();
    user.id = id;
    return user;
  }
}
