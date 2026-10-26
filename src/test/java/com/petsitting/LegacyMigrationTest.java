package com.petsitting;

import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.assertj.core.api.Assertions.*;

class LegacyMigrationTest {
    @Test void originalDatabaseCanUpgradeWithoutLosingAccountsAndOrders() throws Exception {
        String url="jdbc:h2:mem:legacy;DB_CLOSE_DELAY=-1";
        try(var connection=DriverManager.getConnection(url,"sa","")) {
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/h2/V1__initial.sql"));
            try(var sql=connection.createStatement()){
                sql.executeUpdate("INSERT INTO accounts VALUES (1,'OWNER'),(2,'SITTER')");
                sql.executeUpdate("INSERT INTO pets VALUES ('old-pet',1,'团子','换水')");
                sql.executeUpdate("INSERT INTO slots VALUES ('old-slot',2,CURRENT_TIMESTAMP,DATEADD('HOUR',1,CURRENT_TIMESTAMP),5000,TRUE)");
                sql.executeUpdate("INSERT INTO orders VALUES ('old-order',1,'old-pet','old-slot','ACCEPTED','old-key',5000)");
            }
        }
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration/h2","classpath:db/migration/common").baselineOnMigrate(true).baselineVersion("0").load().migrate();
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement();var rows=statement.executeQuery("SELECT workflow_state,price_cents,pet_id FROM orders WHERE id='old-order'")){
            assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("ACCEPTED");assertThat(rows.getInt(2)).isEqualTo(5000);assertThat(rows.getString(3)).isEqualTo("old-pet");
        }
    }
}
