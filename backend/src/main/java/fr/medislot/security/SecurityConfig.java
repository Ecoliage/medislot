package fr.medislot.security;

import fr.medislot.repository.ClinicRepository;
import fr.medislot.repository.MembershipRepository;
import fr.medislot.repository.UserRepository;
import fr.medislot.tenant.TenantFilter;
import jakarta.servlet.DispatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

  @Bean
  PasswordEncoder encoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService users(UserRepository users) {
    return email ->
        users
            .findByEmail(email.toLowerCase(java.util.Locale.ROOT))
            .map(u -> User.withUsername(u.email).password(u.password).roles(u.role).build())
            .orElseThrow(() -> new UsernameNotFoundException("Identité inconnue"));
  }

  @Bean
  @ConditionalOnExpression("!'${medislot.google.client-id:}'.isBlank()")
  ClientRegistrationRepository googleClient(
      @Value("${medislot.google.client-id}") String id,
      @Value("${medislot.google.client-secret}") String secret) {
    return new InMemoryClientRegistrationRepository(
        CommonOAuth2Provider.GOOGLE.getBuilder("google").clientId(id).clientSecret(secret).build());
  }

  @Bean
  SecurityFilterChain filter(
      HttpSecurity http,
      UserService googleUsers,
      ClinicRepository clinics,
      UserRepository accounts,
      MembershipRepository memberships,
      ObjectProvider<ClientRegistrationRepository> googleClient)
      throws Exception {
    LoginThrottle throttle = new LoginThrottle(java.time.Clock.systemUTC());
    http.addFilterBefore(throttle, UsernamePasswordAuthenticationFilter.class);
    http.addFilterAfter(
        new TenantFilter(clinics, accounts, memberships), AuthorizationFilter.class);
    http.authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/api/csrf",
                        "/api/register",
                        "/api/login",
                        "/api/login/google",
                        "/api/clinic",
                        "/api/professionals/**",
                        "/api/events",
                        "/actuator/health",
                        "/api/praticiens/**",
                        "/api/compte/**")
                    .permitAll()
                    .requestMatchers("/api/debug/**", "/api/platform/**", "/api/clinics", "/api/clinics/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .requestCache(c -> c.disable())
        .formLogin(
            f ->
                f.loginProcessingUrl("/api/login")
                    .successHandler(
                        (req, res, auth) -> {
                          throttle.succeeded(req);
                          res.setStatus(204);
                        })
                    .failureHandler(
                        (req, res, ex) -> {
                          throttle.failed(req);
                          res.setStatus(401);
                          res.setContentType("application/problem+json;charset=UTF-8");
                          res.getWriter()
                              .write("{\"title\":\"Identifiants incorrects\",\"status\":401}");
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/logout")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/problem+json;charset=UTF-8");
                          res.getWriter().write("{\"title\":\"Connexion requise\",\"status\":401}");
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/problem+json;charset=UTF-8");
                          res.getWriter()
                              .write(
                                  "{\"title\":\"Action refusée ou jeton CSRF expiré\",\"status\":403}");
                        }));
    if (googleClient.getIfAvailable() != null) {
      http.oauth2Login(
          o ->
              o.userInfoEndpoint(u -> u.oidcUserService(googleUsers))
                  .successHandler((req, res, auth) -> res.sendRedirect("/"))
                  .failureHandler(
                      (req, res, ex) -> {
                        log.warn("Connexion Google refusée : {}", ex.getMessage());
                        if (ex instanceof OAuth2AuthenticationException e
                            && UserService.LOCAL_ACCOUNT.equals(e.getError().getErrorCode())) {
                          res.sendRedirect("/?error=google&motif=compte-local");
                        } else {
                          res.sendRedirect("/?error=google");
                        }
                      }));
    }
    return http.build();
  }
}
