package fr.medislot.geo;

import java.util.List;

public interface NearbyFinder {

  List<Nearby> findWithin(NearbyQuery query);
}
