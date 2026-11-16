package fr.medislot.geo;

public record BoundingBox(double minLat, double maxLat, double minLon, double maxLon) {

  public static BoundingBox around(double lat, double lon, double radiusKm) {
    double angularRadius = radiusKm / GeoDistance.EARTH_RADIUS_KM;
    double latSpan = Math.toDegrees(angularRadius);
    double south = Math.max(lat - latSpan, -90);
    double north = Math.min(lat + latSpan, 90);

    double cosLat = Math.cos(Math.toRadians(lat));
    // Si le cercle englobe un pôle, toutes les longitudes sont possibles.
    if (Math.abs(lat) + latSpan >= 90 || Math.sin(angularRadius) >= cosLat) {
      return new BoundingBox(south, north, -180, 180);
    }

    double lonSpan = Math.toDegrees(Math.asin(Math.sin(angularRadius) / cosLat));
    double west = lon - lonSpan;
    double east = lon + lonSpan;
    if (west < -180) west += 360;
    if (east > 180) east -= 360;
    return new BoundingBox(south, north, west, east);
  }

  public boolean crossesAntimeridian() {
    return minLon > maxLon;
  }
}
