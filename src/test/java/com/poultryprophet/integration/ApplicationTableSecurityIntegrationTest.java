package com.poultryprophet.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="POULTRY_DISPOSABLE_DB_URL", matches="jdbc:postgresql://127\\.0\\.0\\.1:25432/poultry_prophet_validation")
class ApplicationTableSecurityIntegrationTest {
    @Test void browserRolesCannotReadApplicationTablesAndOwnerStillWorks() {
        String url=System.getenv("POULTRY_DISPOSABLE_DB_URL");
        var admin=new JdbcTemplate(new DriverManagerDataSource(url,"poultry_validation","local-disposable-only"));
        String schema="security_check_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE SCHEMA "+schema);
        try {
            admin.execute("DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon NOLOGIN; END IF; IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated NOLOGIN; END IF; END $$");
            var ds=new DriverManagerDataSource(url+"?currentSchema="+schema,"poultry_validation","local-disposable-only");
            var jdbc=new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE app_user(id bigint PRIMARY KEY, secret text)");
            jdbc.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON app_user TO anon,authenticated");
            jdbc.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA "+schema+" GRANT ALL ON TABLES TO anon,authenticated");
            var migration=new ResourceDatabasePopulator(new ClassPathResource("db/reviewed-migration/V33__restrict_application_tables_to_backend.sql"));
            migration.setSeparator(org.springframework.jdbc.datasource.init.ScriptUtils.EOF_STATEMENT_SEPARATOR);
            migration.execute(ds); migration.execute(ds);
            for(String role:java.util.List.of("anon","authenticated"))
                assertThat(jdbc.queryForObject("SELECT has_table_privilege(?,?,'SELECT')",Boolean.class,role,schema+".app_user")).isFalse();
            assertThat(jdbc.queryForObject("SELECT relrowsecurity FROM pg_class WHERE oid=?::regclass",Boolean.class,schema+".app_user")).isTrue();
            jdbc.update("INSERT INTO app_user VALUES(1,'private')");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user",Long.class)).isEqualTo(1L);
            jdbc.execute("CREATE TABLE future_table(id bigint)");
            assertThat(jdbc.queryForObject("SELECT has_table_privilege('anon',?,'SELECT')",Boolean.class,schema+".future_table")).isFalse();
        } finally { admin.execute("DROP SCHEMA "+schema+" CASCADE"); }
    }
}
