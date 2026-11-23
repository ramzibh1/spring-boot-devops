package tn.esprit.devops.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
@RestController
public class HealthController {
    @GetMapping("/")
    public Map<String,String> home() { return Map.of("application","spring-boot-devops","status","UP","message","Spring Boot backend is running"); }
    @GetMapping("/api/health")
    public Map<String,String> health() { return Map.of("status","UP"); }
}
