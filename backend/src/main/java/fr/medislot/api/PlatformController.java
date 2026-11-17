package fr.medislot.api;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform view: an administrator sees every clinic with aggregated counts, never its rows. */
@RestController
@RequestMapping("/api/platform")
public class PlatformController {
  private static final String SELECT =
      "select slug, name, city, color, practitioners, appointments_30d, created_at"
          + " from platform_clinic";
  private static final RowMapper<PlatformClinic> ROW =
      (rs, i) ->
          new PlatformClinic(
              rs.getString("slug"),
              rs.getString("name"),
              rs.getString("city"),
              rs.getString("color"),
              rs.getLong("practitioners"),
              rs.getLong("appointments_30d"),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbc;

  public PlatformController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record PlatformClinic(
      String slug,
      String name,
      String city,
      String color,
      long practitioners,
      long appointments30d,
      Instant createdAt) {}

  @GetMapping("/clinics")
  public List<PlatformClinic> clinics() {
    return jdbc.query(SELECT + " order by name", ROW);
  }
}
