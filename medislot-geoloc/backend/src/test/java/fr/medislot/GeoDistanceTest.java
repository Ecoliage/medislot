package fr.medislot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.medislot.geo.BoundingBox;
import fr.medislot.geo.GeoDistance;
import org.junit.jupiter.api.Test;

class GeoDistanceTest {

  private static final double FOURVIERE_LAT = 45.7623;
  private static final double FOURVIERE_LON = 4.8227;

  @Test
  void parisToLyonIsAboutFourHundredKilometres() {
    double km = GeoDistance.haversineKm(48.8566, 2.3522, 45.7640, 4.8357);

    assertEquals(391.5, km, 0.5);
  }

  @Test
  void bellecourToFourviereIsAboutNineHundredMetres() {
    double km =
        GeoDistance.haversineKm(
            GeoFixtures.BELLECOUR_LAT, GeoFixtures.BELLECOUR_LON, FOURVIERE_LAT, FOURVIERE_LON);

    assertEquals(0.872, km, 0.01);
  }

  @Test
  void samePointIsAtZeroDistance() {
    assertEquals(0.0, GeoDistance.haversineKm(45.7576, 4.8317, 45.7576, 4.8317), 1e-12);
  }

  @Test
  void swappedLatAndLonGiveAWrongDistance() {
    double correct =
        GeoDistance.haversineKm(
            GeoFixtures.BELLECOUR_LAT, GeoFixtures.BELLECOUR_LON, FOURVIERE_LAT, FOURVIERE_LON);
    double swapped =
        GeoDistance.haversineKm(
            GeoFixtures.BELLECOUR_LON, GeoFixtures.BELLECOUR_LAT, FOURVIERE_LAT, FOURVIERE_LON);

    assertTrue(correct < 1.0);
    assertTrue(swapped > 1000.0, "lat et lon inversés : " + swapped + " km");
  }

  @Test
  void roundKmRoundsToTheNearestTenthOfAKilometre() {
    assertEquals(0.3, GeoDistance.roundKm(0.27785), 1e-12);
    assertEquals(1.3, GeoDistance.roundKm(1.2932), 1e-12);
    assertEquals(1.0, GeoDistance.roundKm(0.96), 1e-12);
    assertEquals(5.1, GeoDistance.roundKm(5.05), 1e-12);
    assertEquals(0.0, GeoDistance.roundKm(0.04), 1e-12);
  }

  @Test
  void boundingBoxEdgesAreFiveKilometresFromTheCentre() {
    double lat = GeoFixtures.BELLECOUR_LAT;
    double lon = GeoFixtures.BELLECOUR_LON;
    BoundingBox box = BoundingBox.around(lat, lon, 5.0);

    assertFalse(box.crossesAntimeridian());
    assertEquals(5.0, GeoDistance.haversineKm(lat, lon, box.maxLat(), lon), 0.01);
    assertEquals(5.0, GeoDistance.haversineKm(lat, lon, box.minLat(), lon), 0.01);
    assertEquals(5.0, GeoDistance.haversineKm(lat, lon, lat, box.maxLon()), 0.1);
    assertEquals(5.0, GeoDistance.haversineKm(lat, lon, lat, box.minLon()), 0.1);
  }

  @Test
  void boundingBoxCoversPoleAndAntimeridian() {
    BoundingBox pole = BoundingBox.around(89.99, 10.0, 5.0);
    assertEquals(90.0, pole.maxLat(), 0.0);
    assertEquals(-180.0, pole.minLon(), 0.0);
    assertEquals(180.0, pole.maxLon(), 0.0);
    assertFalse(pole.crossesAntimeridian());

    BoundingBox eastOfAntimeridian = BoundingBox.around(0.0, 179.99, 5.0);
    assertTrue(eastOfAntimeridian.crossesAntimeridian());
    assertTrue(eastOfAntimeridian.minLon() > 179.9 && eastOfAntimeridian.minLon() < 180.0);
    assertTrue(eastOfAntimeridian.maxLon() < -179.9 && eastOfAntimeridian.maxLon() > -180.0);

    BoundingBox westOfAntimeridian = BoundingBox.around(0.0, -179.99, 5.0);
    assertTrue(westOfAntimeridian.crossesAntimeridian());

    assertFalse(BoundingBox.around(0.0, 0.0, 5.0).crossesAntimeridian());
  }
}
