package com.petsitting.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class AuthService {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final com.petsitting.directory.DirectoryMapper directory;
    public AuthService(JdbcTemplate db, PasswordEncoder passwords, JwtEncoder encoder, com.petsitting.directory.DirectoryMapper directory) {
        this.db = db; this.passwords = passwords; this.encoder = encoder; this.directory=directory;
    }
    public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserView user) {}
    private String normalize(String username) { return username.toLowerCase(Locale.ROOT); }
    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(BAD_REQUEST, "密码的 UTF-8 长度不能超过 72 字节");
    }
    @Transactional
    public AuthResponse register(String username, String password, String displayName, String role) {
        if (!role.equals("OWNER") && !role.equals("SITTER"))
            throw new ResponseStatusException(BAD_REQUEST, "只能注册主人或喂养员账户");
        if (displayName.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "请输入昵称");
        validatePassword(password);
        long id = directory.nextAccountId();
        directory.advanceAccountId();
        db.update("INSERT INTO accounts(id,role) VALUES (?,?)", id, role);
        try {
            db.update("INSERT INTO credentials VALUES (?,?,?,?)", id, normalize(username), displayName.strip(), passwords.encode(password));
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(CONFLICT, "用户名已被注册");
        }
        return issue(user(id));
    }
    @Transactional
    public AuthResponse login(String username, String password) {
        validatePassword(password);
        var rows = com.petsitting.directory.DirectoryService.upper(db.queryForList("SELECT c.account_id,c.password_hash FROM credentials c JOIN accounts a ON a.id=c.account_id WHERE c.username=? AND a.enabled=TRUE", normalize(username)));
        // 对不存在的用户同样做 BCrypt 校验，避免快速返回暴露用户名。
        String hash = rows.isEmpty() ? "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy" : (String) rows.getFirst().get("PASSWORD_HASH");
        boolean valid = passwords.matches(password, hash);
        if (rows.isEmpty() || !valid) throw new ResponseStatusException(UNAUTHORIZED, "用户名或密码错误");
        return issue(user(((Number) rows.getFirst().get("ACCOUNT_ID")).longValue()));
    }
    private AuthResponse issue(UserView user) {
        Instant now = Instant.now(), expires = now.plusSeconds(7200);
        String tokenId = UUID.randomUUID().toString();
        db.update("DELETE FROM login_sessions WHERE expires_at < ?", OffsetDateTime.now());
        db.update("INSERT INTO login_sessions VALUES (?,?,?)", tokenId, user.id(), expires.atOffset(ZoneOffset.UTC));
        var claims = JwtClaimsSet.builder().issuer("pet-sitting-platform").subject(Long.toString(user.id()))
            .id(tokenId).issuedAt(now).expiresAt(expires).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", expires, user);
    }
    public UserView user(long id) {
        var users = db.query("SELECT a.id,a.role,a.enabled,c.username,c.display_name FROM accounts a JOIN credentials c ON c.account_id=a.id WHERE a.id=?",
            (rs, row) -> new UserView(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getString("role"),rs.getBoolean("enabled")), id);
        if (users.isEmpty()) throw new ResponseStatusException(UNAUTHORIZED, "账户不存在，请重新登录");
        return users.getFirst();
    }
    public List<UserView> users() {
        return db.query("SELECT a.id,a.role,a.enabled,c.username,c.display_name FROM accounts a JOIN credentials c ON c.account_id=a.id ORDER BY a.id",
            (rs, row) -> new UserView(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getString("role"),rs.getBoolean("enabled")));
    }
    @Transactional public void accountStatus(long actor,long id,boolean enabled) {
        String role=db.queryForObject("SELECT role FROM accounts WHERE id=?",String.class,actor);
        if(!"ADMIN".equals(role))throw new ResponseStatusException(FORBIDDEN,"只有管理员可以管理账户");
        if(actor==id)throw new ResponseStatusException(CONFLICT,"不能修改自己的账户状态");
        directory.nextAccountId(); // 串行化账户状态修改，保护最后一个管理员。
        var target=user(id);
        if(!enabled&&"ADMIN".equals(target.role())&&db.queryForObject("SELECT COUNT(*) FROM accounts WHERE role='ADMIN' AND enabled=TRUE",Integer.class)<=1)
            throw new ResponseStatusException(CONFLICT,"至少保留一位可用管理员");
        db.update("UPDATE accounts SET enabled=? WHERE id=?",enabled,id);
        if(!enabled)db.update("DELETE FROM login_sessions WHERE account_id=?",id);
    }
    public void logout(Jwt token) { db.update("DELETE FROM login_sessions WHERE token_id=? AND account_id=?", token.getId(), actor(token)); }
    public static long actor(Jwt token) { return Long.parseLong(token.getSubject()); }
}
