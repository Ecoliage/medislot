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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class NearbyPostgresTest {

  @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:18");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", db::getJdbcUrl);
    registry.add("spring.datasource.username", db::getUsername);
    registry.add("spring.datasource.password", db::getPassword);
    registry.add("medislot.demo-seed", () -> "true");
  }

  @Autowired NearbyService service;

  @Autowired NearbyFinder finder;

  @Test
  void bellecourWithinFiveKilometresFindsSixPractitionersByDistance() {
    assertTrue(finder instanceof BoxNearbyFinder);

    NearbyPage page =
        service.search(
            NearbyQuery.of(GeoFixtures.BELLECOUR_LAT, GeoFixtures.BELLECOUR_LON, 5.0, "", 0, 50));

    assertEquals(GeoFixtures.LYON_WITHIN_5_KM_BY_DISTANCE, names(page.items()));
    assertEquals(6, page.total());
  }

  @Test
  void middleOfTheOceanFindsNobody() {
    NearbyPage page = service.search(NearbyQuery.of(0.0, 0.0, 5.0, "", 0, 6));

    assertTrue(page.items().isEmpty());
    assertEquals(0, page.total());
  }

  @Test
  void parisFindsItsPractitioners() {
    NearbyPage page =
        service.search(
            NearbyQuery.of(GeoFixtures.PARIS_LAT, GeoFixtures.PARIS_LON, 5.0, "", 0, 50));

    assertEquals(GeoFixtures.PARIS_BY_DISTANCE, names(page.items()));
  }

  private static List<String> names(List<Nearby> nearby) {
    return nearby.stream().map(item -> item.professional().name).toList();
  }
}
