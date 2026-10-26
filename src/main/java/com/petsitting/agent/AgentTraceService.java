package com.petsitting.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petsitting.BookingService;
import com.petsitting.directory.DirectoryService;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class AgentTraceService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final DirectoryService directory;
    private final BookingService bookings;
    public AgentTraceService(JdbcTemplate db,ObjectMapper json,DirectoryService directory,BookingService bookings) {
        this.db=db;this.json=json;this.directory=directory;this.bookings=bookings;
    }
    public String begin(long actor,String session) {
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO agent_runs(id,owner_id,session_id,outcome,duration_ms,input_tokens,output_tokens,created_at) VALUES (?,?,?,'RUNNING',0,0,0,?)",id,actor,session,OffsetDateTime.now());
        return id;
    }
    public void finish(String run,String outcome,long duration,long input,long output,String error) {
        db.update("UPDATE agent_runs SET outcome=?,duration_ms=?,input_tokens=?,output_tokens=?,error_code=? WHERE id=?",outcome,duration,input,output,error,run);
    }
    public <T> T execute(String run,int sequence,String name,Map<String,Object> arguments,Supplier<T> action) {
        long start=System.nanoTime();String outcome="SUCCEEDED",error="";Object result=Map.of();
        try { T value=action.get();result=value;return value; }
        catch(ResponseStatusException ex) { outcome="FAILED";error="BUSINESS_"+ex.getStatusCode().value();throw ex; }
        catch(RuntimeException ex) { outcome="FAILED";error="EXECUTION_ERROR";throw ex; }
        finally {
            db.update("INSERT INTO agent_tool_traces(id,run_id,sequence_no,tool_name,arguments_json,result_json,outcome,error_code,duration_ms,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(),run,sequence,name,encode(arguments),encode(sanitize(result)),outcome,error,(System.nanoTime()-start)/1_000_000,OffsetDateTime.now());
        }
    }
    // Retain IDs, prices and times needed for diagnosis; exclude contacts, locations and pet notes.
    private Object sanitize(Object value) {
        Object normalized=json.convertValue(value,Object.class);
        if(normalized instanceof List<?> items) return items.stream().limit(20).map(this::sanitize).toList();
        if(normalized instanceof Map<?,?> values) {
            var safe=new LinkedHashMap<String,Object>();
            values.forEach((key,item)->{if(Set.of("ID","NAME","SPECIES","SITTER_NAME","PET_NAME","STARTS_AT","ENDS_AT","PRICE_CENTS","STATUS","KIND","TARGET_ID","ACCOUNT_ID").contains(key.toString()))safe.put(key.toString(),item);});
            return safe;
        }
        return Map.of("type",normalized==null?"null":normalized.getClass().getSimpleName());
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch(Exception ex) { throw new IllegalStateException("追踪序列化失败",ex); }
    }
    public String previousContext(long actor,String session) {
        // These are actual prior tool results, not prose claimed by the model.
        var rows=new ArrayList<>(bookings.list("SELECT t.tool_name,t.result_json FROM agent_tool_traces t JOIN agent_runs r ON r.id=t.run_id WHERE r.owner_id=? AND r.session_id=? AND t.outcome='SUCCEEDED' AND r.outcome<>'RUNNING' ORDER BY r.created_at DESC,t.sequence_no DESC LIMIT 8",actor,session));
        String encoded=encode(rows);
        while(encoded.length()>20000&&!rows.isEmpty()) { rows.removeLast();encoded=encode(rows); }
        return encoded;
    }
    public List<Map<String,Object>> sessionRuns(long actor,String session) {
        var account=bookings.one("SELECT role FROM accounts WHERE id=?",actor);
        directory.role(actor,(String)account.get("ROLE"));
        bookings.one("SELECT id FROM agent_sessions WHERE id=? AND owner_id=?",session,actor);
        return bookings.list("SELECT * FROM agent_runs WHERE session_id=? ORDER BY created_at DESC LIMIT 50",session);
    }
    public List<Map<String,Object>> recent(long actor) {
        directory.role(actor,"ADMIN");
        return bookings.list("SELECT * FROM agent_runs ORDER BY created_at DESC LIMIT 100");
    }
    public Map<String,Object> detail(long actor,String run) {
        var user=bookings.one("SELECT role,enabled FROM accounts WHERE id=?",actor);
        if(!Boolean.TRUE.equals(user.get("ENABLED")))throw new ResponseStatusException(UNAUTHORIZED,"账户已停用");
        var row=bookings.one("SELECT * FROM agent_runs WHERE id=?",run);
        if(!"ADMIN".equals(user.get("ROLE"))&&((Number)row.get("OWNER_ID")).longValue()!=actor)
            throw new ResponseStatusException(FORBIDDEN,"无权查看此调用");
        var traces=bookings.list("SELECT * FROM agent_tool_traces WHERE run_id=? ORDER BY sequence_no",run).stream().map(t->new AgentDtos.ToolTrace(
            (String)t.get("ID"),((Number)t.get("SEQUENCE_NO")).intValue(),(String)t.get("TOOL_NAME"),
            (String)t.get("ARGUMENTS_JSON"),(String)t.get("RESULT_JSON"),(String)t.get("OUTCOME"),
            (String)t.get("ERROR_CODE"),((Number)t.get("DURATION_MS")).longValue(),BookingService.time(t.get("CREATED_AT")))).toList();
        row.put("TOOLS",traces);return row;
    }
}
