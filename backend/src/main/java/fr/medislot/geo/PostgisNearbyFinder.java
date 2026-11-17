package fr.medislot.geo;

import fr.medislot.domain.Professional;
import fr.medislot.repository.ProfessionalRepository;
import fr.medislot.tenant.TenantContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("postgis")
public class PostgisNearbyFinder implements NearbyFinder {

  // ST_MakePoint attend (lon, lat) : l'ordre inverse de celui qu'on dit à l'oral.
  static final String SQL =
      """
            SELECT id, ST_Distance(location, origin.point) / 1000.0 AS distance_km
            FROM professional,
                 (SELECT ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography AS point) AS origin
            WHERE ST_DWithin(location, origin.point, :radiusMeters)
              AND clinic_id = :clinic
              AND (:specialty = '' OR specialty = :specialty)
            """;

  private final NamedParameterJdbcTemplate jdbc;
  private final ProfessionalRepository professionals;

  public PostgisNearbyFinder(
      NamedParameterJdbcTemplate jdbc, ProfessionalRepository professionals) {
    this.jdbc = jdbc;
    this.professionals = professionals;
  }

  @Override
  public List<Nearby> findWithin(NearbyQuery query) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("lat", query.lat());
    params.addValue("lon", query.lon());
    params.addValue("radiusMeters", query.radiusKm() * 1000);
    params.addValue("specialty", query.specialty());
    params.addValue("clinic", TenantContext.require());

    Map<Long, Double> distances = new HashMap<>();
    jdbc.query(
        SQL,
        params,
        row -> {
          distances.put(row.getLong("id"), row.getDouble("distance_km"));
        });

    List<Nearby> found = new ArrayList<>();
    for (Professional p : professionals.findAllById(distances.keySet())) {
      found.add(new Nearby(p, distances.get(p.id)));
    }
    return found;
  }
}
