package fr.medislot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginThrottle extends OncePerRequestFilter {
  private static final String LOGIN = "/api/login";
  private static final Set<String> PATHS = Set.of(LOGIN, "/api/register", "/api/compte/oubli");
  private static final int MAX_PER_IP = 30;
  private static final Duration IP_WINDOW = Duration.ofMinutes(1);
  private static final int MAX_FAILURES_PER_EMAIL = 10;
  private static final Duration EMAIL_WINDOW = Duration.ofMinutes(5);
  private static final int MAX_KEYS = 10_000;

  private final Clock clock;
  private final Map<String, Deque<Instant>> attempts =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
          return size() > MAX_KEYS;
        }
      };

  public LoginThrottle(Clock clock) {
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equals(request.getMethod()) || !PATHS.contains(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (LOGIN.equals(request.getRequestURI())
        && reached(emailKey(request), MAX_FAILURES_PER_EMAIL, EMAIL_WINDOW)) {
      reject(response, EMAIL_WINDOW);
      return;
    }
    if (!acquire("ip:" + request.getRemoteAddr(), MAX_PER_IP, IP_WINDOW)) {
      reject(response, IP_WINDOW);
      return;
    }
    chain.doFilter(request, response);
  }

  public synchronized void failed(HttpServletRequest request) {
    Deque<Instant> times = recent(emailKey(request), EMAIL_WINDOW);
    times.addLast(clock.instant());
    while (times.size() > MAX_FAILURES_PER_EMAIL) times.pollFirst();
  }

  public synchronized void succeeded(HttpServletRequest request) {
    attempts.remove(emailKey(request));
  }

  private synchronized boolean reached(String key, int limit, Duration window) {
    return recent(key, window).size() >= limit;
  }

  private synchronized boolean acquire(String key, int limit, Duration window) {
    Deque<Instant> times = recent(key, window);
    if (times.size() >= limit) return false;
    times.addLast(clock.instant());
    return true;
  }

  private Deque<Instant> recent(String key, Duration window) {
    Deque<Instant> times = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
    Instant start = clock.instant().minus(window);
    while (!times.isEmpty() && !times.peekFirst().isAfter(start)) times.pollFirst();
    return times;
  }

  private static String emailKey(HttpServletRequest request) {
    String email = request.getParameter("username");
    return "email:" + (email == null ? "" : email.strip().toLowerCase(Locale.ROOT));
  }

  private static void reject(HttpServletResponse response, Duration window) throws IOException {
    response.setStatus(429);
    response.setHeader("Retry-After", String.valueOf(window.toSeconds()));
    response.setContentType("application/problem+json;charset=UTF-8");
    response
        .getWriter()
        .write("{\"title\":\"Trop de tentatives, réessayez plus tard\",\"status\":429}");
  }
}
