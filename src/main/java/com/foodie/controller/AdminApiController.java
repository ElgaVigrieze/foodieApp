package com.foodie.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.Statement;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final DataSource dataSource;

    @PostMapping("/backup")
    public String backup() throws Exception {
        Path backupDir = Path.of("./data/backups");
        Files.createDirectories(backupDir);
        Path backupFile = backupDir.resolve("foodie-db_manual-backup.zip").toAbsolutePath();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("BACKUP TO '" + backupFile.toString().replace("\\", "/") + "'");
        }

        return "Backup saved to: " + backupFile;
    }
}
