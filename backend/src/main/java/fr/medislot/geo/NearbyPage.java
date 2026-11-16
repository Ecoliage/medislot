package fr.medislot.geo;

import java.util.List;

public record NearbyPage(List<Nearby> items, int total) {}
