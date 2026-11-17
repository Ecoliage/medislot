package fr.medislot.api;

import fr.medislot.application.HistoryLabels;
import fr.medislot.repository.AuditRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clinic")
public class ClinicLogController {
  private static final int LIMIT = 100;

  private final AuditRepository audit;

  public ClinicLogController(AuditRepository audit) {
    this.audit = audit;
  }

  public record LogEntry(
      Long id,
      String action,
      String label,
      Long appointmentId,
      String practitioner,
      Instant createdAt) {}

  @GetMapping("/logs")
  public List<LogEntry> logs() {
    return audit.findRecent(PageRequest.of(0, LIMIT)).stream()
        .map(
            line ->
                new LogEntry(
                    line.id(),
                    line.action(),
                    HistoryLabels.of(line.action()),
                    line.appointmentId(),
                    line.practitioner(),
                    line.createdAt()))
        .toList();
  }
}
