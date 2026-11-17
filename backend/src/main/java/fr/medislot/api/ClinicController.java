package fr.medislot.api;

import fr.medislot.domain.Clinic;
import fr.medislot.repository.ClinicRepository;
import fr.medislot.repository.UserRepository;
import fr.medislot.tenant.TenantContext;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ClinicController {
  private final ClinicRepository clinics;
  private final UserRepository users;

  public ClinicController(ClinicRepository clinics, UserRepository users) {
    this.clinics = clinics;
    this.users = users;
  }

  public record ClinicDto(String slug, String name, String city, String color) {
    static ClinicDto from(Clinic clinic) {
      return new ClinicDto(clinic.slug, clinic.name, clinic.city, clinic.color);
    }
  }

  @GetMapping("/clinic")
  public ClinicDto current() {
    return clinics
        .findById(TenantContext.require())
        .map(ClinicDto::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cabinet introuvable."));
  }

  @GetMapping("/me/clinics")
  public List<ClinicDto> mine(Principal principal) {
    Long userId = users.findByEmail(principal.getName()).orElseThrow().id;
    return clinics.findByMember(userId).stream().map(ClinicDto::from).toList();
  }
}
