package com.petsitting.directory;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class DirectoryService {
    private final DirectoryMapper mapper;
    private final JdbcTemplate db;
    public DirectoryService(DirectoryMapper mapper, JdbcTemplate db) { this.mapper=mapper; this.db=db; }
    public static Map<String,Object> upper(Map<String,Object> source) {
        if (source==null) throw new ResponseStatusException(NOT_FOUND,"资源不存在");
        var row=new LinkedHashMap<String,Object>();
        source.forEach((key,value)->row.put(key.toUpperCase(Locale.ROOT),value)); return row;
    }
    public static List<Map<String,Object>> upper(List<Map<String,Object>> rows) { return rows.stream().map(DirectoryService::upper).toList(); }
    public void role(long actor,String role) {
        var rows=db.queryForList("SELECT role,enabled FROM accounts WHERE id=?",actor);
        if(rows.isEmpty()) throw new ResponseStatusException(UNAUTHORIZED,"账户不存在");
        if(!Boolean.TRUE.equals(upper(rows.getFirst()).get("ENABLED")))throw new ResponseStatusException(UNAUTHORIZED,"账户已停用");
        if(!role.equals(upper(rows.getFirst()).get("ROLE"))) throw new ResponseStatusException(FORBIDDEN,"当前角色无权操作");
    }
    public void owned(Map<String,Object> row,long actor) {
        if(((Number)row.get("OWNER_ID")).longValue()!=actor) throw new ResponseStatusException(FORBIDDEN,"只能操作自己的资料");
        if(Boolean.TRUE.equals(row.get("DELETED"))) throw new ResponseStatusException(NOT_FOUND,"资料已删除");
    }
    public List<Map<String,Object>> pets(long actor) { role(actor,"OWNER"); return upper(mapper.pets(actor)); }
    @Transactional public Map<String,Object> savePet(long actor,String id,DirectoryController.Pet body) {
        role(actor,"OWNER");
        if(id==null) { id=UUID.randomUUID().toString(); mapper.addPet(id,actor,body.name(),body.notes(),body.species(),body.breed(),body.ageMonths()); }
        else { owned(upper(mapper.pet(id)),actor); mapper.updatePet(id,actor,body.name(),body.notes(),body.species(),body.breed(),body.ageMonths()); }
        return upper(mapper.pet(id));
    }
    @Transactional public void deletePet(long actor,String id) { role(actor,"OWNER"); owned(upper(mapper.pet(id)),actor); mapper.deletePet(id,actor); }
    public List<Map<String,Object>> addresses(long actor) { role(actor,"OWNER"); return upper(mapper.addresses(actor)); }
    @Transactional public Map<String,Object> saveAddress(long actor,String id,DirectoryController.Address body) {
        role(actor,"OWNER");
        if(id==null) { id=UUID.randomUUID().toString(); mapper.addAddress(id,actor,body.contact(),body.phone(),body.region(),body.detail()); }
        else { owned(upper(mapper.address(id)),actor); mapper.updateAddress(id,actor,body.contact(),body.phone(),body.region(),body.detail()); }
        return upper(mapper.address(id));
    }
    @Transactional public void deleteAddress(long actor,String id) { role(actor,"OWNER"); owned(upper(mapper.address(id)),actor); mapper.deleteAddress(id,actor); }
    public Map<String,Object> profile(long actor) { role(actor,"SITTER"); var row=mapper.profile(actor); return row==null ? Map.of("STATUS","NOT_SUBMITTED") : upper(row); }
    @Transactional public Map<String,Object> saveProfile(long actor,DirectoryController.Profile body) {
        role(actor,"SITTER"); db.queryForObject("SELECT id FROM accounts WHERE id=? FOR UPDATE",Long.class,actor);
        if(mapper.profile(actor)==null) mapper.addProfile(actor,body.bio(),"",body.priceCents());
        else mapper.updateProfile(actor,body.bio(),"",body.priceCents());
        return profile(actor);
    }
    public List<Map<String,Object>> sitters() { return upper(mapper.sitters()); }
    public List<Map<String,Object>> profiles(long actor) { role(actor,"ADMIN"); return upper(mapper.profiles()); }
    @Transactional public Map<String,Object> audit(long actor,long sitter,DirectoryController.Audit body) {
        role(actor,"ADMIN"); upper(mapper.profile(sitter));db.queryForObject("SELECT id FROM accounts WHERE id=? FOR UPDATE",Long.class,sitter);
        var row=upper(mapper.profile(sitter));
        if(!"PENDING".equals(row.get("STATUS"))) throw new ResponseStatusException(CONFLICT,"只能审核待审核资料");
        mapper.audit(sitter,body.approved()?"APPROVED":"REJECTED",body.note()); return upper(mapper.profile(sitter));
    }
}
