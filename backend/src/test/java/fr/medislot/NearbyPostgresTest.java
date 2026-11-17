package fr.medislot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.medislot.geo.BoxNearbyFinder;
import fr.medislot.geo.Nearby;
import fr.medislot.geo.NearbyFinder;
import fr.medislot.geo.NearbyPage;
import fr.medislot.geo.NearbyQuery;
import fr.medislot.geo.NearbyService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NearbyPostgresTest extends PostgresTestBase {

  @Autowired NearbyService service;

  @Autowired NearbyFinder finder;

  @Test
  void bellecourWithinFiveKilometresFindsSixPractitionersByDistance() {
    assertTrue(finder instanceof BoxNearbyFinder);
    enter(CLINIC_B);

    NearbyPage page =
        service.search(
            NearbyQuery.of(GeoFixtures.BELLECOUR_LAT, GeoFixtures.BELLECOUR_LON, 5.0, "", 0, 50));

    assertEquals(GeoFixtures.LYON_WITHIN_5_KM_BY_DISTANCE, names(page.items()));
    assertEquals(6, page.total());
  }

  @Test
  void middleOfTheOceanFindsNobody() {
    enter(CLINIC_B);
    NearbyPage page = service.search(NearbyQuery.of(0.0, 0.0, 5.0, "", 0, 6));

    assertTrue(page.items().isEmpty());
    assertEquals(0, page.total());
  }

  @Test
  void parisFindsItsPractitioners() {
    enter(CLINIC_A);
    NearbyPage page =
        service.search(
            NearbyQuery.of(GeoFixtures.PARIS_LAT, GeoFixtures.PARIS_LON, 5.0, "", 0, 50));

    assertEquals(GeoFixtures.PARIS_BY_DISTANCE, names(page.items()));
  }

  private static List<String> names(List<Nearby> nearby) {
    return nearby.stream().map(item -> item.professional().name).toList();
  }
}
