package com.example.touristpermit;

import com.example.touristpermit.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStartupIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayMigrationsCreateAllTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains(
                "users", "tourist_profiles", "permit_areas", "permit_applications", "permit_documents",
                "permits", "verification_tokens", "permit_verifications", "refresh_tokens", "audit_logs");
    }
}
