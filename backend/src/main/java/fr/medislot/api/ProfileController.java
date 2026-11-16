package fr.medislot.api;

import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/patients")
public class ProfileController {

  private static final Path DOSSIER_AVATARS =
      Path.of(System.getProperty("java.io.tmpdir"), "medislot-avatars");

  public record Profil(Long id, String name, String email, String role) {}

  public record Modification(
      @NotBlank @Size(max = 120) String name, @Email @NotBlank @Size(max = 254) String email) {}

  private final UserRepository users;

  public ProfileController(UserRepository users) {
    this.users = users;
  }

  @GetMapping("/{id}")
  public Profil profil(@PathVariable Long id, Principal principal) {
    AppUser u = compteDe(id, principal);
    return new Profil(u.id, u.name, u.email, u.role);
  }

  @PutMapping("/{id}")
  public Profil modifier(
      @PathVariable Long id, @Valid @RequestBody Modification maj, Principal principal) {
    AppUser u = compteDe(id, principal);
    u.name = maj.name().strip();
    u.email = maj.email().strip().toLowerCase(java.util.Locale.ROOT);
    users.save(u);
    return new Profil(u.id, u.name, u.email, u.role);
  }

  @PostMapping("/{id}/avatar")
  public Map<String, Object> envoyerAvatar(
      @PathVariable Long id, @RequestParam("fichier") MultipartFile fichier, Principal principal)
      throws IOException {
    compteDe(id, principal);
    Files.createDirectories(DOSSIER_AVATARS);
    String nom = id + "-avatar";
    Path cible = DOSSIER_AVATARS.resolve(nom);
    try (var entree = fichier.getInputStream()) {
      Files.copy(entree, cible, StandardCopyOption.REPLACE_EXISTING);
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("taille", fichier.getSize());
    m.put("url", "/api/patients/" + id + "/avatar?fichier=" + nom);
    return m;
  }

  @GetMapping("/{id}/avatar")
  public ResponseEntity<byte[]> lireAvatar(
      @PathVariable Long id, @RequestParam String fichier, Principal principal) throws IOException {
    compteDe(id, principal);
    Path chemin = DOSSIER_AVATARS.resolve(fichier).normalize();
    if (!chemin.startsWith(DOSSIER_AVATARS)
        || !chemin.getFileName().toString().equals(id + "-avatar")
        || !Files.exists(chemin)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar introuvable.");
    }
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(Files.readAllBytes(chemin));
  }

  private AppUser compteDe(Long id, Principal principal) {
    AppUser u =
        users
            .findByEmail(principal.getName())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Connexion requise."));
    if (!u.id.equals(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profil introuvable.");
    }
    return u;
  }
}
