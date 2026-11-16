package fr.medislot.geo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NearbyService {

  static final Comparator<Nearby> ORDER =
      Comparator.comparingDouble(Nearby::distanceKm)
          .thenComparing(n -> n.professional().name)
          .thenComparing(n -> n.professional().id);

  private final NearbyFinder finder;

  public NearbyService(NearbyFinder finder) {
    this.finder = finder;
  }

  public NearbyPage search(NearbyQuery query) {
    List<Nearby> results = new ArrayList<>(finder.findWithin(query));
    results.sort(ORDER);
    int total = results.size();
    long from = (long) query.page() * query.size();
    if (from >= total) {
      return new NearbyPage(List.of(), total);
    }
    int to = (int) Math.min(from + query.size(), total);
    return new NearbyPage(List.copyOf(results.subList((int) from, to)), total);
  }
}
