package fr.medislot;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import fr.medislot.application.SmsGateway;
import fr.medislot.repository.ClinicRepository;
import fr.medislot.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
abstract class PostgresTestBase {
  static final String CLINIC_A = "cabinet-a";
  static final String CLINIC_B = "cabinet-b";
  static final String HEADER = "X-Clinic";
  static final String PATIENT = "patient@medislot.test";
  static final String OTHER = "autre@medislot.test";
  static final String APP_ROLE = "medislot_app";
  static final String APP_PASSWORD = "medislot-app-local-only";

  static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18");

  static {
    DB.start();
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DB::getJdbcUrl);
    registry.add("spring.flyway.user", DB::getUsername);
    registry.add("spring.flyway.password", DB::getPassword);
    registry.add("medislot.demo-seed", () -> "true");
  }

  @MockitoBean SmsGateway sms;
  @Autowired WebApplicationContext context;
  @Autowired ClinicRepository clinics;

  MockMvc mvc;

  @BeforeEach
  void buildMvc() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void clearClinic() {
    TenantContext.clear();
  }

  Long clinicId(String slug) {
    return clinics.findBySlug(slug).orElseThrow().id;
  }

  void enter(String slug) {
    TenantContext.set(clinicId(slug));
  }

  static RequestPostProcessor as(String email) {
    return SecurityMockMvcRequestPostProcessors.user(email).roles("PATIENT");
  }
}
