package fr.medislot.application;

public record BookingChanged(
    Long clinicId, Long patientId, java.util.List<Long> professionalIds) {}
