package com.petsitting.booking;

import com.petsitting.directory.DirectoryService;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class FeedbackService {
    private final JdbcTemplate db;
    private final DirectoryService directory;
    private final BookingRepository repository;
    private String id() { return UUID.randomUUID().toString(); }
    private Map<String,Object> one(String sql,Object... args) { return repository.one(sql,args); }
    private List<Map<String,Object>> list(String sql,Object... args) { return repository.list(sql,args); }
    private void role(long actor,String expected) { directory.role(actor,expected); }
    private void require(boolean condition,String message) { BookingRepository.require(condition,message); }
    private final OrderService orders;
    private Map<String,Object> detail(long actor,String id) { return orders.detail(actor,id); }
    public FeedbackService(JdbcTemplate db,DirectoryService directory,BookingRepository repository,OrderService orders) { this.db=db; this.directory=directory; this.repository=repository;this.orders=orders; }
    @Transactional public Map<String,Object> review(long actor,String orderId,int rating,String content) {
        role(actor,"OWNER"); var order=detail(actor,orderId);
        require(((Number)order.get("OWNER_ID")).longValue()==actor&&"COMPLETED".equals(order.get("STATUS")),"订单完成后主人才能评价");
        one("SELECT * FROM orders WHERE id=? FOR UPDATE",orderId);
        var previous=list("SELECT * FROM reviews WHERE order_id=?",orderId);
        if(!previous.isEmpty()) throw new ResponseStatusException(CONFLICT,"该订单已评价");
        String review=id(); db.update("INSERT INTO reviews VALUES (?,?,?,?,?,?)",review,orderId,actor,rating,content,OffsetDateTime.now());
        return one("SELECT * FROM reviews WHERE id=?",review);
    }
    public List<Map<String,Object>> reviews(long actor,String orderId) { detail(actor,orderId); return list("SELECT * FROM reviews WHERE order_id=?",orderId); }
    @Transactional public Map<String,Object> complain(long actor,String orderId,String content) {
        detail(actor,orderId); String complaint=id(); db.update("INSERT INTO complaints(id,order_id,reporter_id,content,created_at) VALUES (?,?,?,?,?)",complaint,orderId,actor,content,OffsetDateTime.now()); return one("SELECT * FROM complaints WHERE id=?",complaint);
    }
    public List<Map<String,Object>> complaints(long actor) {
        var account=one("SELECT role FROM accounts WHERE id=?",actor);
        return "ADMIN".equals(account.get("ROLE"))?list("SELECT * FROM complaints ORDER BY created_at DESC"):list("SELECT * FROM complaints WHERE reporter_id=? ORDER BY created_at DESC",actor);
    }
    @Transactional public Map<String,Object> resolve(long actor,String complaint,String resolution) {
        role(actor,"ADMIN"); var row=one("SELECT * FROM complaints WHERE id=? FOR UPDATE",complaint);
        require("OPEN".equals(row.get("STATUS")),"投诉已处理");
        db.update("UPDATE complaints SET status='RESOLVED',resolution=? WHERE id=?",resolution,complaint); return one("SELECT * FROM complaints WHERE id=?",complaint);
    }
}
