package com.petsitting.agent;

import com.petsitting.BookingService;
import com.petsitting.directory.DirectoryService;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All authority comes from the authenticated account, never from model arguments. */
@Service
public class RoleAgentService {
    private final BookingService bookings;
    private final DirectoryService directory;
    private final JdbcTemplate db;
    public RoleAgentService(BookingService bookings,DirectoryService directory,JdbcTemplate db){this.bookings=bookings;this.directory=directory;this.db=db;}
    public String role(long actor){
        String role=(String)bookings.one("SELECT role FROM accounts WHERE id=?",actor).get("ROLE");
        directory.role(actor,role);return role;
    }
    public Map<String,Object> overview(long actor){
        String role=role(actor);
        if(role.equals("ADMIN"))return bookings.stats(actor);
        var orders=bookings.orders(actor);
        return Map.of("orders",orders.size(),"pending",orders.stream().filter(o->"PENDING".equals(o.get("STATUS"))).count(),
            "active",orders.stream().filter(o->Set.of("ACCEPTED","IN_PROGRESS","WAITING_CONFIRM").contains(o.get("STATUS"))).count());
    }
    public List<Map<String,Object>> orders(long actor){role(actor);return bookings.orders(actor).stream().limit(30).map(this::summary).toList();}
    private Map<String,Object> summary(Map<String,Object> row){
        var result=new LinkedHashMap<String,Object>();
        for(String key:List.of("ID","STATUS","PET_NAME","SITTER_NAME","STARTS_AT","ENDS_AT","PRICE_CENTS","PET_SNAPSHOT"))if(row.get(key)!=null)result.put(key,row.get(key));
        return result;
    }
    public Map<String,Object> order(long actor,String id){role(actor);var result=summary(bookings.detail(actor,id));result.put("RECORDS",bookings.records(actor,id));return result;}
    public List<Map<String,Object>> complaints(long actor){directory.role(actor,"ADMIN");return bookings.complaints(actor).stream().filter(c->"OPEN".equals(c.get("STATUS"))).limit(30).toList();}
    public List<Map<String,Object>> profiles(long actor){directory.role(actor,"ADMIN");return directory.profiles(actor).stream().filter(p->"PENDING".equals(p.get("STATUS"))).limit(30).map(p->{var r=new LinkedHashMap<String,Object>();for(String k:List.of("ACCOUNT_ID","BIO","STATUS","PRICE_CENTS"))if(p.get(k)!=null)r.put(k,p.get(k));return (Map<String,Object>)r;}).toList();}
    public Map<String,Object> complaint(long actor,String id){directory.role(actor,"ADMIN");var row=bookings.one("SELECT * FROM complaints WHERE id=?",id);var result=new LinkedHashMap<>(row);result.put("ORDER",order(actor,(String)row.get("ORDER_ID")));return result;}
    public void validate(long actor,String kind,String target){
        if("FULFILLMENT".equals(kind)){directory.role(actor,"SITTER");var order=bookings.detail(actor,target);bookings.require(((Number)order.get("SITTER_ID")).longValue()==actor&&"IN_PROGRESS".equals(order.get("STATUS")),"仅能为本人服务中的订单生成记录");}
        else {bookings.require("RESOLUTION".equals(kind),"未知草稿类型");directory.role(actor,"ADMIN");bookings.require("OPEN".equals(bookings.one("SELECT * FROM complaints WHERE id=?",target).get("STATUS")),"投诉已经处理");}
    }
    public Map<String,Object> draft(long actor,String session,String kind,String target,String content){
        validate(actor,kind,target);bookings.one("SELECT id FROM agent_sessions WHERE id=? AND owner_id=?",session,actor);
        bookings.require(content!=null&&!content.isBlank()&&content.length()<=1000,"草稿需包含1至1000字");
        String id=UUID.randomUUID().toString();db.update("INSERT INTO agent_action_drafts(id,actor_id,session_id,kind,target_id,content,expires_at) VALUES (?,?,?,?,?,?,?)",id,actor,session,kind,target,content,OffsetDateTime.now().plusMinutes(15));
        return bookings.one("SELECT * FROM agent_action_drafts WHERE id=?",id);
    }
    @Transactional public Map<String,Object> confirm(long actor,String id,String content){
        role(actor);var draft=bookings.one("SELECT * FROM agent_action_drafts WHERE id=? AND actor_id=? FOR UPDATE",id,actor);
        if(draft.get("RESULT_ID")!=null)return Map.of("ID",draft.get("RESULT_ID"),"replayed",true);
        bookings.require(BookingService.time(draft.get("EXPIRES_AT")).isAfter(OffsetDateTime.now()),"草稿已过期，请重新生成");
        bookings.require(content!=null&&!content.isBlank()&&content.length()<=1000,"请核实并填写1至1000字内容");
        String kind=(String)draft.get("KIND"),target=(String)draft.get("TARGET_ID");validate(actor,kind,target);
        var result="FULFILLMENT".equals(kind)?bookings.record(actor,target,content,List.of()):bookings.resolve(actor,target,content);
        db.update("UPDATE agent_action_drafts SET result_id=?,content=? WHERE id=?",result.get("ID"),content,id);return result;
    }
}
