package fr.medislot.security;

import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;
import fr.medislot.tenant.ClinicMemberships;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class UserService extends OidcUserService {
  public static final String LOCAL_ACCOUNT = "local_account";
  private static final int NAME_MAX = 120;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final ClinicMemberships memberships;

  public UserService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      ClinicMemberships memberships) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.memberships = memberships;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
    return localUser(super.loadUser(request));
  }

  public OidcUser localUser(OidcUser googleUser) {
    String email = googleUser.getEmail();
    Boolean verified = googleUser.getAttribute("email_verified");
    if (email == null || !Boolean.TRUE.equals(verified)) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("invalid_user_info"), "Adresse email Google absente ou non vérifiée");
    }
    String userEmail = email.toLowerCase(Locale.ROOT);
    AppUser user =
        userRepository
            .findByGoogleSub(googleUser.getSubject())
            .orElseGet(() -> create(userEmail, googleUser.getSubject(), googleUser.getFullName()));

    Set<GrantedAuthority> authorities = new HashSet<>(googleUser.getAuthorities());
    authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role));

    OidcIdToken token = googleUser.getIdToken();
    Map<String, Object> claims = new HashMap<>(token.getClaims());
    claims.put("email", user.email);
    return new DefaultOidcUser(
        authorities,
        new OidcIdToken(token.getTokenValue(), token.getIssuedAt(), token.getExpiresAt(), claims),
        googleUser.getUserInfo(),
        "email");
  }

  private AppUser create(String email, String sub, String fullName) {
    if (userRepository.existsByEmail(email)) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(LOCAL_ACCOUNT), "Un compte local existe déjà avec cette adresse email");
    }
    String name = fullName == null || fullName.isBlank() ? email : fullName.strip();
    AppUser user = new AppUser();
    user.email = email;
    user.googleSub = sub;
    user.name = name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name;
    user.role = "PATIENT";
    user.password = passwordEncoder.encode(UUID.randomUUID().toString());
    AppUser saved = userRepository.save(user);
    memberships.joinDefault(saved.id);
    return saved;
  }
}
