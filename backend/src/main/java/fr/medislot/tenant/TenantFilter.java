package fr.medislot.tenant;

import fr.medislot.domain.Clinic;
import fr.medislot.repository.ClinicRepository;
import fr.medislot.repository.MembershipRepository;
import fr.medislot.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class TenantFilter extends OncePerRequestFilter {
  public static final String HEADER = "X-Clinic";
  public static final String PARAM = "clinic";
  public static final String MISSING = "Cabinet manquant ou invalide.";
  public static final String UNKNOWN = "Cabinet introuvable.";

  private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
  private static final int SLUG_MAX = 40;
  private static final String API = "/api/";
  private static final Set<String> SHARED_PATHS =
      Set.of(
          "/api/csrf",
          "/api/login",
          "/api/login/google",
          "/api/logout",
          "/api/me",
          "/api/clinics");
  private static final Set<String> SHARED_PREFIXES = Set.of("/api/compte/", "/api/patients/", "/api/platform/", "/api/clinics/");

  private final ClinicRepository clinics;
  private final UserRepository users;
  private final MembershipRepository memberships;

  public TenantFilter(
      ClinicRepository clinics, UserRepository users, MembershipRepository memberships) {
    this.clinics = clinics;
    this.users = users;
    this.memberships = memberships;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return !path.startsWith(API)
        || SHARED_PATHS.contains(path)
        || SHARED_PREFIXES.stream().anyMatch(path::startsWith);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String slug = requestedSlug(request);
    if (slug == null) {
      problem(response, 400, MISSING);
      return;
    }
    Optional<Clinic> clinic = clinics.findBySlug(slug);
    if (clinic.isEmpty() || !allowed(clinic.get())) {
      problem(response, 404, UNKNOWN);
      return;
    }
    TenantContext.set(clinic.get().id);
    try {
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  private static String requestedSlug(HttpServletRequest request) {
    String header = request.getHeader(HEADER);
    String parameter = request.getParameter(PARAM);
    if (header != null && parameter != null && !header.equals(parameter)) return null;
    String slug = header != null ? header : parameter;
    if (slug == null || slug.length() > SLUG_MAX || !SLUG.matcher(slug).matches()) return null;
    return slug;
  }

  private boolean allowed(Clinic clinic) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return true;
    }
    return users
        .findByEmail(authentication.getName())
        .map(user -> memberships.existsByUserIdAndClinicId(user.id, clinic.id))
        .orElse(false);
  }

  private static void problem(HttpServletResponse response, int status, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json;charset=UTF-8");
    response
        .getWriter()
        .write("{\"title\":\"" + message + "\",\"status\":" + status + ",\"detail\":\"" + message + "\"}");
  }
}
