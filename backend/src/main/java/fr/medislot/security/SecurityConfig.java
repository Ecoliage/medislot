package fr.medislot.security;

import fr.medislot.repository.UserRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private String frontendUrl = "http://localhost:8088";
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(UserRepository users) {
        return email -> users.findByEmail(
                email.toLowerCase(java.util.Locale.ROOT))
                .map(u -> User.withUsername(u.email)
                        .password(u.password)
                        .roles(u.role.startsWith("ROLE_")
                                ? u.role.substring(5)
                                : u.role)
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Identité inconnue"));
    }

    @Bean
    SecurityFilterChain filter(
            HttpSecurity http,
            UserService oidcUserService) throws Exception {

        http.authorizeHttpRequests(a -> a
                .dispatcherTypeMatchers(
                        DispatcherType.ASYNC,
                        DispatcherType.ERROR)
                .permitAll()

                .requestMatchers(
                        "/api/csrf",
                        "/api/register",
                        "/api/login",
                        "/api/professionals/**",
                        "/api/events",
                        "/actuator/health",
                        "/api/praticiens/**",
                        "/api/compte/**")
                .permitAll()

                .requestMatchers("/api/debug/**")
                .hasRole("ADMIN")

                .requestMatchers("/api/**")
                .authenticated()

                .anyRequest()
                .permitAll())

                .requestCache(c -> c.disable())

                // Authentification classique
                .formLogin(f -> f
                        .loginProcessingUrl("/api/login")

                        .successHandler((req, res, auth) -> res.setStatus(204))

                        .failureHandler((req, res, ex) -> {
                            res.setStatus(401);
                            res.setContentType("application/problem+json");
                            res.getWriter().write(
                                    "{\"title\":\"Identifiants incorrects\",\"status\":401}");
                        }))

                // Authentification Google
                .oauth2Login(o -> o

                        .userInfoEndpoint(u -> u.oidcUserService(oidcUserService))
                        .successHandler((req, res, auth) -> res.sendRedirect(frontendUrl))
                        .failureHandler((req, res, ex) -> {
                            log.error("Échec de l'authentification Google", ex);
                            res.sendRedirect(frontendUrl + "/login?error=google");
                        }))

                // Déconnexion
                .logout(l -> l
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))

                // Gestion des erreurs
                .exceptionHandling(e -> e

                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(401);
                            res.setContentType("application/problem+json");
                            res.getWriter().write(
                                    "{\"title\":\"Connexion requise\",\"status\":401}");
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(403);
                            res.setContentType("application/problem+json");
                            res.getWriter().write(
                                    "{\"title\":\"Action refusée ou jeton CSRF expiré\",\"status\":403}");
                        }));

        return http.build();
    }
}
