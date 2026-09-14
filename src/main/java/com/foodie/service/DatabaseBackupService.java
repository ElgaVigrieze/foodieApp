package com.foodie.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;
import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Backs up the H2 database every 4 hours using H2's SCRIPT command.
 * This works without file locking issues since it goes through JDBC.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DatabaseBackupService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    private final DataSource dataSource;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    /**
     * Runs every 4 hours (first backup 1 minute after startup).
     */
    @Scheduled(fixedRate = 4 * 60 * 60 * 1000, initialDelay = 60_000)
    public void backupDatabase() {
        // H2-only backup - skip on PostgreSQL
        if (datasourceUrl.contains("postgresql") || datasourceUrl.contains("neon")) {
            log.debug("Skipping H2 backup - running on PostgreSQL");
            return;
        }
        try {
            Path backupDir = Path.of("./data/backups");
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            Path backupFile = backupDir.resolve("foodie-backup_" + timestamp + ".zip");

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("BACKUP TO '" + backupFile.toAbsolutePath().toString().replace("\\", "/") + "'");
            }

            log.info("Database backed up to: {}", backupFile);
            cleanOldBackups(backupDir, 10);

        } catch (Exception e) {
            log.error("Failed to backup database: {}", e.getMessage());
        }
    }

    private void cleanOldBackups(Path backupDir, int keepCount) throws IOException {
        var backups = Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith("foodie-backup_"))
                .sorted()
                .toList();

        if (backups.size() > keepCount) {
            for (int i = 0; i < backups.size() - keepCount; i++) {
                Files.delete(backups.get(i));
                log.debug("Deleted old backup: {}", backups.get(i));
            }
        }
    }
}
