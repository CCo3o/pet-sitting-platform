package com.petsitting.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"dev", "test"})
public class DevAccounts implements ApplicationRunner {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    public DevAccounts(JdbcTemplate db, PasswordEncoder passwords) { this.db = db; this.passwords = passwords; }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        seed(1, "OWNER", "owner", "宠物主人");
        seed(2, "SITTER", "sitter", "喂养员");
        seed(3, "OWNER", "owner_two", "第二位主人");
        seed(4, "ADMIN", "admin", "平台管理员");
        if(db.queryForObject("SELECT COUNT(*) FROM sitter_profiles WHERE account_id=2",Integer.class)==0)
            db.update("INSERT INTO sitter_profiles(account_id,bio,region,price_cents,status) VALUES (2,'细心照顾猫狗，提供喂粮、换水、清理猫砂和照片记录。','示范区',5000,'APPROVED')");
        for(long owner:new long[]{1,3}) if(db.queryForObject("SELECT COUNT(*) FROM addresses WHERE owner_id=?",Integer.class,owner)==0)
            db.update("INSERT INTO addresses(id,owner_id,contact,phone,region,detail) VALUES (?,?,?,?,?,?)","demo-address-"+owner,owner,"演示主人","13800000000","示范区","演示街道 1 号（虚拟地址）");
    }
    private void seed(long id, String role, String username, String name) {
        if (db.queryForObject("SELECT COUNT(*) FROM accounts WHERE id=?", Integer.class, id) == 0)
            db.update("INSERT INTO accounts(id,role) VALUES (?,?)", id, role);
        if (db.queryForObject("SELECT COUNT(*) FROM credentials WHERE account_id=?", Integer.class, id) == 0)
            db.update("INSERT INTO credentials VALUES (?,?,?,?)", id, username, name, passwords.encode("PetDemo123!"));
    }
}
