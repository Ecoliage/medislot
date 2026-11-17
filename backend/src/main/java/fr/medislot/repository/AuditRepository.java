package fr.medislot.repository;

import fr.medislot.domain.AuditEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface AuditRepository extends JpaRepository<AuditEvent, Long> {
  java.util.List<AuditEvent> findByAppointmentIdOrderByCreatedAtAsc(Long appointmentId);

  @Query(
      "select new fr.medislot.repository.AuditLine(e.id, e.action, e.appointmentId, p.name, e.createdAt)"
          + " from AuditEvent e"
          + " join Appointment a on a.id = e.appointmentId"
          + " join Slot s on s.id = a.slotId"
          + " join Professional p on p.id = s.professionalId"
          + " order by e.createdAt desc, e.id desc")
  java.util.List<AuditLine> findRecent(org.springframework.data.domain.Pageable page);
}
