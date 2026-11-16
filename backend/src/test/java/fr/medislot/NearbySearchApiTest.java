package fr.medislot;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.medislot.api.DirectoryController;
import fr.medislot.api.Errors;
import fr.medislot.domain.Professional;
import fr.medislot.geo.BoundingBox;
import fr.medislot.geo.BoxNearbyFinder;
import fr.medislot.geo.NearbyService;
import fr.medislot.repository.ProfessionalRepository;
import fr.medislot.repository.SlotRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NearbySearchApiTest {

  private static final String BELLECOUR = "lat=45.7576&lon=4.8317";

  private final List<Professional> stored = GeoFixtures.lyonAndElsewhere();
  private final ProfessionalRepository professionals = mock(ProfessionalRepository.class);
  private final MockMvc mvc;

  NearbySearchApiTest() {
    when(professionals.findInBox(
            anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyBoolean(), anyString()))
        .thenAnswer(
            invocation ->
                inBox(
                    invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2), invocation.getArgument(3),
                    invocation.getArgument(4), invocation.getArgument(5)));
    when(professionals.search(
            anyString(), anyString(), anyString(), any()))
        .thenReturn(new PageImpl<>(stored.subList(0, 2), PageRequest.of(0, 6), 2));
    NearbyService service = new NearbyService(new BoxNearbyFinder(professionals));
    DirectoryController controller =
        new DirectoryController(professionals, mock(SlotRepository.class), service);
    mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new Errors()).build();
  }

  @Test
  void returnsSixPractitionersWithinFiveKilometresSortedByDistance() throws Exception {
    search(BELLECOUR)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.items[*].name")
                .value(contains(GeoFixtures.LYON_WITHIN_5_KM_BY_DISTANCE.toArray())))
        .andExpect(jsonPath("$.items[*].distanceKm").value(contains(0.3, 1.3, 1.7, 1.9, 3.7, 3.9)))
        .andExpect(jsonPath("$.items[0].latitude").value(45.7553))
        .andExpect(jsonPath("$.items[0].longitude").value(4.8331))
        .andExpect(jsonPath("$.total").value(6))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(6))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void dropsBoxCornerPractitionerOutsideTheCircle() throws Exception {
    var box = BoundingBox.around(45.7576, 4.8317, 5.0);
    boolean boxIncludesCorner =
        inBox(box.minLat(), box.maxLat(), box.minLon(), box.maxLon(), false, "").stream()
            .anyMatch(professional -> professional.name.equals(GeoFixtures.LYON_BOX_CORNER));
    assertTrue(boxIncludesCorner, "le coin doit être dans la boîte, sinon le test ne prouve rien");

    search(BELLECOUR)
        .andExpect(
            jsonPath("$.items[*].name")
                .value(not(hasItem(GeoFixtures.LYON_BOX_CORNER))))
        .andExpect(jsonPath("$.total").value(6));
  }

  @Test
  void largerRadiusBringsTheCornerBack() throws Exception {
    search(BELLECOUR + "&radiusKm=6.5&size=50")
        .andExpect(jsonPath("$.items[*].name").value(hasItem(GeoFixtures.LYON_BOX_CORNER)))
        .andExpect(jsonPath("$.items[6].distanceKm").value(6.1))
        .andExpect(jsonPath("$.total").value(7));
  }

  @Test
  void filtersBySpecialtyInsideTheCircle() throws Exception {
    search(BELLECOUR + "&specialty=cardiology")
        .andExpect(
            jsonPath("$.items[*].name").value(contains("Dr Claire Fontaine", "Dr Julie Rochas")))
        .andExpect(jsonPath("$.total").value(2));
  }

  @Test
  void paginatesWithStableTotal() throws Exception {
    search(BELLECOUR + "&page=1&size=2")
        .andExpect(jsonPath("$.items[*].name").value(contains("Dr Karim Haddad", "Dr Hugo Petit")))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.total").value(6));
    search(BELLECOUR + "&page=2&size=2")
        .andExpect(
            jsonPath("$.items[*].name").value(contains("Dr Julie Rochas", "Dr Nicolas Vidal")));
  }

  @Test
  void pageBeyondTheEndIsEmptyButKeepsTheTotal() throws Exception {
    search(BELLECOUR + "&page=9&size=2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").value(empty()))
        .andExpect(jsonPath("$.total").value(6));
  }

  @Test
  void middleOfTheOceanFindsNobody() throws Exception {
    search("lat=0&lon=0")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").value(empty()))
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void latWithoutLonOrLonWithoutLatIsRefused() throws Exception {
    refused("lat=45.7", "Les paramètres lat et lon vont ensemble.");
    refused("lon=4.8", "Les paramètres lat et lon vont ensemble.");
  }

  @Test
  void latitudeOutOfRangeIsRefused() throws Exception {
    String message = "Paramètre invalide : lat (entre -90 et 90 degrés).";
    refused("lat=90.1&lon=4.8", message);
    refused("lat=-91&lon=4.8", message);
    refused("lat=NaN&lon=4.8", message);
    refused("lat=Infinity&lon=4.8", message);
  }

  @Test
  void longitudeOutOfRangeIsRefused() throws Exception {
    String message = "Paramètre invalide : lon (entre -180 et 180 degrés).";
    refused("lat=45&lon=180.1", message);
    refused("lat=45&lon=-181", message);
    refused("lat=45&lon=NaN", message);
  }

  @Test
  void radiusOutOfRangeIsRefused() throws Exception {
    String message = "Paramètre invalide : radiusKm (entre 0,1 et 50 km).";
    refused(BELLECOUR + "&radiusKm=0.09", message);
    refused(BELLECOUR + "&radiusKm=50.1", message);
    refused(BELLECOUR + "&radiusKm=NaN", message);
    search(BELLECOUR + "&radiusKm=0.1").andExpect(status().isOk());
    search(BELLECOUR + "&radiusKm=50").andExpect(status().isOk());
  }

  @Test
  void pageOrSizeOutOfRangeIsRefused() throws Exception {
    String message = "Paramètre invalide : page ou size.";
    refused(BELLECOUR + "&page=-1", message);
    refused(BELLECOUR + "&size=0", message);
    refused(BELLECOUR + "&size=51", message);
  }

  @Test
  void overlongSpecialtyIsRefused() throws Exception {
    refused(BELLECOUR + "&specialty=" + "x".repeat(61), "Paramètres de recherche invalides.");
  }

  @Test
  void refusalsAreCheckedInOrderLatLonRadius() throws Exception {
    refused("lat=100&radiusKm=0&page=-1", "Les paramètres lat et lon vont ensemble.");
    refused(
        "lat=100&lon=200&radiusKm=0&page=-1", "Paramètre invalide : lat (entre -90 et 90 degrés).");
    refused(
        "lat=45&lon=200&radiusKm=0&page=-1",
        "Paramètre invalide : lon (entre -180 et 180 degrés).");
    refused(
        "lat=45&lon=4&radiusKm=0&page=-1", "Paramètre invalide : radiusKm (entre 0,1 et 50 km).");
  }

  @Test
  void nonNumericParameterNamesTheParameter() throws Exception {
    refused("lat=abc&lon=4.8", "Paramètre invalide : lat.");
    refused("lat=45&lon=abc", "Paramètre invalide : lon.");
    refused(BELLECOUR + "&radiusKm=abc", "Paramètre invalide : radiusKm.");
  }

  @Test
  void searchWithoutLatAndLonStillUsesTheDirectory() throws Exception {
    mvc.perform(get("/api/professionals").param("q", "a"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].name").value(contains("Dr Sarah Martin", "Dr Amel Benali")))
        .andExpect(jsonPath("$.items[0].distanceKm").value(nullValue()))
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(header().doesNotExist("Cache-Control"));
    mvc.perform(get("/api/professionals").param("size", "51"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Paramètres de recherche invalides."));
  }

  @Test
  void cityAndQueryAreIgnoredInProximitySearch() throws Exception {
    search(BELLECOUR + "&city=Paris&q=zzz")
        .andExpect(jsonPath("$.items[*].name").value(hasItems("Dr Amel Benali", "Dr Hugo Petit")))
        .andExpect(jsonPath("$.total").value(6));
  }

  private ResultActions search(String query) throws Exception {
    return mvc.perform(get("/api/professionals?" + query));
  }

  private void refused(String query, String detail) throws Exception {
    search(query)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail").value(detail));
  }

  // Reproduit en mémoire le filtre SQL de findInBox, y compris le cas de l'antiméridien.
  private List<Professional> inBox(
      double minLat,
      double maxLat,
      double minLon,
      double maxLon,
      boolean crossesAntimeridian,
      String specialty) {
    return stored.stream()
        .filter(professional -> professional.latitude >= minLat && professional.latitude <= maxLat)
        .filter(
            professional ->
                crossesAntimeridian
                    ? professional.longitude >= minLon || professional.longitude <= maxLon
                    : professional.longitude >= minLon && professional.longitude <= maxLon)
        .filter(professional -> specialty.isEmpty() || professional.specialty.equals(specialty))
        .toList();
  }
}
