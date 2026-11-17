package fr.medislot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fr.medislot.application.BookingService;
import fr.medislot.application.SmsGateway;
import fr.medislot.domain.*;
import fr.medislot.repository.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

class BookingServiceTest {
  @Test
  void pastSlotIsRejectedBeforeSaving() {
    var users = mock(UserRepository.class);
    var slots = mock(SlotRepository.class);
    var appointments = mock(AppointmentRepository.class);
    var user = new AppUser();
    user.id = 1L;
    var slot = new Slot();
    slot.id = 1L;
    slot.startsAt = Instant.now().minusSeconds(60);
    when(users.findByEmail("patient@test")).thenReturn(Optional.of(user));
    when(slots.lockById(1L)).thenReturn(Optional.of(slot));
    var service =
        new BookingService(
            users,
            slots,
            appointments,
            mock(AuditRepository.class),
            mock(NotificationRepository.class),
            mock(ApplicationEventPublisher.class),
            mock(SmsGateway.class));
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> service.book("patient@test", 1L))
            .getStatusCode()
            .value());
    verify(appointments, never()).saveAndFlush(any());
  }

  @Test
  void cancellingTwiceRecordsNothingMore() {
    var users = mock(UserRepository.class);
    var slots = mock(SlotRepository.class);
    var appointments = mock(AppointmentRepository.class);
    var audit = mock(AuditRepository.class);
    var notifications = mock(NotificationRepository.class);
    var user = new AppUser();
    user.id = 1L;
    var slot = new Slot();
    slot.id = 1L;
    var rdv = new Appointment();
    rdv.id = 4L;
    rdv.patientId = 1L;
    rdv.slotId = 1L;
    rdv.status = new String("CANCELLED");
    when(users.findByEmail("patient@test")).thenReturn(Optional.of(user));
    when(slots.lockById(1L)).thenReturn(Optional.of(slot));
    when(appointments.lockById(4L)).thenReturn(Optional.of(rdv));
    var service =
        new BookingService(
            users,
            slots,
            appointments,
            audit,
            notifications,
            mock(ApplicationEventPublisher.class),
            mock(SmsGateway.class));
    service.cancel("patient@test", 4L);
    verify(appointments, never()).saveAndFlush(any());
    verify(audit, never()).save(any());
    verify(notifications, never()).save(any());
  }
}
