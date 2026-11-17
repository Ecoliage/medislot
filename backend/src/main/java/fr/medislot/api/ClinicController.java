package fr.medislot.api;

import fr.medislot.application.PlatformJournal;
import fr.medislot.clinic.ClinicLogos;
import fr.medislot.domain.AppUser;
import fr.medislot.domain.Clinic;
import fr.medislot.domain.PlatformEvent;
import fr.medislot.repository.ClinicRepository;
import fr.medislot.repository.UserRepository;
import fr.medislot.tenant.ClinicMemberships;
import fr.medislot.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.security.Principal;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ClinicController {
  private static final String NOT_FOUND = "Cabinet introuvable.";

  private final ClinicRepository clinics;
  private final UserRepository users;
  private final ClinicMemberships memberships;
  private final ClinicLogos logos;
  private final PlatformJournal journal;

  public ClinicController(
      ClinicRepository clinics,
      UserRepository users,
      ClinicMemberships memberships,
      ClinicLogos logos,
      PlatformJournal journal) {
    this.clinics = clinics;
    this.users = users;
    this.memberships = memberships;
    this.logos = logos;
    this.journal = journal;
  }

  public record ClinicDto(String slug, String name, String city, String color) {
    static ClinicDto from(Clinic clinic) {
      return new ClinicDto(clinic.slug, clinic.name, clinic.city, clinic.color);
    }
  }

  /** What administrators read and edit; the public ClinicDto stays limited to the theme. */
  public record ClinicDetails(
      String slug, String name, String city, String address, String color, boolean hasLogo) {
    static ClinicDetails from(Clinic clinic) {
      return new ClinicDetails(
          clinic.slug, clinic.name, clinic.city, clinic.address, clinic.color, clinic.logoFile != null);
    }
  }

  public record NewClinic(
      @NotBlank
          @Size(max = 40)
          @Pattern(
              regexp = "[a-z0-9]+(-[a-z0-9]+)*",
              message = "minuscules, chiffres et tirets uniquement")
          String slug,
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 80) String city,
      @Size(max = 160) String address,
      @NotBlank @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "couleur #rrggbb attendue")
          String color,
      @NotBlank @Email @Size(max = 254) String adminEmail) {}

  /** The slug is the clinic's address (/c/slug/) and is not editable. */
  public record ClinicChanges(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 80) String city,
      @Size(max = 160) String address,
      @NotBlank @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "couleur #rrggbb attendue")
          String color) {}

  @GetMapping("/clinic")
  public ClinicDto current() {
    return clinics
        .findById(TenantContext.require())
        .map(ClinicDto::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
  }

  @GetMapping("/clinics/{slug}")
  public ClinicDetails details(@PathVariable String slug) {
    return ClinicDetails.from(find(slug));
  }

  /**
   * Creates an empty clinic. Its first administrator (an existing account) and the platform
   * administrator who creates it both join it, so that "Ouvrir" works right away.
   */
  @PostMapping("/clinics")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public ClinicDetails create(@Valid @RequestBody NewClinic body, Principal principal) {
    if (clinics.findBySlug(body.slug()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce slug est déjà utilisé.");
    }
    AppUser firstAdmin =
        users
            .findByEmail(body.adminEmail().strip().toLowerCase(Locale.ROOT))
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Aucun compte n'existe avec cet e-mail."));
    Clinic clinic = new Clinic();
    clinic.slug = body.slug();
    apply(clinic, body.name(), body.city(), body.address(), body.color());
    clinics.saveAndFlush(clinic);
    memberships.join(firstAdmin.id, clinic.id);
    users
        .findByEmail(principal.getName())
        .ifPresent(admin -> memberships.join(admin.id, clinic.id));
    journal.record(PlatformEvent.CLINIC_CREATED, clinic, principal.getName());
    return ClinicDetails.from(clinic);
  }

  @PutMapping("/clinics/{slug}")
  @Transactional
  public ClinicDetails update(
      @PathVariable String slug, @Valid @RequestBody ClinicChanges body, Principal principal) {
    Clinic clinic = find(slug);
    apply(clinic, body.name(), body.city(), body.address(), body.color());
    clinics.saveAndFlush(clinic);
    journal.record(PlatformEvent.CLINIC_UPDATED, clinic, principal.getName());
    return ClinicDetails.from(clinic);
  }

  /** PNG or SVG, 200 KB max, sent as multipart field "file". Replaces the previous logo. */
  @PutMapping(value = "/clinics/{slug}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional
  public ClinicDetails uploadLogo(
      @PathVariable String slug, @RequestParam("file") MultipartFile file, Principal principal)
      throws IOException {
    Clinic clinic = find(slug);
    if (file.getSize() > ClinicLogos.MAX_BYTES) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le logo dépasse 200 Ko.");
    }
    clinic.logoFile = logos.store(clinic.id, file.getBytes(), clinic.logoFile);
    clinics.saveAndFlush(clinic);
    journal.record(PlatformEvent.CLINIC_LOGO_CHANGED, clinic, principal.getName());
    return ClinicDetails.from(clinic);
  }

  /** An SVG may carry scripts: it is served under a CSP that runs none, and never sniffed. */
  @GetMapping("/clinics/{slug}/logo")
  public ResponseEntity<byte[]> logo(@PathVariable String slug) {
    ClinicLogos.Logo logo =
        logos
            .read(find(slug).logoFile)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun logo."));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(logo.kind().mediaType))
        .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
        .body(logo.bytes());
  }

  private Clinic find(String slug) {
    return clinics
        .findBySlug(slug)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
  }

  private static void apply(Clinic clinic, String name, String city, String address, String color) {
    clinic.name = name.trim();
    clinic.city = city.trim();
    clinic.address = address == null || address.isBlank() ? null : address.trim();
    clinic.color = color.toLowerCase(Locale.ROOT);
  }
}
