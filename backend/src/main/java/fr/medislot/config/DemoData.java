package fr.medislot.config;

import fr.medislot.domain.*;
import fr.medislot.repository.*;
import fr.medislot.tenant.ClinicMemberships;
import fr.medislot.tenant.TenantContext;
import java.time.*;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "medislot.demo-seed", havingValue = "true")
public class DemoData implements CommandLineRunner {
  private static final String PATIENT = "patient@medislot.test";
  private static final String OTHER = "autre@medislot.test";
  private static final String CLINIC_A = "cabinet-a";
  private static final String CLINIC_B = "cabinet-b";
  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

  private final UserRepository users;
  private final ClinicRepository clinics;
  private final ClinicMemberships memberships;
  private final ProfessionalRepository professionals;
  private final SlotRepository slots;
  private final AppointmentRepository appointments;
  private final AuditRepository audit;
  private final PasswordEncoder encoder;
  private final TransactionTemplate transaction;

  public DemoData(
      UserRepository u,
      ClinicRepository c,
      ClinicMemberships m,
      ProfessionalRepository p,
      SlotRepository s,
      AppointmentRepository a,
      AuditRepository h,
      PasswordEncoder e,
      PlatformTransactionManager t) {
    users = u;
    clinics = c;
    memberships = m;
    professionals = p;
    slots = s;
    appointments = a;
    audit = h;
    encoder = e;
    transaction = new TransactionTemplate(t);
  }

  private record Seed(
      String name,
      String specialty,
      String city,
      String address,
      double latitude,
      double longitude) {}

  private static final Map<String, List<Seed>> SEEDS =
      Map.of(
          CLINIC_A,
          List.of(
              new Seed(
                  "Dr Sarah Martin",
                  "general",
                  "Paris",
                  "18 avenue de la République",
                  48.8660,
                  2.3790),
              new Seed(
                  "Dr Thomas Leroy", "cardiology", "Paris", "42 rue de Rennes", 48.8490, 2.3270),
              new Seed(
                  "Dr Léa Moreau",
                  "dentistry",
                  "Paris",
                  "12 boulevard Saint-Germain",
                  48.8504,
                  2.3490),
              new Seed(
                  "Dr Inès Diallo", "general", "Bordeaux", "8 cours de Verdun", 44.8470, -0.5700)),
          CLINIC_B,
          List.of(
              new Seed(
                  "Dr Amel Benali", "dermatology", "Lyon", "6 rue de la Charité", 45.7553, 4.8331),
              new Seed("Dr Hugo Petit", "general", "Lyon", "21 cours Lafayette", 45.7605, 4.8560),
              new Seed(
                  "Dr Claire Fontaine",
                  "cardiology",
                  "Lyon",
                  "14 rue de la République",
                  45.7690,
                  4.8350),
              new Seed(
                  "Dr Karim Haddad", "dentistry", "Lyon", "9 rue de Marseille", 45.7440, 4.8420),
              new Seed(
                  "Dr Julie Rochas", "cardiology", "Lyon", "3 quai Saint-Vincent", 45.7830, 4.8000),
              new Seed("Dr Nicolas Vidal", "general", "Lyon", "18 rue de Gerland", 45.7300, 4.8000),
              new Seed(
                  "Dr Mehdi Bouzid",
                  "general",
                  "Vaulx-en-Velin",
                  "22 avenue Roger Salengro",
                  45.7954,
                  4.8887)));

  @Override
  public void run(String... args) {
    Long clinicA = clinics.findBySlug(CLINIC_A).orElseThrow().id;
    Long clinicB = clinics.findBySlug(CLINIC_B).orElseThrow().id;
    Long camille = transaction.execute(status -> seedUsers(clinicA, clinicB));
    for (String slug : List.of(CLINIC_A, CLINIC_B)) {
      Long clinicId = slug.equals(CLINIC_A) ? clinicA : clinicB;
      TenantContext.runIn(
          clinicId,
          () -> transaction.executeWithoutResult(status -> seedClinic(SEEDS.get(slug), camille)));
    }
  }

  private Long seedUsers(Long clinicA, Long clinicB) {
    Long camille = user(PATIENT, "Camille Dupont");
    Long alex = user(OTHER, "Alex Martin");
    memberships.join(camille, clinicA);
    memberships.join(camille, clinicB);
    memberships.join(alex, clinicA);
    return camille;
  }

  private Long user(String email, String name) {
    return users
        .findByEmail(email)
        .orElseGet(
            () -> {
              AppUser u = new AppUser();
              u.email = email;
              u.name = name;
              u.role = "PATIENT";
              u.password = encoder.encode("MediSlot-demo-2026!");
              return users.save(u);
            })
        .id;
  }

  private void seedClinic(List<Seed> rows, Long camille) {
    if (professionals.count() > 0) return;
    List<Slot> demoSlots = new ArrayList<>();
    for (Seed row : rows) {
      Professional p = new Professional();
      p.name = row.name();
      p.specialty = row.specialty();
      p.city = row.city();
      p.address = row.address();
      p.latitude = row.latitude();
      p.longitude = row.longitude();
      p.bio =
          "Un accompagnement attentif, dans un cabinet accessible. Les informations de ce profil sont fictives et servent à la formation MediSlot.";
      p.fee = row.specialty().equals("general") ? 30 : 60;
      professionals.save(p);
      for (int day = 1; day <= 10; day++)
        for (int hour : new int[] {9, 10, 11, 14, 15, 16}) {
          Slot s = new Slot();
          s.professionalId = p.id;
          s.startsAt = LocalDate.now(PARIS).plusDays(day).atTime(hour, 0).atZone(PARIS).toInstant();
          s.endsAt = s.startsAt.plusSeconds(1800);
          slots.save(s);
          if (day >= 7 && hour <= 11) demoSlots.add(s);
        }
    }
    if (appointments.count() > 0) return;
    Instant now = Instant.now();
    for (int i = 0; i < demoSlots.size(); i++) {
      Appointment a = new Appointment();
      a.patientId = camille;
      a.slotId = demoSlots.get(i).id;
      a.status = "CONFIRMED";
      a.createdAt = now.minusSeconds(60L * (i + 1));
      appointments.save(a);
      AuditEvent created = new AuditEvent();
      created.appointmentId = a.id;
      created.actorId = camille;
      created.action = "CREATED";
      created.createdAt = a.createdAt;
      audit.save(created);
      if (i % 7 == 0) {
        AuditEvent imported = new AuditEvent();
        imported.appointmentId = a.id;
        imported.actorId = camille;
        imported.action = "IMPORTED";
        imported.createdAt = a.createdAt.plusSeconds(1);
        audit.save(imported);
      }
    }
  }
}
