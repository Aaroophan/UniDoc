package com.unidoc.demo;

import com.unidoc.config.DemoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/** Non-HTTP operation. File storage is never injected or touched by this runner. */
@Service
public class DemoResetService {
    private static final Logger log = LoggerFactory.getLogger(DemoResetService.class);
    private static final String[] TABLE_ORDER = {
            "notifications", "request_status_history", "issued_documents", "request_attachments",
            "document_requests", "student_profiles", "document_types", "users"
    };

    private final DemoProperties properties;
    private final JdbcTemplate jdbc;

    public DemoResetService(DemoProperties properties, JdbcTemplate jdbc) {
        this.properties = properties;
        this.jdbc = jdbc;
    }

    /** Checks all safeguards inside the same transaction/connection used to delete. */
    @Transactional
    public Map<String, Integer> resetDatabase() {
        DemoResetSafety.requireGuards(properties, System.getenv("SERVER_PORT"));
        log.warn("Guarded demo reset requested: only database rows will be deleted. Uploads are preserved.");
        return jdbc.execute((ConnectionCallback<Map<String, Integer>>) connection -> {
            String currentDatabase;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
                if (!result.next()) throw new SQLException("No current database selected.");
                currentDatabase = result.getString(1);
            }
            DemoResetSafety.requireLocalUnidoc(
                    connection.getMetaData().getURL(), connection.getCatalog(), currentDatabase);
            Map<String, Integer> counts = new LinkedHashMap<>();
            try (Statement statement = connection.createStatement()) {
                for (String table : TABLE_ORDER) {
                    // All table names are internal constants; FK checks stay enabled.
                    int rows = statement.executeUpdate("DELETE FROM `" + table + "`");
                    counts.put(table, rows);
                }
            }
            log.warn("Guarded demo reset DELETE statements completed within transaction schema=unidoc tableCount={} removedRows={}",
                    counts.size(), counts.values().stream().mapToInt(Integer::intValue).sum());
            return counts;
        });
    }
}
