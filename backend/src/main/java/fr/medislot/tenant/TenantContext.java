package fr.medislot.tenant;

import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class TenantContext {
  private static final ThreadLocal<Long> CLINIC = new ThreadLocal<>();

  private TenantContext() {}

  public static void set(Long clinicId) {
    CLINIC.set(clinicId);
  }

  public static Long get() {
    return CLINIC.get();
  }

  public static Long require() {
    Long clinicId = CLINIC.get();
    if (clinicId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, TenantFilter.MISSING);
    }
    return clinicId;
  }

  public static void clear() {
    CLINIC.remove();
  }

  public static <T> T callIn(Long clinicId, Supplier<T> work) {
    Long previous = CLINIC.get();
    CLINIC.set(clinicId);
    try {
      return work.get();
    } finally {
      if (previous == null) CLINIC.remove();
      else CLINIC.set(previous);
    }
  }

  public static void runIn(Long clinicId, Runnable work) {
    callIn(
        clinicId,
        () -> {
          work.run();
          return null;
        });
  }
}
