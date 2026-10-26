package com.petsitting.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

// 单实例开发 / 部署限速；扩展多实例时替换为网关或 Redis 的共享限流。
public class RequestRateLimiter extends OncePerRequestFilter {
    private record Window(long start,int count){}
    private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
    private final ObjectMapper json;
    public RequestRateLimiter(ObjectMapper json){this.json=json;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String path=request.getRequestURI();boolean auth=request.getMethod().equals("POST")&&(path.equals("/api/auth/login")||path.equals("/api/auth/register"));
        boolean agent=request.getMethod().equals("POST")&&path.startsWith("/api/agent/sessions/")&&path.endsWith("/messages");
        if(!auth&&!agent){chain.doFilter(request,response);return;}
        long now=System.currentTimeMillis();
        windows.entrySet().removeIf(entry->now-entry.getValue().start()>60000);
        if(windows.size()>10000){response.setStatus(429);return;}
        String key=request.getRemoteAddr()+":"+(auth?"auth":"agent");
        var current=windows.compute(key,(ignored,previous)->previous==null||now-previous.start()>60000?new Window(now,1):new Window(previous.start(),previous.count()+1));
        if(current.count()>(auth?60:10)){response.setStatus(429);response.setHeader("Retry-After","60");response.setContentType("application/json;charset=UTF-8");json.writeValue(response.getOutputStream(),Map.of("status",429,"message","请求过于频繁，请一分钟后重试"));return;}
        chain.doFilter(request,response);
    }
}
