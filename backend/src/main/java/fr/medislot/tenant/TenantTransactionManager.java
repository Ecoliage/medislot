package fr.medislot.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.hibernate.Session;
import org.springframework.orm.jpa.JpaDialect;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;

@Component("transactionManager")
public class TenantTransactionManager extends JpaTransactionManager {
  static final String BIND_CLINIC = "select set_config('app.current_clinic', ?, true)";

  private final JpaDialect clinicBindingDialect = new ClinicBindingDialect();

  public TenantTransactionManager(EntityManagerFactory entityManagerFactory) {
    super(entityManagerFactory);
  }

  @Override
  public JpaDialect getJpaDialect() {
    return clinicBindingDialect;
  }

  static void bindClinic(Connection connection) throws SQLException {
    Long clinicId = TenantContext.get();
    try (PreparedStatement statement = connection.prepareStatement(BIND_CLINIC)) {
      statement.setString(1, clinicId == null ? "" : String.valueOf(clinicId));
      statement.execute();
    }
  }

  private static final class ClinicBindingDialect extends HibernateJpaDialect {
    @Override
    public Object beginTransaction(EntityManager entityManager, TransactionDefinition definition)
        throws SQLException {
      Object transactionData = super.beginTransaction(entityManager, definition);
      try {
        entityManager.unwrap(Session.class).doWork(TenantTransactionManager::bindClinic);
      } catch (RuntimeException e) {
        cleanupTransaction(transactionData);
        throw e;
      }
      return transactionData;
    }
  }
}
