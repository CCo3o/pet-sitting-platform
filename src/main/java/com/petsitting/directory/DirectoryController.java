package com.petsitting.directory;

import com.petsitting.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class DirectoryController {
    private final DirectoryService service;
    public DirectoryController(DirectoryService service){this.service=service;}
    public record Pet(@NotBlank @Size(max=80) String name,@NotNull @Size(max=1000) String notes,
        @NotNull @Pattern(regexp="CAT|DOG|OTHER") String species,@NotNull @Size(max=80) String breed,@Min(0) @Max(600) int ageMonths){}
    public record Address(@NotBlank @Size(max=40) String contact,@NotBlank @Pattern(regexp="[0-9+ -]{7,20}") String phone,@NotBlank @Size(max=80) String region,@NotBlank @Size(max=200) String detail){}
    public record Profile(@NotBlank @Size(max=1000) String bio,@Size(max=80) String region,@Min(100) @Max(100000) int priceCents){}
    public record Audit(boolean approved,@NotNull @Size(max=500) String note){}
    @PutMapping("/pets/{id}") public Map<String,Object> pet(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@Valid @RequestBody Pet body){return service.savePet(AuthService.actor(jwt),id,body);}
    @PostMapping("/pet-profiles") public Map<String,Object> pet(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Pet body){return service.savePet(AuthService.actor(jwt),null,body);}
    @DeleteMapping("/pets/{id}") public void deletePet(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){service.deletePet(AuthService.actor(jwt),id);}
    @GetMapping("/addresses") public List<Map<String,Object>> addresses(@AuthenticationPrincipal Jwt jwt){return service.addresses(AuthService.actor(jwt));}
    @PostMapping("/addresses") public Map<String,Object> address(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Address body){return service.saveAddress(AuthService.actor(jwt),null,body);}
    @PutMapping("/addresses/{id}") public Map<String,Object> address(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@Valid @RequestBody Address body){return service.saveAddress(AuthService.actor(jwt),id,body);}
    @DeleteMapping("/addresses/{id}") public void deleteAddress(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){service.deleteAddress(AuthService.actor(jwt),id);}
    @GetMapping("/sitters") public List<Map<String,Object>> sitters(){return service.sitters();}
    @GetMapping("/sitter/profile") public Map<String,Object> profile(@AuthenticationPrincipal Jwt jwt){return service.profile(AuthService.actor(jwt));}
    @PutMapping("/sitter/profile") public Map<String,Object> profile(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Profile body){return service.saveProfile(AuthService.actor(jwt),body);}
    @GetMapping("/admin/sitters") public List<Map<String,Object>> profiles(@AuthenticationPrincipal Jwt jwt){return service.profiles(AuthService.actor(jwt));}
    @PostMapping("/admin/sitters/{id}/audit") public Map<String,Object> audit(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody Audit body){return service.audit(AuthService.actor(jwt),id,body);}
}
