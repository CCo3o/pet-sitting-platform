package com.petsitting.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }
    @Bean SecretKey jwtKey(@Value("${app.jwt.secret-base64:}") String configured,org.springframework.core.env.Environment environment) {
        byte[] bytes;
        if (configured.isBlank()) { if(environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod")))throw new IllegalStateException("生产环境必须设置 APP_JWT_SECRET_BASE64");bytes = new byte[32]; new SecureRandom().nextBytes(bytes); }
        else bytes = Base64.getDecoder().decode(configured);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT 密钥至少需要 32 字节");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKey key, JdbcTemplate db) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> session = token -> {
            try {
                long actor = Long.parseLong(token.getSubject());
                Integer count = db.queryForObject("SELECT COUNT(*) FROM login_sessions s JOIN credentials c ON c.account_id=s.account_id JOIN accounts a ON a.id=s.account_id WHERE a.enabled=TRUE AND s.token_id=? AND s.account_id=? AND s.expires_at > CURRENT_TIMESTAMP", Integer.class, token.getId(), actor);
                if (count != null && count == 1) return OAuth2TokenValidatorResult.success();
            } catch (NumberFormatException | NullPointerException ex) { /* 无效 subject */ }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "登录已失效", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("pet-sitting-platform"), session));
        return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, ObjectMapper json, JdbcTemplate db) throws Exception {
        http.addFilterBefore(new RequestRateLimiter(json),org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);
        http.csrf(csrf -> csrf.disable()) // Bearer token 通过 Authorization 请求头传递，不使用 cookie 身份。
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable())
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/health", "/api/auth/login", "/api/auth/register").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/slots").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, ex) -> error(response, json, 401, "请先登录，或重新登录"))
                .accessDeniedHandler((request, response, ex) -> error(response, json, 403, "当前角色无权访问")))
            .oauth2ResourceServer(o -> o
                .authenticationEntryPoint((request, response, ex) -> error(response, json, 401, "登录已失效，请重新登录"))
                .accessDeniedHandler((request, response, ex) -> error(response, json, 403, "当前角色无权访问"))
                .jwt(j -> j.jwtAuthenticationConverter(token -> {
                    String role = db.queryForObject("SELECT role FROM accounts WHERE id=?", String.class, AuthService.actor(token));
                    return new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                })));
        return http.build();
    }
    private static void error(HttpServletResponse response, ObjectMapper json, int status, String message) throws java.io.IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), Map.of("status", status, "message", message));
    }
}
