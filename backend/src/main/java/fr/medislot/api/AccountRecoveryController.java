package fr.medislot.api;

import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/compte")
public class AccountRecoveryController {

  private static final Logger LOG = LoggerFactory.getLogger(AccountRecoveryController.class);
  private record Jeton(String email, Instant expireLe) {}

  private static final Map<String, Jeton> JETONS = new ConcurrentHashMap<>();
  private static final Duration VALIDITE_JETON = Duration.ofMinutes(15);
  private static final String MESSAGE_NEUTRE =
      "Si un compte existe avec cette adresse, un e-mail de réinitialisation a été envoyé.";

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final SecureRandom hasard = new SecureRandom();

  public AccountRecoveryController(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  @PostMapping("/oubli")
  public Map<String, Object> motDePasseOublie(@RequestBody Map<String, String> corps) {
    String email = corps.getOrDefault("email", "").strip().toLowerCase(java.util.Locale.ROOT);
    Optional<AppUser> compte = users.findByEmail(email);
    purgerJetonsExpires();
    if (compte.isPresent()) {
      byte[] octets = new byte[32];
      hasard.nextBytes(octets);
      String jeton = HexFormat.of().formatHex(octets);
      JETONS.values().removeIf(j -> j.email().equals(email));
      JETONS.put(jeton, new Jeton(email, Instant.now().plus(VALIDITE_JETON)));
      LOG.info("Jeton de réinitialisation émis pour le compte {}", compte.get().id);
    }
    Map<String, Object> reponse = new LinkedHashMap<>();
    reponse.put("message", MESSAGE_NEUTRE);
    return reponse;
  }

  @PostMapping("/reinitialiser")
  public Map<String, Object> reinitialiser(@RequestBody Map<String, String> corps) {
    String jeton = corps.getOrDefault("jeton", "");
    String nouveau = corps.getOrDefault("nouveauMotDePasse", "");
    if (nouveau.length() < 12
        || nouveau.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Le mot de passe doit contenir entre 12 caractères et 72 octets.");
    }
    purgerJetonsExpires();
    Jeton valide = JETONS.remove(jeton);
    AppUser u = valide == null ? null : users.findByEmail(valide.email()).orElse(null);
    if (u == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jeton inconnu ou expiré.");
    }
    u.password = encoder.encode(nouveau);
    users.save(u);
    LOG.info("Mot de passe réinitialisé pour le compte {}", u.id);
    Map<String, Object> reponse = new LinkedHashMap<>();
    reponse.put("ok", true);
    reponse.put("message", "Mot de passe modifié. Vous pouvez vous connecter.");
    return reponse;
  }

  private static void purgerJetonsExpires() {
    Instant maintenant = Instant.now();
    JETONS.values().removeIf(j -> j.expireLe().isBefore(maintenant));
  }
}
