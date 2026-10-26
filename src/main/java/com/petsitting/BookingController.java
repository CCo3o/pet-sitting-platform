package com.petsitting;

import jakarta.validation.Valid;
import com.petsitting.auth.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class BookingController {
    private final BookingService service;
    public BookingController(BookingService service) { this.service = service; }
    public record Pet(@NotBlank @Size(max=80) String name, @NotNull @Size(max=1000) String notes) {}
    public record Slot(@NotNull OffsetDateTime start, @NotNull OffsetDateTime end, @Positive int priceCents) {}
    public record Booking(@NotBlank String petId, @NotBlank String slotId, @NotBlank String addressId) {}
    public record Completion(@NotBlank @Size(max=2000) String note) {}
    @GetMapping("/health") public Map<String,String> health() { return Map.of("status", "UP"); }
    @PostMapping("/pets") public Map<String,Object> pet(@AuthenticationPrincipal Jwt identity, @Valid @RequestBody Pet body) { return service.addPet(AuthService.actor(identity), body.name(), body.notes()); }
    @GetMapping("/pets") public List<Map<String,Object>> pets(@AuthenticationPrincipal Jwt identity) { return service.pets(AuthService.actor(identity)); }
    @PostMapping("/slots") public Map<String,Object> slot(@AuthenticationPrincipal Jwt identity, @Valid @RequestBody Slot body) { return service.addSlot(AuthService.actor(identity), body.start(), body.end(), body.priceCents()); }
    @GetMapping("/slots") public List<Map<String,Object>> slots() { return service.slots(); }
    @PostMapping("/orders") public Map<String,Object> book(@AuthenticationPrincipal Jwt identity, @RequestHeader("Idempotency-Key") @NotBlank @Size(max=100) String key, @Valid @RequestBody Booking body) { return service.book(AuthService.actor(identity), body.petId(), body.slotId(), body.addressId(), key); }
    @GetMapping("/orders") public List<Map<String,Object>> orders(@AuthenticationPrincipal Jwt identity) { return service.orders(AuthService.actor(identity)); }
    @PostMapping("/orders/{id}/accept") public Map<String,Object> accept(@AuthenticationPrincipal Jwt identity, @PathVariable String id) { return service.transition(AuthService.actor(identity), id, "accept", ""); }
    @PostMapping("/orders/{id}/cancel") public Map<String,Object> cancel(@AuthenticationPrincipal Jwt identity, @PathVariable String id) { return service.transition(AuthService.actor(identity), id, "cancel", ""); }
    @PostMapping("/orders/{id}/complete") public Map<String,Object> complete(@AuthenticationPrincipal Jwt identity, @PathVariable String id, @Valid @RequestBody Completion body) { return service.transition(AuthService.actor(identity), id, "complete", body.note()); }
    @GetMapping("/orders/{id}/records") public List<Map<String,Object>> records(@AuthenticationPrincipal Jwt identity, @PathVariable String id) { return service.records(AuthService.actor(identity), id); }
    public record RecordBody(@NotBlank @Size(max=2000) String note,@NotNull @Size(max=6) List<@NotBlank String> photos){}
    public record ReviewBody(@Min(1) @Max(5) int rating,@NotBlank @Size(max=1000) String content){}
    @GetMapping("/sitter/slots") public List<Map<String,Object>> ownSlots(@AuthenticationPrincipal Jwt jwt){return service.ownSlots(AuthService.actor(jwt));}
    @DeleteMapping("/slots/{id}") public void deleteSlot(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){service.deleteSlot(AuthService.actor(jwt),id);}
    @GetMapping("/orders/{id}") public Map<String,Object> detail(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return service.detail(AuthService.actor(jwt),id);}
    @PostMapping("/orders/{id}/reject") public Map<String,Object> reject(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@Valid @RequestBody Completion body){return service.transition(AuthService.actor(jwt),id,"reject",body.note());}
    @PostMapping("/orders/{id}/pay") public Map<String,Object> pay(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return service.transition(AuthService.actor(jwt),id,"pay","");}
    @PostMapping("/orders/{id}/start") public Map<String,Object> start(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return service.transition(AuthService.actor(jwt),id,"start","");}
    @PostMapping("/orders/{id}/confirm") public Map<String,Object> confirm(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return service.transition(AuthService.actor(jwt),id,"confirm","");}
    @PostMapping("/orders/{id}/records") public Map<String,Object> record(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@Valid @RequestBody RecordBody body){return service.record(AuthService.actor(jwt),id,body.note(),body.photos());}
    @PostMapping("/orders/{id}/reviews") public Map<String,Object> review(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@Valid @RequestBody ReviewBody body){return service.review(AuthService.actor(jwt),id,body.rating(),body.content());}
    @GetMapping("/orders/{id}/reviews") public List<Map<String,Object>> reviews(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return service.reviews(AuthService.actor(jwt),id);}
    @PostMapping("/orders/{id}/complaints") public Map<String,Object> complain(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@Valid @RequestBody Completion body){return service.complain(AuthService.actor(jwt),id,body.note());}
    @GetMapping("/complaints") public List<Map<String,Object>> complaints(@AuthenticationPrincipal Jwt jwt){return service.complaints(AuthService.actor(jwt));}
    @PostMapping("/admin/complaints/{id}/resolve") public Map<String,Object> resolve(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@Valid @RequestBody Completion body){return service.resolve(AuthService.actor(jwt),id,body.note());}
    @GetMapping("/admin/stats") public Map<String,Object> stats(@AuthenticationPrincipal Jwt jwt){return service.stats(AuthService.actor(jwt));}
}
