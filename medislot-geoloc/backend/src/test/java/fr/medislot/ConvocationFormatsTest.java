package fr.medislot;

import static org.junit.jupiter.api.Assertions.*;

import fr.medislot.document.ConvocationFormats;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConvocationFormatsTest {
  @Test
  void timeInWinterIsParisTime() {
    assertEquals("9 h 30", ConvocationFormats.time(Instant.parse("2026-01-12T08:30:00Z")));
  }

  @Test
  void timeInSummerIsParisTime() {
    assertEquals("9 h 30", ConvocationFormats.time(Instant.parse("2026-07-06T07:30:00Z")));
  }

  @Test
  void dayIsInFrenchWithAccents() {
    assertEquals(
        "lundi 2 février 2026", ConvocationFormats.day(Instant.parse("2026-02-02T09:00:00Z")));
  }
}
