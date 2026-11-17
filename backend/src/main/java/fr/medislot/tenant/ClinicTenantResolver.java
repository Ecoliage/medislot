package fr.medislot.tenant;

import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

@Component
public class ClinicTenantResolver
    implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {
  public static final Long NO_CLINIC = 0L;

  @Override
  public Long resolveCurrentTenantIdentifier() {
    Long clinicId = TenantContext.get();
    return clinicId == null ? NO_CLINIC : clinicId;
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return false;
  }

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
  }
}
