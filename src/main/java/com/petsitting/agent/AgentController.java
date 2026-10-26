package com.petsitting.agent;

import com.petsitting.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class AgentController {
    private final RoleAgentService roles;private final AgentService agent;private final DraftService drafts;private final AgentTraceService traces;
    public AgentController(AgentService agent,DraftService drafts,AgentTraceService traces,RoleAgentService roles){this.roles=roles;this.agent=agent;this.drafts=drafts;this.traces=traces;}
    public record Message(@NotBlank @Size(max=2000) String message){}
    @GetMapping("/agent/status") public Map<String,Object> status(){return agent.status();}
    @PostMapping("/agent/sessions") public Map<String,Object> create(@AuthenticationPrincipal Jwt jwt){return agent.session(AuthService.actor(jwt));}
    @GetMapping("/agent/sessions") public List<Map<String,Object>> list(@AuthenticationPrincipal Jwt jwt){return agent.sessions(AuthService.actor(jwt));}
    @GetMapping("/agent/sessions/{id}") public Map<String,Object> get(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return agent.get(AuthService.actor(jwt),id);}
    @PostMapping("/agent/sessions/{id}/messages") public AgentDtos.ChatResponse chat(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@Valid @RequestBody Message body){return AgentDtos.ChatResponse.from(agent.chat(AuthService.actor(jwt),id,body.message()));}
    @GetMapping("/agent/drafts/{id}") public Map<String,Object> draft(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return drafts.get(AuthService.actor(jwt),id);}
    @PostMapping("/agent/drafts/{id}/confirm") public Map<String,Object> confirm(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return drafts.confirm(AuthService.actor(jwt),id);}
    @GetMapping("/admin/agent-metrics") public Map<String,Object> metrics(@AuthenticationPrincipal Jwt jwt){return agent.metrics(AuthService.actor(jwt));}
    @GetMapping("/agent/sessions/{id}/runs") public List<Map<String,Object>> runs(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return traces.sessionRuns(AuthService.actor(jwt),id);}
    @GetMapping("/agent/runs/{id}") public Map<String,Object> trace(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return traces.detail(AuthService.actor(jwt),id);}
    @GetMapping("/admin/agent-runs") public List<Map<String,Object>> recent(@AuthenticationPrincipal Jwt jwt){return traces.recent(AuthService.actor(jwt));}
    public record Confirmation(@NotBlank @Size(max=1000) String content){}
    @GetMapping("/agent/overview") public Map<String,Object> overview(@AuthenticationPrincipal Jwt jwt){return roles.overview(AuthService.actor(jwt));}
    @PostMapping("/agent/action-drafts/{id}/confirm") public Map<String,Object> confirmAction(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@Valid @RequestBody Confirmation body){return roles.confirm(AuthService.actor(jwt),id,body.content());}
}

