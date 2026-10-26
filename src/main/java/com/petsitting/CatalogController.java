package com.petsitting;

import com.petsitting.auth.AuthService;
import com.petsitting.directory.DirectoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class CatalogController {
    private final BookingService bookings;private final DirectoryService directory;private final JdbcTemplate db;
    public CatalogController(BookingService bookings,DirectoryService directory,JdbcTemplate db){this.bookings=bookings;this.directory=directory;this.db=db;}
    public record ServiceContent(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=1000) String description,boolean enabled){}
    @GetMapping("/service-catalog") public Map<String,Object> get(){return bookings.one("SELECT * FROM service_catalog WHERE id='daily-care'");}
    @PutMapping("/admin/service-catalog") public Map<String,Object> save(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ServiceContent body){directory.role(AuthService.actor(jwt),"ADMIN");db.update("UPDATE service_catalog SET name=?,description=?,enabled=? WHERE id='daily-care'",body.name(),body.description(),body.enabled());return get();}
}
