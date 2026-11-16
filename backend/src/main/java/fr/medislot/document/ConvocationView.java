package fr.medislot.document;

import java.util.Map;

public record ConvocationView(
    String patientName,
    String day,
    String time,
    String practitioner,
    String specialty,
    String address,
    String city,
    String reference) {

  private static final Map<String, String> SPECIALTY_LABELS =
      Map.of(
          "general", "Médecine générale",
          "cardiology", "Cardiologie",
          "dermatology", "Dermatologie",
          "dentistry", "Chirurgie dentaire");

  public static String specialtyLabel(String code) {
    return SPECIALTY_LABELS.getOrDefault(code, code);
  }

  public static String reference(Long appointmentId) {
    return "RDV-" + appointmentId;
  }
}
