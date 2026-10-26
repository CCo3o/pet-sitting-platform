package com.petsitting.agent;

import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@Service
public class AgentQuotaService {
    private final JdbcTemplate db;
    public AgentQuotaService(JdbcTemplate db){this.db=db;}
    @Transactional public void reserve(long actor){
        LocalDate day=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        db.queryForObject("SELECT id FROM accounts WHERE id=? FOR UPDATE",Long.class,actor);
        var counts=db.queryForList("SELECT calls FROM agent_quotas WHERE owner_id=? AND quota_date=?",Integer.class,actor,day);
        int used=counts.isEmpty()?0:counts.getFirst();
        if(used>=50)throw new ResponseStatusException(TOO_MANY_REQUESTS,"今日预约助手调用次数已达上限");
        if(counts.isEmpty())db.update("INSERT INTO agent_quotas VALUES (?,?,1)",actor,day);
        else db.update("UPDATE agent_quotas SET calls=calls+1 WHERE owner_id=? AND quota_date=?",actor,day);
    }
}
