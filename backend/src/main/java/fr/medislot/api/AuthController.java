package fr.medislot.api;

import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;
import fr.medislot.tenant.ClinicMemberships;
import fr.medislot.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AuthController {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(AuthController.class);
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final ClinicMemberships memberships;

  public AuthController(
      UserRepository users, PasswordEncoder encoder, ClinicMemberships memberships) {
    this.users = users;
    this.encoder = encoder;
    this.memberships = memberships;
  }

  public record Identity(Long id, String name, String email, String role) {}

  public record Registration(
      @Email @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotBlank
          @Size(max = 120)
          @Pattern(regexp = "\\P{Cc}*", message = "Le nom contient des caractères non autorisés.")
          String name) {}

  @GetMapping("/csrf")
  public java.util.Map<String, String> csrf(CsrfToken token) {
    return java.util.Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/me")
  public Identity me(java.security.Principal principal) {
    AppUser u = users.findByEmail(principal.getName()).orElseThrow();
    return new Identity(u.id, u.name, u.email, u.role);
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Identity register(@Valid @RequestBody Registration input) {
    Long clinicId = TenantContext.require();
    if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Le mot de passe doit contenir au maximum 72 octets UTF-8.");
    String email = input.email().strip().toLowerCase(java.util.Locale.ROOT);
    if (users.existsByEmail(email))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse est déjà utilisée.");
    AppUser u = new AppUser();
    u.email = email;
    u.name = input.name().strip();
    u.password = encoder.encode(input.password());
    u.role = "PATIENT";
    users.saveAndFlush(u);
    memberships.join(u.id, clinicId);
    log.info("Nouvel inscrit : compte {}", u.id);
    return new Identity(u.id, u.name, u.email, u.role);
  }
}
