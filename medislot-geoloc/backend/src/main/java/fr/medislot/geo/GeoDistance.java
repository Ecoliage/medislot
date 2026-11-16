package fr.medislot.geo;

public final class GeoDistance {

  public static final double EARTH_RADIUS_KM = 6371.0088;

  private GeoDistance() {}

  public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double sinLat = Math.sin(dLat / 2);
    double sinLon = Math.sin(dLon / 2);
    double h =
        sinLat * sinLat
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinLon * sinLon;
    // Les arrondis flottants peuvent donner h légèrement > 1 pour deux points antipodaux.
    if (h > 1) h = 1;
    return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
  }

  public static double roundKm(double km) {
    return Math.round(km * 10) / 10.0;
  }
}
