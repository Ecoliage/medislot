package fr.medislot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.medislot.geo.GeoDistance;
import fr.medislot.geo.Nearby;
import fr.medislot.geo.NearbyFinder;
import fr.medislot.geo.NearbyPage;
import fr.medislot.geo.NearbyQuery;
import fr.medislot.geo.NearbyService;
import fr.medislot.geo.PostgisNearbyFinder;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@ActiveProfiles("postgis")
class PostgisNearbyTest {

  private static final DockerImageName POSTGIS_IMAGE =
      DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres");

  @Container
  static PostgreSQLContainer db =
      new PostgreSQLContainer(POSTGIS_IMAGE)
          // L'image postgis n'existe pas en arm64 : on force l'émulation amd64 (Mac M1+).
          .withCreateContainerCmdModifier(command -> command.withPlatform("linux/amd64"))
          .withStartupTimeout(Duration.ofMinutes(5));

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
  void bellecourWithinFiveKilometresMatchesTheBoxFinder() {
    assertTrue(finder instanceof PostgisNearbyFinder);

    NearbyPage page =
        service.search(
            NearbyQuery.of(GeoFixtures.BELLECOUR_LAT, GeoFixtures.BELLECOUR_LON, 5.0, "", 0, 50));

    assertEquals(GeoFixtures.LYON_WITHIN_5_KM_BY_DISTANCE, names(page.items()));
    assertEquals(6, page.total());
  }

  @Test
  void distancesStayCloseToHaversine() {
    NearbyPage page =
        service.search(
            NearbyQuery.of(GeoFixtures.BELLECOUR_LAT, GeoFixtures.BELLECOUR_LON, 5.0, "", 0, 50));

    for (Nearby nearby : page.items()) {
      double haversine =
          GeoDistance.haversineKm(
              GeoFixtures.BELLECOUR_LAT, GeoFixtures.BELLECOUR_LON,
              nearby.professional().latitude, nearby.professional().longitude);
      assertEquals(haversine, nearby.distanceKm(), haversine * 0.005);
    }
  }

  @Test
  void oceanIsEmptyAndParisFindsItsPractitioners() {
    assertTrue(service.search(NearbyQuery.of(0.0, 0.0, 5.0, "", 0, 6)).items().isEmpty());

    NearbyPage paris =
        service.search(
            NearbyQuery.of(GeoFixtures.PARIS_LAT, GeoFixtures.PARIS_LON, 5.0, "", 0, 50));

    assertEquals(GeoFixtures.PARIS_BY_DISTANCE, names(paris.items()));
  }

  private static List<String> names(List<Nearby> nearby) {
    return nearby.stream().map(item -> item.professional().name).toList();
  }
}
