package fr.medislot;

import static org.junit.jupiter.api.Assertions.*;

import fr.medislot.application.HistoryLabels;
import org.junit.jupiter.api.Test;

class HistoryLabelsTest {
  @Test
  void importedAppointmentsHaveALabel() {
    assertEquals("Rendez-vous importé", HistoryLabels.of("IMPORTED"));
  }
}
