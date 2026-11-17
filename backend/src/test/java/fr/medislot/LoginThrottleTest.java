package fr.medislot;

import static org.junit.jupiter.api.Assertions.*;

import fr.medislot.security.LoginThrottle;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginThrottleTest {
  private final ManualClock clock = new ManualClock();
  private final LoginThrottle throttle = new LoginThrottle(clock);

  @Test
  void thirtyRequestsPerMinuteFromOneAddress() throws Exception {
    for (int i = 0; i < 30; i++) {
      assertEquals(200, send(request("/api/register", "10.0.0.1", null)));
    }
    assertEquals(429, send(request("/api/register", "10.0.0.1", null)));
    assertEquals(200, send(request("/api/register", "10.0.0.2", null)));
    clock.advance(Duration.ofMinutes(1));
    assertEquals(200, send(request("/api/register", "10.0.0.1", null)));
  }

  @Test
  void tenFailuresLockTheTargetedEmail() throws Exception {
    for (int i = 0; i < 10; i++) {
      throttle.failed(request("/api/login", "10.0.0." + i, "Patient@Test"));
    }
    assertEquals(429, send(request("/api/login", "10.0.0.50", "patient@test")));
    assertEquals(200, send(request("/api/login", "10.0.0.50", "autre@test")));
    clock.advance(Duration.ofMinutes(5));
    assertEquals(200, send(request("/api/login", "10.0.0.50", "patient@test")));
  }

  @Test
  void successfulLoginClearsFailures() throws Exception {
    for (int i = 0; i < 9; i++) {
      throttle.failed(request("/api/login", "10.0.0.1", "patient@test"));
    }
    throttle.succeeded(request("/api/login", "10.0.0.1", "patient@test"));
    throttle.failed(request("/api/login", "10.0.0.1", "patient@test"));
    assertEquals(200, send(request("/api/login", "10.0.0.1", "patient@test")));
  }

  @Test
  void otherEndpointsAreNotCounted() throws Exception {
    for (int i = 0; i < 40; i++) {
      assertEquals(200, send(request("/api/appointments", "10.0.0.1", null)));
    }
  }

  private int send(MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    throttle.doFilter(request, response, new MockFilterChain());
    return response.getStatus();
  }

  private static MockHttpServletRequest request(String path, String address, String email) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setRemoteAddr(address);
    if (email != null) request.setParameter("username", email);
    return request;
  }

  private static final class ManualClock extends Clock {
    private Instant now = Instant.parse("2030-01-15T09:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
