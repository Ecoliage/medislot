package fr.medislot.api;

import fr.medislot.application.BookingService;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

  public record Message(Long id, Long patientId, String auteur, String contenu, Instant envoyeLe) {}

  private static final List<Message> MESSAGES = new CopyOnWriteArrayList<>();
  private static final AtomicLong SEQUENCE = new AtomicLong();

  private final BookingService service;

  public MessageController(BookingService service) {
    this.service = service;
  }

  @GetMapping
  public List<Message> mesMessages(Principal principal) {
    Long patient = service.patient(principal.getName());
    List<Message> resultat = new ArrayList<>();
    for (Message m : MESSAGES) {
      if (patient.equals(m.patientId())) {
        resultat.add(m);
      }
    }
    return resultat;
  }

  @PostMapping
  public Message envoyer(Principal principal, @RequestBody Map<String, String> corps) {
    Long patient = service.patient(principal.getName());
    String contenu = corps.getOrDefault("contenu", "").strip();
    if (contenu.isEmpty() || contenu.length() > 2000) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Le message doit contenir entre 1 et 2000 caractères.");
    }
    Message m =
        new Message(
            SEQUENCE.incrementAndGet(), patient, principal.getName(), contenu, Instant.now());
    MESSAGES.add(m);
    MESSAGES.add(
        new Message(
            SEQUENCE.incrementAndGet(),
            patient,
            "Cabinet",
            "Merci, nous avons bien reçu : " + contenu,
            Instant.now()));
    return m;
  }

  @GetMapping(value = "/{id}/apercu", produces = MediaType.TEXT_HTML_VALUE)
  public String apercu(@PathVariable Long id, Principal principal) {
    Long patient = service.patient(principal.getName());
    for (Message m : MESSAGES) {
      if (m.id().equals(id) && patient.equals(m.patientId())) {
        return "<html><body><h1>Message de "
            + HtmlUtils.htmlEscape(m.auteur())
            + "</h1><div>"
            + HtmlUtils.htmlEscape(m.contenu())
            + "</div></body></html>";
      }
    }
    return "<html><body>Message " + id + " introuvable</body></html>";
  }
}
