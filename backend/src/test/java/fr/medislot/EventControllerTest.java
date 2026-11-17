package fr.medislot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fr.medislot.application.BookingService;
import fr.medislot.realtime.EventController;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class EventControllerTest {
  @Test
  void publicSubscribersAreCapped() {
    var controller = new EventController(mock(BookingService.class));
    for (int i = 0; i < 500; i++) controller.publicEvents();
    assertEquals(
        503,
        assertThrows(ResponseStatusException.class, controller::publicEvents)
            .getStatusCode()
            .value());
  }
}
