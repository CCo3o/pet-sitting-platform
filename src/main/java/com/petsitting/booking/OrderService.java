package com.petsitting.booking;

import com.petsitting.directory.DirectoryService;
import com.petsitting.BookingService;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class OrderService {
    private final JdbcTemplate db;
    private final DirectoryService directory;
    private final BookingRepository repository;
    private String id() { return UUID.randomUUID().toString(); }
    private Map<String,Object> one(String sql,Object... args) { return repository.one(sql,args); }
    private List<Map<String,Object>> list(String sql,Object... args) { return repository.list(sql,args); }
    private void role(long actor,String expected) { directory.role(actor,expected); }
    private void lockAccount(long actor) { one("SELECT id FROM accounts WHERE id=? FOR UPDATE",actor); }
    private void require(boolean condition,String message) { BookingRepository.require(condition,message); }
    public OrderService(JdbcTemplate db,DirectoryService directory,BookingRepository repository) { this.db=db; this.directory=directory; this.repository=repository; }
    private static final String ORDER_SELECT="SELECT o.*,s.sitter_id,s.starts_at,s.ends_at,s.region,c.display_name AS sitter_name FROM orders o JOIN slots s ON o.slot_id=s.id JOIN credentials c ON c.account_id=s.sitter_id ";
    @Transactional public Map<String,Object> book(long actor,String pet,String slot,String key) {
        var addresses=directory.addresses(actor);
        require(!addresses.isEmpty(),"请先添加上门地址");
        return book(actor,pet,slot,(String)addresses.getFirst().get("ID"),key);
    }
    @Transactional public Map<String,Object> book(long actor,String pet,String slot,String address,String key) {
        role(actor,"OWNER"); lockAccount(actor);
        var previous=list("SELECT * FROM orders WHERE owner_id=? AND request_key=?",actor,key);
        if(!previous.isEmpty()) {
            var order=previous.getFirst();
            require(pet.equals(order.get("PET_ID"))&&slot.equals(order.get("SLOT_ID"))&&address.equals(order.get("ADDRESS_ID")),"幂等键已用于不同预约");
            return order;
        }
        var catalog=one("SELECT * FROM service_catalog WHERE id='daily-care'");
        require(Boolean.TRUE.equals(catalog.get("ENABLED")),"当前服务项目已暂停预约");
        var profile=one("SELECT * FROM pets WHERE id=? FOR UPDATE",pet); directory.owned(profile,actor);
        var location=one("SELECT * FROM addresses WHERE id=? FOR UPDATE",address); directory.owned(location,actor);
        var initial=one("SELECT * FROM slots WHERE id=?",slot);
        lockAccount(((Number)initial.get("SITTER_ID")).longValue());
        var window=one("SELECT * FROM slots WHERE id=? FOR UPDATE",slot);
        var sitter=one("SELECT * FROM sitter_profiles WHERE account_id=?",window.get("SITTER_ID"));
        require(Boolean.TRUE.equals(one("SELECT enabled FROM accounts WHERE id=?",window.get("SITTER_ID")).get("ENABLED")),"喂养员账户不可用");
        require("APPROVED".equals(sitter.get("STATUS")),"喂养员当前未通过审核");

        require(!Boolean.TRUE.equals(window.get("WITHDRAWN"))&&!Boolean.TRUE.equals(window.get("BOOKED")),"时段已被预约或撤下");
        require(BookingService.time(window.get("STARTS_AT")).isAfter(OffsetDateTime.now()),"时段已过期");
        String order=id();
        String addressSnapshot=location.get("CONTACT")+" / "+location.get("PHONE")+" / "+location.get("REGION")+" "+location.get("DETAIL");
        String petSnapshot=profile.get("NAME")+" / "+profile.get("SPECIES")+" / "+profile.get("NOTES");
        db.update("INSERT INTO orders(id,owner_id,pet_id,slot_id,status,request_key,price_cents,workflow_state,address_id,address_snapshot,pet_snapshot) VALUES (?,?,?,?,'PENDING',?,?,'PENDING',?,?,?)",order,actor,pet,slot,key,window.get("PRICE_CENTS"),address,addressSnapshot,petSnapshot);
        db.update("UPDATE slots SET booked=TRUE WHERE id=?",slot);
        db.update("UPDATE orders SET service_snapshot=? WHERE id=?",catalog.get("NAME")+"："+catalog.get("DESCRIPTION"),order);
        return one("SELECT * FROM orders WHERE id=?",order);
    }
    public List<Map<String,Object>> orders(long actor) {
        var account=one("SELECT role FROM accounts WHERE id=?",actor);
        if("ADMIN".equals(account.get("ROLE"))) return list(ORDER_SELECT+"ORDER BY o.created_at DESC");
        return list(ORDER_SELECT+("OWNER".equals(account.get("ROLE"))?"WHERE o.owner_id=? ":"WHERE s.sitter_id=? ")+"ORDER BY o.created_at DESC",actor);
    }
    public Map<String,Object> detail(long actor,String orderId) {
        var order=one(ORDER_SELECT+"WHERE o.id=?",orderId); authorize(actor,order); return order;
    }
    public void authorize(long actor,Map<String,Object> order) {
        String role=(String)one("SELECT role FROM accounts WHERE id=?",actor).get("ROLE");
        if(!"ADMIN".equals(role)&&((Number)order.get("OWNER_ID")).longValue()!=actor&&((Number)order.get("SITTER_ID")).longValue()!=actor)
            throw new ResponseStatusException(FORBIDDEN,"无权查看此订单");
    }
    @Transactional public Map<String,Object> transition(long actor,String orderId,String action,String note) {
        var initial=one("SELECT * FROM orders WHERE id=?",orderId);
        var slot=one("SELECT * FROM slots WHERE id=? FOR UPDATE",initial.get("SLOT_ID"));
        var order=one("SELECT * FROM orders WHERE id=? FOR UPDATE",orderId);
        OrderState state=OrderState.valueOf((String)order.get("STATUS"));
        OrderAction command=OrderAction.parse(action);
        OrderState target;
        if(command.ownerAction()) {
            role(actor,"OWNER");
            if(((Number)order.get("OWNER_ID")).longValue()!=actor) throw new ResponseStatusException(FORBIDDEN,"只能处理自己的订单");
        } else {
            role(actor,"SITTER");
            if(((Number)slot.get("SITTER_ID")).longValue()!=actor) throw new ResponseStatusException(FORBIDDEN,"只能处理分配给自己的订单");
        }
        switch(command) {
            case PAY:
                require(state==OrderState.PENDING||state==OrderState.ACCEPTED,"当前状态不能支付");
                db.update("UPDATE orders SET payment_state='PAID',updated_at=CURRENT_TIMESTAMP WHERE id=?",orderId);
                return one("SELECT * FROM orders WHERE id=?",orderId);
            case CANCEL:
                target=OrderState.CANCELLED;
                if(state==target) return order;
                require(state==OrderState.PENDING||state==OrderState.ACCEPTED,"当前状态不能取消");
                require(BookingService.time(slot.get("STARTS_AT")).isAfter(OffsetDateTime.now()),"服务开始后不能取消，请提交投诉联系管理员");
                break;
            case REJECT: target=OrderState.REJECTED; if(state==target) return order; require(state==OrderState.PENDING,"只能拒绝待接单预约"); break;
            case ACCEPT: target=OrderState.ACCEPTED; if(state==target) return order; require(state==OrderState.PENDING,"只能接受待接单预约"); require(BookingService.time(order.get("CREATED_AT")).plusHours(2).isAfter(OffsetDateTime.now())&&BookingService.time(slot.get("STARTS_AT")).isAfter(OffsetDateTime.now()),"接单已超时"); break;
            case START:
                target=OrderState.IN_PROGRESS; if(state==target) return order;
                require(state==OrderState.ACCEPTED,"接单后才能开始服务");
                require("PAID".equals(order.get("PAYMENT_STATE")),"主人完成模拟支付后才能开始服务");
                require(!BookingService.time(slot.get("STARTS_AT")).isAfter(OffsetDateTime.now()),"预约开始时间尚未到达"); break;
            case COMPLETE:
                target=OrderState.WAITING_CONFIRM; if(state==target) return order;
                require(state==OrderState.IN_PROGRESS,"开始服务后才能提交完成");
                require(!BookingService.time(slot.get("ENDS_AT")).isAfter(OffsetDateTime.now()),"服务时段结束后才能提交完成");
                require(db.queryForObject("SELECT COUNT(*) FROM service_records WHERE order_id=?",Integer.class,orderId)>0,"请先上传喂养记录"); break;
            case CONFIRM: target=OrderState.COMPLETED; if(state==target) return order; require(state==OrderState.WAITING_CONFIRM,"只能确认待验收订单"); break;
            default: throw new ResponseStatusException(BAD_REQUEST,"未知操作");
        }
        boolean release=target==OrderState.CANCELLED||target==OrderState.REJECTED;
        if(release) db.update("UPDATE slots SET booked=FALSE WHERE id=?",initial.get("SLOT_ID"));
        String legacy=release?"CANCELLED":target==OrderState.COMPLETED?"COMPLETED":"ACCEPTED";
        db.update("UPDATE orders SET workflow_state=?,status=?,updated_at=CURRENT_TIMESTAMP,cancel_reason=?,payment_state=CASE WHEN ?=TRUE AND payment_state='PAID' THEN 'REFUNDED' ELSE payment_state END WHERE id=?",target.name(),legacy,release?note:"",release,orderId);
        return one("SELECT * FROM orders WHERE id=?",orderId);
    }
    @Transactional public void expire(String orderId) {
        var initial=one("SELECT * FROM orders WHERE id=?",orderId);
        one("SELECT * FROM slots WHERE id=? FOR UPDATE",initial.get("SLOT_ID"));
        var order=one("SELECT * FROM orders WHERE id=? FOR UPDATE",orderId);
        if(!"PENDING".equals(order.get("STATUS"))) return;
        if(BookingService.time(order.get("CREATED_AT")).plusHours(2).isAfter(OffsetDateTime.now())&&BookingService.time(one("SELECT * FROM slots WHERE id=?",initial.get("SLOT_ID")).get("STARTS_AT")).isAfter(OffsetDateTime.now())) return;
        db.update("UPDATE orders SET workflow_state='EXPIRED',status='CANCELLED',cancel_reason='接单超时',payment_state=CASE WHEN payment_state='PAID' THEN 'REFUNDED' ELSE payment_state END,updated_at=CURRENT_TIMESTAMP WHERE id=?",orderId);
        db.update("UPDATE slots SET booked=FALSE WHERE id=?",initial.get("SLOT_ID"));
    }
}
