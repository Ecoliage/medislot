package fr.medislot.geo;

import fr.medislot.domain.Professional;
import fr.medislot.repository.ProfessionalRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!postgis")
public class BoxNearbyFinder implements NearbyFinder {

  private final ProfessionalRepository professionals;

  public BoxNearbyFinder(ProfessionalRepository professionals) {
    this.professionals = professionals;
  }

  @Override
  public List<Nearby> findWithin(NearbyQuery query) {
    BoundingBox box = BoundingBox.around(query.lat(), query.lon(), query.radiusKm());
    List<Professional> candidates =
        professionals.findInBox(
            box.minLat(),
            box.maxLat(),
            box.minLon(),
            box.maxLon(),
            box.crossesAntimeridian(),
            query.specialty());
    List<Nearby> found = new ArrayList<>();
    for (Professional p : candidates) {
      double km = GeoDistance.haversineKm(query.lat(), query.lon(), p.latitude, p.longitude);
      if (km <= query.radiusKm()) {
        found.add(new Nearby(p, km));
      }
    }
    return found;
  }
}
