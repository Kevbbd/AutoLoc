package fr.ece.autoloc.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Démonstration du TP", description = "Les trois routes du TP Spring Security : publique, authentifiée, réservée à ADMIN.")
@RestController
public class SecurityDemoController {

    @Operation(summary = "Route publique : 200 pour tout le monde")
    @GetMapping("/api/public/ping")
    public String publicPing() {
        return "Public endpoint is reachable";
    }

    @Operation(summary = "Route authentifiée : 401 sans compte, 200 pour USER et ADMIN")
    @GetMapping("/api/hello")
    public String hello() {
        return "Hello, authenticated user";
    }

    @Operation(summary = "Route ADMIN : 401 sans compte, 403 pour USER, 200 pour ADMIN")
    @GetMapping("/api/admin/report")
    public String adminReport() {
        return "Confidential admin report";
    }
}
