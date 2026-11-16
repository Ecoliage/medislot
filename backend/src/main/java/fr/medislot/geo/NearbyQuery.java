package fr.medislot.geo;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record NearbyQuery(
    double lat, double lon, double radiusKm, String specialty, int page, int size) {

  public static final double DEFAULT_RADIUS_KM = 5.0;
  public static final double MIN_RADIUS_KM = 0.1;
  public static final double MAX_RADIUS_KM = 50.0;
  public static final int MAX_SIZE = 50;
  public static final int MAX_SPECIALTY_LENGTH = 60;

  static final String TOGETHER = "Les paramètres lat et lon vont ensemble.";
  static final String BAD_LAT = "Paramètre invalide : lat (entre -90 et 90 degrés).";
  static final String BAD_LON = "Paramètre invalide : lon (entre -180 et 180 degrés).";
  static final String BAD_RADIUS = "Paramètre invalide : radiusKm (entre 0,1 et 50 km).";
  static final String BAD_PAGE = "Paramètre invalide : page ou size.";
  static final String BAD_SEARCH = "Paramètres de recherche invalides.";

  public static NearbyQuery of(
      Double lat, Double lon, Double radiusKm, String specialty, int page, int size) {
    if (lat == null || lon == null) throw refusal(TOGETHER);
    if (outOfBounds(lat, 90)) throw refusal(BAD_LAT);
    if (outOfBounds(lon, 180)) throw refusal(BAD_LON);

    double radius = radiusKm != null ? radiusKm : DEFAULT_RADIUS_KM;
    if (!Double.isFinite(radius) || radius < MIN_RADIUS_KM || radius > MAX_RADIUS_KM)
      throw refusal(BAD_RADIUS);
    if (page < 0 || size < 1 || size > MAX_SIZE) throw refusal(BAD_PAGE);

    String normalizedSpecialty = specialty == null ? "" : specialty;
    if (normalizedSpecialty.length() > MAX_SPECIALTY_LENGTH) throw refusal(BAD_SEARCH);
    return new NearbyQuery(lat, lon, radius, normalizedSpecialty, page, size);
  }

  private static boolean outOfBounds(double value, double max) {
    return !Double.isFinite(value) || Math.abs(value) > max;
  }

  private static ResponseStatusException refusal(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
