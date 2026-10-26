package com.petsitting.agent;

import com.petsitting.BookingService;
import com.petsitting.directory.DirectoryService;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftService {
    private final BookingService bookings;
    private final DirectoryService directory;
    private final JdbcTemplate db;
    public DraftService(BookingService bookings,DirectoryService directory,JdbcTemplate db){this.bookings=bookings;this.directory=directory;this.db=db;}
    @Transactional public Map<String,Object> create(long actor,String session,String pet,String slot,String address) {
        directory.role(actor,"OWNER");
        var conversation=bookings.one("SELECT * FROM agent_sessions WHERE id=? AND owner_id=?",session,actor);
        var profile=bookings.one("SELECT * FROM pets WHERE id=?",pet); directory.owned(profile,actor);
        var location=bookings.one("SELECT * FROM addresses WHERE id=?",address); directory.owned(location,actor);
        var window=bookings.one("SELECT s.* FROM slots s JOIN sitter_profiles p ON p.account_id=s.sitter_id WHERE s.id=? AND p.status='APPROVED'",slot);
        bookings.require(!Boolean.TRUE.equals(window.get("WITHDRAWN"))&&!Boolean.TRUE.equals(window.get("BOOKED"))&&BookingService.time(window.get("STARTS_AT")).isAfter(OffsetDateTime.now()),"时段不可预约，请重新查询");

        String id=UUID.randomUUID().toString();
        var catalog=bookings.one("SELECT * FROM service_catalog WHERE id='daily-care'");
        bookings.require(Boolean.TRUE.equals(catalog.get("ENABLED")),"当前服务项目已暂停预约");
        db.update("INSERT INTO booking_drafts(id,owner_id,session_id,pet_id,slot_id,address_id,price_cents,expires_at) VALUES (?,?,?,?,?,?,?,?)",id,actor,session,pet,slot,address,window.get("PRICE_CENTS"),OffsetDateTime.now().plusMinutes(15));
        db.update("UPDATE booking_drafts SET service_snapshot=?,pet_snapshot=?,address_snapshot=? WHERE id=?",catalog.get("NAME")+"："+catalog.get("DESCRIPTION"),profile.get("NAME")+" / "+profile.get("SPECIES")+" / "+profile.get("NOTES"),location.get("CONTACT")+" / "+location.get("PHONE")+" / "+location.get("REGION")+" "+location.get("DETAIL"),id);
        return get(actor,id);
    }
    public Map<String,Object> get(long actor,String id) {
        return bookings.one("SELECT d.*,p.name AS pet_name,a.region,a.detail,s.starts_at,s.ends_at,c.display_name AS sitter_name FROM booking_drafts d JOIN pets p ON p.id=d.pet_id JOIN addresses a ON a.id=d.address_id JOIN slots s ON s.id=d.slot_id JOIN credentials c ON c.account_id=s.sitter_id WHERE d.id=? AND d.owner_id=?",id,actor);
    }
    @Transactional public Map<String,Object> confirm(long actor,String id) {
        directory.role(actor,"OWNER");
        var draft=bookings.one("SELECT * FROM booking_drafts WHERE id=? AND owner_id=? FOR UPDATE",id,actor);
        if(draft.get("ORDER_ID")!=null) return bookings.detail(actor,(String)draft.get("ORDER_ID"));
        bookings.require(BookingService.time(draft.get("EXPIRES_AT")).isAfter(OffsetDateTime.now()),"草稿已过期，请重新生成");
        bookings.one("SELECT id FROM accounts WHERE id=? FOR UPDATE",actor);
        var initial=bookings.one("SELECT * FROM slots WHERE id=?",draft.get("SLOT_ID"));
        bookings.one("SELECT id FROM accounts WHERE id=? FOR UPDATE",initial.get("SITTER_ID"));
        var slot=bookings.one("SELECT * FROM slots WHERE id=? FOR UPDATE",draft.get("SLOT_ID"));
        var catalog=bookings.one("SELECT * FROM service_catalog WHERE id='daily-care'");
        var pet=bookings.one("SELECT * FROM pets WHERE id=? FOR UPDATE",draft.get("PET_ID"));
        var location=bookings.one("SELECT * FROM addresses WHERE id=? FOR UPDATE",draft.get("ADDRESS_ID"));
        bookings.require((catalog.get("NAME")+"："+catalog.get("DESCRIPTION")).equals(draft.get("SERVICE_SNAPSHOT")),"服务内容已变化，请重新生成草稿");
        bookings.require((pet.get("NAME")+" / "+pet.get("SPECIES")+" / "+pet.get("NOTES")).equals(draft.get("PET_SNAPSHOT"))&&(location.get("CONTACT")+" / "+location.get("PHONE")+" / "+location.get("REGION")+" "+location.get("DETAIL")).equals(draft.get("ADDRESS_SNAPSHOT")),"宠物或地址资料已变化，请重新生成草稿");
        bookings.require(((Number)slot.get("PRICE_CENTS")).intValue()==((Number)draft.get("PRICE_CENTS")).intValue(),"报价已变化，请重新确认草稿");
        var order=bookings.book(actor,(String)draft.get("PET_ID"),(String)draft.get("SLOT_ID"),(String)draft.get("ADDRESS_ID"),"agent:"+id);
        db.update("UPDATE booking_drafts SET order_id=? WHERE id=?",order.get("ID"),id); return order;
    }
}
