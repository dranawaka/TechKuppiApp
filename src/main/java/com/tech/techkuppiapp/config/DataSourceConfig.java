package com.tech.techkuppiapp.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Ensures the PostgreSQL database exists before creating the DataSource.
 * When the JDBC URL points to PostgreSQL, connects to the default "postgres" database first,
 * creates the target database if it does not exist, then returns the normal DataSource.
 */
@Configuration
@ConditionalOnProperty(name = "spring.datasource.url")
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);
    private static final String POSTGRESQL_PREFIX = "jdbc:postgresql://";

    @Bean
    public DataSource dataSource(Environment env) {
        String url = env.getProperty("spring.datasource.url");
        boolean createIfNotExists = env.getProperty("spring.datasource.create-database-if-not-exists", Boolean.class, true);
        if (createIfNotExists && url != null && url.startsWith(POSTGRESQL_PREFIX)) {
            ensurePostgresDatabaseExists(env, url);
        }

        return createDataSource(env);
    }

    private void ensurePostgresDatabaseExists(Environment env, String url) {
        String database = parseDatabaseName(url);
        if (database == null || database.isEmpty() || "postgres".equalsIgnoreCase(database) || "template1".equalsIgnoreCase(database)) {
            return;
        }

        String bootstrapUrl = buildBootstrapUrl(url);
        String username = env.getProperty("spring.datasource.username", "postgres");
        String password = env.getProperty("spring.datasource.password", "");

        String safeDbName = sanitizeIdentifier(database);
        try (Connection conn = DriverManager.getConnection(bootstrapUrl, username, password != null ? password : "")) {
            conn.setAutoCommit(true);
            boolean exists;
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
                ps.setString(1, safeDbName);
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }
            if (!exists) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("CREATE DATABASE " + safeDbName);
                }
                log.info("Created PostgreSQL database: {}", safeDbName);
            } else {
                log.debug("PostgreSQL database already exists: {}", safeDbName);
            }
        } catch (Exception e) {
            log.warn("Could not ensure database exists (application may still start if DB already exists): {}", e.getMessage());
        }
    }

    private static String parseDatabaseName(String url) {
        if (url == null || !url.startsWith(POSTGRESQL_PREFIX)) return null;
        int start = POSTGRESQL_PREFIX.length();
        int slash = url.indexOf('/', start);
        if (slash < 0) return null;
        String afterSlash = url.substring(slash + 1);
        int q = afterSlash.indexOf('?');
        return q >= 0 ? afterSlash.substring(0, q).trim() : afterSlash.trim();
    }

    private static String buildBootstrapUrl(String url) {
        if (url == null || !url.startsWith(POSTGRESQL_PREFIX)) return url;
        int start = POSTGRESQL_PREFIX.length();
        int slash = url.indexOf('/', start);
        if (slash < 0) return url;
        String params = "";
        int q = url.indexOf('?', slash);
        if (q > 0) params = url.substring(q);
        return url.substring(0, slash + 1) + "postgres" + params;
    }

    /** Sanitize for use as PostgreSQL identifier (alphanumeric and underscore only; no SQL injection). */
    private static String sanitizeIdentifier(String name) {
        if (name == null) return "";
        String s = name.trim();
        if (s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') sb.append(c);
        }
        return sb.length() > 0 ? sb.toString() : "techkuppi";
    }

    private static DataSource createDataSource(Environment env) {
        DataSourceProperties props = new DataSourceProperties();
        props.setUrl(env.getProperty("spring.datasource.url"));
        props.setUsername(env.getProperty("spring.datasource.username"));
        props.setPassword(env.getProperty("spring.datasource.password"));
        props.setDriverClassName(env.getProperty("spring.datasource.driver-class-name"));
        return props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
}
