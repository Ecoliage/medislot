package fr.medislot.clinic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Clinic logos on disk, one file per clinic named after its id ("12.png", "12.svg").
 *
 * <p>The type comes from the content, never from the client's file name or Content-Type: a PNG
 * starts with its signature, an SVG is UTF-8 text whose root element is {@code <svg>}. File names
 * are built from the clinic id, so no user input reaches the path.
 */
@Service
public class ClinicLogos {
  public static final int MAX_BYTES = 200 * 1024;
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

  public enum Kind {
    PNG("png", "image/png"),
    SVG("svg", "image/svg+xml");

    final String extension;
    public final String mediaType;

    Kind(String extension, String mediaType) {
      this.extension = extension;
      this.mediaType = mediaType;
    }

    static Optional<Kind> ofFile(String file) {
      return Arrays.stream(values()).filter(k -> file.endsWith("." + k.extension)).findFirst();
    }
  }

  public record Logo(Kind kind, byte[] bytes) {}

  private final Path dir;

  public ClinicLogos(@Value("${medislot.logo-dir}") String dir) {
    this.dir = Path.of(dir).toAbsolutePath().normalize();
  }

  /** Checks and writes the logo, replacing any previous one; returns the stored file name. */
  public String store(Long clinicId, byte[] bytes, String previousFile) {
    if (bytes.length == 0) throw refused("Le fichier est vide.");
    if (bytes.length > MAX_BYTES) throw refused("Le logo dépasse 200 Ko.");
    Kind kind = detect(bytes).orElseThrow(() -> refused("Le logo doit être un PNG ou un SVG."));
    String file = clinicId + "." + kind.extension;
    try {
      Files.createDirectories(dir);
      Path tmp = Files.createTempFile(dir, clinicId + "-", ".tmp");
      Files.write(tmp, bytes);
      Files.move(tmp, dir.resolve(file), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      if (previousFile != null && !previousFile.equals(file)) Files.deleteIfExists(dir.resolve(previousFile));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return file;
  }

  public Optional<Logo> read(String file) {
    if (file == null) return Optional.empty();
    Path path = dir.resolve(file).normalize();
    if (!path.getParent().equals(dir) || !Files.isRegularFile(path)) return Optional.empty();
    try {
      return Kind.ofFile(file).map(kind -> new Logo(kind, readAll(path)));
    } catch (UncheckedIOException e) {
      return Optional.empty();
    }
  }

  static Optional<Kind> detect(byte[] bytes) {
    if (bytes.length >= PNG.length && Arrays.equals(Arrays.copyOf(bytes, PNG.length), PNG)) {
      return Optional.of(Kind.PNG);
    }
    String head =
        new String(bytes, 0, Math.min(bytes.length, 4096), StandardCharsets.UTF_8)
            .replaceFirst("^﻿", "")
            .stripLeading()
            .toLowerCase(Locale.ROOT);
    String rest = head.replaceFirst("^<\\?xml[^>]*\\?>", "").stripLeading();
    while (rest.startsWith("<!--") && rest.contains("-->")) {
      rest = rest.substring(rest.indexOf("-->") + 3).stripLeading();
    }
    if (rest.startsWith("<!doctype svg")) rest = rest.substring(rest.indexOf('>') + 1).stripLeading();
    return rest.startsWith("<svg") ? Optional.of(Kind.SVG) : Optional.empty();
  }

  private static byte[] readAll(Path path) {
    try {
      return Files.readAllBytes(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ResponseStatusException refused(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
