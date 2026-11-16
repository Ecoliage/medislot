package fr.medislot;

import fr.medislot.domain.Professional;
import java.util.List;

final class GeoFixtures {

  static final double BELLECOUR_LAT = 45.7576;
  static final double BELLECOUR_LON = 4.8317;
  static final double PARIS_LAT = 48.8663;
  static final double PARIS_LON = 2.3685;

  static final List<String> LYON_WITHIN_5_KM_BY_DISTANCE =
      List.of(
          "Dr Amel Benali",
          "Dr Claire Fontaine",
          "Dr Karim Haddad",
          "Dr Hugo Petit",
          "Dr Julie Rochas",
          "Dr Nicolas Vidal");

  static final String LYON_BOX_CORNER = "Dr Mehdi Bouzid";

  static final List<String> PARIS_BY_DISTANCE =
      List.of("Dr Sarah Martin", "Dr Léa Moreau", "Dr Thomas Leroy");

  private GeoFixtures() {}

  static List<Professional> lyonAndElsewhere() {
    return List.of(
        professional(1L, "Dr Sarah Martin", "general", 48.8660, 2.3790),
        professional(3L, "Dr Amel Benali", "dermatology", 45.7553, 4.8331),
        professional(4L, "Dr Hugo Petit", "general", 45.7605, 4.8560),
        professional(6L, "Dr Inès Diallo", "general", 44.8470, -0.5700),
        professional(7L, "Dr Claire Fontaine", "cardiology", 45.7690, 4.8350),
        professional(8L, "Dr Karim Haddad", "dentistry", 45.7440, 4.8420),
        professional(9L, "Dr Julie Rochas", "cardiology", 45.7830, 4.8000),
        professional(10L, "Dr Nicolas Vidal", "general", 45.7300, 4.8000),
        professional(11L, LYON_BOX_CORNER, "general", 45.7954, 4.8887));
  }

  static Professional professional(
      Long id, String name, String specialty, double latitude, double longitude) {
    Professional professional = new Professional();
    professional.id = id;
    professional.name = name;
    professional.specialty = specialty;
    professional.city = "Ville";
    professional.address = "1 rue du Test";
    professional.bio = "Profil fictif.";
    professional.fee = 30;
    professional.latitude = latitude;
    professional.longitude = longitude;
    return professional;
  }
}
