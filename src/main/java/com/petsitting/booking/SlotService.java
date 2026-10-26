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
public class SlotService {
    private final JdbcTemplate db;
    private final DirectoryService directory;
    private final BookingRepository repository;
    private String id() { return UUID.randomUUID().toString(); }
    private Map<String,Object> one(String sql,Object... args) { return repository.one(sql,args); }
    private List<Map<String,Object>> list(String sql,Object... args) { return repository.list(sql,args); }
    private void role(long actor,String expected) { directory.role(actor,expected); }
    private void lockAccount(long actor) { one("SELECT id FROM accounts WHERE id=? FOR UPDATE",actor); }
    private void require(boolean condition,String message) { BookingRepository.require(condition,message); }
    public SlotService(JdbcTemplate db,DirectoryService directory,BookingRepository repository) { this.db=db; this.directory=directory; this.repository=repository; }
    @Transactional public Map<String,Object> addSlot(long actor,OffsetDateTime start,OffsetDateTime end,int price) {
        role(actor,"SITTER");
        require(start.isAfter(OffsetDateTime.now())&&end.isAfter(start),"时段必须在未来且结束晚于开始");
        require(Duration.between(start,end).toMinutes()>=30&&Duration.between(start,end).toHours()<=4,"服务时长须为 30 分钟至 4 小时");
        require(price>=100&&price<=100000,"报价须为 1 至 1000 元");
        lockAccount(actor);
        var profile=one("SELECT * FROM sitter_profiles WHERE account_id=?",actor);
        require("APPROVED".equals(profile.get("STATUS")),"资料审核通过后才能发布时段");
        require(db.queryForObject("SELECT COUNT(*) FROM slots WHERE sitter_id=? AND starts_at < ? AND ends_at > ?",Integer.class,actor,end,start)==0,"时段与已有时段重叠，请预留路途时间");
        String slot=id();
        db.update("INSERT INTO slots(id,sitter_id,starts_at,ends_at,price_cents,region) VALUES (?,?,?,?,?,?)",slot,actor,start,end,price,"");
        return one("SELECT * FROM slots WHERE id=?",slot);
    }
    public List<Map<String,Object>> slots() {
        return list("SELECT s.*,c.display_name AS sitter_name FROM slots s JOIN sitter_profiles p ON p.account_id=s.sitter_id JOIN credentials c ON c.account_id=s.sitter_id JOIN accounts a ON a.id=s.sitter_id WHERE a.enabled=TRUE AND s.withdrawn=FALSE AND s.booked=FALSE AND s.starts_at > ? AND p.status='APPROVED' ORDER BY s.starts_at",OffsetDateTime.now());
    }
    public List<Map<String,Object>> ownSlots(long actor) { role(actor,"SITTER"); return list("SELECT * FROM slots WHERE sitter_id=? ORDER BY starts_at DESC",actor); }
    @Transactional public void deleteSlot(long actor,String slot) {
        role(actor,"SITTER"); lockAccount(actor);
        var window=one("SELECT * FROM slots WHERE id=? FOR UPDATE",slot);
        if(((Number)window.get("SITTER_ID")).longValue()!=actor) throw new ResponseStatusException(FORBIDDEN,"只能删除自己的时段");
        require(!Boolean.TRUE.equals(window.get("BOOKED")),"已预约时段不能删除");
        // 保留历史订单外键，撤下时段后不再出现在可预约列表。
        db.update("UPDATE slots SET withdrawn=TRUE WHERE id=?",slot);
    }
}
