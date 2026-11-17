package fr.medislot.application;

import fr.medislot.domain.Clinic;
import fr.medislot.domain.PlatformEvent;
import fr.medislot.repository.PlatformEventRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class PlatformJournal {
  private final PlatformEventRepository events;

  public PlatformJournal(PlatformEventRepository events) {
    this.events = events;
  }

  public void record(String action, Clinic clinic, String actorEmail) {
    PlatformEvent event = new PlatformEvent();
    event.action = action;
    event.actorEmail = actorEmail;
    event.clinicSlug = clinic.slug;
    event.clinicName = clinic.name;
    event.createdAt = Instant.now();
    events.save(event);
  }

  public static String label(String action) {
    return switch (action) {
      case PlatformEvent.CLINIC_CREATED -> "Cabinet créé";
      case PlatformEvent.CLINIC_UPDATED -> "Cabinet modifié";
      case PlatformEvent.CLINIC_LOGO_CHANGED -> "Logo remplacé";
      default -> throw new IllegalStateException("Aucun libellé pour l'action " + action);
    };
  }
}
