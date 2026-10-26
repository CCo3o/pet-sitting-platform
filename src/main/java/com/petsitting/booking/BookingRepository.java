package com.petsitting.booking;

import com.petsitting.directory.DirectoryService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Repository
public class BookingRepository {
    private final JdbcTemplate db;
    public BookingRepository(JdbcTemplate db) { this.db = db; }
    public Map<String,Object> one(String sql,Object... args) {
        var rows=db.queryForList(sql,args);
        if(rows.isEmpty()) throw new ResponseStatusException(NOT_FOUND,"资源不存在");
        return row(rows.getFirst());
    }
    public List<Map<String,Object>> list(String sql,Object... args) {
        return db.queryForList(sql,args).stream().map(this::row).toList();
    }
    private Map<String,Object> row(Map<String,Object> source) {
        var result=DirectoryService.upper(source);
        if(result.containsKey("WORKFLOW_STATE")) result.put("STATUS",result.get("WORKFLOW_STATE"));
        return result;
    }
    public static void require(boolean condition,String message) {
        if(!condition) throw new ResponseStatusException(CONFLICT,message);
    }
}
