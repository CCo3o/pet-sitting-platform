package com.petsitting.booking;

import com.petsitting.directory.DirectoryService;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminStatsService {
    private final JdbcTemplate db;
    private final DirectoryService directory;
    public AdminStatsService(JdbcTemplate db,DirectoryService directory) { this.db=db;this.directory=directory; }
    public Map<String,Object> stats(long actor) {
        directory.role(actor,"ADMIN");
        return Map.of("users",count("SELECT COUNT(*) FROM credentials"),
            "orders",count("SELECT COUNT(*) FROM orders"),
            "completed",count("SELECT COUNT(*) FROM orders WHERE workflow_state='COMPLETED'"),
            "pendingSitters",count("SELECT COUNT(*) FROM sitter_profiles WHERE status='PENDING'"),
            "openComplaints",count("SELECT COUNT(*) FROM complaints WHERE status='OPEN'"));
    }
    private long count(String sql) { return db.queryForObject(sql,Long.class); }
}
