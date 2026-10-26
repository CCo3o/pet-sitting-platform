package com.petsitting.auth;

import com.petsitting.directory.DirectoryMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Profile("prod")
public class ProductionAdmin implements ApplicationRunner {
    private final JdbcTemplate db;private final DirectoryMapper mapper;private final PasswordEncoder passwords;
    private final String username,password;
    public ProductionAdmin(JdbcTemplate db,DirectoryMapper mapper,PasswordEncoder passwords,@Value("${ADMIN_USERNAME:admin}") String username,@Value("${ADMIN_PASSWORD:}") String password){this.db=db;this.mapper=mapper;this.passwords=passwords;this.username=username;this.password=password;}
    @Override @Transactional public void run(ApplicationArguments args)throws Exception{
        try(var connection=db.getDataSource().getConnection()){if(!connection.getMetaData().getURL().startsWith("jdbc:mysql:"))throw new IllegalStateException("生产环境需要 MySQL 数据库");}
        if(!username.matches("[a-z0-9_]{3,32}")||password.length()<12||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalStateException("生产管理员需设置合法用户名及 12 字符以上密码（最多72字节）");
        long id=mapper.nextAccountId();
        var rows=db.queryForList("SELECT a.role FROM credentials c JOIN accounts a ON a.id=c.account_id WHERE c.username=?",username);
        if(!rows.isEmpty()){
            if(!"ADMIN".equals(com.petsitting.directory.DirectoryService.upper(rows.getFirst()).get("ROLE")))throw new IllegalStateException("初始化管理员用户名已被其他角色占用");
            return;
        }
        mapper.advanceAccountId();db.update("INSERT INTO accounts(id,role) VALUES (?,'ADMIN')",id);
        db.update("INSERT INTO credentials VALUES (?,?,?,?)",id,username,"平台管理员",passwords.encode(password));
    }
}
