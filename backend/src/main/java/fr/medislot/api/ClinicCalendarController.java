package fr.medislot.api;

import fr.medislot.domain.Clinic;
import fr.medislot.repository.ClinicRepository;
import fr.medislot.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Minimised view of a practitioner's calendar for platform administrators: dates, times and
 * statuses, with patients replaced by a pseudonym. Reads run inside the clinic, under its RLS.
 */
@RestController
@RequestMapping("/api/clinics/{slug}/professionals")
public class ClinicCalendarController {
  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

  private final ClinicRepository clinics;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate readOnly;
  private final byte[] pseudonymKey;

  public ClinicCalendarController(
      ClinicRepository clinics,
      JdbcTemplate jdbc,
      PlatformTransactionManager transactions,
      @Value("${medislot.pseudonym-key:}") String pseudonymKey) {
    this.clinics = clinics;
    this.jdbc = jdbc;
    this.readOnly = new TransactionTemplate(transactions);
    this.readOnly.setReadOnly(true);
    this.pseudonymKey = key(pseudonymKey);
  }

  public record PractitionerDto(Long id, String name, String specialty) {}

  public record CalendarEntry(Long id, Instant startsAt, String patient, String status) {}

  @GetMapping
  public List<PractitionerDto> practitioners(@PathVariable String slug) {
    return inClinic(
        slug,
        clinic ->
            jdbc.query(
                "select id, name, specialty from professional order by name",
                (rs, i) ->
                    new PractitionerDto(
                        rs.getLong("id"), rs.getString("name"), rs.getString("specialty"))));
  }

  /** Appointments of the week (Monday to Sunday, Paris time) that contains {@code week}. */
  @GetMapping("/{professionalId}/appointments")
  public List<CalendarEntry> appointments(
      @PathVariable String slug,
      @PathVariable Long professionalId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
    LocalDate monday = week.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    Instant from = monday.atStartOfDay(PARIS).toInstant();
    Instant until = monday.plusWeeks(1).atStartOfDay(PARIS).toInstant();
    return inClinic(
        slug,
        clinic -> {
          List<Long> found =
              jdbc.queryForList(
                  "select id from professional where id = ?", Long.class, professionalId);
          if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Praticien introuvable.");
          }
          return jdbc.query(
              """
              select a.id, s.starts_at, a.patient_id, a.status
              from appointment a join slot s on s.id = a.slot_id
              where s.professional_id = ? and s.starts_at >= ? and s.starts_at < ?
              order by s.starts_at, a.id""",
              (rs, i) ->
                  new CalendarEntry(
                      rs.getLong("id"),
                      rs.getTimestamp("starts_at").toInstant(),
                      pseudonym(clinic.id, rs.getLong("patient_id")),
                      rs.getString("status")),
              professionalId,
              Timestamp.from(from),
              Timestamp.from(until));
        });
  }

  private <T> T inClinic(String slug, java.util.function.Function<Clinic, T> work) {
    Clinic clinic =
        clinics
            .findBySlug(slug)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cabinet introuvable."));
    return TenantContext.callIn(clinic.id, () -> readOnly.execute(status -> work.apply(clinic)));
  }

  /** Stable per clinic and patient, not reversible without the key, different across clinics. */
  String pseudonym(Long clinicId, Long patientId) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(pseudonymKey, "HmacSHA256"));
      byte[] digest =
          mac.doFinal((clinicId + ":" + patientId).getBytes(StandardCharsets.UTF_8));
      return "P-" + HexFormat.of().formatHex(digest, 0, 2).toUpperCase(Locale.ROOT);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Without a configured key, a random one: pseudonyms then change at each restart. */
  private static byte[] key(String configured) {
    if (!configured.isBlank()) return configured.getBytes(StandardCharsets.UTF_8);
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    return random;
  }
}
