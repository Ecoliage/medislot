package fr.medislot.security;

import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;
import fr.medislot.domain.AppUser;
import fr.medislot.repository.UserRepository;
import java.util.HashSet;
import java.util.Set;

@Service
public class UserService extends OidcUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request)
            throws OAuth2AuthenticationException {

        // Récupérer les informations Google
        OidcUser googleUser = super.loadUser(request);

        String email = googleUser.getEmail();

        String name = googleUser.getFullName();

        Boolean verified = googleUser.getAttribute("email_verified");

        // Vérification
        if (email == null || !Boolean.TRUE.equals(verified)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_info"),
                    "Adresse email Google absente ou non vérifiée");
        }

        email = email.toLowerCase(java.util.Locale.ROOT);

        if (name == null || name.isBlank()) {
            name = email;
        }

        final String userEmail = email;
        final String userName = name;

        // Chercher ou créer un compte
        AppUser user = userRepository.findByEmail(userEmail)
                .orElseGet(() -> {

                    AppUser newUser = new AppUser();

                    newUser.email = userEmail;
                    newUser.name = userName;
                    newUser.role = "PATIENT";

                    // Mot de passe aléatoire
                    newUser.password = passwordEncoder.encode(
                            UUID.randomUUID().toString());

                    return userRepository.save(newUser);
                });

        // Conservation des autorités Google
        Set<org.springframework.security.core.GrantedAuthority> authorities = new HashSet<>(
                googleUser.getAuthorities());

        // Ajout du rôle provenant de PostgreSQL
        String role = user.role;

        if (role == null || role.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_user_role"),
                    "Le compte ne possède aucun rôle");
        }

        if (role.startsWith("ROLE_")) {
            role = role.substring(5);
        }

        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

        // Utiliser l'email comme nom du principal
        // pour conserver la compatibilité avec Principal.getName().
        return new DefaultOidcUser(
                authorities,
                googleUser.getIdToken(),
                googleUser.getUserInfo(),
                "email");
    }
}
