package fr.medislot;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

class RlsDirectSqlTest extends PostgresTestBase {
  private static final List<String> OWN_TABLES =
      List.of("professional", "slot", "appointment", "audit_event", "notification");

  @Test
  void withoutClinicNoRowIsVisible() throws SQLException {
    try (Connection connection = asApplication()) {
      for (String table : OWN_TABLES) {
        assertEquals(0, count(connection, table), table);
      }
      connection.setAutoCommit(false);
      bind(connection, "");
      for (String table : OWN_TABLES) {
        assertEquals(0, count(connection, table), table + " avec une variable vide");
      }
    }
  }

  @Test
  void withAClinicOnlyItsRowsAreVisible() throws SQLException {
    try (Connection connection = asApplication()) {
      long clinicA = clinic(connection, CLINIC_A);
      long clinicB = clinic(connection, CLINIC_B);
      connection.setAutoCommit(false);

      bind(connection, String.valueOf(clinicA));
      assertEquals(4, count(connection, "professional"));
      assertEquals(List.of(clinicA), distinctClinics(connection, "slot"));
      assertEquals(List.of(clinicA), distinctClinics(connection, "appointment"));
      connection.commit();

      bind(connection, String.valueOf(clinicB));
      assertEquals(7, count(connection, "professional"));
      assertEquals(List.of(clinicB), distinctClinics(connection, "slot"));
      connection.commit();

      assertEquals(0, count(connection, "professional"), "la variable ne survit pas au commit");
    }
  }

  @Test
  void writingIntoAnotherClinicIsRefused() throws SQLException {
    try (Connection connection = asApplication()) {
      long clinicA = clinic(connection, CLINIC_A);
      long clinicB = clinic(connection, CLINIC_B);
      connection.setAutoCommit(false);
      bind(connection, String.valueOf(clinicA));

      SQLException insert =
          assertThrows(
              SQLException.class,
              () ->
                  update(
                      connection,
                      "insert into professional (clinic_id, name, specialty, city, address, bio, fee)"
                          + " values (?, 'Dr Intrus', 'general', 'Lyon', '1 rue du Test', 'Fictif', 30)",
                      clinicB));
      assertTrue(insert.getMessage().contains("row-level security"), insert.getMessage());
      connection.rollback();

      bind(connection, String.valueOf(clinicA));
      SQLException move =
          assertThrows(
              SQLException.class,
              () -> update(connection, "update professional set clinic_id = ?", clinicB));
      assertTrue(move.getMessage().contains("row-level security"), move.getMessage());
      connection.rollback();

      bind(connection, String.valueOf(clinicA));
      assertEquals(0, update(connection, "delete from notification where clinic_id = ?", clinicB));
      connection.rollback();
    }
  }

  @Test
  void rlsIsEnabledAndForcedAndTheApplicationRoleCannotBypassIt() throws SQLException {
    try (Connection connection = asApplication();
        Statement statement = connection.createStatement()) {
      try (ResultSet tables =
          statement.executeQuery(
              "select relname, relrowsecurity, relforcerowsecurity from pg_class"
                  + " where relname in ('professional','slot','appointment','audit_event','notification')"
                  + " order by relname")) {
        int seen = 0;
        while (tables.next()) {
          seen++;
          assertTrue(tables.getBoolean("relrowsecurity"), tables.getString("relname"));
          assertTrue(tables.getBoolean("relforcerowsecurity"), tables.getString("relname"));
        }
        assertEquals(OWN_TABLES.size(), seen);
      }
      try (ResultSet role =
          statement.executeQuery(
              "select rolsuper, rolbypassrls from pg_roles where rolname = current_user")) {
        assertTrue(role.next());
        assertFalse(role.getBoolean("rolsuper"));
        assertFalse(role.getBoolean("rolbypassrls"));
      }
      try (ResultSet policies =
          statement.executeQuery(
              "select count(*) from pg_policies where policyname = 'clinic_isolation'"
                  + " and qual is not null and with_check is not null")) {
        assertTrue(policies.next());
        assertEquals(OWN_TABLES.size(), policies.getInt(1));
      }
    }
  }

  private static Connection asApplication() throws SQLException {
    return DriverManager.getConnection(DB.getJdbcUrl(), APP_ROLE, APP_PASSWORD);
  }

  private static void bind(Connection connection, String clinic) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("select set_config('app.current_clinic', ?, true)")) {
      statement.setString(1, clinic);
      statement.execute();
    }
  }

  private static long clinic(Connection connection, String slug) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("select id from clinic where slug = ?")) {
      statement.setString(1, slug);
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next());
        return result.getLong(1);
      }
    }
  }

  private static long count(Connection connection, String table) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("select count(*) from " + table)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static List<Long> distinctClinics(Connection connection, String table)
      throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery("select distinct clinic_id from " + table + " order by 1")) {
      List<Long> found = new java.util.ArrayList<>();
      while (result.next()) found.add(result.getLong(1));
      return found;
    }
  }

  private static int update(Connection connection, String sql, long clinic) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, clinic);
      return statement.executeUpdate();
    }
  }
}
