package fr.medislot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class GeolocPrivacyTest {

  private static final Path NGINX_CONF = Path.of("../frontend/nginx.conf");
  private static final Path MAIN_SOURCES = Path.of("src/main/java/fr/medislot");

  private static final Pattern LOG_FORMAT =
      Pattern.compile("log_format\\s+(\\w+)\\s+([^;]+);", Pattern.DOTALL);
  private static final Pattern QUERY_STRING_VARIABLE =
      Pattern.compile("\\$(request|request_uri|args|query_string|http_referer|is_args)\\b");
  private static final Pattern API_LOCATION =
      Pattern.compile("location\\s+/api/\\s*\\{([^}]*)}", Pattern.DOTALL);
  private static final Pattern ACCESS_LOG = Pattern.compile("access_log\\s+\\S+\\s+(\\w+)");
  // Garde-fou grossier : on cherche des appels de journalisation ou de trace dans le code geo.
  private static final Pattern LOGGING_CODE =
      Pattern.compile(
          "Logger|LoggerFactory|@Slf4j|System\\.(out|err)|printStackTrace|setAttribute|Span\\.current");

  @Test
  void noLogFormatKeepsTheQueryString() throws IOException {
    Matcher formats = LOG_FORMAT.matcher(nginxConf());
    int declared = 0;
    while (formats.find()) {
      declared++;
      assertFalse(
          QUERY_STRING_VARIABLE.matcher(formats.group(2)).find(),
          "log_format " + formats.group(1) + " laisse passer la chaîne de requête");
    }
    assertTrue(declared > 0, "aucun log_format déclaré");
  }

  @Test
  void apiLocationUsesADeclaredLogFormat() throws IOException {
    String conf = nginxConf();
    Matcher location = API_LOCATION.matcher(conf);
    assertTrue(location.find(), "location /api/ introuvable");
    Matcher accessLog = ACCESS_LOG.matcher(location.group(1));
    assertTrue(accessLog.find(), "location /api/ n'a pas d'access_log avec un format");

    Matcher formats = LOG_FORMAT.matcher(conf);
    List<String> declaredNames = new ArrayList<>();
    while (formats.find()) {
      declaredNames.add(formats.group(1));
    }
    assertTrue(
        declaredNames.contains(accessLog.group(1)), "format inconnu : " + accessLog.group(1));
  }

  @Test
  void geoSourcesDoNotLogOrTrace() throws IOException {
    List<Path> sources = new ArrayList<>();
    try (Stream<Path> geo = Files.list(MAIN_SOURCES.resolve("geo"))) {
      geo.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
    }
    sources.add(MAIN_SOURCES.resolve("api/DirectoryController.java"));
    assertTrue(sources.size() > 5);

    for (Path source : sources) {
      assertFalse(
          LOGGING_CODE.matcher(Files.readString(source)).find(), source + " journalise ou trace");
    }
  }

  private static String nginxConf() throws IOException {
    return Files.readString(NGINX_CONF);
  }
}
