package com.petsitting.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petsitting.BookingService;
import com.petsitting.directory.DirectoryService;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class AgentService {
    private final JdbcTemplate db;private final BookingService bookings;private final DirectoryService directory;
    private final DraftService drafts;private final AgentModel model;private final ObjectMapper json;
    private final AgentQuotaService quota;
    private final AgentTraceService traces;
    private final RoleAgentService roles;
    public AgentService(JdbcTemplate db,BookingService bookings,DirectoryService directory,DraftService drafts,AgentModel model,ObjectMapper json,AgentQuotaService quota,AgentTraceService traces,RoleAgentService roles){this.roles=roles;this.db=db;this.bookings=bookings;this.directory=directory;this.drafts=drafts;this.model=model;this.json=json;this.quota=quota;this.traces=traces;}
    public Map<String,Object> status(){return Map.of("configured",model.configured(),"provider","DeepSeek","toolContractVersion",2);}
    @org.springframework.transaction.annotation.Transactional public Map<String,Object> session(long actor){
        roles.role(actor);
        db.queryForObject("SELECT id FROM accounts WHERE id=? FOR UPDATE",Long.class,actor);
        bookings.require(db.queryForObject("SELECT COUNT(*) FROM agent_sessions WHERE owner_id=?",Integer.class,actor)<50,"对话数量达到上限，请使用已有对话");
        String id=UUID.randomUUID().toString();db.update("INSERT INTO agent_sessions(id,owner_id,history,updated_at) VALUES (?,?,'[]',?)",id,actor,OffsetDateTime.now());return get(actor,id);
    }
    public List<Map<String,Object>> sessions(long actor){roles.role(actor);return bookings.list("SELECT id,updated_at FROM agent_sessions WHERE owner_id=? ORDER BY updated_at DESC",actor);}
    public Map<String,Object> get(long actor,String id){roles.role(actor);var row=bookings.one("SELECT id,history,updated_at FROM agent_sessions WHERE id=? AND owner_id=?",id,actor);try{row.put("HISTORY",json.readValue(row.get("HISTORY").toString(),new TypeReference<List<Map<String,String>>>(){}));}catch(Exception ex){throw new IllegalStateException("对话记录解析失败",ex);}return row;}
    public Map<String,Object> chat(long actor,String id,String message){
        roles.role(actor);get(actor,id);
        if(!model.configured())throw new ResponseStatusException(SERVICE_UNAVAILABLE,"预约助手尚未启用，请使用普通预约或联系管理员");
        int claimed=db.update("UPDATE agent_sessions SET busy_until=? WHERE id=? AND owner_id=? AND (busy_until IS NULL OR busy_until<?)",OffsetDateTime.now().plusMinutes(5),id,actor,OffsetDateTime.now());
        bookings.require(claimed==1,"上一条消息仍在处理，请稍后再发送");
        long start=System.nanoTime();String outcome="FAILED",errorCode="";long input=0,output=0;
        String run=traces.begin(actor,id);
        try {
            @SuppressWarnings("unchecked") var history=new ArrayList<>((List<Map<String,String>>)get(actor,id).get("HISTORY"));
            bookings.require(history.size()<40,"本轮对话已达上限，请新建对话");
            quota.reserve(actor);
            history.add(Map.of("role","USER","content",message));
            var tools=new AgentTools(actor,id,directory,bookings,drafts,traces,run);
            String role=roles.role(actor);
            if(!role.equals("OWNER"))tools.withRole(role,new RoleAgentTools(actor,id,run,role,roles,traces));
            var reply=model.reply(history,tools);input=reply.inputTokens();output=reply.outputTokens();
            if(reply.content()==null||reply.content().isBlank())throw new IllegalStateException("模型返回空内容");
            history.add(Map.of("role","ASSISTANT","content",reply.content()));
            db.update("UPDATE agent_sessions SET history=?,updated_at=? WHERE id=?",json.writeValueAsString(history),OffsetDateTime.now(),id);
            outcome=tools.draft()==null?"REPLIED":"DRAFTED";
            var result=new LinkedHashMap<String,Object>();result.put("runId",run);result.put("reply",reply.content());result.put("draft",tools.draft());result.put("durationMs",(System.nanoTime()-start)/1_000_000);return result;
        } catch(ResponseStatusException ex){errorCode="BUSINESS_"+ex.getStatusCode().value();throw ex;}
        catch(Exception ex){errorCode="PROVIDER_OR_PROTOCOL_ERROR";throw new ResponseStatusException(BAD_GATEWAY,"预约助手暂时未能完成请求，请稍后重试；尚未创建订单");}
        finally {
            db.update("UPDATE agent_sessions SET busy_until=NULL WHERE id=? AND owner_id=?",id,actor);
            traces.finish(run,outcome,(System.nanoTime()-start)/1_000_000,input,output,errorCode);
        }
    }
    public Map<String,Object> metrics(long actor){directory.role(actor,"ADMIN");return bookings.one("SELECT COUNT(*) AS calls,COALESCE(SUM(CASE WHEN outcome='FAILED' THEN 1 ELSE 0 END),0) AS failures,COALESCE(SUM(CASE WHEN outcome='DRAFTED' THEN 1 ELSE 0 END),0) AS drafts,COALESCE(AVG(duration_ms),0) AS avg_duration_ms,COALESCE(SUM(input_tokens),0) AS input_tokens,COALESCE(SUM(output_tokens),0) AS output_tokens FROM agent_runs");}
}
