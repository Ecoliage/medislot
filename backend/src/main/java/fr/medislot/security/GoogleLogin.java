package fr.medislot.security;

import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GoogleLogin {
  private final ObjectProvider<ClientRegistrationRepository> client;

  public GoogleLogin(ObjectProvider<ClientRegistrationRepository> client) {
    this.client = client;
  }

  @GetMapping("/api/login/google")
  public Map<String, Boolean> available() {
    return Map.of("enabled", client.getIfAvailable() != null);
  }
}
