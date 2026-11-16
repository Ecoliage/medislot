package fr.medislot.document;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ConvocationFormats {

  public static final ZoneId PARIS = ZoneId.of("Europe/Paris");
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH).withZone(PARIS);
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("H 'h' mm", Locale.FRENCH).withZone(PARIS);

  private ConvocationFormats() {}

  public static String day(Instant instant) {
    return DAY.format(instant);
  }

  public static String time(Instant instant) {
    return TIME.format(instant);
  }
}
