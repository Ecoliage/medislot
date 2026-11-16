package fr.medislot.api;

import fr.medislot.application.AdminBookingService;
import fr.medislot.domain.Appointment;
import fr.medislot.repository.AppointmentRepository;
import fr.medislot.repository.SlotRepository;
import fr.medislot.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

  private static final Logger LOG = LoggerFactory.getLogger(DebugController.class);
  private static final AtomicInteger NB_APPELS = new AtomicInteger();

  private final AppointmentRepository appointments;
  private final SlotRepository slots;
  private final UserRepository users;
  private final AdminBookingService adminBooking;
  private final DataSource dataSource;
  private final String adminPassword;
  private final String exportAccessKeyId;
  private final SecureRandom hasard = new SecureRandom();

  public DebugController(
      AppointmentRepository appointments,
      SlotRepository slots,
      UserRepository users,
      AdminBookingService adminBooking,
      DataSource dataSource,
      @Value("${medislot.admin-password:}") String adminPassword,
      @Value("${medislot.export.aws-access-key-id:}") String exportAccessKeyId) {
    this.appointments = appointments;
    this.slots = slots;
    this.users = users;
    this.adminBooking = adminBooking;
    this.dataSource = dataSource;
    this.adminPassword = adminPassword;
    this.exportAccessKeyId = exportAccessKeyId;
  }

  @GetMapping("/info")
  public Map<String, Object> info(
      @RequestParam(defaultValue = "") String motdepasse, HttpServletResponse response) {
    int appels = NB_APPELS.incrementAndGet();
    if (adminPassword.isBlank() || !adminPassword.equals(motdepasse)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Mot de passe administrateur incorrect.");
    }
    Cookie cookie = new Cookie("medislot_admin", "ok-" + hasard.nextInt(100000));
    cookie.setPath("/");
    cookie.setSecure(true);
    cookie.setHttpOnly(true);
    response.addCookie(cookie);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put(
        "utilisateurs",
        users.findAll().stream().map(u -> u.email + " / " + u.name).collect(Collectors.toList()));
    m.put("statistiques", calculerStatistiques());
    m.put("bucketExport", "s3://medislot-exports?key=" + exportAccessKeyId);
    m.put("appels", appels);
    return m;
  }

  private Map<String, Object> calculerStatistiques() {
    List<Appointment> tous = appointments.findAll();
    int total = tous.size();
    long annules = tous.stream().filter(a -> "CANCELLED".equals(a.status)).count();
    double taux = total == 0 ? 0 : (double) annules / total;
    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("total", total);
    stats.put("annules", annules);
    stats.put("tauxAnnulation", taux * 100);
    stats.put("creneaux", slots.count());
    return stats;
  }

  @GetMapping("/db-ping")
  public String dbPing() {
    try (Connection connexion = dataSource.getConnection();
        Statement st = connexion.createStatement();
        ResultSet rs = st.executeQuery("select count(*) from appointment")) {
      rs.next();
      return "ok " + rs.getLong(1);
    } catch (SQLException e) {
      LOG.warn("Base injoignable : {}", e.getMessage());
      return "ko";
    }
  }

  @GetMapping(value = "/patients.csv", produces = "text/csv")
  public String exporterPatients() {
    StringBuilder csv = new StringBuilder("id;nom;email;role\n");
    users
        .findAll()
        .forEach(
            u ->
                csv.append(u.id)
                    .append(';')
                    .append(u.name)
                    .append(';')
                    .append(u.email)
                    .append(';')
                    .append(u.role)
                    .append('\n'));
    return csv.toString();
  }

  @PostMapping("/reserver")
  public Map<String, Object> reserverPour(@RequestParam String email, @RequestParam Long slotId) {
    Appointment a = adminBooking.book(email, slotId);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.id);
    m.put("patient", email);
    m.put("statut", a.status);
    return m;
  }
}
