package fr.medislot.api;

import fr.medislot.application.PlatformJournal;
import fr.medislot.repository.PlatformEventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformEventController {
  private final PlatformEventRepository events;

  public PlatformEventController(PlatformEventRepository events) {
    this.events = events;
  }

  public record PlatformEventDto(
      Long id,
      String action,
      String label,
      String actorEmail,
      String clinicSlug,
      String clinicName,
      Instant createdAt) {}

  @GetMapping("/events")
  public List<PlatformEventDto> events() {
    return events.findTop100ByOrderByCreatedAtDescIdDesc().stream()
        .map(
            event ->
                new PlatformEventDto(
                    event.id,
                    event.action,
                    PlatformJournal.label(event.action),
                    event.actorEmail,
                    event.clinicSlug,
                    event.clinicName,
                    event.createdAt))
        .toList();
  }
}
