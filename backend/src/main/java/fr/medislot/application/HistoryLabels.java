package fr.medislot.application;

public final class HistoryLabels {
  private HistoryLabels() {}

  public static String of(String action) {
    return switch (action) {
      case "CREATED" -> "Réservation confirmée";
      case "RESCHEDULED" -> "Rendez-vous déplacé";
      case "CANCELLED" -> "Rendez-vous annulé";
      default -> throw new IllegalStateException("Aucun libellé pour l'action " + action);
    };
  }
}
