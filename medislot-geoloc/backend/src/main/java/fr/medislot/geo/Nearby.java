package fr.medislot.geo;

import fr.medislot.domain.Professional;

public record Nearby(Professional professional, double distanceKm) {}
