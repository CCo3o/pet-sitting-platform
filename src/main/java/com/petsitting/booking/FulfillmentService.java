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
public class FulfillmentService {
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
    public FulfillmentService(JdbcTemplate db,DirectoryService directory,BookingRepository repository,OrderService orders) { this.db=db; this.directory=directory; this.repository=repository;this.orders=orders; }
    @Transactional public Map<String,Object> record(long actor,String orderId,String note,List<String> photos) {
        role(actor,"SITTER"); var initial=detail(actor,orderId);
        var order=one("SELECT * FROM orders WHERE id=? FOR UPDATE",orderId);
        require("IN_PROGRESS".equals(order.get("STATUS")),"只能在服务中添加喂养记录");
        if(((Number)initial.get("SITTER_ID")).longValue()!=actor) throw new ResponseStatusException(FORBIDDEN,"只能记录自己的服务");
        for(String photo:photos) require(db.queryForObject("SELECT COUNT(*) FROM media_files WHERE id=? AND owner_id=? AND order_id=?",Integer.class,photo,actor,orderId)==1,"照片不属于当前服务");
        String record=id();
        try {
            String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(photos);
            db.update("INSERT INTO service_records(id,order_id,note,created_at,photos) VALUES (?,?,?,?,?)",record,orderId,note,OffsetDateTime.now(),json);
        } catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalArgumentException(ex); }
        return one("SELECT * FROM service_records WHERE id=?",record);
    }
    public List<Map<String,Object>> records(long actor,String orderId) { detail(actor,orderId); return list("SELECT * FROM service_records WHERE order_id=? ORDER BY created_at",orderId); }
}
